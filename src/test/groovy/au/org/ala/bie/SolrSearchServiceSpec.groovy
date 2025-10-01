package au.org.ala.bie

import au.org.ala.bie.util.ConservationListsSource
import grails.testing.services.ServiceUnitTest
import org.apache.solr.client.solrj.SolrClient
import org.apache.solr.client.solrj.SolrQuery
import org.apache.solr.client.solrj.SolrRequest
import org.apache.solr.client.solrj.SolrServerException
import org.apache.solr.client.solrj.response.QueryResponse
import org.apache.solr.common.SolrDocumentList
import spock.lang.Specification
import spock.lang.Unroll

/**
 * See the API for {@link grails.test.mixin.services.ServiceUnitTestMixin} for usage instructions
 */
class SolrSearchServiceSpec extends Specification implements ServiceUnitTest<SolrSearchService> {

    SolrClient liveSolrClient
    def listService

    def setup() {
        liveSolrClient = Mock(SolrClient)
        listService = [conservationLists: { -> [] }] as Object

        service.liveSolrClient = liveSolrClient
        service.listService = listService
    }

    def cleanup() {
    }

    @Unroll
    def "test findByScientificName(#query, #exact, #incVern)"() {
        given:
        def documentList = new SolrDocumentList()
        documentList.setNumFound(0)
        documentList.setStart(0)

        def queryResponse = Mock(QueryResponse) {
            getResults() >> documentList
            getFacetFields() >> []
            getHeader() >> [:]
            getHighlighting() >> [:]
        }

        when:
        def result = service.findByScientificName(query, [], 0, 1, 'score', 'desc', exact, incVern)

        then:
        1 * liveSolrClient.query(_ as SolrQuery, _ as SolrRequest.METHOD) >> queryResponse
        result.query == solrQ
        //TODO Test generation of SearchResultsDTO from Query Response

        where:
        // TODO test a name that can't be parsed by NameParser too
        query      | exact | incVern || solrQ
        "Macropus rufus" | true  | true    || 'idxtype:TAXON AND (text:"macropus\\ rufus" OR commonName:"macropus\\ rufus" OR guid:"macropus\\ rufus" OR scientificName:"macropus\\ rufus" OR  text:"macropus\\ rufus")'
        "Macropus rufus" | true  | false   || 'idxtype:TAXON AND (text:"macropus\\ rufus" OR guid:"macropus\\ rufus" OR scientificName:"macropus\\ rufus")'
        "Macropus rufus" | false | true    || 'idxtype:TAXON AND (text:macropus\\ rufus OR commonName:macropus\\ rufus OR guid:macropus\\ rufus OR  text:macropus\\ rufus)'
        "Macropus rufus" | false | false   || 'idxtype:TAXON AND (text:macropus\\ rufus OR guid:macropus\\ rufus)'
    }

    def "test findByScientificName with Solr Exception"() {
        when:
        def result = service.findByScientificName('Macropus Rufus', [], 0, 1, 'score', 'desc', true, true)

        then:
        1 * liveSolrClient.query(_ as SolrQuery, _ as SolrRequest.METHOD) >> { throw new SolrServerException('Service Unavailable') }
        result.status == 'ERROR'
    }
}