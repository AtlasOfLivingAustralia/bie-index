package au.org.ala.bie

import grails.test.mixin.TestFor
import spock.lang.Specification

/**
 * See the API for {@link grails.test.mixin.services.ServiceUnitTestMixin} for usage instructions
 */
@TestFor(KnowledgeBaseService)
class KnowledgeBaseServiceSpec extends Specification {

    static List pages = []
    static String firstPageUrl = ""
    static String randomPageUrl = ""
    static Map firstPageMap = [:]
    static Map randomPageMap = [:]
    static int randomNum
    static Integer max = 10

    def setupSpec() {
        // call web service once only and store results in static vars
        pages = service.resources("", max)
        firstPageUrl = pages.get(0)
        firstPageMap = service.getResource(firstPageUrl)
        Random rand = new Random()
        randomNum = rand.nextInt(10)
        randomPageUrl = pages.get(randomNum)
        randomPageMap = service.getResource(randomPageUrl)

        log.info "resources is ${pages.size()} in size"
        log.info "first page URL = ${firstPageUrl}"
        log.info "random page URL = ${randomPageUrl}"
        log.info "random page Map = ${randomPageMap}"
    }

    def cleanup() {
    }

    void "test resources (List) should not be empty"() {
        expect:
            pages.size() > 9
    }

    void "test first page URL has expected domain"() {
        expect:
            firstPageUrl?.startsWith("https://support.ala.org.au")
    }

    void "test random page URL has expected domain"() {
        expect:
            randomPageUrl?.startsWith("https://support.ala.org.au")
    }

    void "test first page has body text"() {
        expect:
            firstPageMap.containsKey("id")
            firstPageMap.containsKey("title")
            firstPageMap.containsKey("body")
            firstPageMap.get("body").size() > 100
    }

    void "test random page has body text"() {
        expect:
            randomPageMap.containsKey("id")
            randomPageMap.containsKey("title")
            randomPageMap.containsKey("body")
            randomPageMap.get("body").size() > 100
    }

}
