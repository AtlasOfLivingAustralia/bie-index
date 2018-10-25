package au.org.ala.bie

import au.org.ala.bie.util.Encoder
import groovy.json.JsonSlurper

import java.text.MessageFormat

/**
 * Interface to the spatial layers
 */
class LayerService {
    def grailsApplication

    /**
     * Get a list of available layers
     *
     * @return The list of available layers
     */
    List layers() {
        def url = Encoder.buildServiceUrl(grailsApplication.config.layers.service, grailsApplication.config.layers.layers)
        def slurper = new JsonSlurper()
        def json = slurper.parseText(url.getText('UTF-8'))
        return json
    }
    /**
     * Get the contents of a layer
     *
     * @param uid The layer UID
     *
     * @return The layer contents
     */
    def get(uid) {
        def url = Encoder.buildServiceUrl(grailsApplication.config.layers.service, grailsApplication.config.layers.layer, uid)
        def slurper = new JsonSlurper()
        def json = slurper.parseText(url.getText('UTF-8'))
        return json
    }

    /**
     * Get the regions associated with a layer and place it into a temporary file.
     *
     * @param uid The layer UID
     *
     * @return The gzipped CSV file containing the layer information
     */
    File getRegions(uid) {
        def tempFilePath = MessageFormat.format(grailsApplication.config.layers.temporaryFilePattern, uid)
        def url = Encoder.buildServiceUrl(grailsApplication.config.layers.service,  grailsApplication.config.layers.objects, uid)
        def file = new File(tempFilePath)
        def stream = file.newOutputStream()
        stream << url.openStream()
        stream.flush()
        stream.close()
        return file
    }
}
