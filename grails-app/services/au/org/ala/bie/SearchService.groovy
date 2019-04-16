package au.org.ala.bie

import au.org.ala.bie.search.IndexDocType
import au.org.ala.bie.util.Encoder
import grails.web.servlet.mvc.GrailsParameterMap
import org.apache.solr.client.solrj.SolrQuery
import org.apache.solr.client.solrj.response.FacetField
import org.apache.solr.common.SolrDocument
import org.apache.solr.common.params.MapSolrParams
import org.gbif.nameparser.PhraseNameParser

import java.text.MessageFormat

/**
 * A set of search services for the BIE.
 */
class SearchService {

    static BULK_BATCH_SIZE = 20
    static GET_ALL_SIZE = 40

    def grailsApplication
    def conservationListsSource
    def indexService
    def biocacheService

    def additionalResultFields = null

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
            def tid = Encoder.escapeSolr(taxon.guid)
            query = "(guid:\"${tid}\" OR rkid_${taxon.rank.toLowerCase().replaceAll('\\s', '_')}:\"${tid}\")"
        }

        def response = indexService.query(true, query, ["rankID:[7000 TO *]", "imageAvailable:true"], rows, start, queryContext)
        log.debug "imageSearch response json = ${response}"

        [
                totalRecords: response.results.numFound,
                facetResults: formatFacets(response.facetFields),
                results: formatDocs(response.results, null, null)
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
            return MessageFormat.format(grailsApplication.config.images.image.thumbnail, taxon.image)
        } else if (type == 'small') {
            return MessageFormat.format(grailsApplication.config.images.image.small, taxon.image)
        } else if (type == 'large') {
            return MessageFormat.format(grailsApplication.config.images.image.large, taxon.image)
        } else {
            return MessageFormat.format(grailsApplication.config.images.image.large, taxon.image)
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
        def fqs = params.list('fq')
        def queryTitle = null
        def start = (params.start ?: 0) as Integer
        def rows = (params.rows ?: params.pageSize ?: 10) as Integer


        if (q) {
            queryTitle = q
            if (!q) {
                q = q.replaceFirst("q=", "q=*:*")
            } else if (q.trim() == "*") {
                q = q.replaceFirst("q=*", "q=*:*")
            }
            // boost query syntax was removed from here. NdR.

            // Add fuzzy search term modifier to simple queries with > 1 term (e.g. no braces)
            q = q.trim()
            if (!q.startsWith('(') && q.trim() =~ /\s+/) {
                def qs = q.replaceAll(/[^\p{Alnum}]+/, " ").trim()
                def queryArray = qs.split(/\s+/).findAll({ it.length() > 5}).collect({ it + "~0.8"})
                def nq = queryArray.join(" ")
                log.debug "fuzzy nq = ${nq}"
                q = "\"${q}\"^100 ${nq}"
            }
        } else {
            q = "*:*"
            queryTitle = "all records"
        }
        def response = indexService.search(true, q, fqs, requestedFacets, start, rows, params.sort, params.dir)

        if (response.results.numFound as Integer == 0) {

            try {

                //attempt to parse the name
                def nameParser = new PhraseNameParser()
                def parsedName = nameParser.parse(q)
                if (parsedName && parsedName.canonicalName()) {
                    def canonical = parsedName.canonicalName()
                    // TODO test if this breaks paginating through results... looks like it will
                    response = indexService.search(true, "scientificName:\"${canonical}\"", fqs, requestedFacets, start, rows, params.sort, params.dir)
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
                if (shortProfile)
                    queryTitle = rankName + " " + shortProfile.scientificName
                else
                    queryTitle = rankName + " " + guid
            } catch (Exception e){
                log.warn("Exception thrown parsing name..", e)
            }
        }

        if(!queryTitle){
            queryTitle = "all records"
        }

        log.debug("search called with q = ${q}, returning ${response.results.numFound}")

        [
            totalRecords: response.results.numFound,
            facetResults: formatFacets(response.facetFields, requestedFacets),
            results     : formatDocs(response.results, response.highlighting, params),
            queryTitle  : queryTitle
        ]
    }

    boolean isCollectionOrArray(object) {
        [Collection, Object[]].any { it.isAssignableFrom(object.getClass()) }
    }

    def getHabitats(){
        def children = []
        def response = indexService.query(true, "idxtype:${IndexDocType.HABITAT.name()}", [], 1000)
        return response.results.collect { taxon ->
             [
                    guid:taxon.guid,
                    parentGuid: taxon.parentGuid,
                    name: taxon.name
            ]
        }
    }

    def getHabitatsIDsByGuid(guid){
        guid = Encoder.escapeSolr(guid)
        def response = indexService.query(true, "guid:\"${guid}\"", [ "idxtype:${IndexDocType.HABITAT.name()}" ], 1, 0)

        //construct a tree
        def ids = []
        if(response.results){
            def doc = response.results.get(0)
            ids << doc.name
            ids << getChildHabitatIDs(doc.guid)
        }
        ids.flatten()
    }

    private def getChildHabitatIDs(guid){
        guid = Encoder.escapeSolr(guid)
        def response = indexService.query(true, "parentGuid:\"${guid}\"", [ "idxtype:${IndexDocType.HABITAT.name()}" ], 1000, 0)

        def ids = []
        //construct a tree
        response.results.each {
            ids << it.name
            ids << getChildHabitatIDs(it.guid)
        }
        ids
    }

    def getHabitatByGuid(guid){
        guid = Encoder.escapeSolr(guid)
        def response = indexService.query(true, "guid:\"${guid}\"", [ "idxtype:${IndexDocType.HABITAT.name()}" ], 1, 0)

        //construct a tree
        def root = [:]
        if(response.results){
            def doc = response.results.get(0)
            return [
                    guid:doc.guid,
                    name: doc.name,
                    children: getChildHabitats(doc.guid)
            ]
        }
    }

    private def getChildHabitats(guid){
        guid = Encoder.escapeSolr(guid)
        def response = indexService.query(true, "parentGuid:\"${guid}\"", [ "idxtype:${IndexDocType.HABITAT.name()}" ], 1000, 0)
        return response.results.collect {
             [
                    guid:it.guid,
                    name: it.name,
                    children: getChildHabitats(it.guid)
            ]
        }
    }

    def getHabitatsTree(){
        def response = indexService.query(true, "idxtype:${IndexDocType.HABITAT.name()}", [], 1000, 0)

        //construct a tree
        def root = [:]
        response.results.each {
            if(!it.parentGuid){
                root[it.guid] = [
                    guid:it.guid,
                    name: it.name
                ]
            }
        }
        //look for children of the root
        def nodes = root.values()
        nodes.each { addChildren(response.results, it) }
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


    def getChildConcepts(taxonID, queryString, within, unranked){
        def baseTaxon = lookupTaxon(taxonID)
        def baseRankID = baseTaxon?.rankID ?: -1
        def baseFq = "idxtype:${IndexDocType.TAXON.name()}"
        def fqs = [ baseFq ]
        if (baseRankID > 0 && within) {
            fqs << "rankID:[${unranked ? -1 : baseRankID + 1} TO ${baseRankID + within}]"
        }
        def q = "parentGuid:\"${ Encoder.escapeSolr(taxonID) }\""
        def response = indexService.query(true, q, fqs, 1000, 0, queryString)
        if (response.results.numFound == 0) {
            response = indexService.query(true, q, [ baseFq ], 1000, 0, queryString)
        }
        def children = []
        def taxa = response.results
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
        children.sort { c1, c2 ->
            def r1 = c1.rankID
            def r2 = c2.rankID
            if (r1 != null && r2 != null) {
                if (r1 <= 0 && r2 > 0)
                    return 10000
                if (r2 <= 0 && r1 > 0)
                    return -10000
                if (r2 != r1)
                    return r1 - r2
            }
            return c1.name?.compareTo(c2.name) ?: 0
        }
        children
    }

    /**
     * Retrieve details of a taxon by taxonID
     *
     * @param taxonID
     * @param useOfflineIndex
     * @return
     */
    def lookupTaxon(String taxonID, Boolean useOfflineIndex = false){
        def encID = Encoder.escapeSolr(taxonID)
        def response = indexService.query(!useOfflineIndex, "guid:\"${encID}\" OR linkIdentifier:\"${encID}\"", [ "idxtype:${ IndexDocType.TAXON.name() }" ], 1, 0)
        return response.results.isEmpty() ? null : response.results.get(0)
    }

    /**
     * Retrieve details of a taxon by common name or scientific name.
     * <p>
     * If not found, try looking for a taxon variant
     *
     * @param taxonName The taxon name
     * @param kingdom The kingdom, if available, to help disambiguate homonyms
     * @param useOfflineIndex Use the offline index for lookups
     * @return
     */
    def lookupTaxonByName(String taxonName, String kingdom, Boolean useOfflineIndex = false){
        taxonName = Encoder.escapeSolr(taxonName)
        def q = "+commonNameExact:\"${taxonName}\" OR +scientificName:\"${taxonName}\" OR +nameComplete:\"${taxonName} OR +exact_text:\"${taxonName}\""
        if (kingdom)
            q = "(${q}) AND rk_kingdom:\"${ Encoder.escapeSolr(kingdom) }\""
        def response = indexService.search(!useOfflineIndex, q, [ "idxtype:${ IndexDocType.TAXON.name() }" ], [], 0, 1)
        if (response.results.isEmpty()) {
            q = "+scientificName:\"${taxonName}\" OR +nameComplete:\"${taxonName}\""
            response = indexService.query(!useOfflineIndex, q, [ "idxtype:${ IndexDocType.TAXONVARIANT.name() }" ], 1, 0)
            if (!response.results.isEmpty())
                return lookupTaxon(response.results.get(0).taxonGuid, useOfflineIndex)
        }
        return response.results.isEmpty() ? null : response.results.get(0)
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
        identifier = Encoder.escapeSolr(identifier)
        def response = indexService.query(!useOfflineIndex, "guid:\"${identifier}\"", [], 1, 0)
        def taxonGuid = response.results.isEmpty() ? null : response.results.get(0).taxonGuid
        def taxon = null

        if (taxonGuid) {
            taxon = lookupTaxon(taxonGuid, useOfflineIndex)
        }
        return taxon
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
        taxonID = Encoder.escapeSolr(taxonID)
        vernacularName = Encoder.escapeSolr(vernacularName)
        def response = indexService.query(!useOfflineIndex, "taxonGuid:\"${taxonID}\"", [ "idxtype:${ IndexDocType.COMMON.name() }", "name:\"${vernacularName}\"" ], 1, 0)
        return response.results.isEmpty() ? null : response.results.get(0)
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
        taxonID = Encoder.escapeSolr(taxonID)
        identifier = Encoder.escapeSolr(identifier)
        def response = indexService.query(!useOfflineIndex, "taxonGuid:\"${taxonID}\"", [ "idxtype:${ IndexDocType.IDENTIFIER.name() }", "guid:\"${identifier}\"" ], 1, 0)
        return response.results.isEmpty() ? null : response.results.get(0)
    }

    /**
     * Retrieve details of all vernacular names attached to a taxon.
     *
     * @param taxonID The taxon identifier
     * @param useOfflineIndex
     * @return
     */
    def lookupVernacular(String taxonID, Boolean useOfflineIndex = false){
        taxonID = Encoder.escapeSolr(taxonID)
        def response = indexService.query(!useOfflineIndex, "taxonGuid:\"${taxonID}\"", [ "idxtype:${ IndexDocType.COMMON.name() }" ], GET_ALL_SIZE)
        return response.results
    }

    /**
     * Retrieve details of all identifiers attached to a taxon.
     *
     * @param taxonID The taxon identifier
     * @param useOfflineIndex
     * @return
     */
    def lookupIdentifier(String taxonID, Boolean useOfflineIndex = false){
        taxonID = Encoder.escapeSolr(taxonID)
        def response = indexService.query(!useOfflineIndex, "taxonGuid:\"${taxonID}\"", [ "idxtype:${ IndexDocType.IDENTIFIER.name() }" ], GET_ALL_SIZE)
        return response.results
    }
    
    /**
     * Retrieve details of all name vairants attached to a taxon.
     *
     * @param taxonID The taxon identifier
     * @param useOfflineIndex
     * @return
     */
    def lookupVariant(String taxonID, Boolean useOfflineIndex = false){
        taxonID = Encoder.escapeSolr(taxonID)
        def response = indexService.query(!useOfflineIndex, "taxonGuid:\"${taxonID}\"", [ "idxtype:${ IndexDocType.TAXONVARIANT.name() }" ], GET_ALL_SIZE)
        return response.results
    }

    /**
     * Return a simplified profile object for the docs that match the provided name
     *
     * @param name
     * @return Map with 4 fields
     */
    def getProfileForName(String name){
        name = Encoder.escapeSolr(name)
        def response = indexService.search(true, '"' + name + '"', [ "idxtype:${IndexDocType.TAXON.name()}" ])
        def model = []

        if (response.results.numFound > 0) {
            response.results.each { result ->
                model << [
                    "identifier": result.guid,
                    "name": result.scientificName,
                    "acceptedIdentifier": result.acceptedConceptID ?: (result.taxonomicStatus == "accepted" ? result.guid : ""),
                    "acceptedName": result.acceptedConceptName ?: (result.taxonomicStatus == "accepted" ? result.scientificName : "")
                ]
            }
        }

        model
    }

    Map getLongProfileForName(String name){
        name = Encoder.escapeSolr(name)
        def response = indexService.search(true, '"' + name + '"', [ "idxtype:${IndexDocType.TAXON.name()}" ])
        def model = [:]
        if (response.results.numFound > 0) {
            def result = response.results.get(0)
            //json.response.docs.each { result ->
                model = [
                        "identifier": result.guid,
                        "guid": result.guid,
                        "parentGuid": result.parentGuid,
                        "name": result.scientificName,
                        "nameComplete": result.nameComplete,
                        "commonName" : result.commonName,
                        "commonNameSingle" : result.commonNameSingle,
                        "rank" : result.rank,
                        "rankId" : result.rankID,
                        "acceptedConceptGuid": result.acceptedConceptID ?: result.guid,
                        "acceptedConceptName": result.acceptedConceptName ?: result.scientificName,
                        "taxonomicStatus": result.taxonomicStatus,
                        "imageId": result.image,
                        "imageUrl": (result.image) ? MessageFormat.format(grailsApplication.config.images.service.large, result.image) : "",
                        "thumbnailUrl": (result.image) ? MessageFormat.format(grailsApplication.config.images.service.thumbnail, result.image) : "",
                        "largeImageUrl": (result.image) ? MessageFormat.format(grailsApplication.config.images.service.large, result.image) : "",
                        "smallImageUrl": (result.image) ? MessageFormat.format(grailsApplication.config.images.service.small, result.image) : "",
                        "imageMetadataUrl": (result.image) ? MessageFormat.format(grailsApplication.config.images.service.metadata, result.image) : "",
                        "kingdom": result.rk_kingdom,
                        "phylum": result.rk_phylum,
                        "classs": result.rk_class,
                        "order":result.rk_order,
                        "family": result.rk_family,
                        "genus": result.rk_genus,
                        "author": result.scientificNameAuthorship,
                        "linkIdentifier": result.linkIdentifier
                ]

        }

        model
    }

    def getShortProfile(taxonID){
        log.debug "getShortProfile taxonID = ${taxonID}"
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
            model.put("thumbnail", MessageFormat.format(grailsApplication.config.images.service.thumbnail, taxon.image))
            model.put("imageURL", MessageFormat.format(grailsApplication.config.images.service.large, taxon.image))
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
            if (!matches) // Error return
                return null
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
        if (!guidList)
            return []
        def queryList = guidList.collect({'"' + Encoder.escapeSolr(it) + '"'}).join(',')
        def response = indexService.query(true, "guid:(${queryList}) OR linkIdentifier:(${queryList})", [ "idxtype:${IndexDocType.TAXON.name()}" ], BULK_BATCH_SIZE, 0)

        //create the docs....
        if(response.results.numFound > 0){

            def matchingTaxa = []

            response.results.each { doc ->
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
                   taxon.put("thumbnailUrl", MessageFormat.format(grailsApplication.config.images.service.thumbnail, doc.image))
                   taxon.put("smallImageUrl", MessageFormat.format(grailsApplication.config.images.service.small, doc.image))
                   taxon.put("largeImageUrl", MessageFormat.format(grailsApplication.config.images.service.large, doc.image))
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
            return matchingTaxa
        } else {
            return null
        }
    }

    def getTaxon(taxonLookup){

        def taxon = lookupTaxon(taxonLookup)
        if(!taxon) {
            taxon = lookupTaxonByName(taxonLookup, null)
        }
        if(!taxon) {
            taxon = lookupTaxonByPreviousIdentifier(taxonLookup)
            if(!taxon){
                return null
            }
        }

        //retrieve any synonyms
        def encGuid = Encoder.escapeSolr(taxon.guid)
        def response = indexService.query(true, "acceptedConceptID:\"${encGuid}\"", [ "idxtype:${IndexDocType.TAXON.name()}"], GET_ALL_SIZE)
        def synonyms = response.results

        def classification = extractClassification(taxon)

        //retrieve any common names
        response = indexService.query(true, "taxonGuid:\"${encGuid}\"", [ "idxtype:${IndexDocType.COMMON.name()}"], GET_ALL_SIZE)
        def commonNames = response.results.sort { n1, n2 -> n2.priority - n1.priority }


        //retrieve any additional identifiers
        response = indexService.query(true, "taxonGuid:\"${encGuid}\"", [ "idxtype:${IndexDocType.IDENTIFIER.name()}"], GET_ALL_SIZE)
        def identifiers = response.results


        // retrieve any variants
        response = indexService.query(true, "taxonGuid:\"${encGuid}\"", [ "idxtype:${IndexDocType.TAXONVARIANT.name()}"], GET_ALL_SIZE)
        def variants = response.results
        
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
                        nomenclaturalCode: taxon.nomenclaturalCode,
                        taxonomicStatus: taxon.taxonomicStatus,
                        nomenclaturalStatus: taxon.nomenclaturalStatus,
                        rankString: taxon.rank,
                        nameAuthority: taxon.datasetName ?: taxonDatasetName ?: grailsApplication.config.attribution.default,
                        rankID:taxon.rankID,
                        nameAccordingTo: taxon.nameAccordingTo,
                        nameAccordingToID: taxon.nameAccordingToID,
                        namePublishedIn: taxon.namePublishedIn,
                        namePublishedInYear: taxon.namePublishedInYear,
                        namePublishedInID: taxon.namePublishedInID,
                        taxonRemarks: taxon.taxonRemarks,
                        provenance: taxon.provenance,
                        favourite: taxon.favourite,
                        infoSourceURL: taxon.source ?: taxonDatasetURL,
                        datasetURL: taxonDatasetURL
                ],
                taxonName:[],
                classification: classification,
                synonyms:synonyms.collect { synonym ->
                    def datasetURL = getDataset(synonym.datasetID, datasetMap)?.guid
                    def datasetName = getDataset(synonym.datasetID, datasetMap)?.name
                    [
                            nameString: synonym.scientificName,
                            nameComplete: synonym.nameComplete,
                            nameFormatted: synonym.nameFormatted,
                            nameGuid: synonym.guid,
                            nomenclaturalCode: synonym.nomenclaturalCode,
                            taxonomicStatus: synonym.taxonomicStatus,
                            nomenclaturalStatus: synonym.nomenclaturalStatus,
                            nameAccordingTo: synonym.nameAccordingTo,
                            nameAccordingToID: synonym.nameAccordingToID,
                            namePublishedIn: synonym.namePublishedIn,
                            namePublishedInYear: synonym.namePublishedInYear,
                            namePublishedInID: synonym.namePublishedInID,
                            nameAuthority: synonym.datasetName ?: datasetName ?: grailsApplication.config.attribution.synonym ?: grailsApplication.config.attribution.default,
                            taxonRemarks: synonym.taxonRemarks,
                            provenance: synonym.provenance,
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
                            temporal: commonName.temporal,
                            locationID: commonName.locationID,
                            locality: commonName.locality,
                            countryCode: commonName.countryCode,
                            sex: commonName.sex,
                            lifeStage: commonName.lifeStage,
                            isPlural: commonName.isPlural,
                            organismPart: commonName.organismPart,
                            taxonRemarks: commonName.taxonRemarks,
                            provenance: commonName.provenance,
                            labels: commonName.labels,
                            infoSourceName: commonName.datasetName ?: datasetName ?: grailsApplication.config.attribution.common ?: grailsApplication.config.attribution.default,
                            infoSourceURL: commonName.source ?: datasetURL,
                            datasetURL: datasetURL
                    ]
                },
                imageIdentifier: taxon.image,
                conservationStatuses:conservationStatus,
                extantStatuses: [],
                habitats: [],
                categories: [],
                simpleProperties: [],
                images: [],
                identifiers: identifiers.collect { identifier ->
                    def datasetURL = getDataset(identifier.datasetID, datasetMap)?.guid
                    def datasetName = getDataset(identifier.datasetID, datasetMap)?.name
                    [
                            identifier: identifier.guid,
                            nameString: identifier.name,
                            status: identifier.status,
                            subject: identifier.subject,
                            format: identifier.format,
                            provenance: identifier.provenance,
                            infoSourceName: identifier.datasetName ?: datasetName ?: grailsApplication.config.attribution.identifier ?: grailsApplication.config.attribution.default,
                            infoSourceURL: identifier.source ?: datasetURL,
                            datasetURL: datasetURL
                    ]
                },
                variants: variants.collect { variant ->
                    def datasetURL = getDataset(variant.datasetID, datasetMap)?.guid
                    def datasetName = getDataset(variant.datasetID, datasetMap)?.name
                    [
                            nameString: variant.scientificName,
                            nameComplete: variant.nameComplete,
                            nameFormatted: variant.nameFormatted,
                            identifier: variant.guid,
                            nomenclaturalCode: variant.nomenclaturalCode,
                            taxonomicStatus: variant.taxonomicStatus,
                            nomenclaturalStatus: variant.nomenclaturalStatus,
                            nameAccordingTo: variant.nameAccordingTo,
                            nameAccordingToID: variant.nameAccordingToID,
                            namePublishedIn: variant.namePublishedIn,
                            namePublishedInYear: variant.namePublishedInYear,
                            namePublishedInID: variant.namePublishedInID,
                            nameAuthority: variant.datasetName ?: datasetName ?: grailsApplication.config.variantSourceAttribution,
                            taxonRemarks: variant.taxonRemarks,
                            provenance: variant.provenance,
                            infoSourceName: variant.datasetName ?: datasetName ?: grailsApplication.config.variantSourceAttribution,
                            infoSourceURL: variant.source ?: datasetURL,
                            datasetURL: datasetURL,
                            priority: variant.priority
                    ]
                }

        ]
        if (taxon.taxonConceptID)
            model.taxonConcept["taxonConceptID"] = taxon.taxonConceptID
        if (taxon.scientificNameID)
            model.taxonConcept["scientificNameID"] = taxon.scientificNameID
        if (taxon.acceptedConceptID)
            model.taxonConcept["acceptedConceptID"] = taxon.acceptedConceptID
        if (taxon.acceptedConceptName)
            model.taxonConcept["acceptedConceptName"] = taxon.acceptedConceptName
        
        if(getAdditionalResultFields()) {
            def doc = [:]
            getAdditionalResultFields().each { field ->
                if (taxon."${field}") {
                    doc.put(field, taxon."${field}")
                }
            }

            model << doc
        }
        
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

        if (!taxon) return classification // empty list

        classification.add(0, [
                rank : taxon.rank,
                rankID : taxon.rankID,
                scientificName : taxon.scientificName,
                guid:taxonID
        ])

        //get parents
        def parentGuid = taxon.parentGuid
        def seen = [] as Set
        def stop = false

        while(parentGuid && !stop){
            taxon = retrieveTaxon(parentGuid)
            if(taxon && !seen.contains(taxon.guid)) {
                classification.add(0, [
                        rank : taxon.rank,
                        rankID : taxon.rankID,
                        scientificName : taxon.scientificName,
                        guid : taxon.guid
                ])
                seen.add(taxon.guid)
                parentGuid = taxon.parentGuid
            } else {
                stop = true
            }
        }
        classification
    }

    private def formatFacets(List<FacetField> facetFields, List requestedFacets = []){
        def formatted = facetFields.collect { field ->
            def values = field.values.collect { count ->
                [ label: count.name, count: count.count, fieldValue: count.name, fq: count.asFilterQuery ]
            }
            [ fieldName: field.name, fieldResult: values ]
        }

        if (!requestedFacets)
            return formatted
        def ordered = []
        requestedFacets.each { name ->
            def facet = formatted.find { it.fieldName == name }
            if (facet) {
                formatted.remove(facet)
                ordered << facet
            }
        }
        ordered.addAll(formatted)
        return ordered
    }

    /**
     * Munge SOLR document set for output via JSON
     *
     * @param docs
     * @param highlighting
     * @return
     */
    private List formatDocs(List<SolrDocument> docs, highlighting, params) {

        def formatted = []
        def fields = params?.fields?.split(",")?.collect({ String f -> f.trim() }) as Set

        // add occurrence counts
        if(grailsApplication.config.biocache.occurrenceCount.enabled as Boolean){
            docs = populateOccurrenceCounts(docs, params)
        }

        docs.each {
            Map doc = null
            if (it.idxtype == IndexDocType.TAXON.name()) {

                def commonNameSingle = ""
                def commonNames = ""
                if (it.commonNameSingle)
                    commonNameSingle = it.commonNameSingle
                if (it.commonName) {
                    commonNames = it.commonName.join(", ")
                    if (commonNameSingle.isEmpty())
                        commonNameSingle = it.commonName.first()
                }

                doc = [
                        "id"                      : it.id, // needed for highlighting
                        "guid"                    : it.guid,
                        "linkIdentifier"          : it.linkIdentifier,
                        "idxtype"                 : it.idxtype,
                        "name"                    : it.scientificName,
                        "kingdom"                 : it.rk_kingdom,
                        "nomenclaturalCode"       : it.nomenclaturalCode,
                        "scientificName"          : it.scientificName,
                        "scientificNameAuthorship": it.scientificNameAuthorship,
                        "author"                  : it.scientificNameAuthorship,
                        "nameComplete"            : it.nameComplete,
                        "nameFormatted"           : it.nameFormatted,
                        "taxonomicStatus"         : it.taxonomicStatus,
                        "nomenclaturalStatus"     : it.nomenclaturalStatus,
                        "parentGuid"              : it.parentGuid,
                        "rank"                    : it.rank,
                        "rankID"                  : it.rankID ?: -1,
                        "commonName"              : commonNames,
                        "commonNameSingle"        : commonNameSingle,
                        "occurrenceCount"         : it.occurrenceCount,
                        "conservationStatus"      : it.conservationStatus,
                        "favourite"               : it.favourite,
                        "infoSourceName"          : it.datasetName,
                        "infoSourceURL"           : "${grailsApplication.config.collectory.base}/public/show/${it.datasetID}"
                ]

                if (it.acceptedConceptID) {
                    doc.put("acceptedConceptID", it.acceptedConceptID)
                    if (it.acceptedConceptName)
                        doc.put("acceptedConceptName", it.acceptedConceptName)
                    doc.put("guid", it.acceptedConceptID)
                    doc.put("linkIdentifier", null)  // Otherwise points to the synonym
                }

                if (it.image) {
                    doc.put("image", it.image)
                    doc.put("imageUrl", MessageFormat.format(grailsApplication.config.images.service.small, it.image))
                    doc.put("thumbnailUrl", MessageFormat.format(grailsApplication.config.images.service.thumbnail, it.image))
                    doc.put("smallImageUrl", MessageFormat.format(grailsApplication.config.images.service.small, it.image))
                    doc.put("largeImageUrl", MessageFormat.format(grailsApplication.config.images.service.large, it.image))
                }
                //add de-normalised fields
                def map = extractClassification(it)

                doc.putAll(map)
            } else if (it.idxtype == IndexDocType.TAXONVARIANT.name()){
                doc = [
                        "id" : it.id, // needed for highlighting
                        "guid" : it.guid,
                        "taxonGuid" : it.taxonGuid,
                        "linkIdentifier" : it.linkIdentifier,
                        "idxtype": it.idxtype,
                        "name" : it.scientificName,
                        "nomenclaturalCode" : it.nomenclaturalCode,
                        "scientificName" : it.scientificName,
                        "scientificNameAuthorship" : it.scientificNameAuthorship,
                        "author" : it.scientificNameAuthorship,
                        "nameComplete" : it.nameComplete,
                        "nameFormatted" : it.nameFormatted,
                        "taxonomicStatus" : it.taxonomicStatus,
                        "nomenclaturalStatus" : it.nomenclaturalStatus,
                        "rank": it.rank,
                        "rankID": it.rankID ?: -1,
                        "infoSourceName" : it.datasetName,
                        "infoSourceURL" : "${grailsApplication.config.collectory.base}/public/show/${it.datasetID}"
                ]
            } else if (it.idxtype == IndexDocType.COMMON.name()){
                doc = [
                        "id" : it.id, // needed for highlighting
                        "guid" : it.guid,
                        "taxonGuid" : it.taxonGuid,
                        "linkIdentifier" : it.linkIdentifier,
                        "idxtype": it.idxtype,
                        "name" : it.name,
                        "acceptedConceptName": it.acceptedConceptName,
                        "favourite": it.favourite,
                        "infoSourceName" : it.datasetName,
                        "infoSourceURL" : "${grailsApplication.config.collectory.base}/public/show/${it.datasetID}"
                ]
                if (it.image) {
                    doc.put("image", it.image)
                    doc.put("imageUrl", MessageFormat.format(grailsApplication.config.images.service.small, it.image))
                    doc.put("thumbnailUrl", MessageFormat.format(grailsApplication.config.images.service.thumbnail, it.image))
                    doc.put("smallImageUrl", MessageFormat.format(grailsApplication.config.images.service.small, it.image))
                    doc.put("largeImageUrl", MessageFormat.format(grailsApplication.config.images.service.large, it.image))
                }
            } else {
                doc = [
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
                if(it.favourite){
                    doc.put("favourite", it.favourite)
                }
            }
            if (doc) {
                if(getAdditionalResultFields()){
                    getAdditionalResultFields().each { field ->
                        if(it."${field}") {
                            doc.put(field, it."${field}")
                        }
                    }
                }
                if (fields)
                    doc = doc.subMap(fields)
                formatted << doc
            }
        }

        // highlighting should be a LinkedHashMap with key being the 'id' of the matching result
        highlighting.each { k, v ->
            if (v) {
                Map found = formatted.find { it.id == k }
                if (found) {
                    List snips = []
                    v.each { field, snippetList ->
                        snips.addAll(snippetList)
                    }
                    found.put("highlight", snips.toSet().join(grailsApplication.config.search.highlight.join))
                }
            }
        }
        formatted
    }

    private def retrieveTaxon(taxonID){
        taxonID = Encoder.escapeSolr(taxonID)
        def response = indexService.query(true, "guid:\"${taxonID}\" OR linkIdentifier:\"${taxonID}\"", [ "idxtype:${IndexDocType.TAXON.name()}" ], 1, 0)
        return response.results.isEmpty() ? null : response.results.get(0)
    }

    private def extractClassification(queryResult) {
        def map = [:]
        def rankKey = "rank"
        log.debug "queryResult = ${queryResult.getClass().name}"
        Map thisTaxonFields = [
                scientificName: "scientificName",
                guid: "guid",
                taxonConcept: "taxonConceptID"
        ]
        if(queryResult){
            queryResult.keySet().each { key ->
                if (key.startsWith("rk_")) {
                    map.put(key.substring(3), queryResult.get(key))
                } else if (key.startsWith("rkid_")) {
                    map.put(key.substring(5) + "Guid", queryResult.get(key))
                }
            }
            thisTaxonFields.each { key, value ->
                if (queryResult.containsKey(key))
                    map.put(value, queryResult.get(key))
            }
            if (queryResult.containsKey(rankKey)) {
                map.put(queryResult.get(rankKey), queryResult.get("scientificName")) // current name in classification
                map.put(queryResult.get(rankKey) + "Guid", queryResult.get("guid"))
            }
        }
        map
    }

    def getDataset(String datasetID, Map datasets, boolean offline = false) {
        if (!datasetID)
            return null
        def dataset = datasets.get(datasetID)
        if (!dataset) {
            def response = indexService.query(!offline, "datasetID:\"${ Encoder.escapeSolr(datasetID) }\"", [ "idxtype:${IndexDocType.DATARESOURCE.name()}" ], 1, 0)
            dataset = response.results.isEmpty() ? null : response.results.get(0)
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
    private populateOccurrenceCounts(List<SolrDocument> docs, requestParams) {
        List guids = []
        docs.each {
            if (it.idxtype == IndexDocType.TAXON.name() && it.guid) {
                guids.add(it.guid)
            }
        }
        def counts = biocacheService.counts(guids)
        docs.each {
            if (it.idxtype == IndexDocType.TAXON.name() && it.guid && counts.containsKey(it.guid))
                it.put("occurrenceCount", counts.get(it.guid))
        }
        docs
    }

    /**
     * Perform a cursor based SOLR search. USe of cursor results in more efficient and faster deep pagination of search results.
     * Which is useful for iterating over a large search results set.
     *
     * @param useOfflineIndex
     */
    def getCursorSearchResults(MapSolrParams params, Boolean useOfflineIndex = false) throws Exception {
        def query = new SolrQuery()
        query.add(params)
        return indexService.query(query, !useOfflineIndex)
    }

    def getAdditionalResultFields(){
        if(additionalResultFields == null){
            //initialise
            def fields = grailsApplication.config.additionalResultFields.split(",").findAll { !it.isEmpty() }
            additionalResultFields = fields.collect { it }
        }
        additionalResultFields
    }
}
