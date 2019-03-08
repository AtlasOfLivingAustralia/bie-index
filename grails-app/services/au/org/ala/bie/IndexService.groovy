package au.org.ala.bie

import au.org.ala.bie.search.IndexDocType
import au.org.ala.bie.search.IndexFieldDTO
import au.org.ala.bie.util.Encoder
import grails.config.Config
import grails.core.support.GrailsConfigurationAware
import groovy.json.JsonSlurper
import org.apache.solr.client.solrj.SolrQuery
import org.apache.solr.client.solrj.SolrResponse
import org.apache.solr.client.solrj.response.QueryResponse
import org.apache.solr.client.solrj.response.SuggesterResponse
import org.apache.solr.common.SolrDocument
import org.apache.solr.common.SolrInputDocument
import org.apache.solr.common.params.CursorMarkParams
import org.apache.solr.common.params.ModifiableSolrParams
import org.apache.solr.common.params.SolrParams

/**
 * The interface to SOLR based logic.
 */
class IndexService implements GrailsConfigurationAware {
    def grailsApplication
    def liveSolrClient
    def offlineSolrClient
    def updatingLiveSolrClient

    // Configuration
    SolrQuery searchTemplate
    SolrQuery suggestTemplate
    List<String> searchFq
    String searchDefType
    List<String> searchQf

    @Override
    void setConfiguration(Config config) {
        def search = config.solr.search
        searchTemplate = new SolrQuery()
        search.fq.each { searchTemplate.addFilterQuery(it) }
        searchTemplate.set('defType', search.defType)
        searchTemplate.set('qf', search.qf.join(' '))
        search.bq.each { searchTemplate.add('bq', it) }
        searchTemplate.set('q.alt', search.qAlt)
        searchTemplate.set('boost', search.boost)
        if (search.hl.hl as Boolean) {
            searchTemplate.highlightFields = (search.hl.fl as String).split(',')
            searchTemplate.highlightSimplePre = search.hl.simple.pre
            searchTemplate.highlightSimplePost = search.hl.simple.post
        }
        def suggest = config.solr.suggest
        suggestTemplate = new SolrQuery()
    }

    def deleteFromIndexByQuery(query){
        log.info("Deleting from index: " + query + "....")
        offlineSolrClient.deleteByQuery(query)
        offlineSolrClient.commit()
        log.info("Deleted from index: " + query)
    }

    /**
     * Delete the supplied index doc type from the index.
     * @param docType
     * @return
     */
    def deleteFromIndex(IndexDocType docType){
        log.info("Deleting from index: " + docType.name() + "....")
        offlineSolrClient.deleteByQuery("idxtype:" + docType.name())
        log.info("Deleted from index: " + docType.name())
    }

    /**
     * Index the supplied batch of docs.
     * @param docType
     * @param docsToIndex
     * @param offline Use the offline index (defaults to true)
     */
    def indexBatch(List docsToIndex, online = false) throws Exception {
        def client = online ? updatingLiveSolrClient : offlineSolrClient
        def buffer = []

        //convert to SOLR input documents
        docsToIndex.each { map ->
            def solrDoc = new SolrInputDocument()
            map.each{ fieldName, fieldValue ->
                def boost = 1.0f
                if (fieldValue && Map.class.isAssignableFrom(fieldValue.getClass()) && fieldValue["boost"]) {
                    boost = fieldValue.boost
                    fieldValue.remove("boost")
                }
                if(isList(fieldValue)){
                    fieldValue.each {
                        solrDoc.addField(fieldName, it, (float) boost)
                    }
                } else {
                    solrDoc.addField(fieldName, fieldValue, (float) boost)
                }
            }
            buffer << solrDoc
        }

        //add
        client.add(buffer)
        log.debug "Doing SOLR commit for ${buffer.size()} docs"
        //commit
        client.commit(true, false, true)

    }

    public Set<IndexFieldDTO> getIndexFieldDetails(String... fields) throws Exception {
        ModifiableSolrParams params = new ModifiableSolrParams()
        params.set('qt', '/admin/luke')
        params.set('tr', 'luke.xsl')
        if(fields != null) {
            params.set('fl', fields)
            params.set('numTerms', '1')
        } else {
            params.set('numTerms', '0')
        }

        QueryResponse response = liveSolrClient.query(params)
        Set<IndexFieldDTO> results = lukeResponseToIndexFieldDTOs(response, fields != null)
        return results
    }

    /**
     * Get a document by guid.
     *
     * @param guid The document guid
     * @param online True if using the online index
     *
     * @return The document or null for not found
     */
    SolrDocument getTaxonByGuid(String guid, boolean online) {
        def query = new SolrQuery("guid:${ Encoder.escapeSolr(guid) }")

        query.addFilterQuery("idxtype:${IndexDocType.TAXON.name()}")
        query.setStart(0)
        query.setRows(1)
        def response = this.query(query, online)
        return response.results.numFound == 0 ? null : response.results.get(0)
    }

    /**
     * Make a query to the index
     *
     * @param query The query
     * @param online Use the online/offline index
     *
     * @return The solr, which are essentially key->value maps
     */
    QueryResponse query(SolrQuery query, boolean online) {
        def client = online ? liveSolrClient : offlineSolrClient

        return client.query(query)
    }

    /**
     * Query the index
     *
     * @param online Use the online index
     *
     * @param q The query
     * @param fq Any filter queries
     * @param rows The number of rows to get (default 10)
     * @param start The start position (default 0)
     * @param context Additional context parameters
     * @param sort sort field
     * @param dir Sort direction
     * @param cursor Cursor mark if paginating
     *
     * @return The query result
     */
    QueryResponse query(boolean online, String q, List fq = [], Integer rows = 10, Integer start = 0, String context = null, String sort = null, String dir = 'asc', String cursor = null) {
        def query = new SolrQuery(q)

        if (context)
            query.add(context(context))
        if (fq)
            fq.each { query.addFilterQuery(it) }
        if (rows)
            query.setRows(rows)
        if (start)
            query.setStart(start)
        if (sort) {
            query.sort = new SolrQuery.SortClause(sort, dir == 'desc' ? SolrQuery.ORDER.desc : SolrQuery.ORDER.asc)
        }
        if (cursor)
            query.set(CursorMarkParams.CURSOR_MARK_PARAM, cursor)
        return this.query(query, online)
    }

    /**
     * Get context from a context string in the form of solr params.
     *
     * @param context
     * @return
     */
    SolrParams context(String context) {
        if (!context)
            return null
        ModifiableSolrParams params = context.split('(?<!\\\\)&').inject(new ModifiableSolrParams(), { sedd, param ->
            def kv = param.split('=', 2)
            seed.add(kv[0], kv[1])
            sedd
        })
        return params
    }

    /**
     * Do a solr search with standard parameters
     *
     * @param online Use the online index
     * @param q The query
     * @param fqs A list of additional filter queries (empty by default)
     * @param facets A list of facets (empty by default)
     * @param start The start position
     * @param rows The number of rows to retrieve
     * @param sort The field to sort on
     * @param dir The sort direction
     *
     * @return
     */
    QueryResponse search(boolean online, String q, List fqs = [], List facets = [], Integer start = 0, Integer rows = 10, sort = null, dir = SolrQuery.ORDER.asc) {
        SolrQuery query = searchTemplate.getCopy()

        query.query = q
        if (!dir || !(dir instanceof SolrQuery.ORDER))
            dir = dir == 'desc' ? SolrQuery.ORDER.desc : SolrQuery.ORDER.asc
        fqs.each { query.addFilterQuery(it) }
        // Add query parameters
        if (facets) {
            query.facet = true
            query.facetMinCount = 1
            facets.each { query.addFacetField(it) }
        }
        if (start)
            query.start = start
        if (rows)
            query.rows = rows
        if (sort)
            query.sort = new SolrQuery.SortClause(sort, dir)
        return this.query(query, online)
    }

    /**
     * Query suggestions from the index
     *
     * @param online Use the online index
     * @param q The query
     * @param fq Any filter queries
     * @param rows The number of rows to get (default 10)
     * @param start The start position (default 0)
     * @param context Additional context parameters
     *
     * @return The suggestion response (results in groupResponse)
     */
    QueryResponse suggest(boolean online, String q, String idxtype, Integer rows = 10, Integer start = 0, String context = null) {
        def query = suggestTemplate.getCopy()

        query.setRequestHandler('/suggest')
        query.set('suggest.q', q)
        if (context)
            query.add(context(context))
        if (idxtype)
            query.set('suggest.cfq', idxtype)
        if (rows)
            query.set('suggest.count', rows)
        if (start)
            query.setStart(start)
        return this.query(query, online)
    }

    /**
     * Build the suggestion index
     *
     * @param online Use the online index
     *
     * @return The build response
     */
    QueryResponse buildSuggestIndex(boolean online) {
        def query = suggestTemplate.getCopy()

        query.setRequestHandler('/suggest')
        query.set('suggest.build', "true")
        return this.query(query, online)
    }

    private boolean isList(object) {
        [Collection, Object[]].any { it.isAssignableFrom(object.getClass()) }
    }

    /**
     * parses the response string from the service that returns details about the indexed fields
     * @param response
     * @param includeCounts
     * @return
     */
    private Set<IndexFieldDTO> lukeResponseToIndexFieldDTOs(QueryResponse response, boolean includeCounts) {
        Set<IndexFieldDTO> fieldList = includeCounts ? new LinkedHashSet<IndexFieldDTO>() : new TreeSet<IndexFieldDTO>()

        // This is actually a SOLR SimpleOrderedMap
        Iterable<Map.Entry<String,Object>> fields = (Iterable<Map.Entry<String,Object>>) response.response['fields']

        fieldList.addAll(fields.collect {
            def name = it.key
            String schema = it.value['schema']
            String type = it.value['type']
            String distinctString = it.value['distinct']
            Integer distinct
            if (distinctString) {
                try {
                    distinct = Integer.valueOf(distinctString)
                } catch (e) {
                    distinct = null
                }
            } else {
                distinct = null
            }
            new IndexFieldDTO(name: name, dataType: type, indexed: schema.contains('I'), stored: schema.contains('S'), numberDistinctValues: distinct)
        })

        return fieldList
    }
}
