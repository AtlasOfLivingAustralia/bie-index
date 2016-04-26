package au.org.ala.bie

import grails.test.mixin.TestFor
import org.springframework.messaging.core.MessageSendingOperations
import spock.lang.Specification

/**
 * See the API for {@link grails.test.mixin.services.ServiceUnitTestMixin} for usage instructions
 */
@TestFor(ImportService)
class ImportServiceSpec extends Specification {
    ImportService importService

    def setup() {
        importService = new ImportService()
        importService.grailsApplication = grailsApplication
        importService.brokerMessagingTemplate = Mock(MessageSendingOperations)
        importService.indexService = Mock(IndexService)
    }

    def cleanup() {
    }

    def testLoadImages() {
        when:
        grailsApplication.config.indexImages = true
        grailsApplication.config.biocache.solr.url = "http://ala-rufus.it.csiro.au/solr/biocache"
        def images = importService.indexImages()
        then:
        !images.isEmpty()

    }


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

        def images = importService.loadImages(false)
        then:
        !images.isEmpty()
    }

}
