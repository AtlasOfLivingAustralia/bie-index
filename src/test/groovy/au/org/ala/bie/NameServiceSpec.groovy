package au.org.ala.bie

import com.github.tomakehurst.wiremock.junit.WireMockRule
import com.github.tomjankes.wiremock.WireMockGroovy
import grails.test.mixin.TestFor
import org.junit.Rule
import spock.lang.Specification

/**
 * See the API for {@link grails.test.mixin.services.ServiceUnitTestMixin} for usage instructions
 */
@TestFor(NameService)
class NameServiceSpec extends Specification {
    @Rule
    WireMockRule wireMockRule = new WireMockRule()

    def wireMockStub = new WireMockGroovy()

    def setup() {
       service.service = "http://localhost:8080"
    }

    def cleanup() {
    }

    void "test lookup 1"() {
        given:
        wireMockStub.stub {
            request {
                method "GET"
                urlPath "/api/searchByClassification"
                queryParameters {
                    scientificName {
                        equalTo "Dentimitrella austrina"
                    }
                }
            }
            response {
                status 200
                body "{\n" +
                        "  \"success\": true,\n" +
                        "  \"scientificName\": \"Dentimitrella austrina\",\n" +
                        "  \"scientificNameAuthorship\": \"(Gaskoin, 1851)\",\n" +
                        "  \"taxonConceptID\": \"https://biodiversity.org.au/afd/taxa/1bce496c-0fec-4917-b187-9652fb48264c\",\n" +
                        "  \"rank\": \"species\",\n" +
                        "  \"rankID\": 7000,\n" +
                        "  \"lft\": 142528,\n" +
                        "  \"rgt\": 142528,\n" +
                        "  \"matchType\": \"exactMatch\",\n" +
                        "  \"nameType\": \"SCIENTIFIC\",\n" +
                        "  \"kingdom\": \"Animalia\",\n" +
                        "  \"kingdomID\": \"https://biodiversity.org.au/afd/taxa/4647863b-760d-4b59-aaa1-502c8cdf8d3c\",\n" +
                        "  \"phylum\": \"Mollusca\",\n" +
                        "  \"phylumID\": \"https://biodiversity.org.au/afd/taxa/4fb59020-e4a8-4973-adca-a4f662c4645c\",\n" +
                        "  \"classs\": \"Gastropoda\",\n" +
                        "  \"classID\": \"https://biodiversity.org.au/afd/taxa/ab81c7fc-3fc3-4e54-b277-a12a1a9cd0d8\",\n" +
                        "  \"order\": \"Hypsogastropoda\",\n" +
                        "  \"orderID\": \"https://biodiversity.org.au/afd/taxa/5f404f17-e1d1-4015-b6dd-9630147d5480\",\n" +
                        "  \"family\": \"Columbellidae\",\n" +
                        "  \"familyID\": \"https://biodiversity.org.au/afd/taxa/b9df0bc1-709d-406c-b3af-34af9c31f43d\",\n" +
                        "  \"genus\": \"Dentimitrella\",\n" +
                        "  \"genusID\": \"https://biodiversity.org.au/afd/taxa/4a4b0d97-eb1d-4009-8758-0c77b02ece33\",\n" +
                        "  \"species\": \"Dentimitrella austrina\",\n" +
                        "  \"speciesID\": \"https://biodiversity.org.au/afd/taxa/1bce496c-0fec-4917-b187-9652fb48264c\",\n" +
                        "  \"speciesGroup\": [\n" +
                        "    \"Animals\",\n" +
                        "    \"Molluscs\"\n" +
                        "  ],\n" +
                        "  \"speciesSubgroup\": [\n" +
                        "    \"Gastropods, Slugs, Snails\"\n" +
                        "  ],\n" +
                        "  \"issues\": [\n" +
                        "    \"noIssue\"\n" +
                        "  ]\n" +
                        "}"
                headers {
                    "Content-Type" "application/json"
                }
            }
        }
        when:
        def result = service.search("Dentimitrella austrina", null, null, null, null, null, null)
        then:
        result == "https://biodiversity.org.au/afd/taxa/1bce496c-0fec-4917-b187-9652fb48264c"
    }

    void "test lookup 2"() {
        given:
        wireMockStub.stub {
            request {
                method "GET"
                urlPath "/api/searchByClassification"
                queryParameters {
                    scientificName {
                        equalTo "Nothing nowhere"
                    }
                }
            }
            response {
                status 200
                body "{\n" +
                        "  \"success\": false,\n" +
                        "  \"issues\": [\n" +
                        "    \"noIssue\"\n" +
                        "  ]\n" +
                        "}"
                headers {
                    "Content-Type" "application/json"
                }
            }
        }
        when:
        def result = service.search("Nothing nowhere", null, null, null, null, null, null)
        then:
        result == null
    }

}
