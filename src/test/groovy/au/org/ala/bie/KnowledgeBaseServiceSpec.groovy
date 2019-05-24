package au.org.ala.bie

import grails.converters.JSON
import grails.test.mixin.TestFor
import grails.test.mixin.integration.Integration
import spock.lang.Specification

/**
 * See the API for {@link grails.test.mixin.services.ServiceUnitTestMixin} for usage instructions
 */
@Integration
@TestFor(KnowledgeBaseService)
class KnowledgeBaseServiceSpec extends Specification {

    static List pages = []
    static String firstPageUrl = ""
    static String randomPageUrl = ""
    static Map randomPageMap = [:]
    static int randomNum

    def setupSpec() {
        pages = service.resources("")
        firstPageUrl = pages.get(0)
        Random rand = new Random()
        randomNum = rand.nextInt(101)
        randomPageUrl = pages.get(randomNum)
        randomPageMap = service.getResource(randomPageUrl)

        log.warn "resources is ${pages.size()} in size"
        log.warn "first page URL = ${firstPageUrl}"
        log.warn "random page URL = ${randomPageUrl}"
        log.warn "random page Map = ${randomPageMap}"
    }

    def cleanup() {
    }

    void "test resources should not be empty"() {
//        given:
//            setupData()
        expect:
            pages.size() > 1
    }

    void "test first page has expected domain"() {
//        given:
//            setupData()
        expect:
            firstPageUrl?.startsWith("https://support.ala.org.au")
    }

    void "test random page has expected domain"() {
//        given:
//            setupData()
        expect:
            randomPageUrl?.startsWith("https://support.ala.org.au")
    }

    void "test random page has body text"() {
//        given:
//            setupData()
        expect:
            randomPageMap.containsKey("id")
            randomPageMap.containsKey("title")
            randomPageMap.containsKey("body")
            randomPageMap.get("body").size() > 100
    }

}
