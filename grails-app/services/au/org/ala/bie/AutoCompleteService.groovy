package au.org.ala.bie

import au.org.ala.bie.search.AutoCompleteDTO
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
     * @param queryString
     * @return
     */
    def auto(q, queryString){
        log.debug("auto called with q = " + q)
        def autoCompleteList = []
        def additionalParams = "&wt=json"

        if(queryString) {
            if (!q) {
                queryString = queryString.replaceFirst("q=", "q=*:*")
            } else if (q.trim() == "*") {
                queryString = queryString.replaceFirst("q=*", "q=*:*")
            } else if (q) {
                //remove the exist query param
                queryString = queryString.replaceAll("q\\=[\\w\\+ ]*", "")
                //append a wildcard to the search term
                queryString = queryString +
                        "&q=" + URLEncoder.encode(
                        "commonNameExact:\"" + q + "\"^10000000000" +
                        " OR commonName:\"" + q.replaceAll(" ","") + "\"^100000" +
                        " OR commonName:\"" + q + "\"^100000" +
                        " OR rk_genus:\"" + q.capitalize() + "\"" +
                        " OR exact_text:\"" + q + "\"" +
                        " OR auto_text:\"" + q + "\"" +
                        " OR auto_text:\"" + q + "*\"",
                "UTF-8")
            }
        } else {
            queryString = "q=*:*"
        }

        log.info(queryString)

        def queryUrl = grailsApplication.config.indexLiveBaseUrl + "/select?" + queryString + additionalParams

        def queryResponse = new URL(queryUrl).getText("UTF-8")
        def js = new JsonSlurper()
        def json = js.parseText(queryResponse)

        json.response.docs.each {
            autoCompleteList << createAutoCompleteFromIndex(it, q)
        }

        //sort by rank ID
        autoCompleteList = autoCompleteList.sort { it.rankID }

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
    private def createAutoCompleteFromIndex( doc, String value){

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

        scientificNames.add(doc.get("nameComplete"));

        autoDto.setScientificNameMatches(getHighlightedNames([doc.get("nameComplete")], value, "<b>", "</b>"));
        matchedNames.addAll(getHighlightedNames(scientificNames, value, "", ""));

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