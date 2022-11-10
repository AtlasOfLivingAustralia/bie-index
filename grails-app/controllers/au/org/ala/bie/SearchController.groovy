package au.org.ala.bie

import au.ala.org.ws.security.RequireApiKey
import au.org.ala.plugins.openapi.Path
import grails.config.Config
import grails.converters.JSON
import grails.core.support.GrailsConfigurationAware
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.headers.Header
import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.parameters.RequestBody
import io.swagger.v3.oas.annotations.responses.ApiResponse
import org.apache.solr.common.SolrException

import javax.ws.rs.Produces

import static io.swagger.v3.oas.annotations.enums.ParameterIn.PATH
import static io.swagger.v3.oas.annotations.enums.ParameterIn.QUERY

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
    @Operation(
            method = "GET",
            tags = "Search",
            operationId = "Get higher classifications of a taxon",
            summary = "Get higher classifications of taxon with the supplied GUID",
            description = "Provides a list of higher taxa for the requested taxon. Note, this service does not currently support JSONP (use of callback param) but this is planned for a future release.",
            parameters = [
                    @Parameter(
                            name = "id",
                            in = PATH,
                            description = "The guid of a specific taxon, data resource, layer etc. Since guids can be URLs but can also be interpolated into paths, a http:// or https:// prefix may be converted into http:/ or https:/ by the tomcat container. A supplied guid of, eg. https:/id.biodiversity.org.au/node/apni/50587232 will be converted into https://id.biodiversity.org.au/node/apni/50587232",
                            schema = @Schema(implementation = String),
                            example = "https://id.biodiversity.org.au/node/apni/2905748",
                            required = true
                    ),
            ],
            responses = [
                    @ApiResponse(
                            description = "Search results",
                            responseCode = "200",
                            headers = [
                                    @Header(name = 'Access-Control-Allow-Headers', description = "CORS header", schema = @Schema(type = "String")),
                                    @Header(name = 'Access-Control-Allow-Methods', description = "CORS header", schema = @Schema(type = "String")),
                                    @Header(name = 'Access-Control-Allow-Origin', description = "CORS header", schema = @Schema(type = "String"))
                            ]
                    )
            ]

    )
    @Path("/classification/{id}")
    @Produces("application/json")
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
    @Operation(
            method = "GET",
            tags = "Search",
            operationId = "Search for a taxon with images",
            summary = "Search for a taxon with images",
            description = "Return a list of taxa which correspond to a specific taxon id and which have images available",
            parameters = [
                    @Parameter(
                            name = "id",
                            in = PATH,
                            description = "The guid of a specific taxon, data resource, layer etc. Since guids can be URLs but can also be interpolated into paths, a http:// or https:// prefix may be converted into http:/ or https:/ by the tomcat container. A supplied guid of, eg. https:/id.biodiversity.org.au/node/apni/50587232 will be converted into https://id.biodiversity.org.au/node/apni/50587232",
                            schema = @Schema(implementation = String),
                            example = "https://id.biodiversity.org.au/node/apni/2905748",
                            required = true
                    ),
                    @Parameter(
                            name = "start",
                            in = QUERY,
                            description = "The records offset, to enable paging",
                            schema = @Schema(implementation = Integer),
                            example = "1",
                            required = false
                    ),
                    @Parameter(
                            name = "rows",
                            in = QUERY,
                            description = "The number of records to return, to enable paging",
                            schema = @Schema(implementation = Integer),
                            example = "5",
                            required = false
                    ),
                    @Parameter(
                            name = "qc",
                            in = QUERY,
                            description = "Solr query context, passed on to the search engine",
                            schema = @Schema(implementation = String),
                            example = "",
                            required = false
                    )
            ],
            responses = [
                    @ApiResponse(
                            description = "Search results",
                            responseCode = "200",
                            headers = [
                                    @Header(name = 'Access-Control-Allow-Headers', description = "CORS header", schema = @Schema(type = "String")),
                                    @Header(name = 'Access-Control-Allow-Methods', description = "CORS header", schema = @Schema(type = "String")),
                                    @Header(name = 'Access-Control-Allow-Origin', description = "CORS header", schema = @Schema(type = "String"))
                            ]
                    )
            ]

    )
    @Path("/imageSearch/{id}")
    @Produces("application/json")
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
    @Operation(
            method = "GET",
            tags = "Search",
            operationId = "Get the child concepts of a taxon",
            summary = "Get the child concepts of a taxon with the supplied GUID",
            description = "Return the taxon concepts that are direct children of the specified taxon",
            parameters = [
                    @Parameter(
                            name = "id",
                            in = PATH,
                            description = "The guid of a specific taxon, data resource, layer etc. Since guids can be URLs but can also be interpolated into paths, a http:// or https:// prefix may be converted into http:/ or https:/ by the tomcat container. A supplied guid of, eg. https:/id.biodiversity.org.au/node/apni/50587232 will be converted into https://id.biodiversity.org.au/node/apni/50587232",
                            schema = @Schema(implementation = String),
                            example  = "https://id.biodiversity.org.au/node/apni/2905748",
                            required = true
                    ),
                    @Parameter(
                            name = "within",
                            in = QUERY,
                            description = "Get children within this rank range",
                            schema = @Schema(implementation = Integer, defaultValue = "200"),
                            required = false
                    ),
                    @Parameter(
                            name = "unranked",
                            in = QUERY,
                            description = "Include unranked children",
                            schema = @Schema(implementation = Boolean, defaultValue = "True"),
                            required = false
                    ),
            ],
            responses = [
                    @ApiResponse(
                            description = "Search results",
                            responseCode = "200",
                            headers = [
                                    @Header(name = 'Access-Control-Allow-Headers', description = "CORS header", schema = @Schema(type = "String")),
                                    @Header(name = 'Access-Control-Allow-Methods', description = "CORS header", schema = @Schema(type = "String")),
                                    @Header(name = 'Access-Control-Allow-Origin', description = "CORS header", schema = @Schema(type = "String"))
                            ]
                    )
            ]

    )

    @Path("/childConcepts/{id}")
    @Produces("application/json")
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
    /**
     *
     * @return
     */
    @Operation(
            method = "GET",
            tags = "Search",
            operationId = "Look up a taxon guid by name",
            summary = "Look up a taxon guid by name",
            description = "Return a list of taxa which correspond to a specific taxon id and which have images available",
            parameters = [
                    @Parameter(
                            name = "name",
                            in = PATH,
                            description = "The name to search the taxon guid",
                            schema = @Schema(implementation = String),
                            example = "kangaroo",
                            required = true
                    ),
            ],
            responses = [
                    @ApiResponse(
                            description = "Search results",
                            responseCode = "200",
                            headers = [
                                    @Header(name = 'Access-Control-Allow-Headers', description = "CORS header", schema = @Schema(type = "String")),
                                    @Header(name = 'Access-Control-Allow-Methods', description = "CORS header", schema = @Schema(type = "String")),
                                    @Header(name = 'Access-Control-Allow-Origin', description = "CORS header", schema = @Schema(type = "String"))
                            ]
                    )
            ]

    )

    @Path("/guid/{name}")
    @Produces("application/json")
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

    @Operation(
            method = "GET",
            tags = "Search",
            operationId = "Bulk species lookup",
            summary = "Batch lookup of multiple taxon names",
            description = "Search for multiple names with individual name queries and return short profiles for each name",
            parameters = [
                    @Parameter(
                            name = "q",
                            in = QUERY,
                            description = "Query string",
                            schema = @Schema(implementation = String),
                            example = "kangaroo",
                            required = true
                    ),
                    @Parameter(
                            name = "callback",
                            in = QUERY,
                            description = "Name of callback function to wrap JSON output in. Provided for JSONP cross-domain requests",
                            schema = @Schema(implementation = String),
                            example = "handleResponse",
                            required = false
                    ),
            ],
            responses = [
                    @ApiResponse(
                            description = "Search results",
                            responseCode = "200",
                            headers = [
                                    @Header(name = 'Access-Control-Allow-Headers', description = "CORS header", schema = @Schema(type = "String")),
                                    @Header(name = 'Access-Control-Allow-Methods', description = "CORS header", schema = @Schema(type = "String")),
                                    @Header(name = 'Access-Control-Allow-Origin', description = "CORS header", schema = @Schema(type = "String"))
                            ]
                    )
            ]

    )

    @Path("/guid/batch")
    @Produces("application/json")
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
    @Operation(
            method = "GET",
            tags = "Search",
            operationId = "Look up a species by guid for a taxon",
            summary = "Look up a species by guid for the taxon",
            description = "Return a list of of species matching the provided guid",
            parameters = [
                    @Parameter(
                            name = "id",
                            in = PATH,
                            description = "The guid for the taxon concept",
                            schema = @Schema(implementation = String),
                            example  = "https://id.biodiversity.org.au/node/apni/2905748",
                            required = true
                    ),
            ],
            responses = [
                    @ApiResponse(
                            description = "Search results",
                            responseCode = "200",
                            headers = [
                                    @Header(name = 'Access-Control-Allow-Headers', description = "CORS header", schema = @Schema(type = "String")),
                                    @Header(name = 'Access-Control-Allow-Methods', description = "CORS header", schema = @Schema(type = "String")),
                                    @Header(name = 'Access-Control-Allow-Origin', description = "CORS header", schema = @Schema(type = "String"))
                            ]
                    )
            ]

    )

    @Path("/species/{id}")
    @Produces("application/json")
    def taxon(){
        def guid = regularise(params.id)
        log.warn(guid)
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


    @Operation(
            method = "POST",
            tags = "bulk",
            operationId = "Bulk species lookup - revised JSON input ",
            summary = "Batch lookup of multiple taxon names",
            description = "Retrieve taxon information for a list of vernacular or scientific names. This operation can be used to retrieve large lists of taxa. By default, the operation searches for both vernacular names and scientific names. Requires a JSON map as a post body. The JSON map must contain a \"names\" key that is the list of scientific names. json {\"names\":[\"Macropus rufus\",\"Macropus greyi\"]} This service will return a list of results. This differs from the original bulk species lookup by including a null value when a name could not be located. To allow the lookup to consider common names, include a \"vernacular\":true value in the JSON map: json {\"names\":[\"Grevillea\"],\"vernacular\":true}",
            requestBody = @RequestBody(
                    description = " The JSON map object the list of names",
                    required = true,
                    content = [
                            @Content(
                                    mediaType = "application/json",
                                    schema = @Schema(implementation = Object),
                                    examples = [
                                            @ExampleObject(
                                                    value = "{\"names\":[\"Grevillea\"],\"vernacular\":true}"
                                            )
                                    ]
                            )
                    ]
            ),
            responses = [
                    @ApiResponse(
                            description = "Search results",
                            responseCode = "200",
                            headers = [
                                    @Header(name = 'Access-Control-Allow-Headers', description = "CORS header", schema = @Schema(type = "String")),
                                    @Header(name = 'Access-Control-Allow-Methods', description = "CORS header", schema = @Schema(type = "String")),
                                    @Header(name = 'Access-Control-Allow-Origin', description = "CORS header", schema = @Schema(type = "String"))
                            ]
                    )
            ]

    )

    @Path("/species/lookup/bulk")
    @Produces("application/json")
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
    @Operation(
            method = "GET",
            tags = "bulk",
            operationId = "Download the results of a species search",
            summary = "Download the results of a species search",
            description = "Search the BIE and return the taxonomic results of the search in tabular form",
            parameters = [
                    @Parameter(
                            name = "q",
                            in = QUERY,
                            description = "Query of the form field:value e.g. q=genus:Macropus or a free text search e.g. q=gum",
                            schema = @Schema(implementation = String),
                            example = "gum",
                            required = true
                    ),
                    @Parameter(
                            name = "fq",
                            in = QUERY,
                            description = "Filters to be applied to the original query. These are additional params of the form fq=INDEXEDFIELD:VALUE. See http://bie.ala.org.au/ws/indexFields for all the fields that a queryable.",
                            schema = @Schema(implementation = String),
                            example = "imageAvailable:\"true\"",
                            required = false
                    ),
                    @Parameter(
                            name = "fields",
                            in = QUERY,
                            description = "A comma separated list of SOLR fields to include in the download. Fields can be included in the download if they have been stored. See index fields listing. Default fields: taxonConceptID,rank,scientificName,establishmentMeans,rk_genus,rk_family,rk_order,rk_class,rk_phylum,rk_kingdom,datasetName",
                            schema = @Schema(implementation = String),
                            example = "taxonConceptID,rank,scientificName,establishmentMeans,rk_genus,rk_family,rk_order",
                            required = true
                    ),
                    @Parameter(
                            name = "file",
                            in = QUERY,
                            description = "The name of the file to be downloaded. Default: 'species.csv'",
                            schema = @Schema(implementation = String),
                            example = "exmaple_species.csv",
                            required = true
                    ),
            ],
            responses = [
                    @ApiResponse(
                            description = "Search results as CSV file",
                            responseCode = "200",
                            headers = [
                                    @Header(name = 'Access-Control-Allow-Headers', description = "CORS header", schema = @Schema(type = "String")),
                                    @Header(name = 'Access-Control-Allow-Methods', description = "CORS header", schema = @Schema(type = "String")),
                                    @Header(name = 'Access-Control-Allow-Origin', description = "CORS header", schema = @Schema(type = "String"))
                            ]
                    )
            ]

    )

    @Path("/download")
    @Produces("text/csv")
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
    @Operation(
            method = "GET",
            tags = "Search",
            operationId = "Autocomplete search",
            summary = "Autocomplete search",
            description = "Used to provide a list of scientific and common names that can be used to automatically complete a supplied partial name.",
            parameters = [
                    @Parameter(
                            name = "q",
                            in = QUERY,
                            description = "The value to auto complete e.g. q=Mac",
                            schema = @Schema(implementation = String),
                            example = "Mac",
                            required = true
                    ),
                    @Parameter(
                            name = "idxType",
                            in = QUERY,
                            description = "The index type to limit . Values include: * TAXON * REGION * COLLECTION * INSTITUTION * DATASET",
                            schema = @Schema(implementation = String),
                            example = "TAXON",
                            required = false
                    ),
                    @Parameter(
                            name = "kingdom",
                            in = QUERY,
                            description = "The higher-order taxonomic rank to limit the result",
                            schema = @Schema(implementation = String),
                            example = "plantae",
                            required = false
                    ),
                    @Parameter(
                            name = "geoOnly",
                            in = QUERY,
                            description = " (Not Implemented) Limit value to limit result with geospatial occurrence records",
                            schema = @Schema(implementation = Boolean),
                            example = "false",
                            hidden = true,
                            required = false
                    ),
                    @Parameter(
                            name = "limit",
                            in = QUERY,
                            description = "The maximum number of results to return (default = 10)",
                            schema = @Schema(implementation = Integer),
                            example = "10",
                            required = false
                    )
            ],
            responses = [
                    @ApiResponse(
                            description = "Search results",
                            responseCode = "200",
                            headers = [
                                    @Header(name = 'Access-Control-Allow-Headers', description = "CORS header", schema = @Schema(type = "String")),
                                    @Header(name = 'Access-Control-Allow-Methods', description = "CORS header", schema = @Schema(type = "String")),
                                    @Header(name = 'Access-Control-Allow-Origin', description = "CORS header", schema = @Schema(type = "String"))
                            ]
                    )
            ]

    )
    @Path("/search/auto")
    @Produces("application/json")
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
    @Operation(
            method = "GET",
            tags = "Search",
            operationId = "Search the BIE",
            summary = "Search the BIE",
            description = "Search the BIE by solr query  or free text search",
            parameters = [
                    @Parameter(
                            name = "q",
                            in = QUERY,
                            description = "Primary search  query for the form field:value e.g. q=rk_genus:Macropus or freee text e.g q=gum",
                            schema = @Schema(implementation = String),
                            example = "gum",
                            required = true
                    ),
                    @Parameter(
                            name = "fq",
                            in = QUERY,
                            description = "Filters to be applied to the original query. These are additional params of the form fq=INDEXEDFIELD:VALUE  See http://bie.ala.org.au/ws/indexFields for all the fields that a queryable.",
                            schema = @Schema(implementation = String),
                            example = "imageAvailable:\"true\"",
                            required = false
                    ),
                    @Parameter(
                            name = "start",
                            in = QUERY,
                            description = "The records offset, to enable paging",
                            schema = @Schema(implementation = Integer),
                            example = "0",
                            required = false
                    ),
                    @Parameter(
                            name = "pageSize",
                            in = QUERY,
                            description = "The number of records to return",
                            schema = @Schema(implementation = Integer),
                            example = "5",
                            required = false
                    ),
                    @Parameter(
                            name = "sort",
                            in = QUERY,
                            description = "The field to sort the records by: i.e.  -  acceptedConceptName, score, scientificName, commonNameSingle, rank",
                            schema = @Schema(implementation = String),
                            example = "commonNameSingle",
                            required = false
                    ),
                    @Parameter(
                            name = "dir",
                            in = QUERY,
                            description = "Sort direction 'asc' or 'desc'",
                            schema = @Schema(implementation = String),
                            example = "desc",
                            required = false
                    ),
                    @Parameter(
                            name = "facets",
                            in = QUERY,
                            description = "Comma separated list of fields to display facets for. Available fields listed http://bie.ala.org.au/ws/indexFields.",
                            schema = @Schema(implementation = String),
                            example = "datasetName,commonNameExact",
                            required = false
                    )
            ],
            responses = [
                    @ApiResponse(
                            description = "Search results",
                            responseCode = "200",
                            headers = [
                                    @Header(name = 'Access-Control-Allow-Headers', description = "CORS header", schema = @Schema(type = "String")),
                                    @Header(name = 'Access-Control-Allow-Methods', description = "CORS header", schema = @Schema(type = "String")),
                                    @Header(name = 'Access-Control-Allow-Origin', description = "CORS header", schema = @Schema(type = "String"))
                            ]
                    )
            ]

    )

    @Path("/search")
    @Produces("application/json")
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