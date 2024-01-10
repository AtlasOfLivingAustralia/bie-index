package au.org.ala.bie

import au.ala.org.ws.security.RequireApiKey
import au.org.ala.plugins.openapi.Path
import grails.converters.JSON
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.headers.Header
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.security.SecurityRequirement

import javax.ws.rs.Produces

import static io.swagger.v3.oas.annotations.enums.ParameterIn.HEADER
import static io.swagger.v3.oas.annotations.enums.ParameterIn.PATH

/**
 * A controller for managing imoort via web service rather than UI
 */
class ImportServicesController {
    def importService
    def jobService

    /**
     * Import all
     */

    @Operation(
            method = "GET",
            tags = "admin webservices",
            operationId = "Import all features via web service",
            summary = "Import all features via web service",
            security = [@SecurityRequirement(name = 'openIdConnect')],
            description = "Imports all information into the BIE offline index. The definition of \"all\" depends on the configuration of the service but usually includes importing dataset descriptions, spatial layers, taxonomies, etc. and then searching for images, denormalising synonyms and the like.",
            responses = [
                    @ApiResponse(
                            description = "JSON response indicating job status",
                            responseCode = "200",
                            headers = [
                                    @Header(name = 'Access-Control-Allow-Headers', description = "CORS header", schema = @Schema(type = "string")),
                                    @Header(name = 'Access-Control-Allow-Methods', description = "CORS header", schema = @Schema(type = "string")),
                                    @Header(name = 'Access-Control-Allow-Origin', description = "CORS header", schema = @Schema(type = "string"))
                            ]
                    )
            ]

    )
    @Path("/api/services/all")
    @Produces("application/json")
    @RequireApiKey(roles = ['ROLE_ADMIN'])
    def all() {
        def job = execute(
                "importDwca,importCollectory,deleteDanglingSynonyms,importLayers,importLocalities,importRegions,importHabitats,importHabitats," +
                        "importWordPressPages,importOccurrences,importConsevationSpeciesLists,buildVernacularSpeciesLists,buildLinkIdentifiers" +
                        "denormaliseTaxa,loadImages,",
                "admin.button.importall",
                { importService.importAll(importService.importSequence, false) })
        asJson(job.status())
    }

    /**
     * Get a job status
     */

    @Operation(
            method = "GET",
            tags = "admin webservices",
            operationId = "Get a job status",
            summary = "Get a job status",
            security = [@SecurityRequirement(name = 'openIdConnect')],
            description = "Get the status of an import job",
            parameters= [
                    @Parameter(
                        name = "id",
                        in = PATH,
                        description = "The job Id",
                        schema = @Schema(implementation = String),
                        example = "40eafb24-5fde-11ed-9b6a-0242ac120002",
                        required = true
                    )
            ],
            responses = [
                    @ApiResponse(
                            description = "JSON response indicating job status",
                            responseCode = "200",
                            headers = [
                                    @Header(name = 'Access-Control-Allow-Headers', description = "CORS header", schema = @Schema(type = "string")),
                                    @Header(name = 'Access-Control-Allow-Methods', description = "CORS header", schema = @Schema(type = "string")),
                                    @Header(name = 'Access-Control-Allow-Origin', description = "CORS header", schema = @Schema(type = "string"))
                            ]
                    )
            ]
    )
    @Path("/api/services/status/{id}")
    @Produces("application/json")
    @RequireApiKey(roles = ['ROLE_ADMIN'])
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
