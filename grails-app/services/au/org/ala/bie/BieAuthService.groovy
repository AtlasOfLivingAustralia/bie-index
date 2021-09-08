package au.org.ala.bie

import au.org.ala.bie.util.Encoder
import grails.converters.JSON

class BieAuthService {

    def grailsApplication

    def checkApiKey(key) {
        // try the preferred api key store first
        def url = grailsApplication.config.security.apikey.serviceUrl + Encoder.escapeQuery(key)
        def conn = new URL(url).openConnection()
        if (conn.getResponseCode() == 200) {
            String resp = conn.content.text as String
            return JSON.parse(resp)
        } else {
            log.info "Rejected change using key ${key}"
            return [valid:false]
        }
    }
}
