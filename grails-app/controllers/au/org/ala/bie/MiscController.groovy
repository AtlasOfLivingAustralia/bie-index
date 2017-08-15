package au.org.ala.bie

import grails.converters.JSON
import grails.converters.XML

import static grails.web.http.HttpHeaders.CONTENT_DISPOSITION
import static grails.web.http.HttpHeaders.LAST_MODIFIED

class MiscController {

    def speciesGroupService, indexService, bieAuthService, importService

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
                asJson([success: true, updatedTaxa: updatedTaxa])
            } catch (Exception e) {
                asJson([success: false, message: "Internal error occurred: " + e.getMessage() ])
            }
        } else {
            asJson([success: false, message: "Unauthorised access. Failed to update Image in Bie" ])
        }
    }

    private def asJson = { model ->
        response.setContentType("application/json;charset=UTF-8")
        model
    }


}
