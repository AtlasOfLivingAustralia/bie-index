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

        if (grailsApplication.config.lists.useListWs) {
            int pageSize = 1000
            int page = 1
            while (true) {
                def url = Encoder.buildServiceUrl(grailsApplication.config.lists.service, grailsApplication.config.lists.items, pageSize, page)
                def json = JSON.parse(url.getText('UTF-8'))

                if (!json) {
                    break
                }

                lists.addAll(json)
            }
        } else {
            boolean hasAnotherPage = true
            int max = 400
            int offset = 0

            while (hasAnotherPage) {
                def url = Encoder.buildServiceUrl(grailsApplication.config.lists.service, grailsApplication.config.lists.items, uid, max, offset)

                def slurper = new JsonSlurper()
                def json = slurper.parseText(url.getText('UTF-8'))
                items.addAll(json)

                hasAnotherPage = json.size() == max
                offset += max

            }
        }

        return items.collect { item ->
            // item.lsid (lists.useListWs:false), item.classification?.taxonConceptID for matched entries, otherwise taxonID
            def result = [lsid: item.lsid ?: item.classification?.taxonConceptID ?: item.taxonID, name: item.name ?: item.scientificName]

            fields.each { field ->
                def value
                if (grailsApplication.config.lists.useListWs) {
                    value = field ? item?.properties?.find { it.key == field }?.get("value") : null
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
        if (grailsApplication.config.lists.useListWs) {
            def query =
"""
mutation add {
    addSpeciesListItem(inputSpeciesListItem: {scientificName: "${name}", taxonID: "${guid}", speciesListID: "${listDr}", properties: { key:"${extraField}", value:"${extraValue}"}} ) { id }
}
"""
            webService.post(grailsApplication.config.lists.service + "/graphql", [query: query], null, ContentType.APPLICATION_JSON, true, false, [:])
        } else {
            def url = new URL(grailsApplication.config.lists.service + grailsApplication.config.lists.add)
            def query = [druid: listDr]
            def body = [guid: guid, rawScientificName: name]
            body[extraField] = extraValue
            webService.post(url.toString(), body, query, ContentType.APPLICATION_JSON, true, false, [:])
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
                    webService.post(grailsApplication.config.lists.service + "/graphql", [query: query])
                }
            }
        } else {
            def url = new URL(grailsApplication.config.lists.service + grailsApplication.config.lists.remove)
            webService.get(url.toString(), [druid: listDr, guid: guid], ContentType.APPLICATION_JSON, true, false, [:])
        }
    }

    // Could use graphql filterSpeciesList query and loop those results instead of paging through the entire list
    def findSpeciesListItemIds(def listDr, def guid) {
        def foundIds = []

        def pageSize = 1000
        def page = 1
        while (true) {
            def items = webService.get(grailsApplication.config.lists.service + '/speciesListItems/' + listDr, [pageSize: pageSize, page: page])?.resp
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
                def url = Encoder.buildServiceUrl(grailsApplication.config.lists.service, grailsApplication.config.lists.search, pageSize, page)
                def json = JSON.parse(url.getText('UTF-8')).lists

                if (!json) {
                    break
                }

                lists.addAll(json)
            }

        } else {
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
                def json = JSON.parse(url.getText('UTF-8')).lists

                if (!json) {
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

                def json = JSON.parse(url.getText('UTF-8')).lists
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
}
