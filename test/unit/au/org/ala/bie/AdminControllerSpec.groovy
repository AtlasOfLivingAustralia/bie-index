package au.org.ala.bie

import au.org.ala.bie.search.IndexFieldDTO
import grails.test.mixin.TestFor
import spock.lang.Specification

@TestFor(AdminController)
class AdminControllerSpec extends Specification {

    IndexService indexService

    def setup() {
        indexService = Mock(IndexService)
        controller.indexService = indexService
    }

    def cleanup() {
    }

    void "test GET /admin/indexFields JSON response"() {
        given:
        def indexFieldDetails = [new IndexFieldDTO(name: 'a', dataType: 'b', indexed: true, stored: true, numberDistinctValues: null)].toSet()

        when:
        request.addHeader('Accept', JSON_CONTENT_TYPE)
        controller.indexFields()

        then:
        1 * indexService.getIndexFieldDetails(null) >> indexFieldDetails
        response.json.length() == 1
        response.json[0].name == 'a'
        response.json[0].dataType == 'b'
        response.json[0].indexed == true
        response.json[0].stored == true
        response.json[0].numberDistinctValues == null
    }

}
