package au.org.ala.bie

import au.org.ala.bie.search.IndexDocType
import grails.converters.JSON
import groovy.json.JsonSlurper
import org.apache.solr.common.params.MapSolrParams
import org.codehaus.groovy.grails.web.servlet.mvc.GrailsParameterMap
import org.gbif.nameparser.NameParser

/**
 * A set of search services for the BIE.
 */
class SearchService {
    static BULK_BATCH_SIZE = 20

    def grailsApplication
    def conservationListsSource

    /**
     * Retrieve species & subspecies for the supplied taxon which have images.
     *
     * @param taxonID
     * @param start
     * @param rows
     * @return
     */
    def imageSearch(taxonID, start, rows, queryContext){

        def query = "q=*:*"

        if(taxonID){
            //retrieve the taxon rank and then construct the query
            def taxon = lookupTaxon(taxonID)
            if(!taxon){
                return []
            }
            def tid = URLEncoder.encode(taxon.guid, "UTF-8")
            query = "q=(guid:\"${tid}\"+OR+rkid_${taxon.rank.toLowerCase().replaceAll('\\s', '_')}:\"${tid}\")"
        }

        def additionalParams = "&wt=json&fq=rankID:%5B7000%20TO%20*%5D&fq=imageAvailable:true"

        if(start){
            additionalParams = additionalParams + "&start=" + start
        }

        if(rows){
            additionalParams = additionalParams + "&rows=" + rows
        }

        if(queryContext){
            additionalParams = additionalParams + queryContext
        }

        def queryUrl = grailsApplication.config.indexLiveBaseUrl + "/select?" + query + additionalParams

        log.debug(queryUrl)
        def queryResponse = new URL(queryUrl).getText("UTF-8")

        def js = new JsonSlurper()

        def json = js.parseText(queryResponse)

        [
                totalRecords:json.response.numFound,
                facetResults: formatFacets(json.facet_counts?.facet_fields?:[:]),
                results: formatDocs(json.response.docs, null)
        ]
    }
    /**
     * Retrieve species & subspecies for the supplied taxon which have images.
     *
     * @param taxonID
     * @param start
     * @param rows
     * @return
     */
    def imageLinkSearch(taxonID, type, queryContext){
        def result = imageSearch(taxonID, 0, 1, queryContext)
        if (!result || result.isEmpty() || result.totalRecords == 0) {
            return null
        }
        def taxon = result.results.get(0)
        if (!taxon.image || taxon.image.isEmpty()) {
            return null
        }
        if (type == 'thumbnail') {
            return grailsApplication.config.imageThumbnailUrl + taxon.image
        } else if (type == 'small') {
            return grailsApplication.config.imageSmallUrl + taxon.image
        } else if (type == 'large') {
            return grailsApplication.config.imageLargeUrl + taxon.image
        } else {
            return grailsApplication.config.imageLargeUrl + taxon.image
        }
    }


    /**
     * General search service.
     *
     * @param requestedFacets
     * @return
     */
    def search(String q, GrailsParameterMap params, List requestedFacets) {
        params.remove("controller") // remove Grails stuff from query
        params.remove("action") // remove Grails stuff from query
        log.debug "params = ${params.toMapString()}"
        //String queryString = params.toQueryString() //DM - this screws up FQs
        def fqs = params.fq

        String qf = grailsApplication.config.solr.qf // dismax query fields
        String bq = grailsApplication.config.solr.bq  // dismax boost function
        String defType = grailsApplication.config.solr.defType // query parser type
        String qAlt = grailsApplication.config.solr.qAlt // if no query specified use this query
        String hl = grailsApplication.config.solr.hl // highlighting params (can be multiple)
        def additionalParams = "&qf=${qf}&${bq}&defType=${defType}&q.alt=${qAlt}&hl=${hl}&wt=json&facet=${!requestedFacets.isEmpty()}&facet.mincount=1"
        def queryTitle = q

        if (requestedFacets) {
            additionalParams = additionalParams + "&facet.field=" + requestedFacets.join("&facet.field=")
        }

        //pagination params
        additionalParams += "&start=${params.start?:0}&rows=${params.rows?:params.pageSize?:10}"

        if (params.sort) {
            additionalParams += "&sort=${params.sort}%20${params.dir?:'asc'}" // sort dir example "&sort=name asc"
        }

        if(fqs){
            if(isCollectionOrArray(fqs)){
                fqs.each {
                    additionalParams = additionalParams + "&fq=" + URLEncoder.encode(it, 'UTF-8')
                }
            } else {
                additionalParams = additionalParams + "&fq=" +  URLEncoder.encode(fqs, 'UTF-8')
            }
        }

        if (q) {
            if (!q) {
                q = q.replaceFirst("q=", "q=*:*")
            } else if (q.trim() == "*") {
                q = q.replaceFirst("q=*", "q=*:*")
            }
            // boost query syntax was removed from here. NdR.

            // Add fuzzy search term modifier to simple queries with > 1 term (e.g. no braces)
            if (!q.contains("(") && q.trim() =~ /\s+/) {
                q = q.replaceAll('"', " ").trim()
                def queryArray = []
                q.split(/\s+/).each {
                    if (!(it =~ /AND|OR|NOT/)) {
                        queryArray.add(it + "~0.8")
                    } else {
                        queryArray.add(it)
                    }
                }
                def nq = queryArray.join(" ")
                log.debug "fuzzy nq = ${nq}"
                q = "\"${q}\"^100 ${nq}"
            }
        } else {
            q = "*:*"
        }

        String solrUlr = grailsApplication.config.indexLiveBaseUrl + "/select?q=" + URLEncoder.encode(q, 'UTF-8') + additionalParams
        log.debug "solrUlr = ${solrUlr}"
        def queryResponse = new URL(solrUlr).getText("UTF-8")
        def js = new JsonSlurper()
        def json = js.parseText(queryResponse)

        if (json.response.numFound as Integer == 0) {

            try {

                //attempt to parse the name
                def nameParser = new NameParser()
                def parsedName = nameParser.parse(q)
                if (parsedName && parsedName.canonicalName()) {
                    def canonical = parsedName.canonicalName()
                    // TODO test if this breaks paginating through results... looks like it will
                    def sciNameQuery = grailsApplication.config.indexLiveBaseUrl + "/select?q=scientificName:\"" + URLEncoder.encode(canonical, "UTF-8") + "\"" + additionalParams
                    log.debug "sciNameQuery = ${sciNameQuery}"
                    queryResponse = new URL(sciNameQuery).getText("UTF-8")
                    js = new JsonSlurper()
                    json = js.parseText(queryResponse)
                }
            } catch(Exception e){
                //expected behaviour for non scientific name matches
                log.debug "expected behaviour for non scientific name matches: ${e}"
            }
        }

        def matcher = ( queryTitle =~ /(rkid_)([a-z]{1,})(:)(.*)/ )
        if(matcher.matches()){
            try {
                def rankName = matcher[0][2]
                def guid = matcher[0][4]
                def shortProfile = getShortProfile(guid)
                queryTitle = rankName + " " + shortProfile.scientificName
            } catch (Exception e){
                log.warn("Exception thrown parsing name..", e)
            }
        }

        if(!queryTitle){
            queryTitle = "all records"
        }

        log.debug("search called with q = ${q}, returning ${json.response.numFound}")

        [
            totalRecords: json.response.numFound,
            facetResults: formatFacets(json.facet_counts?.facet_fields ?: [:], requestedFacets),
            results     : formatDocs(json.response.docs, json.highlighting),
            queryTitle  : queryTitle
        ]
    }

    boolean isCollectionOrArray(object) {
        [Collection, Object[]].any { it.isAssignableFrom(object.getClass()) }
    }

    def getHabitats(){
        def queryUrl = grailsApplication.config.indexLiveBaseUrl + "/select?wt=json&rows=1000&q=idxtype:" + IndexDocType.HABITAT.toString()
        def queryResponse = new URL(queryUrl).getText("UTF-8")
        def js = new JsonSlurper()
        def json = js.parseText(queryResponse)
        def children = []
        def taxa = json.response.docs
        taxa.each { taxon ->
            children << [
                    guid:taxon.guid,
                    parentGuid: taxon.parentGuid,
                    name: taxon.name
            ]
        }
        children
    }

    def getHabitatsIDsByGuid(guid){
        def queryUrl = grailsApplication.config.indexLiveBaseUrl + "/select?wt=json&rows=1&q=idxtype:" + IndexDocType.HABITAT.toString() +
                "&fq=guid:\"" + URLEncoder.encode(guid, 'UTF-8') + "\""

        def queryResponse = new URL(queryUrl).getText("UTF-8")
        def js = new JsonSlurper()
        def json = js.parseText(queryResponse)

        //construct a tree
        def ids = []
        if(json.response.docs){
            def doc = json.response.docs[0]
            ids << doc.name
            ids << getChildHabitatIDs(doc.guid)
        }

        ids.flatten()
    }

    private def getChildHabitatIDs(guid){
        def queryUrl = grailsApplication.config.indexLiveBaseUrl + "/select?wt=json&rows=1000&q=idxtype:" + IndexDocType.HABITAT.toString() +
                "&fq=parentGuid:\"" + URLEncoder.encode(guid, 'UTF-8') + "\""

        def queryResponse = new URL(queryUrl).getText("UTF-8")
        def js = new JsonSlurper()
        def json = js.parseText(queryResponse)

        def ids = []
        //construct a tree
        json.response.docs.each {
            ids << it.name
            ids << getChildHabitatIDs(it.guid)
        }
        ids
    }

    def getHabitatByGuid(guid){
        def queryUrl = grailsApplication.config.indexLiveBaseUrl + "/select?wt=json&rows=1&q=idxtype:" + IndexDocType.HABITAT.toString() +
                "&fq=guid:\"" + URLEncoder.encode(guid, 'UTF-8') + "\""

        def queryResponse = new URL(queryUrl).getText("UTF-8")
        def js = new JsonSlurper()
        def json = js.parseText(queryResponse)

        //construct a tree
        def root = [:]
        if(json.response.docs){
            def doc = json.response.docs[0]
            return [
                    guid:doc.guid,
                    name: doc.name,
                    children: getChildHabitats(doc.guid)
            ]
        }
    }

    private def getChildHabitats(guid){
        def queryUrl = grailsApplication.config.indexLiveBaseUrl + "/select?wt=json&rows=1000&q=idxtype:" + IndexDocType.HABITAT.toString() +
                "&fq=parentGuid:\"" + URLEncoder.encode(guid, 'UTF-8') + "\""

        def queryResponse = new URL(queryUrl).getText("UTF-8")
        def js = new JsonSlurper()
        def json = js.parseText(queryResponse)

        def subTree = []
        //construct a tree
        json.response.docs.each {
            subTree << [
                    guid:it.guid,
                    name: it.name,
                    children: getChildHabitats(it.guid)
            ]
        }
        subTree
    }

    def getHabitatsTree(){
        def queryUrl = grailsApplication.config.indexLiveBaseUrl + "/select?wt=json&rows=1000&q=idxtype:" + IndexDocType.HABITAT.toString()
        def queryResponse = new URL(queryUrl).getText("UTF-8")
        def js = new JsonSlurper()
        def json = js.parseText(queryResponse)

        //construct a tree
        def root = [:]
        json.response.docs.each {
            if(!it.parentGuid){
                root[it.guid] = [
                    guid:it.guid,
                    name: it.name
                ]
            }
        }
        //look for children of the root
        def nodes = root.values()
        nodes.each { addChildren(json.response.docs, it) }
        root
    }

    private def addChildren(docs, node){
        docs.each {
            if(it.parentGuid && node.guid == it.parentGuid){
                if(!node.children){
                    node.children = [:]
                }
                def childNode = [
                        guid:it.guid,
                        name: it.name
                ]

                node.children[it.guid] = childNode
                addChildren(docs, childNode)
            }
        }
    }


    def getChildConcepts(taxonID, queryString){
        def encID = URLEncoder.encode(taxonID, "UTF-8")
        def queryUrl = grailsApplication.config.indexLiveBaseUrl + "/select?wt=json&rows=1000&q=parentGuid:\"" + encID + "\""

        if(queryString){
            queryUrl = queryUrl + "&" + queryString
        }

        def queryResponse = new URL(queryUrl).getText("UTF-8")
        def js = new JsonSlurper()
        def json = js.parseText(queryResponse)
        def children = []
        def taxa = json.response.docs
        taxa.each { taxon ->
            children << [
                    guid:taxon.guid,
                    parentGuid: taxon.parentGuid,
                    name: taxon.scientificName,
                    nameComplete: taxon.nameComplete ?: taxon.scientificName,
                    nameFormatted: taxon.nameFormatted,
                    author: taxon.scientificNameAuthorship,
                    rank: taxon.rank,
                    rankID:taxon.rankID
            ]
        }
        children.sort { it.name }
    }

    /**
     * Retrieve details of a taxon by taxonID
     *
     * @param taxonID
     * @param useOfflineIndex
     * @return
     */
    def lookupTaxon(String taxonID, Boolean useOfflineIndex = false){
        def indexServerUrlPrefix = grailsApplication.config.indexLiveBaseUrl

        if (useOfflineIndex) {
            indexServerUrlPrefix = grailsApplication.config.indexOfflineBaseUrl
        }

        def encID = URLEncoder.encode(taxonID, 'UTF-8')
        def indexServerUrl = indexServerUrlPrefix+ "/select?wt=json&q=guid:\"" + encID + "\"+OR+linkIdentifier:\"" + encID + "\"&fq=idxtype:" + IndexDocType.TAXON.name()
        def queryResponse = new URL(indexServerUrl).getText("UTF-8")
        def js = new JsonSlurper()
        def json = js.parseText(queryResponse)
        json.response.docs[0]
    }

    /**
     * Retrieve details of a taxon by common name or scientific name
     * @param taxonID
     * @return
     */
    private def lookupTaxonByName(String taxonName, Boolean useOfflineIndex = false){
        def indexServerUrlPrefix = grailsApplication.config.indexLiveBaseUrl
        if (useOfflineIndex) {
            indexServerUrlPrefix = grailsApplication.config.indexOfflineBaseUrl
        }
        def solrServerUrl = indexServerUrlPrefix + "/select?wt=json&q=" +URLEncoder.encode(
                "commonNameExact:\"" + taxonName + "\" OR scientificName:\"" + taxonName + "\" OR exact_text:\"" + taxonName + "\"",
                "UTF-8" // exact_text added to handle case differences in query vs index
        )
        def queryResponse = new URL(solrServerUrl).getText("UTF-8")
        def js = new JsonSlurper()
        def json = js.parseText(queryResponse)
        json.response.docs[0]
    }

    /**
     * Request is for an old identifier - lookup current taxon
     *
     * @param identifier
     * @param useOfflineIndex
     * @return
     * @throws Exception
     */
    private def lookupTaxonByPreviousIdentifier(String identifier, Boolean useOfflineIndex = false) throws Exception {
        def indexServerUrlPrefix = grailsApplication.config.indexLiveBaseUrl
        if (useOfflineIndex) {
            indexServerUrlPrefix = grailsApplication.config.indexOfflineBaseUrl
        }
        def solrServerUrl = indexServerUrlPrefix + "/select?wt=json&q=guid:\"" + URLEncoder.encode(identifier ,"UTF-8") +
                "\"&fq=idxtype:" + IndexDocType.IDENTIFIER.name()
        log.debug "SOLR url = ${solrServerUrl}"
        def queryResponse = new URL(solrServerUrl).getText("UTF-8")
        def js = new JsonSlurper()
        def json = js.parseText(queryResponse)

        def taxonGuid = ""
        def taxon = null

        if (json.response?.docs) {
            taxonGuid = json.response.docs[0].taxonGuid
        }

        if (taxonGuid) {
            taxon = lookupTaxon(taxonGuid, useOfflineIndex)
        }

        taxon
    }

    /**
     * Retrieve details of a specific vernacular name by taxonID
     *
     * @param taxonID The taxon identifier
     * @param name The vernacular name
     * @param useOfflineIndex
     * @return
     */
    def lookupVernacular(String taxonID, String vernacularName, Boolean useOfflineIndex = false){
        def indexServerUrlPrefix = useOfflineIndex ? grailsApplication.config.indexOfflineBaseUrl : grailsApplication.config.indexLiveBaseUrl
        def encID = URLEncoder.encode(taxonID, 'UTF-8')
        def encName = URLEncoder.encode(vernacularName, "UTF-8")
        def indexServerUrl = indexServerUrlPrefix+ "/select?wt=json&q=taxonGuid:\"${encID}\"&fq=(idxtype:${IndexDocType.COMMON.name()}+AND+name:\"${encName}\")"
        def queryResponse = new URL(indexServerUrl).getText("UTF-8")
        def js = new JsonSlurper()
        def json = js.parseText(queryResponse)
        json.response.docs[0]
    }

    /**
     * Retrieve details of a specific identifier by taxonID
     *
     * @param taxonID The taxon identifier
     * @param identifier The identifier
     * @param useOfflineIndex
     * @return
     */
    def lookupIdentifier(String taxonID, String identifier, Boolean useOfflineIndex = false){
        def indexServerUrlPrefix = useOfflineIndex ? grailsApplication.config.indexOfflineBaseUrl : grailsApplication.config.indexLiveBaseUrl
        def encID = URLEncoder.encode(taxonID, 'UTF-8')
        def encIdentifier = URLEncoder.encode(identifier, "UTF-8")
        def indexServerUrl = indexServerUrlPrefix+ "/select?wt=json&q=taxonGuid:\"${encID}\"&fq=(idxtype:${IndexDocType.IDENTIFIER.name()}+AND+guid:\"${encIdentifier}\")"
        def queryResponse = new URL(indexServerUrl).getText("UTF-8")
        def js = new JsonSlurper()
        def json = js.parseText(queryResponse)
        json.response.docs[0]
    }

    /**
     * Retrieve details of all vernacular names attached to a taxon.
     *
     * @param taxonID The taxon identifier
     * @param useOfflineIndex
     * @return
     */
    def lookupVernacular(String taxonID, Boolean useOfflineIndex = false){
        def indexServerUrlPrefix = useOfflineIndex ? grailsApplication.config.indexOfflineBaseUrl : grailsApplication.config.indexLiveBaseUrl
        def encID = URLEncoder.encode(taxonID, 'UTF-8')
        def indexServerUrl = indexServerUrlPrefix+ "/select?wt=json&q=taxonGuid:\"${encID}\"&fq=idxtype:${IndexDocType.COMMON.name()}"
        def queryResponse = new URL(indexServerUrl).getText("UTF-8")
        def js = new JsonSlurper()
        def json = js.parseText(queryResponse)
        json.response.docs
    }

    /**
     * Retrieve details of all identifiers attached to a taxon.
     *
     * @param taxonID The taxon identifier
     * @param useOfflineIndex
     * @return
     */
    def lookupIdentifier(String taxonID, Boolean useOfflineIndex = false){
        def indexServerUrlPrefix = useOfflineIndex ? grailsApplication.config.indexOfflineBaseUrl : grailsApplication.config.indexLiveBaseUrl
        def encID = URLEncoder.encode(taxonID, 'UTF-8')
        def indexServerUrl = indexServerUrlPrefix+ "/select?wt=json&q=taxonGuid:\"${encID}\"&fq=idxtype:${IndexDocType.IDENTIFIER.name()}"
        def queryResponse = new URL(indexServerUrl).getText("UTF-8")
        def js = new JsonSlurper()
        def json = js.parseText(queryResponse)
        json.response.docs
    }


    def getProfileForName(name){

        def additionalParams = "&wt=json"
        def queryString = "q=" + URLEncoder.encode(
                "commonNameExact:\"" + name + "\" OR scientificName:\"" + name + "\" OR exact_text:\"" + name + "\"",
                "UTF-8" // exact_text added to handle case differences in query vs index
        ) + "&fq=idxtype:" + IndexDocType.TAXON.name()
        log.debug "profile search for query: ${queryString}"
        def queryResponse = new URL(grailsApplication.config.indexLiveBaseUrl + "/select?" + queryString + additionalParams).getText("UTF-8")
        def js = new JsonSlurper()
        def json = js.parseText(queryResponse)
        def model = []
        if(json.response.numFound > 0){
            json.response.docs.each { result ->
                model << [
                    "identifier": result.guid,
                    "name": result.scientificName,
                    "acceptedIdentifier": result.acceptedConceptID ?: result.guid,
                    "acceptedName": result.acceptedConceptName
                ]
            }
        }
        model
    }

    def getShortProfile(taxonID){
        def taxon = lookupTaxon(taxonID)
        if(!taxon){
            return null
        }
        def classification = extractClassification(taxon)
        def model = [
                taxonID:taxon.guid,
                scientificName: taxon.scientificName,
                scientificNameAuthorship: taxon.scientificNameAuthorship,
                author: taxon.scientificNameAuthorship,
                rank: taxon.rank,
                rankID:taxon.rankID,
                kingdom: classification.kingdom?:"",
                family: classification.family?:""
        ]

        if (taxon.commonNameSingle) {
            model.put("commonName",  taxon.commonNameSingle)
        } else if (taxon.commonName){
            model.put("commonName",  taxon.commonName.first())
        }

        if(taxon.image){
            model.put("thumbnail", grailsApplication.config.imageThumbnailUrl + taxon.image)
            model.put("imageURL", grailsApplication.config.imageLargeUrl + taxon.image)
        }
        model
    }

    def getTaxa(List guidList){
        def resultMap = [:]
        def matchingTaxa = []

        while (!guidList.isEmpty()) {
            def batch = guidList.take(BULK_BATCH_SIZE)
            def batchSet = (batch.findAll { !resultMap.containsKey(it) }) as Set
            def matches = getTaxaBatch(batchSet)
            if (!(matches instanceof List)) // Error return
                return matches
            matches.each { match ->
                resultMap[match.guid] = match
                if (match.linkIdentifier)
                    resultMap[match.linkIdentifier] = match
            }
            batch.each { guid ->
                matchingTaxa << resultMap[guid]
            }
            guidList = guidList.drop(BULK_BATCH_SIZE)
        }
        return matchingTaxa
    }

    private getTaxaBatch(Collection guidList) {
        def queryList = guidList.collect({'"' + it + '"'}).join(',')
        def postBody = [
                q: "guid:(" + queryList + ") OR linkIdentifier:("  + queryList + ")",
                fq: "idxtype:" + IndexDocType.TAXON.name(),
                rows: BULK_BATCH_SIZE,
                wt: "json"
        ] // will be url-encoded
        def resp = doPostWithParams(grailsApplication.config.indexLiveBaseUrl +  "/select", postBody)

        //create the docs....
        if(resp?.resp?.response){

            def matchingTaxa = []

            resp.resp.response.docs.each { doc ->
               def taxon = [
                       guid: doc.guid,
                       name: doc.scientificName,
                       scientificName: doc.scientificName,
                       author: doc.scientificNameAuthorship,
                       nameComplete: doc.nameComplete?:doc.scientificName,
                       rank: doc.rank,
                       kingdom: doc.rk_kingdom,
                       phylum: doc.rk_phylum,
                       classs: doc.rk_class,
                       order: doc.rk_order,
                       family: doc.rk_family,
                       genus: doc.rk_genus,
                       datasetName: doc.datasetName,
                       datasetID: doc.datasetID
               ]
               if(doc.image){
                   taxon.put("thumbnailUrl", grailsApplication.config.imageThumbnailUrl + doc.image)
                   taxon.put("smallImageUrl", grailsApplication.config.imageSmallUrl + doc.image)
                   taxon.put("largeImageUrl", grailsApplication.config.imageLargeUrl + doc.image)
               }
                if (doc.linkIdentifier)
                    taxon.put("linkIdentifier", doc.linkIdentifier)
               if (doc.commonNameSingle) {
                   taxon.put("commonNameSingle", doc.commonNameSingle)
               } else if (doc.commonName) {
                   taxon.put("commonNameSingle", doc.commonName.first())
               }
               matchingTaxa << taxon
            }
            matchingTaxa
        } else {
            resp
        }
    }

    def getTaxon(taxonLookup){

        def taxon = lookupTaxon(taxonLookup)
        if(!taxon) {
            taxon = lookupTaxonByName(taxonLookup)
        }
        if(!taxon) {
            taxon = lookupTaxonByPreviousIdentifier(taxonLookup)
            if(!taxon){
                return null
            }
        }

        //retrieve any synonyms
        def synonymQueryUrl = grailsApplication.config.indexLiveBaseUrl + "/select?wt=json&q=" +
                URLEncoder.encode("acceptedConceptID:\"" + taxon.guid + "\"", "UTF-8") + "&fq=idxtype:" + IndexDocType.TAXON.name()
        def synonymQueryResponse = new URL(synonymQueryUrl).getText("UTF-8")
        def js = new JsonSlurper()
        def synJson = js.parseText(synonymQueryResponse)

        def synonyms = synJson.response.docs

        //retrieve any common names
        def commonQueryUrl = grailsApplication.config.indexLiveBaseUrl + "/select?wt=json&q=" +
                URLEncoder.encode("taxonGuid:\"" + taxon.guid + "\"", "UTF-8") + "&fq=idxtype:" + IndexDocType.COMMON.name()
        def commonQueryResponse = new URL(commonQueryUrl).getText("UTF-8")
        def commonJson = js.parseText(commonQueryResponse)
        def commonNames = commonJson.response.docs.sort { n1, n2 -> n2.priority - n1.priority }


        //retrieve any additional identifiers
        def identifierQueryUrl = grailsApplication.config.indexLiveBaseUrl + "/select?wt=json&q=" +
                URLEncoder.encode("taxonGuid:\"" + taxon.guid + "\"", "UTF-8") + "&fq=idxtype:" + IndexDocType.IDENTIFIER.name()
        def identifierQueryResponse = new URL(identifierQueryUrl).getText("UTF-8")
        def identifierJson = js.parseText(identifierQueryResponse)
        def identifiers = identifierJson.response.docs
        def classification = extractClassification(taxon)

        //Dataset index
        def datasetMap = [:]
        def taxonDatasetURL = getDataset(taxon.datasetID, datasetMap)?.guid
        def taxonDatasetName = getDataset(taxon.datasetID, datasetMap)?.name

        // Conservation status map
        def clists = conservationListsSource.lists ?: []
        def conservationStatus = clists.inject([:], { ac, cl ->
            final cs = taxon[cl.field]
            if (cs)
                ac.put(cl.label, [ dr: cl.uid, status: cs ])
            ac
        })

        def model = [
                taxonConcept:[
                        guid: taxon.guid,
                        parentGuid: taxon.parentGuid,
                        nameString: taxon.scientificName,
                        nameComplete: taxon.nameComplete,
                        nameFormatted: taxon.nameFormatted,
                        author: taxon.scientificNameAuthorship,
                        rankString: taxon.rank,
                        nameAuthority: taxon.datasetName ?: taxonDatasetName ?: grailsApplication.config.defaultNameSourceAttribution,
                        rankID:taxon.rankID,
                        namePublishedIn: taxon.namePublishedIn,
                        namePublishedInYear: taxon.namePublishedInYear,
                        namePublishedInID: taxon.namePublishedInID,
                        infoSourceURL: taxon.source ?: taxonDatasetURL,
                        datasetURL: taxonDatasetURL
                ],
                taxonName:[],
                classification:classification,
                synonyms:synonyms.collect { synonym ->
                    def datasetURL = getDataset(synonym.datasetID, datasetMap)?.guid
                    def datasetName = getDataset(synonym.datasetID, datasetMap)?.name
                    [
                            nameString: synonym.scientificName,
                            nameComplete: synonym.nameComplete,
                            nameFormatted: synonym.nameFormatted,
                            nameGuid: synonym.guid,
                            namePublishedIn: synonym.namePublishedIn,
                            namePublishedInYear: synonym.namePublishedInYear,
                            namePublishedInID: synonym.namePublishedInID,
                            nameAuthority: synonym.datasetName ?: datasetName ?: grailsApplication.config.synonymSourceAttribution,
                            infoSourceURL: synonym.source ?: datasetURL,
                            datasetURL: datasetURL
                    ]
                },
                commonNames: commonNames.collect { commonName ->
                    def datasetURL = getDataset(commonName.datasetID, datasetMap)?.guid
                    def datasetName = getDataset(commonName.datasetID, datasetMap)?.name
                    [
                            nameString: commonName.name,
                            status: commonName.status,
                            priority: commonName.priority,
                            language: commonName.language ?: grailsApplication.config.commonNameDefaultLanguage,
                            infoSourceName: commonName.datasetName ?: datasetName ?: grailsApplication.config.commonNameSourceAttribution,
                            infoSourceURL: commonName.source ?: datasetURL,
                            datasetURL: datasetURL
                    ]
                },
                imageIdentifier: taxon.image,
                conservationStatuses:conservationStatus,
                extantStatuses: [],
                habitats: [],
                categories: [],
                identifiers: identifiers.collect { identifier ->
                    def datasetURL = getDataset(identifier.datasetID, datasetMap)?.guid
                    def datasetName = getDataset(identifier.datasetID, datasetMap)?.name
                    [
                            identifier: identifier.guid,
                            nameString: identifier.name,
                            status: identifier.status,
                            subject: identifier.subject,
                            format: identifier.format,
                            infoSourceName: identifier.datasetName ?: datasetName ?: grailsApplication.config.identifierSourceAttribution,
                            infoSourceURL: identifier.source ?: datasetURL,
                            datasetURL: datasetURL
                    ]
                }
        ]
        if (taxon.acceptedConceptID)
            model.taxonConcept["acceptedConceptID"] = taxon.acceptedConceptID
        if (taxon.acceptedConceptName)
            model.taxonConcept["acceptedConceptName"] = taxon.acceptedConceptName
        model
    }

    /**
     * Retrieve a classification for the supplied taxonID.
     *
     * @param taxonID
     */
    def getClassification(taxonID){
        def classification = []
        def taxon = retrieveTaxon(taxonID)

        classification.add(0, [
                rank : taxon.rank,
                rankID : taxon.rankID,
                scientificName : taxon.scientificName,
                guid:taxonID
        ])

        //get parents
        def parentGuid = taxon.parentGuid
        def stop = false

        while(parentGuid && !stop){
            taxon = retrieveTaxon(parentGuid)
            if(taxon) {
                classification.add(0, [
                        rank : taxon.rank,
                        rankID : taxon.rankID,
                        scientificName : taxon.scientificName,
                        guid : taxon.guid
                ])
                parentGuid = taxon.parentGuid
            } else {
                stop = true
            }
        }
        classification
    }

    private def formatFacets(Map facetFields, List requestedFacets = []){
        def formatted = []

        if (requestedFacets) {
            // maintain order of facets from facets request parameter
            requestedFacets.each { facetName ->
                if (facetFields.containsKey(facetName)) {
                    def arrayValues = facetFields.get(facetName)
                    def facetValues = []
                    for (int i = 0; i < arrayValues.size(); i += 2) {
                        facetValues << [label: arrayValues[i], count: arrayValues[i + 1], fieldValue: arrayValues[i]]
                    }
                    formatted << [
                            fieldName  : facetName,
                            fieldResult: facetValues
                    ]
                }
            }

        }

        // Catch any remaining facets OR if requestedFacets is empty (not specified)
        facetFields.each { facetName, arrayValues ->
            if (!requestedFacets.contains(facetName)) {
                def facetValues = []
                for (int i =0; i < arrayValues.size(); i+=2){
                    facetValues << [label:arrayValues[i], count: arrayValues[i+1], fieldValue:arrayValues[i] ]
                }
                formatted << [
                        fieldName: facetName,
                        fieldResult: facetValues
                ]
            }
        }

        formatted
    }

    /**
     * Munge SOLR document set for output via JSON
     *
     * @param docs
     * @param highlighting
     * @return
     */
    private List formatDocs(docs, highlighting) {

        def formatted = []

        // add occurrence counts
        docs = populateOccurrenceCounts(docs)

        docs.each {
            if(it.idxtype == IndexDocType.TAXON.name()){

                def commonNameSingle = ""
                def commonNames = ""
                if (it.commonNameSingle)
                    commonNameSingle = it.commonNameSingle
                if(it.commonName){
                     commonNames = it.commonName.join(", ")
                    if (commonNameSingle.isEmpty())
                        commonNameSingle = it.commonName.first()
                }

                Map doc = [
                        "id" : it.id, // needed for highlighting
                        "guid" : it.guid,
                        "linkIdentifier" : it.linkIdentifier,
                        "idxtype": it.idxtype,
                        "name" : it.scientificName,
                        "kingdom" : it.rk_kingdom,
                        "scientificName" : it.scientificName,
                        "author" : it.scientificNameAuthorship,
                        "nameComplete" : it.nameComplete,
                        "nameFormatted" : it.nameFormatted,
                        "taxonomicStatus" : it.taxonomicStatus,
                        "parentGuid" : it.parentGuid,
                        "rank": it.rank,
                        "rankID": it.rankID ?: -1,
                        "commonName" : commonNames,
                        "commonNameSingle" : commonNameSingle,
                        "occurrenceCount" : it.occurrenceCount,
                        "conservationStatus" : it.conservationStatus
                ]

                if(it.acceptedConceptID){
                    doc.put("acceptedConceptID", it.acceptedConceptID)
                    if (it.acceptedConceptName)
                        doc.put("acceptedConceptName", it.acceptedConceptName)
                    doc.put("guid", it.acceptedConceptID)
                    doc.put("linkIdentifier", null)  // Otherwise points to the synonym
                }

                if(it.image){
                    doc.put("image", it.image)
                }

                //add de-normalised fields
                def map = extractClassification(it)

                doc.putAll(map)

                formatted << doc
            } else {
                Map doc = [
                        id : it.id,
                        guid : it.guid,
                        linkIdentifier : it.linkIdentifier,
                        idxtype: it.idxtype,
                        name : it.name,
                        description : it.description
                ]
                if (it.taxonGuid) {
                    doc.put("taxonGuid", it.taxonGuid)
                }
                if(it.centroid){
                    doc.put("centroid", it.centroid)
                }
                formatted << doc
            }
        }

        // highlighting should be a LinkedHashMap with key being the 'id' of the matching result
        highlighting.each { k, v ->
            if (v) {
                Map found = formatted.find { it.id == k }
                List snips = []
                v.each { field, snippetList ->
                    snips.addAll(snippetList)
                }
                found.put("highlight", snips.toSet().join("<br>"))
            }
        }
        formatted
    }

    private def retrieveTaxon(taxonID){
        def encID = URLEncoder.encode(taxonID, "UTF-8")
        def solrServerUrl = grailsApplication.config.indexLiveBaseUrl + "/select?wt=json&q=guid:\"" + encID + "\"+OR+linkIdentifier:\"" + encID +"\"&fq=idxtype:" + IndexDocType.TAXON.name()
        def queryResponse = new URL(solrServerUrl).getText("UTF-8")
        def js = new JsonSlurper()
        def json = js.parseText(queryResponse)
        json.response.docs[0]
    }

    private def extractClassification(queryResult) {
        def map = [:]
        if(queryResult){
            queryResult.keySet().each { key ->
                if (key.startsWith("rk_")) {
                    map.put(key.substring(3), queryResult.get(key))
                }
                if (key.startsWith("rkid_")) {
                    map.put(key.substring(5) + "Guid", queryResult.get(key))
                }
            }
        }
        map
    }

    def doPostWithParams(String url, Map params) {
        def conn = null
        def charEncoding = 'utf-8'
        try {
            String query = ""
            boolean first = true
            for (String name : params.keySet()) {
                query += first ? "?" : "&"
                first = false
                query += name.encodeAsURL() + "=" + params.get(name).encodeAsURL()
            }
            log.debug(url + query)
            conn = new URL(url + query).openConnection()
            conn.setRequestMethod("POST")
            conn.setDoOutput(true)
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

            OutputStreamWriter wr = new OutputStreamWriter(conn.getOutputStream(), charEncoding)

            wr.flush()
            def resp = conn.inputStream.text
            wr.close()
            return [resp: JSON.parse(resp?:"{}")] // fail over to empty json object if empty response string otherwise JSON.parse fails
        } catch (SocketTimeoutException e) {
            def error = [error: "Timed out calling web service. URL= ${url}."]
            log.error(error, e)
            return error
        } catch (Exception e) {
            def error = [error: "Failed calling web service. ${e.getMessage()} URL= ${url}.",
                         statusCode: conn?.responseCode?:"",
                         detail: conn?.errorStream?.text]
            log.error(error, e)
            return error
        }
    }

    def doPostWithParamsExc(String url, Map params) throws Exception {
        def conn = null
        def charEncoding = 'utf-8'
        String query = ""
        boolean first = true
        for (String name : params.keySet()) {
            query += first ? "?" : "&"
            first = false
            query += name.encodeAsURL() + "=" + params.get(name).encodeAsURL()
        }
        log.debug(url + query)
        conn = new URL(url + query).openConnection()
        conn.setRequestMethod("POST")
        conn.setDoOutput(true)
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

        OutputStreamWriter wr = new OutputStreamWriter(conn.getOutputStream(), charEncoding)

        wr.flush()
        def resp = conn.inputStream.text
        wr.close()
        return [resp: JSON.parse(resp?:"{}")] // fail over to empty json object if empty response string otherwise JSON.parse fails
    }

    def getDataset(String datasetID, Map datasets, boolean offline = false) {
        if (!datasetID)
            return null
        def dataset = datasets.get(datasetID)
        if (!dataset) {
            def datasetQueryUrl = (offline ? grailsApplication.config.indexOfflineBaseUrl : grailsApplication.config.indexLiveBaseUrl) + "/select?wt=json&q=" +
                    URLEncoder.encode("datasetID:\"" + datasetID + "\"", "UTF-8") + "&fq=idxtype:" + IndexDocType.DATARESOURCE.name()
            def datasetQueryResponse = new URL(datasetQueryUrl).getText("UTF-8")
            def js = new JsonSlurper()
            def datasetJson = js.parseText(datasetQueryResponse)
            dataset = datasetJson.response.docs && datasetJson.response.docs.size() > 0 ? datasetJson.response?.docs?.get(0) : null
            datasets.put(datasetID, dataset)
        }
        return dataset
    }

    /**
     * Add occurrence counts for taxa in results list
     * Taken from https://github.com/AtlasOfLivingAustralia/bie-service/blob/master/src/main/java/org/ala/web/SearchController.java#L369
     *
     * @param docs
     */
    private populateOccurrenceCounts(List docs) {
        List guids = []
        docs.each {
            if (it.idxtype == IndexDocType.TAXON.name() && it.guid) {
                guids.add(it.guid)
            }
        }

        if (guids.size() > 0) {
            try {
                def url = "${grailsApplication.config.biocacheService.baseUrl}/occurrences/taxaCount"
                Map params = [:]
                params.put("guids", guids.join(","))
                params.put("separator", ",")
                Map results = doPostWithParams(url, params) // returns (JsonObject) Map with guid as key and count as value
                Map guidsCountsMap = results.get("resp")?:[:]
                docs.each {
                    if (it.idxtype == IndexDocType.TAXON.name() && it.guid && guidsCountsMap.containsKey(it.guid))
                        it.put("occurrenceCount", guidsCountsMap.get(it.guid))
                }
            } catch (Exception ex) {
                // do nothing but log it
                log.error("Error populating occurrence counts: ${ex.message}", ex);
            }
        }
        docs
    }

    /**
     * Perform a cursor based SOLR search. USe of cursor results in more efficient and faster deep pagination of search results.
     * Which is useful for iterating over a large search results set.
     *
     * @param query
     * @param filterQuery
     * @param rows
     * @param cursor
     * @param useOfflineIndex
     */
    def getCursorSearchResults(MapSolrParams params, Boolean useOfflineIndex = false) throws Exception {
        def indexServerUrlPrefix = grailsApplication.config.indexLiveBaseUrl
        if (useOfflineIndex) {
            indexServerUrlPrefix = grailsApplication.config.indexOfflineBaseUrl
        }
        log.debug "SOLR params = ${params.toQueryString()}"
        def solrServerUrl = indexServerUrlPrefix + "/select" + params.toQueryString()
        def queryResponse = new URL(solrServerUrl).getText("UTF-8")
        def js = new JsonSlurper()
        def json = js.parseText(queryResponse)

        json
    }
}
