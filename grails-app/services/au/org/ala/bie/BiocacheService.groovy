package au.org.ala.bie

import au.org.ala.bie.search.IndexDocType
import au.org.ala.bie.util.Encoder
import grails.converters.JSON
import grails.transaction.Transactional
import groovy.json.JsonSlurper

/**
 * Biocache service API
 */
class BiocacheService {
    def grailsApplication

    /** Separator for multi-guid queries */
    static SEPARATOR = ','
    /** Default encoding */
    static ENCODING = "UTF-8"

    /**
     * Get counts via taxon guids.
     * <p>
     * If the connection to the biocache fails, then an empty map is returned.
     * </p>
     * <p>
     * A standard filter query is applied to the
     *
     * @param guids The list of guids to get
     * @param fq And filter queries
     *
     * @return A map of guid -> count
     *
     */
    def counts(List guids, List fq = []) {
        if (!guids)
            return [:]
        def slurper = new JsonSlurper()
        def guidParam = guids.join(SEPARATOR)
        def url = Encoder.buildServiceUrl(grailsApplication.config.biocache.service, grailsApplication.config.biocache.occurrenceCount.path, guidParam, SEPARATOR, grailsApplication.config.biocache.occurrenceCount.filterQuery)
        if (fq) {
            def fqParam = fq?.collect({ "fq=${Encoder.escapeQuery(it)}" }).join('&')
            url = new URL(url.toExternalForm() + '&' + fqParam)
        }
        def conn = url.openConnection()
        try {
            conn.setRequestMethod("GET")
            conn.setDoOutput(false)
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            conn.setRequestProperty("Accept-Charset", ENCODING);
            return slurper.parse(conn.inputStream, ENCODING)
        } catch (SocketTimeoutException e) {
            log.error("Timed out calling web service. URL= ${url}.", e)
            return [:]
        } catch (Exception e) {
            log.error("Failed calling web service. ${e.getMessage()} URL= ${url}. statusCode: ${conn?.responseCode} detail: ${conn?.errorStream?.text}", e)
            return [:]
        }
    }

    /**
     * Search the biocache for matching occurrences
     *
     * @param q The query
     * @param fq Filter queries (empty by default)
     * @param facets Any facets (emopty by default)
     * @param rows The number of rows to return (10 buy default)
     * @param start The start position (0 by default)
     * @param sort The sort field (null by default)
     * @param dir The sor direection ('asc' or 'desc')
     *
     * @return The results of the query
     */
    def search(String q, List fq = [], facets = [], rows = 10, start = 0, sort = null, dir = 'asc') {
        def slurper = new JsonSlurper()
        def query = []
        query << "q=${Encoder.escapeQuery(q)}"
        fq.each { query << "fq=${Encoder.escapeQuery(it)}" }
        if (!facets) {
            query << "facet=false"
        } else {
            query << "facet=true"
            query << "facets=${Encoder.escapeQuery(facets.join(","))}"
        }
        query << "pageSize=${rows}"
        query << "startIndex=${start}"
        if (sort) {
            query << "sort=${Encoder.escapeQuery(sort)}"
            query << "dir=${Encoder.escapeQuery(dir)}"
        }
        def url = grailsApplication.config.biocache.service + grailsApplication.config.biocache.search + '?' + query.join('&')
        def response = url.toURL().getText("UTF-8")
        return slurper.parseText(response)
    }
}
