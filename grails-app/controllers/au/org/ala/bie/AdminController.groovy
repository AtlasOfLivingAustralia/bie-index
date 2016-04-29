package au.org.ala.bie
import au.org.ala.web.AlaSecured
import grails.converters.JSON
import grails.converters.XML

@AlaSecured(value = "ROLE_ADMIN", redirectUri = "/")
class AdminController {

    def indexService

    def index() {}

    def indexFields() {
        def fields = indexService.getIndexFieldDetails(null)
        withFormat {
            xml { render fields as XML }
            '*' { render fields as JSON }
        }

    }
}
