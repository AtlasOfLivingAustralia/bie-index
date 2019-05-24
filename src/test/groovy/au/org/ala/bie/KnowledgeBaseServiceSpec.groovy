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
        expect:
            pages.size() > 1
    }

    void "test first page has expected domain"() {
        expect:
            firstPageUrl?.startsWith("https://support.ala.org.au")
    }

    void "test random page has expected domain"() {
        expect:
            randomPageUrl?.startsWith("https://support.ala.org.au")
    }

    void "test random page has body text"() {
        expect:
            randomPageMap.containsKey("id")
            randomPageMap.containsKey("title")
            randomPageMap.containsKey("body")
            randomPageMap.get("body").size() > 100
    }

}
