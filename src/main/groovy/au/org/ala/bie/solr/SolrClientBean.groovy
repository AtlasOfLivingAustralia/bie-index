package au.org.ala.bie.solr

import grails.util.CacheEntry
import org.apache.solr.client.solrj.SolrClient
import org.apache.solr.client.solrj.impl.CloudSolrClient
import org.apache.solr.client.solrj.impl.ConcurrentUpdateSolrClient
import org.apache.solr.client.solrj.impl.HttpSolrClient

/**
 * Delgatable solr client
 * <p>
 * The actual solr client is lazily onstructed from the client parameters.
 * This allows easy
 * </p>
 *
 * @author Doug Palmer &lt;Doug.Palmer@csiro.au&gt;
 * @copyright Copyright &copy; 2018 Atlas of Living Australia
 */
class SolrClientBean {
    /** The actual client. */
    @Lazy
    @Delegate
    SolrClient client = { this.buildClient() }()
    /** The client type */
    ClientType clientType = ClientType.HTTP
    /** The client connection string */
    String connection = "http://localhost:8983"
    /** The queue size for updatable clients */
    int queueSize = 10
    /** The number of threads for updatable clients */
    int threadCount = 4

    /**
     * Default constructor
     */
    SolrClientBean() {
    }

    /**
     * Construct with parameters
     *
     * @param clientType The client type
     * @param connection The connection string
     * @param queueSize The queue size
     * @param threadCount The thread count
     */
    SolrClientBean(String clientType, String connection, int queueSize, int threadCount) {
        this.clientType = clientType
        this.connection = connection
        this.queueSize = queueSize
        this.threadCount = threadCount
    }

    /**
     * Build the actual client
     *
     * @return
     */
    SolrClient buildClient() {
        def builder
        switch (clientType) {
            case ClientType.UPDATE:
                builder = new ConcurrentUpdateSolrClient.Builder(connection)
                builder.withQueueSize(queueSize)
                builder.withThreadCount(threadCount)
                break;
            case ClientType.ZOOKEEPER:
                builder = new CloudSolrClient.Builder()
                builder.withZkHost(connection)
                break;
            default:
                builder = new HttpSolrClient.Builder(connection)
        }
        return builder.build()
    }

    void setClientType(String name) {
       clientType = ClientType.valueOf(name)
    }

    enum ClientType {
        HTTP,
        UPDATE,
        ZOOKEEPER
    }
}
