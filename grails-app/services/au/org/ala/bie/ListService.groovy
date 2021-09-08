package au.org.ala.bie

import au.org.ala.bie.util.Encoder
import groovy.json.JsonSlurper

/**
 * Interface to the list servers
 */
class ListService {
    def grailsApplication

    /**
     * Get the contents of a list
     *
     * @param uid The list UID
     * @param fields Additional fields to get from the list
     */
    def get(uid, List fields = []) {
        def url = Encoder.buildServiceUrl(grailsApplication.config.lists.service, grailsApplication.config.lists.items, uid)
        def slurper = new JsonSlurper()
        def json = slurper.parseText(url.getText('UTF-8'))
        return json.collect { item ->
            def result = [lsid: item.lsid, name: item.name ]
            fields.each { field ->
                def value = field ? item.kvpValues.find { it.key == field }?.get("value") : null
                if (value)
                    result.put(field, value)
            }
            result
        }
    }
}
