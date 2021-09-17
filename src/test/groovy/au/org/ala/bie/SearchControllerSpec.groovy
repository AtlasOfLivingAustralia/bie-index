package au.org.ala.bie

import au.org.ala.bie.search.SearchResultsDTO
import grails.test.mixin.TestFor
import org.grails.web.json.JSONArray
import org.grails.web.json.JSONObject
import spock.lang.Specification

/**
 * See the API for {@link grails.test.mixin.web.ControllerUnitTestMixin} for usage instructions
 */
@TestFor(SearchController)
class SearchControllerSpec extends Specification {

    SearchService searchService
    SolrSearchService solrSearchService

    def setup() {
        searchService = Mock(SearchService)
        solrSearchService = Mock(SolrSearchService)

        controller.searchService = searchService
        controller.solrSearchService = solrSearchService
    }

    def cleanup() {
    }

    void "test GET /guid/batch returns 404 without any params"() {
        setup:
        request.addHeader('Accept', JSON_CONTENT_TYPE)

        when:
        controller.getSpeciesForNames()

        then:
        0 * searchService.getProfileForName(_)
        response.status == 404
    }

    void "test GET /guid/batch returns correct results for a number of q params"() {
        setup:
        // Documented request.contentType = JSON_CONTENT_TYPE doesn't work, nor will setting the 'Accept' header will not work since the config file is not read in unit testing
        controller.response.format = 'json'
        request.addParameter('q', q.toArray(new String[q.size()]))

        when:
        controller.getSpeciesForNames()

        then:
        q.size() * searchService.getProfileForName(_) >>> result
        response.contentType.startsWith(JSON_CONTENT_TYPE)
        response.json instanceof JSONObject
        q.every {
            response.json[it] instanceof JSONArray
            response.json[it].every {
                it.has('identifier')
                it.has('name')
                it.has('acceptedIdentifier')
                it.has('acceptedName')
            }
        }

        where:
        q          || result
        ['a']      || [ [ [ identifier: 'a', name: 'a', acceptedIdentifier: 'a', acceptedName: 'a'] ] ]
        ['a', 'b'] || [ [ [ identifier: 'a', name: 'a', acceptedIdentifier: 'a', acceptedName: 'a'] ], [ [ identifier: 'b', name: 'b', acceptedIdentifier: 'b', acceptedName: 'b'] ] ]
    }

    void "test POST /species/lookup/bulk returns 400 when body is not json"() {
        setup:
        request.method = 'POST'
        request.addHeader('Accept', JSON_CONTENT_TYPE)
        request.contentType = FORM_CONTENT_TYPE
        request.addParameter('vernacular', 'true')
        request.addParameter('names', ['a','b'].toArray(new String[2]))

        when:
        controller.speciesLookupBulk()

        then:
        response.status == 400
    }

    void "test POST /species/lookup/bulk"() {
        setup:
        request.method = 'POST'
        // Documented request.contentType = JSON_CONTENT_TYPE doesn't work, nor will setting the 'Accept' header will not work since the config file is not read in unit testing
        controller.response.format = 'json'
        request.contentType = JSON_CONTENT_TYPE
        request.json = [vernacular: vernacular, names: names]

        when:
        controller.speciesLookupBulk()

        then:
//        names.size() * solrSearchService.findByScientificName(_, _, _, _, _, _, _, vernacular) >> {
//            String guid, filter, start, limit, field, order, exact, includeVernacular ->
//                return new SearchResultsDTO(totalRecords: result.size(), searchResults: [result[guid]])
//        }
        names.size() * searchService.getLongProfileForName(_, _) >> {
            String name, boolean vernacular ->
                return [totalRecords: result.size(), searchResults: [result[name]]]
        }

        response.contentType.startsWith(JSON_CONTENT_TYPE)
        response.json instanceof JSONArray
        response.json.size() == names.size()

        where:
        vernacular  | names || result
        true        | ['a'] || [a: 'alpha']
        false       | ['a', 'b', 'c'] || [a: null, b: 'beta', 'c': 'gamma']
        false       | ['c'] || [c: null ]
    }
}
