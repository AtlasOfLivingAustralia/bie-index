package au.org.ala.bie

import au.org.ala.bie.util.Encoder
import grails.converters.JSON
import groovy.json.JsonSlurper

/**
 * Interface to the collectory
 */
class CollectoryService {
    def grailsApplication

    def useOldCollectory = false

    /**
     * Get a list of available collectory resources of a specific type
     *
     * @param type The type of resource
     *
     * @return The list of available resoureces
     */
    def resources(String type) {
        def url = Encoder.buildServiceUrl(grailsApplication.config.collectory.service, grailsApplication.config.collectory.resources, type)
        def slurper = new JsonSlurper()
        def json = slurper.parseText(url.getText('UTF-8'))
        return json
    }

    /**
     * Get the contents of a collectory entry/resource
     *
     * @param url The resource url
     *
     * @return The entity description contents
     */
    def get(url) {
        def slurper = new JsonSlurper()
        def json = slurper.parseText(url.toURL().getText('UTF-8'))
        return json
    }

    def getBatch(List resources, entityType) {
        if (!useOldCollectory) {
            try {
                def url = Encoder.buildServiceUrl(grailsApplication.config.collectory.service, grailsApplication.config.collectory.find, entityType)
                def bytes = (resources.collect { it.uid } as JSON).toString().getBytes("UTF-8")

                HttpURLConnection conn = (HttpURLConnection) url.openConnection()
                conn.setRequestMethod("POST")
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("Content-Length", String.valueOf(bytes.length))
                conn.setDoOutput(true)
                conn.getOutputStream().write(bytes)

                def txt = conn.getInputStream().text
                def response = JSON.parse(txt)

                conn.disconnect()

                return response.collect { JSON.parse(it) }
            } catch (ignored) {
                useOldCollectory = true
            }
        }

        // fallback to old collectory compatible request
        resources.collect { get(it.uri) }
    }
}
