package au.org.ala.bie

import au.org.ala.bie.search.IndexDocType
import grails.converters.deep.JSON
import groovy.json.JsonSlurper

/**
 * A set of search services for the BIE.
 */
class SearchService {

    def grailsApplication

    def serviceMethod() {}

    /**
     * Retrieve species & subspecies for the supplied taxon which have images.
     *
     * @param taxonID
     * @param start
     * @param rows
     * @return
     */
    def imageSearch(taxonID, start, rows){

        def query = "q=*:*"

        if(taxonID){
            //retrieve the taxon rank and then construct the query
            def taxon = lookupTaxon(taxonID)
            if(!taxon){
                return []
            }
            query = "q=*:*&fq=rkid_" + taxon.rank.toLowerCase() + ":\"" +  URLEncoder.encode(taxon.guid, "UTF-8") + "\""
        }

        def additionalParams = "&wt=json&fq=rankID:%5B7000%20TO%20*%5D&fq=imageAvailable:yes"

        if(start){
            additionalParams = additionalParams + "&start=" + start
        }

        if(rows){
            additionalParams = additionalParams + "&rows=" + rows
        }

        log.debug(grailsApplication.config.solrBaseUrl + "/select?" + query + additionalParams)
        def queryResponse = new URL(grailsApplication.config.solrBaseUrl + "/select?" + query + additionalParams).getText("UTF-8")

        def js = new JsonSlurper()

        def json = js.parseText(queryResponse)

        [
                totalRecords:json.response.numFound,
                facetResults: formatFacets(json.facet_counts?.facet_fields?:[]),
                results: formatDocs(json.response.docs)
        ]
    }


    /**
     * General search service
     *
     * TODO - sorting and page size....
     *
     * @param requestedFacets
     * @return
     */
    def search(q, queryString, requestedFacets){

        def additionalParams = "&wt=json&facet=${!requestedFacets.isEmpty()}&facet.mincount=1"

        if(requestedFacets){
            additionalParams = additionalParams + "&facet.field=" + requestedFacets.join("&facet.field=")
        }

        if (queryString) {
            if (!q) {
                queryString = queryString.replaceFirst("q=", "q=*:*")
            } else if (q.trim() == "*") {
                queryString = queryString.replaceFirst("q=*", "q=*:*")
            } else {
                //remove the exist query param
                queryString = queryString.replaceAll("q\\=[\\w\\+ ]*", "")
                //append a wildcard to the search term
                queryString = queryString +
                        "&q=" + URLEncoder.encode(
                        "commonNameExact:\"" + q + "\"^10000000000" +
                                " OR commonName:\"" + q.replaceAll(" ","") + "\"^100000" +
                                " OR commonName:\"" + q + "\"^100000" +
                                " OR rk_genus:\"" + q.capitalize() + "\"" +
                                " OR exact_text:\"" + q + "\"" +
                                " OR auto_text:\"" + q + "\"" +
                                " OR auto_text:\"" + q + "*\"",
                        "UTF-8")
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

        log.debug("auto called with q = ${q}, returning ${json.response.numFound}")

        [
            totalRecords: json.response.numFound,
            facetResults: formatFacets(json.facet_counts?.facet_fields ?: []),
            results     : formatDocs(json.response.docs)
        ]
    }

    def getChildConcepts(taxonID){

        def solrServerUrl = grailsApplication.config.solrBaseUrl + "/select?wt=json&rows=1000&q=parentGuid:\"" + taxonID + "\""
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
        children
    }

    /**
     * Retrieve details of a taxon
     * @param taxonID
     * @return
     */
    private def lookupTaxon(taxonID){
        def solrServerUrl = grailsApplication.config.solrBaseUrl + "/select?wt=json&q=guid:\"" + taxonID + "\""
        def queryResponse = new URL(solrServerUrl).getText("UTF-8")
        def js = new JsonSlurper()
        def json = js.parseText(queryResponse)
        json.response.docs[0]
    }

    def getProfileForName(name){

        def additionalParams = "&wt=json"
        def queryString = "q=" + URLEncoder.encode(name, "UTF-8")

        def queryResponse = new URL(grailsApplication.config.solrBaseUrl + "/select?" + queryString + additionalParams).getText("UTF-8")
        def js = new JsonSlurper()
        def json = js.parseText(queryResponse)
        def model = []
        if(json.response.numFound > 0){
            json.response.docs.each { result ->
                model << [
                    "identifier": result.guid,
                    "name": result.scientificName,
                    "acceptedIdentifier": result.acceptedConceptID,
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

        if(taxon.commonName){
            model.put("commonName",  taxon.commonName.first())
        }

        if(taxon.image){
            model.put("thumbnail", grailsApplication.config.imageThumbnailUrl + taxon.image)
            model.put("imageURL", grailsApplication.config.imageLargeUrl + taxon.image)
        }
        model
    }

    def getTaxa(List guidList){

        def postBody = [ q: "guid:(\"" + guidList.join( '","') + "\")", wt: "json" ] // will be url-encoded
        def resp = doPostWithParams(grailsApplication.config.solrBaseUrl +  "/select", postBody)

        //create the docs....
        if(resp.resp.response){

            def matchingTaxa = []

            resp.resp.response.docs.each { doc ->
               def taxon = [
                       guid: doc.guid,
                       name: doc.scientificName,
                       scientificName: doc.scientificName,
                       author: doc.scientificNameAuthorship
               ]
               if(doc.image){
                   taxon.put("thumbnailUrl", grailsApplication.config.imageThumbnailUrl + doc.image)
                   taxon.put("smallImageUrl", grailsApplication.config.imageSmallUrl + doc.image)
                   taxon.put("largeImageUrl", grailsApplication.config.imageLargeUrl + doc.image)
               }
               if(doc.commonName){
                   taxon.put("commonNameSingle", doc.commonName.first())
               }

               matchingTaxa << taxon
            }
            matchingTaxa
        } else {
            resp
        }
    }

    def getTaxon(taxonID){

        def taxon = lookupTaxon(taxonID)
        if(!taxon){
            return null
        }

        //retrieve any synonyms
        def synonymQueryUrl = grailsApplication.config.solrBaseUrl + "/select?wt=json&q=acceptedConceptID:\"" + taxonID + "\""
        def synonymQueryResponse = new URL(synonymQueryUrl).getText("UTF-8")
        def js = new JsonSlurper()
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

    private def formatFacets(facetFields){
        def formatted = []
        facetFields.each { facetName, arrayValues ->
            def facetValues = []
            for (int i =0; i < arrayValues.size(); i+=2){
                facetValues << [label:arrayValues[i], count: arrayValues[i+1], fieldValue:arrayValues[i] ]
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
            if(it.idxtype == IndexDocType.TAXON.name()){

                def commonNameSingle = ""
                def commonNames = ""
                if(it.commonName){
                    commonNameSingle = it.commonName.get(0)
                    commonNames = it.commonName.join(", ")
                }

                Map doc = [
                        "guid" : it.guid,
                        "idxtype": it.idxtype,
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
            } else {
                Map doc = [
                        guid : it.guid,
                        idxtype: it.idxtype,
                        name : it.name,
                        description : it.description
                ]
                formatted << doc
            }
        }
        formatted
    }

    private def retrieveTaxon(taxonID){
        def solrServerUrl = grailsApplication.config.solrBaseUrl + "/select?wt=json&q=guid:\"" + taxonID + "\""
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
                query += name.encodeAsURL()+"="+params.get(name).encodeAsURL()
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
}
