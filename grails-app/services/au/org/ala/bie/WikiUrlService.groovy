package au.org.ala.bie

class WikiUrlService {
    def importService
    def listService

    def add(name, guid, url) {
        remove(guid)

        def wikiLists = importService.getWikiUrlLists()

        // add to lists
        listService.add(wikiLists[0].uid, name, guid, wikiLists[0].wikiUrl, url)

        def obj = [taxonID: guid, name: '', url: url]
        def map = [:]
        map.put(guid, obj)
        // index in bie SOLR instance
        importService.updateWikiUrls(true, map)
    }

    def remove(guid) {
        def wikiLists = importService.getWikiUrlLists()

        // remove from lists
        wikiLists.each {
            listService.remove(it.uid, guid)
        }
    }
}
