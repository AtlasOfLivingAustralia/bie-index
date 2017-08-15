package au.org.ala.bie

import grails.converters.JSON
/**
 * A set of JSON based search web services.
 */
class SearchController {
    def searchService, solrSearchService, autoCompleteService, downloadService

    static defaultAction = "search"

    /**
     * Retrieve a classification for the supplied taxon.
     *
     * @return
     */
    def classification(){
        if(!params.id){
            response.sendError(404, "Please provide a GUID")
            return null
        }
        def classification = searchService.getClassification(params.id)

        if (!classification) {
            response.sendError(404, "GUID ${params.id} not found")
        } else {
            render (classification as JSON)
        }
    }

    /**
     * Returns taxa with images.
     *
     * @return
     */
    def imageSearch(){
        render ([searchResults:searchService.imageSearch(params.id, params.start, params.rows, params.qc)] as JSON)
    }

    /**
     * Returns a redirect to an image of the appropriate type
     */
    def imageLinkSearch() {
        def showNoImage = params.containsKey("showNoImage") ? params.boolean("showNoImage") : true
        def url = searchService.imageLinkSearch(params.id, params.imageType, params.qc)

        if (!url && showNoImage) {
            url = resource(dir: "images", file: "noImage85.jpg", absolute: true)
        }
        if (!url) {
            response.sendError(404, "No image for " + params.id)
            return null
        }
        redirect(url: url)
    }

    /**
     * Retrieves child concepts for the supplied taxon ID
     *
     * @return
     */
    def childConcepts(){
        if(!params.id){
            response.sendError(404, "Please provide a GUID")
            return null
        }
        render (searchService.getChildConcepts(params.id, request.queryString) as JSON)
    }

    def guid(){
        if(params.name == 'favicon') return; //not sure why this is happening....
        if(!params.name){
            response.sendError(404, "Please provide a name for lookups")
            return null
        }
        def model = searchService.getProfileForName(params.name)
        if(!model){
            response.sendError(404,"Name not recognised ${params.name}")
            return null
        } else {
            render (model as JSON)
        }
    }

    def shortProfile(){
        if(params.id == 'favicon') return; //not sure why this is happening....
        if(!params.id){
            response.sendError(404, "Please provide a GUID")
            return null
        }
        def model = searchService.getShortProfile(params.id)
        if(!model){
            response.sendError(404,"GUID not recognised ${params.id}")
            return null
        } else {
            render (model as JSON)
        }
    }

    def getSpeciesForNames() {
        def result = params.list('q').collectEntries { [(it): searchService.getProfileForName(it) ] } ?: null
        if (!result)
            respond result
        else
            asJsonP(params,result)
     }

    def bulkGuidLookup(){
        def guidList = request.JSON
        def results = searchService.getTaxa(guidList)
        if(!results){
            response.sendError(404,"GUID not recognised ${params.id}")
            return null
        } else {
            def dto = [searchDTOList: results]
            asJson dto
        }
    }

    /**
     * Retrieves a profile for a taxon.
     *
     * @return
     */
    def taxon(){
        def guid = params.id
        if(guid == 'favicon') return; //not sure why this is happening....
        if(!guid){
            response.sendError(404, "Please provide a GUID")
            return null
        }
        def model = searchService.getTaxon(guid)
        log.debug "taxon model = ${model}"

        if(!model) {
            response.sendError(404, "GUID not recognised ${guid}")
            return null
        } else if (model.taxonConcept?.guid && model.taxonConcept.guid != guid) {
            // old identifier so redirect to current taxon page
            redirect(action: "taxon", params:[id: model.taxonConcept.guid], permanent: true)
        } else {
            asJson model
        }
    }

    def speciesLookupBulk() {
        final req = request.getJSON()
        if (!req) {
            response.sendError(400, "Body could not be parsed or was empty")
        }
        boolean includeVernacular = req.optBoolean('vernacular')
        List<String> names = req['names']
        List result = []

        names.eachWithIndex { name, idx ->
            log.debug "$idx. Looking up name: ${name}"
            result.add(searchService.getLongProfileForName(name))
        }

        render result as JSON
    }

    /**
     * Download CSV file for given search query (q & fq)
     * User provided params: q, fq, file, fields
     *
     * @return
     */
    def download(){
        response.setHeader("Cache-Control", "must-revalidate");
        response.setHeader("Pragma", "must-revalidate");
        response.setHeader("Content-Disposition", "attachment;filename=${params.file?:'species.csv'}");
        response.setContentType("text/csv");
        downloadService.download(params, response.outputStream, request.locale)
    }

    /**
     * Auto complete search service.
     *
     * @return
     */
    def auto(){
        log.debug("auto called with q = " + params.q)
        log.debug("auto called with queryString = " + request.queryString)
        def fq = []
        def limit = params.limit
        def idxType = params.idxType
        def geoOnly = params.geoOnly

        if (limit) {
            fq << "&rows=${limit}"
        }

        if (idxType) {
            fq << "&fq=idxtype:${idxType.toUpperCase()}"
        }

        if (geoOnly) {
            // TODO needs WS lookup to biocache-service (?)
        }

        def autoCompleteList = autoCompleteService.auto(params.q, fq)
        def payload = [autoCompleteList:autoCompleteList]
        asJson payload
    }

    /**
     * Main search across the entire index.
     *
     * @return
     */
    def search(){
        try {
            def facets = []
            def requestFacets = params.getList("facets")
            if(requestFacets){
                requestFacets.each {
                    it.split(",").each { facet -> facets << facet }
                }
            }
            def results = searchService.search(params.q, params, facets)
            asJson([searchResults: results])
        } catch (Exception e){
            log.error(e.getMessage(), e)
            render(["error": e.getMessage(), indexServer: grailsApplication.config.indexLiveBaseUrl] as JSON)
        }
    }

    def habitats(){
        asJson([searchResults: searchService.getHabitats()])
    }

    def habitatTree(){
        asJson([searchResults: searchService.getHabitatsTree()])
    }

    def getHabitat(){
        asJson([searchResults: searchService.getHabitatByGuid(params.guid)])
    }

    def getHabitatIDs(){
        asJson([searchResults: searchService.getHabitatsIDsByGuid(params.guid)])
    }

    /**
     * Due to bug in Grails that prevents the JSONP filter from working with the render method,
     * this utility method is a work around and allows the JSONP callback to be added.
     * And it prevent the unit test from breaking.
     *
     * @param params
     * @param responseBody
     * @return
     */
    private asJsonP(params, responseBody) {
        response.setContentType("application/json;charset=UTF-8")
        def output = responseBody as JSON
        if (params.callback) {
            log.debug "adding callback"
            output = params.callback + "(" + (responseBody as JSON) + ")"
        }
        render output
    }


    private def asJson = { model ->
        response.setContentType("application/json;charset=UTF-8")
        render(model as JSON)
    }
}