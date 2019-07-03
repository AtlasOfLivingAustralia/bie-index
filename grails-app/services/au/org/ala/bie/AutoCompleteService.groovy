package au.org.ala.bie

import au.org.ala.bie.search.AutoCompleteDTO
import au.org.ala.bie.util.Encoder
import grails.converters.JSON
import groovy.json.JsonSlurper
import org.apache.solr.client.solrj.response.Group
import org.apache.solr.client.solrj.response.GroupCommand
import org.apache.solr.client.solrj.response.Suggestion
import org.apache.solr.client.solrj.util.ClientUtils

import java.util.regex.Pattern

/**
 * Provides auto complete services.
 */
class AutoCompleteService {

    def grailsApplication
    def indexService

    def serviceMethod() {}

    List auto(String q, String idxtype, String kingdom, Integer rows, List<Locale> locales){
        Boolean useLegacyAuto = grailsApplication.config.autocomplete.legacy as Boolean
        List results

        if (useLegacyAuto) {
            def fq = idxtype ? ["idxtype:${idxtype}"] : [p]
            results = autoLegacy(q, idxtype, rows)
        } else {
            results = autoSuggest(q, idxtype, kingdom, rows, locales)
        }

        results
    }

    /**
     * Autocomplete service. This relies on the /suggest service which should be configured
     * in SOLR in the files solrconfig.xml and schema.xml.
     *
     * @param q The query
     * @param idxtype A restriction on the type of result
     * @param rows The number of rows to retrieve
     * @param locales The prefrred locales to use for matching common names
     *
     * @return
     */
    List autoSuggest(String q, String idxtype, String kingdom, Integer rows, List<Locale> locales){
        log.debug("auto called with q = " + q)

        if (!q)
            q = "*"
        if (!rows)
            rows = 10
        def response = indexService.suggest(true, q, idxtype, rows * 2)
        List<Suggestion> suggestions = response.suggesterResponse.suggestions.inject([], { List s, String k, List<Suggestion> v ->
            s.addAll(v)
            s
        })
        suggestions.sort({ o1, o2 -> o2.weight - o1.weight })
        Set<String> seen = new HashSet<>(suggestions.size())
        List<AutoCompleteDTO> autoList = []
        def si = suggestions.iterator()
        while (autoList.size() < rows && si.hasNext()) {
            def suggest = si.next()
            def doc = indexService.getTaxonByGuid(suggest.payload, true)
            if (!doc)
                continue
            if (idxtype && !idxtype.equalsIgnoreCase(doc.idxtype))
                continue
            if (kingdom && !kingdom.equalsIgnoreCase(doc.rk_kingdom))
                continue
            def dto = createAutoCompleteFromIndex(doc, q)
            dto.matchedNames.each { name ->
                if (!seen.contains(name)) {
                    seen.add(name)
                    AutoCompleteDTO dtoc = dto.clone()
                    dtoc.matchedNames.remove(name)
                    dtoc.matchedNames.add(0, name)
                    autoList << dtoc
                }
            }
        }
        return autoList
    }

    /**
     * Legacy Autocomplete service. This uses the normal /select service.
     *
     * @param q Query
     * @param fq Filter query
     * @param rows Rows to return
     * @return
     */
    List autoLegacy(String q, List fq, Integer rows) {
        log.debug("auto called with q = " + q)

        def response = indexService.search(true, q, fq, [], 0, rows)
        def autoCompleteList = response?.results?.collect {
            createAutoCompleteFromIndex(it, q)
        }

        // sort by rank ID
        // code removed by Nick (2016-08-02) see issue #72 - boost query values now perform same function

        log.debug("results: " + autoCompleteList.size())
        autoCompleteList ? autoCompleteList : []
    }

    /**
     * Creates an auto complete DTO from the supplied result.
     * @param qr
     * @param doc
     * @param value
     * @return
     */
    private def createAutoCompleteFromIndex(Map doc, String value){
        log.debug "doc = ${doc as JSON}"
        def autoDto = new AutoCompleteDTO();
        autoDto.guid = doc.guid
        autoDto.name = doc.scientificName

        if(doc.acceptedConceptID){
            autoDto.guid = doc.acceptedConceptID
        }

        if(doc.commonName){
            autoDto.commonName =  doc.commonName.first()
        }

        autoDto.rankString = doc.rank
        autoDto.rankID = doc.rankID

        List<String> matchedNames = [] // temp list to stored matched names

        if(doc.commonName ){
            autoDto.setCommonNameMatches(getHighlightedNames(doc.commonName, value, "<b>", "</b>"));
            matchedNames.addAll(getHighlightedNames(doc.commonName, value, "", ""));
        }

        String[] name1 = new String[0];
        Object o = doc.get("scientificNameRaw");
        if(o != null){
            if(o instanceof String){
                name1 = ((String)o).split(",");
            }
            else if (o instanceof ArrayList){
                name1 = ((List<String>) o).toArray(name1);
            }
        }

        ArrayList<String> scientificNames = new ArrayList<String>();
        for(String name : name1){
            scientificNames.add(name);
        }

        String nc = doc.get("scientificName")
        if (nc != null) {
            scientificNames.add(nc);
            autoDto.setScientificNameMatches(getHighlightedNames([nc], value, "<b>", "</b>"));
        }

        if (scientificNames) {
            matchedNames.addAll(getHighlightedNames(scientificNames, value, "", ""));
        } else if (doc.doc_name) {
            matchedNames.addAll(getHighlightedNames(doc.doc_name, value, "", ""));
        }


        if(!matchedNames){
            matchedNames << autoDto.name
        }

        autoDto.matchedNames = matchedNames

        autoDto
    }

    /**
     * Applies a prefix and suffix to higlight the search terms in the
     * supplied list.
     *
     * NC: This is a workaround as I can not get SOLR highlighting to work for partial term matches.
     *
     * @param names
     * @param m
     * @return
     */
    private List<String> getHighlightedNames(names, java.util.regex.Matcher m, String prefix, String suffix){
        LinkedHashSet<String> hlnames = null;
        List<String> lnames = null;
        if(names != null){
            hlnames = new LinkedHashSet<String>();
            for(String name : names){
                String name1 = concatName(name.trim());
                m.reset(name1);
                if(m.find()){
                    //insert <b> and </b>at the start and end index
                    name = name.substring(0, m.start()) + prefix + name.substring(m.start(), m.end()) +
                            suffix + name.substring(m.end(), name.length());
                    hlnames.add(name);
                }
            }
            if(!hlnames.isEmpty()){
                lnames = new ArrayList<String>(hlnames);
                Collections.sort(lnames);
            } else {
                lnames = new ArrayList<String>();
            }
        }
        return lnames;
    }

    /**
     * if word highlight enabled then do the exact match, otherwise do the concat match
     *
     * @param names
     * @param term
     * @param prefix
     * @param suffix
     * @return
     */
    private List<String> getHighlightedNames(List<String> names, String term, String prefix, String suffix){
        LinkedHashSet<String> hlnames =null;
        List<String> lnames = null;
        String value = null;
        boolean isHighlight = false;

        //have word highlight
        if(prefix != null && suffix != null && prefix.trim().length() > 0 && suffix.trim().length() > 0 && term != null){
            value = cleanName(term.trim());
            isHighlight = true;
        } else {
            value = concatName(term);
        }

        Pattern p = Pattern.compile(value, Pattern.CASE_INSENSITIVE);
        java.util.regex.Matcher m = p.matcher(value);
        if(names != null){
            hlnames = new LinkedHashSet<String>();
            for(String name : names){
                String name1 = null;
                name = name.trim();
                if(isHighlight){
                    name1 = name;
                } else {
                    name1 = concatName(name);
                }
                m.reset(name1);
                if(m.find()){
                    //insert <b> and </b>at the start and end index
                    name = name.substring(0, m.start()) + prefix + name.substring(m.start(), m.end()) + suffix + name.substring(m.end(), name.length());
                    hlnames.add(name);
                }
            }
            if(!hlnames.isEmpty()){
                lnames = new ArrayList<String>(hlnames);
                Collections.sort(lnames);
            } else {
                lnames = new ArrayList<String>();
            }
        }
        return lnames;
    }

    private static String concatName(String name){
        String patternA = "[^a-zA-Z]";
        /* replace multiple whitespaces between words with single blank */
        String patternB = "\\b\\s{2,}\\b";

        String cleanQuery = "";
        if(name != null){
            cleanQuery = ClientUtils.escapeQueryChars(name);//.toLowerCase();
            cleanQuery = cleanQuery.toLowerCase();
            cleanQuery = cleanQuery.replaceAll(patternA, "");
            cleanQuery = cleanQuery.replaceAll(patternB, "");
            cleanQuery = cleanQuery.trim();
        }
        cleanQuery
    }

    private static String cleanName(String name){
        String patternA = "[^a-zA-Z]";
        /* replace multiple whitespaces between words with single blank */
        String patternB = "\\b\\s{2,}\\b";

        String cleanQuery = "";
        if(name != null){
            cleanQuery = ClientUtils.escapeQueryChars(name);//.toLowerCase();
            cleanQuery = cleanQuery.toLowerCase();
            cleanQuery = cleanQuery.replaceAll(patternA, " ");
            cleanQuery = cleanQuery.replaceAll(patternB, " ");
            cleanQuery = cleanQuery.trim();
        }
        cleanQuery
    }
}