package au.org.ala.bie

import au.org.ala.bie.util.Encoder
import grails.config.Config
import grails.core.support.GrailsConfigurationAware
import grails.transaction.Transactional
import groovy.json.JsonSlurper

class NameService implements GrailsConfigurationAware {
    String service

    @Override
    void setConfiguration(Config config) {
        this.service = config.getRequiredProperty("naming.service")
    }


    /**
     * Get a name match.
     * <p>
     * Fields other than the scientific name can be null but can be used,
     * if set, to make the match more acvcurtate.
     * </p>
     * <p>
     * Fuzzy mataches and higher order matches are excluded
     * </p>
     *
     * @param scientificName The scientific name
     * @param kingdom The kingdom
     * @param phylum The phylum
     * @param class_ The class
     * @param order The order
     * @param family The family
     * @param rank The rank
     * @param fields Additional fields to get from the list
     *
     * @return The lsid of the matched name or null for not found
     */
    def search(String scientificName, String kingdom, String phylum, String class_, String order, String family, String rank) {
        def query = "scientificName=" + Encoder.escapeQuery(scientificName);
        if (kingdom) {
            query = query + "&kingdom=" + Encoder.escapeQuery(kingdom)
        }
        if (phylum) {
            query = query + "&phylum=" + Encoder.escapeQuery(phylum)
        }
        if (class_) {
            query = query + "&class=" + Encoder.escapeQuery(class_)
        }
        if (order) {
            query = query + "&order=" + Encoder.escapeQuery(order)
        }
        if (family) {
            query = query + "&family=" + Encoder.escapeQuery(family)
        }
        if (rank) {
            query = query + "&rank=" + Encoder.escapeQuery(rank)
        }
        def url = new URL(this.service + "/api/searchByClassification?" + query)
        def slurper = new JsonSlurper()
        def json = slurper.parseText(url.getText('UTF-8'))
        if (!json.success)
            return null
        def matchType = json.matchType
        if (matchType == "fuzzyMatch" || matchType == "higherMatch")
            return null
        return json.taxonConceptID
     }
}
