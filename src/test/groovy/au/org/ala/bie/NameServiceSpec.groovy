package au.org.ala.bie

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import grails.testing.services.ServiceUnitTest
import spock.lang.Shared
import spock.lang.Specification

import static com.github.tomakehurst.wiremock.client.WireMock.*
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig

/**
 * See the API for {@link grails.test.mixin.services.ServiceUnitTestMixin} for usage instructions
 */
class NameServiceSpec extends Specification implements ServiceUnitTest<NameService> {

    @Shared
    WireMockServer wireMockServer

    def setupSpec() {
        wireMockServer = new WireMockServer(wireMockConfig().port(8080))
        wireMockServer.start()
        WireMock.configureFor("localhost", 8080)
    }

    def cleanupSpec() {
        wireMockServer?.stop()
    }

    def setup() {
        service.service = "http://localhost:8080"
        wireMockServer.resetAll()
    }

    def cleanup() {
    }

    void "test lookup 1"() {
        given:
        wireMockServer.stubFor(WireMock.get(urlPathEqualTo("/api/searchByClassification"))
                .withQueryParam("scientificName", equalTo("Dentimitrella austrina"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                {
                  "success": true,
                  "scientificName": "Dentimitrella austrina",
                  "scientificNameAuthorship": "(Gaskoin, 1851)",
                  "taxonConceptID": "https://biodiversity.org.au/afd/taxa/1bce496c-0fec-4917-b187-9652fb48264c",
                  "rank": "species",
                  "rankID": 7000,
                  "lft": 142528,
                  "rgt": 142528,
                  "matchType": "exactMatch",
                  "nameType": "SCIENTIFIC",
                  "kingdom": "Animalia",
                  "kingdomID": "https://biodiversity.org.au/afd/taxa/4647863b-760d-4b59-aaa1-502c8cdf8d3c",
                  "phylum": "Mollusca",
                  "phylumID": "https://biodiversity.org.au/afd/taxa/4fb59020-e4a8-4973-adca-a4f662c4645c",
                  "classs": "Gastropoda",
                  "classID": "https://biodiversity.org.au/afd/taxa/ab81c7fc-3fc3-4e54-b277-a12a1a9cd0d8",
                  "order": "Hypsogastropoda",
                  "orderID": "https://biodiversity.org.au/afd/taxa/5f404f17-e1d1-4015-b6dd-9630147d5480",
                  "family": "Columbellidae",
                  "familyID": "https://biodiversity.org.au/afd/taxa/b9df0bc1-709d-406c-b3af-34af9c31f43d",
                  "genus": "Dentimitrella",
                  "genusID": "https://biodiversity.org.au/afd/taxa/4a4b0d97-eb1d-4009-8758-0c77b02ece33",
                  "species": "Dentimitrella austrina",
                  "speciesID": "https://biodiversity.org.au/afd/taxa/1bce496c-0fec-4917-b187-9652fb48264c",
                  "speciesGroup": [
                    "Animals",
                    "Molluscs"
                  ],
                  "speciesSubgroup": [
                    "Gastropods, Slugs, Snails"
                  ],
                  "issues": [
                    "noIssue"
                  ]
                }
                """)))

        when:
        def result = service.search("Dentimitrella austrina", null, null, null, null, null, null)

        then:
        result == "https://biodiversity.org.au/afd/taxa/1bce496c-0fec-4917-b187-9652fb48264c"
    }

    void "test lookup 2"() {
        given:
        wireMockServer.stubFor(WireMock.get(urlPathEqualTo("/api/searchByClassification"))
                .withQueryParam("scientificName", equalTo("Nothing nowhere"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                {
                  "success": false,
                  "issues": [
                    "noIssue"
                  ]
                }
                """)))

        when:
        def result = service.search("Nothing nowhere", null, null, null, null, null, null)

        then:
        result == null
    }
}