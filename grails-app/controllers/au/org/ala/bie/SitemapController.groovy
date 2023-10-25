package au.org.ala.bie

class SitemapController {
    def index(Integer idx) {
        if (!grailsApplication.config.sitemap.enabled) {
            response.status = 404
            return
        }

        File index = new File(grailsApplication.config.sitemap.dir + '/sitemap.xml')
        if (!index.exists()) {
            response.status = 404
            return
        }

        response.contentType = "application/xml"

        if (idx == null) {
            // return sitemap index
            response.outputStream << index.newInputStream()
        } else {
            // return sitemap urls
            File part = new File(grailsApplication.config.sitemap.dir + '/sitemap' + idx + ".xml")
            if (!part.exists()) {
                response.status = 404
                return
            }
            response.outputStream << part.newInputStream()
        }
    }
}
