package au.org.ala.bie

import au.ala.org.ws.security.RequireApiKey
import au.org.ala.plugins.openapi.Path
import grails.converters.JSON
import grails.converters.XML
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.headers.Header
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import org.apache.http.HttpStatus

import javax.ws.rs.Produces

import static grails.web.http.HttpHeaders.CONTENT_DISPOSITION
import static grails.web.http.HttpHeaders.LAST_MODIFIED
import static io.swagger.v3.oas.annotations.enums.ParameterIn.HEADER
import static io.swagger.v3.oas.annotations.enums.ParameterIn.QUERY

class MiscController {

    def speciesGroupService, indexService, bieAuthService, importService, imageService, wikiUrlService

    def speciesGroups() {
        try {
            def details = speciesGroupService.configFileDetails()
            details.is.withStream { is ->
                response.contentLength = details.size
                response.contentType = 'application/json'
                // next line causes link to download file to downloads folder, is this needed? (NdR)
                response.setHeader(CONTENT_DISPOSITION, "attachment; filename=subgroups.json")
                response.setDateHeader(LAST_MODIFIED, details.lastModified)
                response.outputStream << is
            }
        } catch (FileNotFoundException e) {
            response.sendError(404)
        }
        return
    }

    // Documented in openapi.yml
    def ranks() {
        render importService.ranks() as JSON
    }

    def indexFields() {
        def fields = indexService.getIndexFieldDetails(null)

        withFormat {
            '*' { render fields as JSON }
            json { render fields as JSON }
            html { render fields as JSON }
            xml { render fields as XML }
        }

    }

    def updateImages() {

        def checkRequest = bieAuthService.checkApiKey(request.getHeader("Authorization"))

        if (checkRequest.valid) {
            try {
                // contain list of guids and images
                List<Map> preferredImagesList = request.getJSON()
                def updatedTaxa = importService.updateDocsWithPreferredImage(preferredImagesList)
                asJson(HttpStatus.SC_OK, [success: true, updatedTaxa: updatedTaxa])
            } catch (Exception e) {
                asJson(HttpStatus.SC_INTERNAL_SERVER_ERROR, [success: false, message: "Internal error occurred: " + e.getMessage() ])
            }
        } else {
            asJson(HttpStatus.SC_INTERNAL_SERVER_ERROR, [success: false, message: "Unauthorised access. Failed to update Image in Bie" ])
        }
    }


    /**
     * Do logouts through this app so we can invalidate the session.
     *
     * @param casUrl the url for logging out of cas
     * @param appUrl the url to redirect back to after the logout
     */
    def logout = {
        session.invalidate()
        redirect(url:"${params.casUrl}?url=${params.appUrl}")
    }


    private def asJson = { status, model ->
        response.status = status
        response.setContentType("application/json;charset=UTF-8")
        model as JSON
    }

    @Operation(
            method = "GET",
            tags = "admin webservices",
            operationId = "setImages",
            summary = "Set the preferred and hidden images for a taxon",
            security = [@SecurityRequirement(name = 'openIdConnect')],
            parameters= [
                    @Parameter(
                            name = "name",
                            in = QUERY,
                            description = "Scientific Name",
                            schema = @Schema(implementation = String),
                            required = true
                    ),
                    @Parameter(
                            name = "guid",
                            in = QUERY,
                            description = "Taxon ID",
                            schema = @Schema(implementation = String),
                            required = true
                    ),
                    @Parameter(
                            name = "prefer",
                            in = QUERY,
                            description = "Comma delimited preferred Image IDs",
                            schema = @Schema(implementation = String),
                            required = true
                    ),
                    @Parameter(
                            name = "hide",
                            in = QUERY,
                            description = "Comma delimited hidden Image IDs",
                            schema = @Schema(implementation = String),
                            required = true
                    ),
                    @Parameter(name = "Authorization", in = HEADER, schema = @Schema(implementation = String), required = true)
            ],
            responses = [
                    @ApiResponse(
                            responseCode = "200",
                            headers = [
                                    @Header(name = 'Access-Control-Allow-Headers', description = "CORS header", schema = @Schema(type = "String")),
                                    @Header(name = 'Access-Control-Allow-Methods', description = "CORS header", schema = @Schema(type = "String")),
                                    @Header(name = 'Access-Control-Allow-Origin', description = "CORS header", schema = @Schema(type = "String"))
                            ]
                    )
            ]
    )
    @Path("/api/setImages")
    @Produces("application/json")
    @RequireApiKey
    def setImages() {
        imageService.prefer(params.name, params.guid, params.prefer)
        imageService.hide(params.name, params.guid, params.hide)
    }


    @Operation(
            method = "GET",
            tags = "admin webservices",
            operationId = "setWikiUrl",
            summary = "Set the preferred wiki URL for a taxon",
            security = [@SecurityRequirement(name = 'openIdConnect')],
            parameters= [
                    @Parameter(
                            name = "name",
                            in = QUERY,
                            description = "Scientific Name",
                            schema = @Schema(implementation = String),
                            required = true
                    ),
                    @Parameter(
                            name = "guid",
                            in = QUERY,
                            description = "Taxon ID",
                            schema = @Schema(implementation = String),
                            required = true
                    ),
                    @Parameter(
                            name = "url",
                            in = QUERY,
                            description = "URL",
                            schema = @Schema(implementation = String),
                            required = true
                    ),
                    @Parameter(name = "Authorization", in = HEADER, schema = @Schema(implementation = String), required = true)
            ],
            responses = [
                    @ApiResponse(
                            responseCode = "200",
                            headers = [
                                    @Header(name = 'Access-Control-Allow-Headers', description = "CORS header", schema = @Schema(type = "String")),
                                    @Header(name = 'Access-Control-Allow-Methods', description = "CORS header", schema = @Schema(type = "String")),
                                    @Header(name = 'Access-Control-Allow-Origin', description = "CORS header", schema = @Schema(type = "String"))
                            ]
                    )
            ]
    )
    @Path("/api/setUrl")
    @Produces("application/json")
    @RequireApiKey
    def setWikiUrl() {
        wikiUrlService.add(params.name, params.guid, params.url)
    }
}
