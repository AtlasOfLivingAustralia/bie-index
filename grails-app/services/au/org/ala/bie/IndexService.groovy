package au.org.ala.bie

import au.org.ala.bie.search.IndexDocType
import grails.transaction.Transactional
import org.apache.solr.client.solrj.SolrServer
import org.apache.solr.client.solrj.impl.ConcurrentUpdateSolrServer
import org.apache.solr.common.SolrInputDocument

/**
 * The interface to SOLR based logic.
 */
class IndexService {

    def serviceMethod() {}

    def grailsApplication

    private static SolrServer solrServer = null

    /**
     * Delete the supplied index doc type from the index.
     * @param docType
     * @return
     */
    def deleteFromIndex(IndexDocType docType){
        log.info("Deleting from index: " + docType.name() + "....")
        getSolrServer().deleteByQuery("idxtype:" + docType.name())
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
        getSolrServer().add(buffer)

        //commit
        solrServer.commit(true, false, true)

    }

    private SolrServer getSolrServer(){
        if(solrServer == null){
            log.info("Initialising connection to solr server: ${grailsApplication.config.indexOfflineBaseUrl}")
            solrServer = new ConcurrentUpdateSolrServer(grailsApplication.config.indexOfflineBaseUrl, 10, 4)
        }
        solrServer
    }

    private boolean isList(object) {
        [Collection, Object[]].any { it.isAssignableFrom(object.getClass()) }
    }
}
