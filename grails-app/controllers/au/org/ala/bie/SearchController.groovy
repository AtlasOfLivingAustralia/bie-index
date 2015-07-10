package au.org.ala.bie

import grails.converters.JSON
import groovy.json.JsonSlurper
import org.apache.commons.io.FilenameUtils

class SearchController {

    def grailsApplication

    static defaultAction = "search"

    /**
     * Retrieve a classification for the supplied taxon.
     *
     * @return
     */
    def classification(){

        def classification = []
        def taxon = retrieveTaxon(params.id)
        
        classification.add(0, [
                rank : taxon.rank,
                rankID : taxon.rankID,
                scientificName : taxon.scientificName,
                guid:params.id
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
        render classification as JSON
    }

    /**
     * Returns taxa with images.
     *
     * @return
     */
    def imageSearch(){

        def additionalParams = "&wt=json&fq=rankID:%5B7000%20TO%20*%5D&fq=imageAvailable:yes"

        def rank = params.taxonRank?:''.trim()
        def scientificName = params.scientificName?:''.trim()

        def query = ""

        if(rank && scientificName){
            //append to query
            query = "q=*:*&fq=rk_" + rank.toLowerCase() + ":\"" +  URLEncoder.encode(scientificName, "UTF-8") + "\""
        } else {
            query = "q=*:*"
        }

        if(params.start){
            additionalParams = additionalParams + "&start=" + params.start
        }

        if(params.rows){
            additionalParams = additionalParams + "&rows=" + params.rows
        }

        println(grailsApplication.config.solrBaseUrl + "/select?" + query + additionalParams)
        def queryResponse = new URL(grailsApplication.config.solrBaseUrl + "/select?" + query + additionalParams).getText("UTF-8")

        def js = new JsonSlurper()

        def json = js.parseText(queryResponse)

        def model = [
                searchResults:[
                        totalRecords:json.response.numFound,
                        facetResults: formatFacets(json.facet_counts?.facet_fields?:[]),
                        results: formatDocs(json.response.docs)
                ]
        ]

        render (model as JSON)
    }

    /**
     * Retrieves child concepts for the supplied taxon ID
     *
     * @return
     */
    def childConcepts(){

        def solrServerUrl = grailsApplication.config.solrBaseUrl + "/select?wt=json&rows=1000&q=parentGuid:\"" + params.id + "\""
        def queryResponse = new URL(solrServerUrl).getText("UTF-8")
        def js = new JsonSlurper()
        def json = js.parseText(queryResponse)
        def children = []
        def taxa = json.response.docs
        taxa.each { taxon ->
            children << [
                    guid:taxon.guid,
                    parentGuid: taxon.parentGuid,
                    name: taxon.scientificName,
                    nameComplete: taxon.scientificName,
                    author: taxon.scientificNameAuthorship,
                    rank: taxon.rank,
                    rankID:taxon.rankID
            ]
        }

        render (children as JSON)
    }

    private def retrieveTaxon(taxonID){
        def solrServerUrl = grailsApplication.config.solrBaseUrl + "/select?wt=json&q=guid:\"" + taxonID + "\""
        def queryResponse = new URL(solrServerUrl).getText("UTF-8")
        def js = new JsonSlurper()
        def json = js.parseText(queryResponse)
        json.response.docs[0]
    }

    /**
     * Retrieves a profile for a taxon.
     *
     * @return
     */
    def taxon(){

        if(params.id == 'favicon') return; //not sure why this is happening....

        def solrServerUrl = grailsApplication.config.solrBaseUrl + "/select?wt=json&q=guid:\"" + params.id + "\""
        def queryResponse = new URL(solrServerUrl).getText("UTF-8")
        def js = new JsonSlurper()
        def json = js.parseText(queryResponse)

        def taxon = json.response.docs[0]
        if(!taxon){
            response.sendError(404, "Taxon identifier not recognised")
            return
        }

        //retrieve any synonyms
        def synonymQueryUrl = grailsApplication.config.solrBaseUrl + "/select?wt=json&q=acceptedConceptID:\"" + params.id + "\""
        def synonymQueryResponse = new URL(synonymQueryUrl).getText("UTF-8")
        def synJson = js.parseText(synonymQueryResponse)

        def synonyms = synJson.response.docs

        def classification = extractClassification(taxon)

        def model = [
                taxonConcept:[
                        guid:taxon.guid,
                        parentGuid: taxon.parentGuid,
                        nameString: taxon.scientificName,
                        author: taxon.scientificNameAuthorship,
                        rankString: taxon.rank,
                        nameAuthority: taxon.dataset ?: grailsApplication.config.defaultNameSourceAttribution,
                        rankID:taxon.rankID
                ],
                taxonName:[],
                classification:classification,
                synonyms:[],
                commonNames:{
                    def cn = []
                    taxon.commonName.each {
                        cn << [
                            nameString: it,
                            infoSourceName: grailsApplication.config.commonNameSourceAttribution
                        ]
                    }
                    cn
                }.call(),
                conservationStatuses:[], //TODO need to be indexed from list tool
                extantStatuses: [],
                habitats: [],
                identifiers: []
        ]

        synonyms.each { synonym ->
            model.synonyms << [
                    nameString: synonym.scientificName,
                    nameGuid: synonym.guid
            ]
        }

        render (model as JSON)
    }

    def auto(){

        log.debug("auto called with q = " + params.q)
        def autoCompleteList = []
        def additionalParams = "&wt=json"
        def queryString = request.queryString

        if(queryString) {
            if (!params.q) {
                queryString = request.queryString.replaceFirst("q=", "q=*:*")
            } else if (params.q.trim() == "*") {
                queryString = request.queryString.replaceFirst("q=*", "q=*:*")
            }
        } else {
            queryString = "q=*:*"
        }

        def queryResponse = new URL(grailsApplication.config.solrBaseUrl + "/select?" + queryString + additionalParams).getText("UTF-8")
        def js = new JsonSlurper()
        def json = js.parseText(queryResponse)

        json.response.docs.each {
            def result = [
                "guid" : it.guid,
                "name" : it.scientificName,
                "occurrenceCount" : 0,
                "georeferencedCount" : 0,
                "scientificNameMatches" : [],
                "commonNameMatches" : [],
                "rankString": it.rank,
                "rankID": it.rankID ?: -1,
                "commonName" : [],
                "commonNameSingle" : []
            ]
            autoCompleteList << result
        }

//        autoCompleteList: [
//                {
//                    guid: "urn:lsid:biodiversity.org.au:afd.taxon:c0da0b13-5d26-471f-9bf6-49af50896692",
//                    name: "Bregmaceros mcclellandi",
//                    occurrenceCount: 3,
//                    georeferencedCount: 3,
//                    scientificNameMatches: [
//                            "Breg<b>mac</b>eros mcclellandi"
//                    ],
//                    commonNameMatches: [
//                            "<b>Mac</b> Lelland's Unicorn-codfish",
//                            "<b>Mac</b>clelland's Unicorn-cod"
//                    ],
//                    commonName: "Codlet, Mac Lelland's Unicorn-codfish, Macclelland's Unicorn-cod, Spotted Codlet, Unicorn Cod, Unicorn Codlet",
//                    matchedNames: [
//                            "Mac Lelland's Unicorn-codfish",
//                            "Macclelland's Unicorn-cod",
//                            "Bregmaceros mcclellandi"
//                    ],
//                    rankId: 7000,
//                    rankString: "species",
//                    left: 398264,
//                    right: 398265
//                },
        log.debug("results: " + autoCompleteList.size())
        def payload = [autoCompleteList : autoCompleteList]
        render payload as JSON
    }


    /**
     * Main taxon search
     *
     * @return
     */
    def search(){

        try {
            def additionalParams = "&wt=json&facet.field=imageAvailable&facet.field=idxtype&facet.field=dataset&facet.field=rank&facet.field=dataProvider&facet.field=taxonomicStatus&facet=true&facet.mincount=1"
            def queryString = request.queryString

            if (queryString) {
                if (!params.q) {
                    queryString = request.queryString.replaceFirst("q=", "q=*:*")
                } else if (params.q.trim() == "*") {
                    queryString = request.queryString.replaceFirst("q=*", "q=*:*")
                }
            } else {
                queryString = "q=*:*"
            }

            def queryResponse = new URL(grailsApplication.config.solrBaseUrl + "/select?" + queryString + additionalParams).getText("UTF-8")
            def js = new JsonSlurper()
            def json = js.parseText(queryResponse)


            if (json.response.numFound as Integer == 0) {

                println(grailsApplication.config.solrBaseUrl + "/select?" + queryString + additionalParams)
                queryResponse = new URL(grailsApplication.config.solrBaseUrl + "/select?" + queryString + additionalParams).getText("UTF-8")
                js = new JsonSlurper()
                json = js.parseText(queryResponse)
            }

            def model = [
                    searchResults: [
                            totalRecords: json.response.numFound,
                            facetResults: formatFacets(json.facet_counts?.facet_fields ?: []),
                            results     : formatDocs(json.response.docs)
                    ]
            ]

            log.debug("auto called with q = " + params.q + ", returning " + model.searchResults.totalRecords)

            render(model as JSON)
        } catch (Exception e){
            log.error(e.getMessage(), e)
            render(["error": e.getMessage(), indexServer: grailsApplication.config.solrBaseUrl] as JSON)
        }
    }

    private def formatFacets(facetFields){
        def formatted = []
        facetFields.each { facetName, arrayValues ->
            def facetValues = []
            for (int i =0; i < arrayValues.size(); i+=2){
                facetValues << [label:arrayValues[i], count: arrayValues[i+1], fieldValue:arrayValues[i] ]  //todo internationalise label
            }
            formatted << [
                    fieldName: facetName,
                    fieldResult: facetValues
            ]
        }
        formatted
    }

    private def formatDocs(docs){

        def formatted = []

        docs.each {
            if(it.idxtype == "TAXON"){

                def commonNameSingle = ""
                def commonNames = ""
                if(it.commonName){
                    commonNameSingle = it.commonName.get(0)
                    commonNames = it.commonName.join(", ")
                }

                Map doc = [
                    "guid" : it.guid,
                    "idxType": "TAXON",
                    "name" : it.scientificName,
                    "kingdom" : it.rk_kingdom,
                    "scientificName" : it.scientificName,
                    "author" : it.scientificNameAuthorship,
                    "nameComplete" : it.nameComplete,
                    "parentGuid" : it.parentGuid,
                    "rank": it.rank,
                    "rankID": it.rankID ?: -1,
                    "commonName" : commonNames,
                    "commonNameSingle" : commonNameSingle
                ]

                if(it.acceptedConceptID){
                    doc.put("acceptedConceptID", it.acceptedConceptID)
                    doc.put("guid", it.acceptedConceptID)
                }

                if(it.synonymDescription_s == "synonym"){
                    doc.put("acceptedConceptName", it.acceptedConceptName)
                }

                if(it.image){
                    doc.put("image", it.image)
                }

                //add de-normalised fields
                def map = extractClassification(it)

                doc.putAll(map)

                formatted << doc
            }
        }
        formatted
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
}