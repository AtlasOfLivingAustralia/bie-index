package au.org.ala.bie

import au.org.ala.bie.util.Encoder
import groovy.json.JsonSlurper

/**
 * Interface to the collectory
 */
class CollectoryService {
    def grailsApplication

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
}
