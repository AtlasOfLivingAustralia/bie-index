package au.org.ala.bie.solr

import org.apache.solr.client.solrj.impl.CloudSolrClient
import org.apache.solr.client.solrj.impl.ConcurrentUpdateSolrClient
import org.apache.solr.client.solrj.impl.HttpSolrClient
import spock.lang.Ignore
import spock.lang.Specification

/**
 * Test cases for {@link SolrClientBean}
 *
 * @author Doug Palmer &lt;Doug.Palmer@csiro.au&gt;
 * @copyright Copyright &copy; 2018 Atlas of Living Australia
 */
class SolrClientBeanSpec extends Specification {
    def "create http client"() {
        given:
        def bean = new SolrClientBean()
        bean.clientType = 'HTTP'
        bean.connection = 'http://localhost:10228'

        when:
        def client = bean.client

        then:
            client != null
            client instanceof HttpSolrClient
    }

    def "create update client"() {
        given:
        def bean = new SolrClientBean()
        bean.clientType = 'UPDATE'
        bean.connection = 'http://localhost:10228'
        bean.queueSize = 4
        bean.threadCount = 10

        when:
        def client = bean.client

        then:
        client != null
        client instanceof ConcurrentUpdateSolrClient
    }

    @Ignore // Until we get a zookeeper setup
    def "create zookeeper client"() {
        given:
        def bean = new SolrClientBean()
        bean.clientType = 'ZOOKEEPER'
        bean.connection = 'http://localhost:10228'

        when:
        def client = bean.client

        then:
        client != null
        client instanceof CloudSolrClient
    }

    def "test delegation"() {
        given:
        def bean = new SolrClientBean()
        bean.clientType = 'HTTP'
        bean.connection = 'http://localhost:10228'
        when:
        def binder = bean.binder
        then:
        binder != null
    }

}
