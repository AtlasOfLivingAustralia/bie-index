package au.org.ala.bie

import grails.test.mixin.TestFor
import org.gbif.api.exception.UnparsableException
import org.gbif.api.vocabulary.NameType
import org.springframework.messaging.core.MessageSendingOperations
import spock.lang.Ignore
import spock.lang.Specification

/**
 * See the API for {@link grails.test.mixin.services.ServiceUnitTestMixin} for usage instructions
 */
@TestFor(ImportService)
class ImportServiceSpec extends Specification {
    ImportService importService

    def setup() {
        importService = new ImportService()
        importService.brokerMessagingTemplate = Mock(MessageSendingOperations)
        importService.indexService = Mock(IndexService)
    }

    def cleanup() {
    }

    def "test parser 1"() {
        when:
        def parser = importService.nameParser
        def pn = parser.parse("Acacia dealbata")
        then:
        pn.type == NameType.SCIENTIFIC
    }


    def "test parser 2"() {
        when:
        def parser = importService.nameParser
        def pn = parser.parse("Acacia dealbata x sussia")
        then:
        def ex = thrown UnparsableException
        ex.type == NameType.HYBRID
    }

    @Ignore("This can be used for debugging to trace the load images service")
    def testImportImage() {
        when:
        grailsApplication.config.indexOfflineBaseUrl = "http://localhost:8983/solr/bie-offline"
        grailsApplication.config.biocache.solr.url = "http://ala-rufus.it.csiro.au/solr/biocache"
        grailsApplication.config.speciesList.url = "http://lists.ala.org.au/ws/speciesListItems/"
        grailsApplication.config.speciesList.params = "?includeKVP=true"
        grailsApplication.config.imageLists = [
                [ drUid: "dr4778", imageId: "imageId" ]
        ]
        grailsApplication.config.imageRanks = [
                [ rank: "family", idField: null, nameField: "family" ],
                [ rank: "species", idField: "species_guid", nameField: "matched_name" ]
        ]
        grailsApplication.config.imageSources =  [
                [ drUid: "dr130", boost: 10 ]
        ]
        importService.setConfiguration(grailsApplication.config)

        def images = importService.loadImages(false)
        then:
        !images.isEmpty()
    }

}
