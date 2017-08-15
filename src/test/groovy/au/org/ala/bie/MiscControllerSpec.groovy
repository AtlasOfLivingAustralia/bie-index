package au.org.ala.bie

import au.org.ala.bie.search.IndexFieldDTO
import grails.test.mixin.TestFor
import spock.lang.Specification

@TestFor(MiscController)
class MiscControllerSpec extends Specification {

    IndexService indexService

    def setup() {
        indexService = Mock(IndexService)
        controller.indexService = indexService
    }

    def cleanup() {
    }

    void "test GET /indexFields JSON response"() {
        given:
        def indexFieldDetails = [new IndexFieldDTO(name: 'a', dataType: 'b', indexed: true, stored: true, numberDistinctValues: null)].toSet()

        when:
        // Documented request.contentType = JSON_CONTENT_TYPE doesn't work, nor will setting the 'Accept' header will not work since the config file is not read in unit testing
        controller.response.format = 'json'
        controller.indexFields()

        then:
        1 * indexService.getIndexFieldDetails(null) >> indexFieldDetails
        response.contentType.startsWith(JSON_CONTENT_TYPE)
        response.json.length() == 1
        response.json[0].name == 'a'
        response.json[0].dataType == 'b'
        response.json[0].indexed == true
        response.json[0].stored == true
        response.json[0].numberDistinctValues == null
    }

}
