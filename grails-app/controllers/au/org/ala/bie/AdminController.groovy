package au.org.ala.bie
import au.org.ala.web.AlaSecured

@AlaSecured(value = "ROLE_ADMIN", redirectUri = "/")
class AdminController {
    def indexService

    def index() {
        [info: indexService.info()]
    }

    // Documented in openapi.yml, not migrating to annotations because it is not intended for external use.
    def indexFields() {
        redirect(controller: "misc", action: "indexFields") // shouldn't get triggered due UrlMappings
    }
}
