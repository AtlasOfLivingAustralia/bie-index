package au.org.ala.bie.util

import groovy.json.JsonSlurper
import org.slf4j.LoggerFactory

/**
 * A source of conservation status information.
 * <p>
 * More description.
 * </p>
 *
 * @author Doug Palmer &lt;Doug.Palmer@csiro.au&gt;
 *
 * @copyright Copyright (c) 2016 CSIRO
 */
class ConservationListsSource {
    static log = LoggerFactory.getLogger(ConservationListsSource.class)

    def defaultSourceField = 'status'
    def lists = []

    ConservationListsSource(String url) {
        try {
            log.info("Loading conservation lists from: " + url)
            JsonSlurper slurper = new JsonSlurper()
            def config = slurper.parse(new URL(url))
            defaultSourceField = config?.defaultSourceField ?: 'status'
            lists = config?.lists ?: []
            log.info("Loaded " + lists.size() + " lists")
        } catch (Exception ex) {
            log.error("Unable to inifialise conservation status source from " + url, ex)
        }

    }
}
