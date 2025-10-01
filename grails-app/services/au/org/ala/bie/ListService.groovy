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
    def conservationListsSource

    // max dynamic list age in hours
    def MAX_DYNAMIC_LIST_AGE = 2
    def dynamicListAge = 0
    def dynamicList

    /**
     * Get the contents of a list
     *
     * @param uid The list UID
     * @param fields Additional fields to get from the list
     */
    def get(uid, List fields = []) {
        def items = []
        Boolean useListWs = grailsApplication.config.getProperty("lists.useListWs", Boolean, false)

        if (useListWs) {
            int pageSize = 1000
            int page = 1
            while (true) {
                def url = Encoder.buildServiceUrl(grailsApplication.config.lists.service, grailsApplication.config.lists.items, uid, pageSize, page)
                def response = fetchWithExtraHeaders(url)
                def json = response ? JSON.parse(response) : null

                if (!json || json.isEmpty()) {
                    break
                }

                items.addAll(json)
                page++
            }
        } else {
            boolean hasAnotherPage = true
            int max = 400
            int offset = 0

            while (hasAnotherPage) {
                def url = Encoder.buildServiceUrl(grailsApplication.config.lists.service, grailsApplication.config.lists.items, uid, max, offset)
                def response = fetchWithExtraHeaders(url)
                def json = response ? JSON.parse(response) : null
                items.addAll(json)

                hasAnotherPage = json.size() == max
                offset += max

            }
        }

        if (!items) {
            return []
        }

        return items.collect { item ->
            // item.lsid (lists.useListWs:false), item.classification?.taxonConceptID for matched entries, otherwise taxonID
            def result = [lsid: item.lsid ?: item.classification?.taxonConceptID ?: item.taxonID, name: item.name ?: item.scientificName]

            fields.each { String field ->
                def value
                if (useListWs) {
                    // v2 API removes spaces in property keys,so try both versions
                    def field2 = field ? field.replaceAll(' ', '_') : null
                    def value1 = field ? item?.classification?.find { it.key == field }?.value : null
                    def value2 = field ? item?.properties?.find { it.key == field }?.get("value") : null
                    def value3 = field2 ? item?.properties?.find { it.key == field2 }?.get("value") : null
                    value = value1 ?: value2 ?: value3
                } else {
                    value = field ? item.kvpValues.find { it.key == field }?.get("value") : null
                }
                if (value)
                    result.put(field, value)
            }
            result
        }
    }

    def add(listDr, name, guid, extraField, extraValue) {
        def headers = getUserAgentHeader()

        if (grailsApplication.config.lists.useListWs) {
            def query =
"""
mutation add {
    addSpeciesListItem(inputSpeciesListItem: {scientificName: "${name}", taxonID: "${guid}", speciesListID: "${listDr}", properties: { key:"${extraField}", value:"${extraValue}"}} ) { id }
}
"""

            webService.post(grailsApplication.config.lists.service + "/graphql", [query: query], null, ContentType.APPLICATION_JSON, true, false, headers)
        } else {
            def url = new URL(grailsApplication.config.lists.service + grailsApplication.config.lists.add)
            def query = [druid: listDr]
            def body = [guid: guid, rawScientificName: name]
            body[extraField] = extraValue
            webService.post(url.toString(), body, query, ContentType.APPLICATION_JSON, true, false, headers)
        }
    }

    def remove(listDr, guid) {
        if (grailsApplication.config.lists.useListWs) {
            // find all species list item ids for this guid
            def ids = findSpeciesListItemIds(listDr, guid)

            if (ids) {
                for (def id : ids) {
                    // delete the species list item
                    def query =
    """
    mutation delete {
        removeSpeciesListItem(id: "${id}") { id }
    }
    """
                    webService.post(grailsApplication.config.lists.service + "/graphql", [query: query], getUserAgentHeader())
                }
            }
        } else {
            def url = new URL(grailsApplication.config.lists.service + grailsApplication.config.lists.remove)
            webService.get(url.toString(), [druid: listDr, guid: guid], ContentType.APPLICATION_JSON, true, false, getUserAgentHeader())
        }
    }

    // Could use graphql filterSpeciesList query and loop those results instead of paging through the entire list
    def findSpeciesListItemIds(def listDr, def guid) {
        def foundIds = []

        def pageSize = 1000
        def page = 1
        while (true) {
            def items = webService.get(grailsApplication.config.lists.service + '/speciesListItems/' + listDr, [pageSize: pageSize, page: page],
                    ContentType.APPLICATION_JSON, true, false, getUserAgentHeader())?.resp
            page++

            if (!items) {
                break
            }

            for (def item : items) {
                // classification.taxonConceptID is the matching done by species-list
                // taxonID is the ID of the created item, just in case matching by species-list is not yet done
                if (item?.classification?.taxonConceptID == guid || item?.taxonID == guid) {
                    foundIds.add(item.id)
                }
            }
        }

        foundIds
    }

    def resources() {
        def lists = []

        if (grailsApplication.config.lists.useListWs) {
            int pageSize = 1000
            int page = 1
            while (true) {
                log.debug "Fetching page ${page} of lists"
                def url = Encoder.buildServiceUrl(grailsApplication.config.lists.service, grailsApplication.config.lists.search, pageSize, page)
                def response = fetchWithExtraHeaders(url)
                def json = JSON.parse(response)?.lists

                if (!json || json.isEmpty()) {
                    break
                }

                lists.addAll(json)
                page++
            }

        } else {
            boolean hasAnotherPage = true
            int max = 400
            int offset = 0

            while (hasAnotherPage) {
                def url = Encoder.buildServiceUrl(grailsApplication.config.lists.service, grailsApplication.config.lists.search, max, offset)
                def response = fetchWithExtraHeaders(url)
                def json = JSON.parse(response)?.lists

                if (!json || json.isEmpty()) {
                    break
                }

                lists.addAll(json)

                hasAnotherPage = json.size() == max
                offset += max
            }
        }

        lists
    }

    def dynamicConservationLists() {
        // return no dynamic lists when lists.conservation is not defined
        if (!grailsApplication.config.lists.conservation) {
            return []
        }

        // use cached copy
        if (System.currentTimeMillis() < dynamicListAge + MAX_DYNAMIC_LIST_AGE * 60 * 60 * 1000) {
            return dynamicList
        }

        def lists = []

        if (grailsApplication.config.lists.useListWs) {
            int pageSize = 1000
            int page = 1
            while (true) {
                def url = Encoder.buildServiceUrl(grailsApplication.config.lists.service, grailsApplication.config.lists.conservation, pageSize, page)
                def response = fetchWithExtraHeaders(url)
                def json = JSON.parse(response)?.lists

                if (!json || json.isEmpty()) {
                    break
                }

                lists.addAll(json)
            }
        } else {
            boolean hasAnotherPage = true
            int max = 400
            int offset = 0

            while (hasAnotherPage) {
                def url = Encoder.buildServiceUrl(grailsApplication.config.lists.service, grailsApplication.config.lists.conservation, max, offset)
                def response = fetchWithExtraHeaders(url)
                def json = JSON.parse(response)?.lists

                if (!json || json.isEmpty()) {
                    break
                }

                lists.addAll(json)

                hasAnotherPage = json.size() == max
                offset += max
            }
        }

        dynamicList = lists
        dynamicListAge = System.currentTimeMillis()

        lists
    }

    def conservationLists() {
        def lists = conservationListsSource.lists

        // if no lists are defined in conservation-lists.json, use default lists
        if (!lists) {
            lists = dynamicConservationLists().collect {
                // 2024-02-21 species-list uses id. While it has a dataResourceUid for migrated lists this is not in sync with collections
                [
                        uid  : it.id ?: it.dataResourceUid,
                        field: 'conservationStatus' + (it.id ?: it.dataResourceUid) + '_s',
                        term : 'conservationStatus' + (it.id ?: it.dataResourceUid),
                        label: it.listName ?: it.title
                ]
            }
        }

        lists
    }

    /**
     * Fetches content from a URL with browser-like headers to pass AWS WAF
     * @param url The URL to fetch (can be String or URL object)
     * @return The response text, or null if request fails
     */
    String fetchWithExtraHeaders(def url) {
        try {
            URL urlObj = url instanceof URL ? url : new URL(url.toString())
            def connection = urlObj.openConnection()
            def appName = grailsApplication.config.getProperty('info.app.name', String, 'bie-index')
            def appVersion = grailsApplication.config.getProperty('info.app.version', String, '3.1')
            log.debug("app.name: ${appName} | app.version: ${appVersion}")

            connection.setRequestProperty("User-Agent", "${appName}/${appVersion}")
            connection.setConnectTimeout(10000) // 10 seconds
            connection.setReadTimeout(30000)    // 30 seconds

            if (connection instanceof HttpURLConnection) {
                def httpConn = (HttpURLConnection) connection
                httpConn.connect()
                int responseCode = httpConn.responseCode
                if (responseCode == 404) {
                    log.warn("404 (no further data for requested \"page\"): ${url}")
                    return null
                }
                return httpConn.inputStream.getText('UTF-8')
            } else {
                return connection.inputStream.getText('UTF-8')
            }

        } catch (Exception e) {
            log.warn("Failed to fetch data from ${url}: ${e.message}", e)
            return null
        }
    }

    Map getUserAgentHeader() {
        def appName = grailsApplication.config.getProperty('info.app.name', String, 'bie-index')
        def appVersion = grailsApplication.config.getProperty('info.app.version', String, '3.1')
        log.debug("app.name: ${appName} | app.version: ${appVersion}")
        String value = "${appName}/${appVersion}" as String
        return ["User-Agent": value]
    }
}
