package au.org.ala.bie

import com.google.common.io.Resources
import grails.test.mixin.TestFor
import org.apache.solr.client.solrj.SolrClient
import org.apache.solr.client.solrj.impl.XMLResponseParser
import org.apache.solr.client.solrj.response.QueryResponse
import spock.lang.Specification

/**
 * See the API for {@link grails.test.mixin.services.ServiceUnitTestMixin} for usage instructions
 */
@TestFor(IndexService)
class IndexServiceSpec extends Specification {

    SolrClient liveSolrClient

    void setup() {
        liveSolrClient = Stub(SolrClient)

        service.liveSolrClient = liveSolrClient
    }

    void cleanup() {
    }

    void "test getIndexFieldDetails"() {
        setup:
        def resource = Resources.getResource(resourceLocation)
        def queryResponse = new QueryResponse(new XMLResponseParser().processResponse(resource.newReader()), liveSolrClient)
        def rootNode = new XmlParser().parse(resource.newReader())
        def fields = rootNode.lst.find { it.@name == 'fields' }.lst
        liveSolrClient.query(_) >> queryResponse

        when:
        def result = service.getIndexFieldDetails(*names)

        then:
        result.size() == fields.size()
        names.every { name ->
            result.find { it.name == name }
        }

        where:
        resourceLocation | names
        'resources/luke-response.xml' | []
        'resources/luke-response-acceptedConceptID.xml' | ['acceptedConceptID']
    }

}
