package au.org.ala.bie

import au.org.ala.bie.search.IndexDocType
import au.org.ala.bie.search.IndexFieldDTO
import org.apache.solr.client.solrj.response.QueryResponse
import org.apache.solr.common.SolrInputDocument
import org.apache.solr.common.params.ModifiableSolrParams

/**
 * The interface to SOLR based logic.
 */
class IndexService {

    def liveSolrClient
    def offlineSolrClient

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
     */
    def indexBatch(List docsToIndex){

        def buffer = []

        //convert to SOLR input documents
        docsToIndex.each { map ->
            def solrDoc = new SolrInputDocument()
            map.each{ fieldName, fieldValue ->
                if(isList(fieldValue)){
                    fieldValue.each {
                        solrDoc.addField(fieldName, it)
                    }
                } else {
                    solrDoc.addField(fieldName, fieldValue)
                }
            }
            buffer << solrDoc
        }

        //add
        offlineSolrClient.add(buffer)

        //commit
        offlineSolrClient.commit(true, false, true)

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
