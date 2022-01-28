package au.org.ala.bie

import grails.config.Config
import grails.converters.JSON
import grails.core.support.GrailsConfigurationAware
import org.apache.solr.common.SolrException

/**
 * A set of JSON based search web services.
 */
class SearchController implements GrailsConfigurationAware {
    def searchService, solrSearchService, autoCompleteService, downloadService

    static defaultAction = "search"

    // Caused by the grails structure eliminating the // from http://x.y.z type URLs
    static BROKEN_URLPATTERN = /^[a-z]+:\/[^\/].*/

    /** The default locale to use when choosing common names */
    Locale defaultLocale

    @Override
    void setConfiguration(Config config) {
        defaultLocale = Locale.forLanguageTag(config.commonName.defaultLanguage)
    }

    /**
     * Retrieve a classification for the supplied taxon.
     *
     * @return
     */
    // Documented in openapi.yml
    def classification(){
        if(!params.id){
            response.sendError(404, "Please provide a GUID")
            return null
        }
        def guid = regularise(params.id)
        def classification = searchService.getClassification(guid)

        if (!classification) {
            response.sendError(404, "GUID ${guid} not found")
        } else {
            render (classification as JSON)
        }
    }

    /**
     * Returns taxa with images.
     *
     * @return
     */
    // Documented in openapi.yml
    def imageSearch(){
        def start = params.start as Integer
        def rows = params.rows as Integer
        def locales = [request.locale, defaultLocale]
        render ([searchResults:searchService.imageSearch(regularise(params.id), start, rows, params.qc, locales)] as JSON)
    }

    /**
     * Bulk lookup of image information for a list of
     * taxon GUIDs
     */
    // Documented in apenapi.yml
    def bulkImageLookup() {
        final locales = [request.locale, defaultLocale]
        final req = request.getJSON()
        if (!req) {
            response.sendError(400, "Body could not be parsed or was empty")
        }
        def result = []
        req.each { guid ->
            def taxon = searchService.getTaxon(guid, locales)
            def imageId = taxon?.imageIdentifier
            def image = null
            if (imageId) {
                image = [
                        imageId: imageId,
                        thumbnail: searchService.formatImage(imageId, 'thumbnail'),
                        small: searchService.formatImage(imageId, 'small'),
                        large: searchService.formatImage(imageId, 'large'),
                        metadata: searchService.formatImage(imageId, 'metadata')
                ]
            }
            result << image
        }

        render result as JSON

    }

    /**
     * Returns a redirect to an image of the appropriate type
     */
    // Documented in openapi.yml
    def imageLinkSearch() {
        def showNoImage = params.boolean('showNoImage', true)
        def guid = regularise(params.id)
        def locales = [request.locale, defaultLocale]
        def imageType = params.imageType
        def url = searchService.imageLinkSearch(guid, imageType, params.qc, locales)

        if (!url && showNoImage) {
            url = resource(dir: "images", file: "noImage85.jpg", absolute: true)
        }
        if (!url) {
            response.sendError(404, "No image for " + guid)
            return null
        }
        redirect(url: url)
    }

    /**
     * Retrieves child concepts for the supplied taxon ID
     *
     * @return
     */
    // Documented in openapi.yml
    def childConcepts(){
        def taxonID = params.id
        if(!taxonID) {
            response.sendError(400, "Please provide a GUID")
            return null
        }
        def within = params.int('within', 2000)
        def unranked = params.boolean('unranked', true)
        ['within', 'unranked', 'controller', 'action', 'id'].each {params.remove(it) }
        def extra = params.toQueryString().replaceFirst('^\\?', '')
        render (searchService.getChildConcepts(regularise(taxonID), extra, within, unranked) as JSON)
    }

    // Documented in openapi.yml
    def guid(){
        if(params.name == 'favicon') return; //not sure why this is happening....
        if(!params.name){
            response.sendError(400, "Please provide a name for lookups")
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

    // Documented in openapi.yml
    def shortProfile(){
        def guid = regularise(params.id)
        if(guid == 'favicon') return; //not sure why this is happening....
        if(!guid){
            response.sendError(400, "Please provide a GUID")
            return null
        }
        def model = searchService.getShortProfile(guid)
        if(!model){
            response.sendError(404,"GUID not recognised ${guid}")
            return null
        } else {
            render (model as JSON)
        }
    }

    // Documented in openapi.yml
    def getSpeciesForNames() {
        def result = params.list('q').collectEntries { [(it): searchService.getProfileForName(it) ] } ?: null
        if (!result)
            respond result
        else
            asJsonP(params,result)
     }

    // Documented in openapi.yml
    def bulkGuidLookup(){
        def guidList = request.JSON
        if(!(guidList in List) || guidList == null){
            response.sendError(400, "Please provide a GUID list")
            return null
        }
        def results = searchService.getTaxa(guidList)
        if (results == null)
            results = []
        def dto = [searchDTOList: results]
        asJson dto
    }

    /**
     * Retrieves a profile for a taxon.
     *
     * @return
     */
    // Documented in openapi.yml
    def taxon(){
        def guid = regularise(params.id)
        def locales = [request.locale, defaultLocale]
        if(guid == 'favicon') return; //not sure why this is happening....
        if(!guid){
            response.sendError(400, "Please provide a GUID")
            return null
        }
        def model = searchService.getTaxon(guid, locales)
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

    // Documented in openapi.yml
    def speciesLookupBulk() {
        final req = request.getJSON()
        if (!req) {
            response.sendError(400, "Body could not be parsed or was empty")
        }
        boolean includeVernacular = req.optBoolean('vernacular', true)
        List<String> names = req['names']
        List result = []

        names.eachWithIndex { name, idx ->
            log.debug "$idx. Looking up name: ${name}"
            result.add(searchService.getLongProfileForName(name, includeVernacular))
        }

        render result as JSON
    }

    /**
     * Download CSV file for given search query (q & fq)
     * User provided params: q, fq, file, fields
     *
     * @return
     */
    // Documented in openapi.yml
    def download(){
        if (!params.q?.trim()) {
            response.sendError(400, "A q parameter is required")
        } else {
            response.setHeader("Cache-Control", "must-revalidate");
            response.setHeader("Pragma", "must-revalidate");
            response.setHeader("Content-Disposition", "attachment;filename=${params.file ?: 'species.csv'}");
            response.setContentType("text/csv");
            downloadService.download(params, response.outputStream, request.locale)
        }
    }

    /**
     * Auto complete search service.
     *
     * @return
     */
    // Documented in openapi.yml
    def auto(){
        def limit = params.limit?.toInteger()
        def idxType = params.idxType
        def geoOnly = params.geoOnly
        def kingdom = params.kingdom
        def locales = [request.locale, defaultLocale]
        def payload

        if (geoOnly) {
            // TODO needs WS lookup to biocache-service (?)
        }

        try {
            def autoCompleteList = autoCompleteService.auto(params.q, idxType, kingdom, limit, locales)
            payload = [autoCompleteList: autoCompleteList]
        } catch (SolrException ex) { // Can be caused by list not being ready
            payload = [autoCompleteList: [], error: ex.getMessage()]
        }
        asJson payload
    }

    /**
     * Main search across the entire index.
     *
     * @return
     */
    // Documented in openapi.yml
    def search(){
        try {
            def facets = []
            def requestFacets = params.getList("facets")
            def locales = [request.locale, defaultLocale]
            if(requestFacets){
                requestFacets.each {
                    it.split(",").each { facet -> facets << facet }
                }
            }
            def results = searchService.search(params.q, params, facets, locales)
            asJson([searchResults: results])
        } catch (Exception e){
            log.error(e.getMessage(), e)
            render(["error": e.getMessage(), indexServer: grailsApplication.config.indexLiveBaseUrl] as JSON)
        }
    }

    // Documented in openapi.yml
    def habitats(){
        asJson([searchResults: searchService.getHabitats()])
    }

    // Documented in openapi.yml
    def habitatTree(){
        asJson([searchResults: searchService.getHabitatsTree()])
    }

    // Documented in openapi.yml
    def getHabitat(){
        asJson([searchResults: searchService.getHabitatByGuid(params.guid)])
    }

    // Documented in openapi.yml
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

    private regularise(String guid) {
        if (!guid)
            return guid
        if (guid ==~ BROKEN_URLPATTERN) {
            guid = guid.replaceFirst(":/", "://")
        }
        return guid
    }
}