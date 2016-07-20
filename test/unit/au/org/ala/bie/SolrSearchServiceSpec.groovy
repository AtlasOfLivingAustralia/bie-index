package au.org.ala.bie

import au.org.ala.bie.util.ConservationListsSource
import com.google.common.io.Resources
import grails.test.mixin.TestFor
import org.apache.solr.client.solrj.SolrClient
import org.apache.solr.client.solrj.SolrServerException
import org.apache.solr.client.solrj.impl.XMLResponseParser
import org.apache.solr.client.solrj.response.QueryResponse
import spock.lang.Specification
import spock.lang.Unroll

/**
 * See the API for {@link grails.test.mixin.services.ServiceUnitTestMixin} for usage instructions
 */
@TestFor(SolrSearchService)
class SolrSearchServiceSpec extends Specification {

    SolrClient liveSolrClient
    ConservationListsSource conservationListsSource

    def setup() {
        liveSolrClient = Mock(SolrClient)
        conservationListsSource = Mock(ConservationListsSource)

        service.liveSolrClient = liveSolrClient
        service.conservationListsSource = conservationListsSource
    }

    def cleanup() {
    }

    @Unroll
    def "test findByScientificName(#query, #exact, #incVern)"() {
        setup:
        def resource = Resources.getResource(resourcePath)
        def queryResponse = new QueryResponse(new XMLResponseParser().processResponse(resource.newReader()), liveSolrClient)
//        def rootNode = new XmlParser().parse(resource.newReader())

        when:
        def result = service.findByScientificName(query, [], 0, 1, 'score', 'desc', exact, incVern)

        then:
        1 * liveSolrClient.query(_) >> queryResponse
        result.query == solrQ
        //TODO Test generation of SearchResultsDTO from Query Response

        where:
        // TODO test a name that can't be parsed by NameParser too
        // TODO generate XML files for each solrQ with appropriate start, limit, facets, etc.
        // At the moment, the resource is from the first solr query and doesn't include facets, limits, etc
        query      | exact | incVern | resourcePath                                       || solrQ
        "Macropus rufus" | true  | true    | 'resources/solr-response-macropus-rufus.xml' || 'idxtype:TAXON AND (text:"macropus\\ rufus" OR commonName:"macropus\\ rufus" OR guid:"macropus\\ rufus" OR scientificName:"macropus\\ rufus" OR  text:"macropus\\ rufus")'
        "Macropus rufus" | true  | false   | 'resources/solr-response-macropus-rufus.xml' || 'idxtype:TAXON AND (text:"macropus\\ rufus" OR guid:"macropus\\ rufus" OR scientificName:"macropus\\ rufus")'
        "Macropus rufus" | false | true    | 'resources/solr-response-macropus-rufus.xml' || 'idxtype:TAXON AND (text:macropus\\ rufus OR commonName:macropus\\ rufus OR guid:macropus\\ rufus OR  text:macropus\\ rufus)'
        "Macropus rufus" | false | false   | 'resources/solr-response-macropus-rufus.xml' || 'idxtype:TAXON AND (text:macropus\\ rufus OR guid:macropus\\ rufus)'
    }

    def "test findByScientificName with Solr Exception"() {
        setup:

        when:
        def result = service.findByScientificName('Macropus Rufus', [], 0, 1, 'score', 'desc', true, true)

        then:
        1 * liveSolrClient.query(_) >> { throw new SolrServerException('Service Unavailable')}
        result.status == 'ERROR'
    }
}
