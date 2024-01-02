package au.org.ala.bie

import au.org.ala.bie.util.Encoder
import groovy.json.JsonSlurper
import org.apache.http.entity.ContentType
import grails.converters.JSON

/**
 * Interface to the list servers
 */
class ListService {
    def grailsApplication
    def webService

    /**
     * Get the contents of a list
     *
     * @param uid The list UID
     * @param fields Additional fields to get from the list
     */
    def get(uid, List fields = []) {
        boolean hasAnotherPage = true
        int max = 400
        int offset = 0

        def items = []

        while (hasAnotherPage) {
            def url = Encoder.buildServiceUrl(grailsApplication.config.lists.service, grailsApplication.config.lists.items, uid, max, offset)

            def slurper = new JsonSlurper()
            def json = slurper.parseText(url.getText('UTF-8'))
            items.addAll(json)

            hasAnotherPage = json.size() == max
            offset += max

        }

        return items.collect { item ->
            def result = [lsid: item.lsid, name: item.name]
            fields.each { field ->
                def value = field ? item.kvpValues.find { it.key == field }?.get("value") : null
                if (value)
                    result.put(field, value)
            }
            result
        }
    }

    def add(listDr, name, guid, extraField, extraValue) {
        def url = new URL(grailsApplication.config.lists.service + grailsApplication.config.lists.add)
        def query = [druid: listDr]
        def body = [guid: guid, rawScientificName: name]
        body[extraField] = extraValue
        webService.post(url.toString(), body, query, ContentType.APPLICATION_JSON, true, false, [:])
    }

    def remove(listDr, guid) {
        def url = new URL(grailsApplication.config.lists.service + grailsApplication.config.lists.remove)

        webService.get(url.toString(), [druid: listDr, guid: guid], ContentType.APPLICATION_JSON, true, false, [:])
    }

    def resources() {
        def lists = []
        boolean hasAnotherPage = true
        int max = 400
        int offset = 0

        while (hasAnotherPage) {
            def url = Encoder.buildServiceUrl(grailsApplication.config.lists.service, grailsApplication.config.lists.search, max, offset)

            def json = JSON.parse(url.getText('UTF-8')).lists
            lists.addAll(json)

            hasAnotherPage = json.size() == max
            offset += max
        }

        lists
    }
}
