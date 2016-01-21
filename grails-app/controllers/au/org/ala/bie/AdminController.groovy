package au.org.ala.bie
import au.org.ala.web.AlaSecured

@AlaSecured(value = "ROLE_ADMIN", redirectUri = "/")
class AdminController {

    def indexService

    def index() {}

    def indexFields() {
        respond indexService.getIndexFieldDetails(null)
    }

}
