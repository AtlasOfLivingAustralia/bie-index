package au.org.ala.bie

import au.org.ala.bie.search.AutoCompleteDTO
import grails.converters.JSON
import groovy.json.JsonSlurper
import org.apache.solr.client.solrj.util.ClientUtils

import java.util.regex.Pattern

/**
 * Provides auto complete services.
 */
class AutoCompleteService {

    def grailsApplication

    def serviceMethod() {}

    /**
     * Autocomplete service
     *
     * @param q
     * @param otherParams
     * @return
     */
    def auto(String q, String otherParams){
        log.debug("auto called with q = " + q)

        def autoCompleteList = []
        // TODO store param string in config var
        String qf = "qf=commonNameSingle^100+commonName^100+auto_text^100+text"
        String bq = "bq=taxonomicStatus:accepted^1000&bq=rankID:7000^500&bq=rankID:6000^100&bq=-scientificName:\"*+x+*\"^100"
        def additionalParams = "&defType=edismax&${qf}&${bq}&wt=json"
        String query = ""

        if (!q || q.trim() == "*") {
            query = otherParams + "&q=*:*"
        } else if (q) {
            // encode query (no fields needed due to qf params
            query = otherParams + "&q=" + URLEncoder.encode("" + q + "","UTF-8")
        }

        log.info "queryString = ${otherParams}"

        def queryUrl = grailsApplication.config.indexLiveBaseUrl + "/select?" + query + additionalParams
        log.debug "queryUrl = |${queryUrl}|"
        def queryResponse = new URL(queryUrl).getText("UTF-8")
        def js = new JsonSlurper()
        def json = js.parseText(queryResponse)

        json.response.docs.each {
            autoCompleteList << createAutoCompleteFromIndex(it, q)
        }

        // sort by rank ID
        // code removed by Nick (2016-08-02) see issue #72 - boost query values now perform same function

        log.debug("results: " + autoCompleteList.size())
        autoCompleteList
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