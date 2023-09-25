package au.org.ala.bie

class ImageService {
    def importService
    def listService

    def prefer(name, guid, preferredImages) {

        def imageLists = importService.imagesLists

        // remove from lists
        imageLists.each {
            listService.remove(it.uid, guid)
        }

        // add to lists
        listService.add(imageLists[0].uid, name, guid, imageLists[0].imageId, preferredImages)

        def obj = [taxonID: guid, name: '', imageId: preferredImages]
        def map = [:]
        map.put(guid, obj)

        // index in bie SOLR instance
        importService.updatePreferredImages(true, map)
    }

    def hide(name, guid, hiddenImages) {

        def imageLists = importService.hiddenImagesLists

        // remove from lists
        imageLists.each {
            listService.remove(it.uid, guid)
        }

        // add to lists
        listService.add(imageLists[0].uid, name, guid, imageLists[0].imageId, hiddenImages)

        def obj = [taxonID: guid, name: '', imageId: hiddenImages]
        def map = [:]
        map.put(guid, obj)

        // index in bie SOLR instance
        importService.updateHiddenImages(true, map)
    }
}
