import org.apache.solr.client.solrj.impl.ConcurrentUpdateSolrServer
import org.apache.solr.client.solrj.impl.HttpSolrServer

// Place your Spring DSL code here
beans = {
    liveSolrServer(HttpSolrServer, application.config.indexLiveBaseUrl)
    offlineSolrServer(ConcurrentUpdateSolrServer, application.config.indexOfflineBaseUrl, 10, 4)
}
