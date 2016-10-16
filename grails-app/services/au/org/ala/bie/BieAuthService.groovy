package au.org.ala.bie

import grails.converters.JSON

class BieAuthService {

    def grailsApplication

    def checkApiKey(key) {
        // try the preferred api key store first
        def url = grailsApplication.config.security.apikey.serviceUrl + key
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
