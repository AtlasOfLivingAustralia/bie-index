package au.org.ala.bie

import au.ala.org.ws.security.RequireApiKey
import grails.converters.JSON

/**
 * A controller for managing imoort via web service rather than UI
 */
@RequireApiKey
class ImportServicesController {
    def importService
    def jobService

    /**
     * Import all
     */
    def all() {
        def job = execute(
                "importDwca,importCollectory,deleteDanglingSynonyms,importLayers,importLocalities,importRegions,importHabitats,importHabitats," +
                        "importWordPressPages,importOccurrences,importConsevationSpeciesLists,buildVernacularSpeciesLists,buildLinkIdentifiers" +
                        "denormaliseTaxa,loadImages,",
                "admin.button.importall",
                { importService.importAll() })
        asJson(job.status())
    }

    /**
     * Get a job status
     */
    def status() {
        def id = params.id
        def status = jobService.get(id)?.status() ?: notFoundStatus(id)
        asJson(status)
    }

    private def asJson = { model ->
        response.setContentType("application/json;charset=UTF-8")
        render (model as JSON)
    }

    private def execute(String type, String titleCode, Closure task) {
        def title = message(code: titleCode)
        def types = type.split(',') as Set
        def job = jobService.existing(types)
        if (job) {
            return job
        }
        job = jobService.create(types, title, task)
    }

    private def notFoundStatus(id) {
        return [success: false, active: false, id: id, lifecycle: 'ERROR', lastUpdated: new Date(), message: 'Not found']
    }
}
