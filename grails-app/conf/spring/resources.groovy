import org.apache.solr.client.solrj.impl.ConcurrentUpdateSolrClient
import org.apache.solr.client.solrj.impl.HttpSolrClient

// Place your Spring DSL code here
beans = {
    liveSolrClient(HttpSolrClient, application.config.indexLiveBaseUrl)
    offlineSolrClient(ConcurrentUpdateSolrClient, application.config.indexOfflineBaseUrl, 10, 4)
    updatingLiveSolrClient(ConcurrentUpdateSolrClient, application.config.indexLiveBaseUrl, 10, 4)
}
