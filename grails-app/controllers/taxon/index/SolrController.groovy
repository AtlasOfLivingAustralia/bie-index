package taxon.index

import grails.converters.JSON
import groovy.json.JsonSlurper
import org.apache.commons.io.FilenameUtils

class SolrController {

    def index() { }

    def solrBaseUrl = "http://130.56.248.115/solr/bie_denormed"

    def classification(){

        def classification = []
        def taxon = retrieveTaxon(params.id)

        classification.add(0, [
                rank : taxon.rank,
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
                        scientificName : taxon.scientificName,
                        guid:params.id
                ])
                parentGuid = taxon.parentGuid
            } else {
                stop = true
            }
        }
        render classification as JSON
    }

    def imageSearch(){

        def additionalParams = "&wt=json&fq=rank:Species&fq=image:[*%20TO%20*]"

        def rank = params.taxonRank
        def scientificName = params.scientificName

        def query = ""

        if(rank && scientificName){
            //append to query
            query = "q=*:*&fq=rk_" + rank.toLowerCase() + ":" +  scientificName
        } else {
            query = "q=*:*"
        }

        if(params.start){
            additionalParams = additionalParams + "&start=" + params.start
        }

        if(params.rows){
            additionalParams = additionalParams + "&rows=" + params.rows
        }


        println(solrBaseUrl + "/select?" + query + additionalParams)
        def queryResponse = new URL(solrBaseUrl + "/select?" + query + additionalParams).text

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

    def childConcepts(){

        def solrServerUrl = solrBaseUrl + "/select?wt=json&q=parentGuid:" + params.id
        def queryResponse = new URL(solrServerUrl).text
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
                    author: taxon.author,
                    rank: taxon.rank,
                    rankID:taxon.rankId
            ]
        }

        render (children as JSON)
    }

    private def retrieveTaxon(taxonID){
        def solrServerUrl = solrBaseUrl + "/select?wt=json&q=guid:" + taxonID
        def queryResponse = new URL(solrServerUrl).text
        def js = new JsonSlurper()
        def json = js.parseText(queryResponse)
        json.response.docs[0]
    }

    def taxon(){

        if(params.id == 'favicon') return; //not sure why this is happening....

        def solrServerUrl = solrBaseUrl + "/select?wt=json&q=guid:" + params.id
        def queryResponse = new URL(solrServerUrl).text
        def js = new JsonSlurper()
        def json = js.parseText(queryResponse)

        def taxon = json.response.docs[0]

        //retrieve any synonyms
        def synonymQueryUrl = solrBaseUrl + "/select?wt=json&q=acceptedConceptID:" + params.id
        def synonymQueryResponse = new URL(synonymQueryUrl).text
        def synJson = js.parseText(synonymQueryResponse)

        def synonyms = synJson.response.docs

        def model = [
                taxonConcept:[
                        guid:taxon.guid,
                        parentGuid: taxon.parentGuid,
                        nameString: taxon.scientificName,
                        author: taxon.author,
                        rankString: taxon.rank,
                        infoSourceName: taxon.dataset,
                        rankID:taxon.rankId
                ],
                taxonName:[],
                classification:[],
                synonyms:[],
                commonNames:{
                    def cn = []
                    taxon.commonName.each {
                        cn << [
                            nameString: it,
                            infoSourceName: "UK Species Inventory"
                        ]
                    }
                    cn
                }.call(),
                conservationStatuses:[]
        ]

        synonyms.each { synonym ->
            model.synonyms << [
                    nameString: synonym.scientificName,
                    nameGuid: synonym.guid
            ]
        }

        render (model as JSON)
    }

    def search(){

        def additionalParams = "&wt=json&facet.field=taxonGroup_s&facet.field=dataset&facet.field=rank&facet.field=dataProvider_s&facet.field=taxonomicStatus_s&facet.field=establishmentMeans_s&facet=true&facet.mincount=1"
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

        def queryResponse = new URL(solrBaseUrl + "/select?" + queryString + additionalParams).text

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

    def formatFacets(facetFields){
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

    def formatDocs(docs){

        def formatted = []



        docs.each {
            if(it.idxtype == "TAXON"){

                def commonNameSingle = ""
                def commonNames = ""
                if(it.commonName){
                    commonNameSingle = it.commonName.get(0)
                    commonNames = it.commonName.join(", ")
                }

                def doc = [
                    "guid" : it.guid,
                    "idxType": "TAXON",
                    "scientificName" : it.scientificName,
                    "author" : it.author,
                    "nameComplete" : it.nameComplete,
                    "parentGuid" : it.parentGuid,
                    "rank": it.rank,
                    "rankId": it.rankId ?: -1,
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
                    //FIXME this should be factored out and we should just replace with supplied paths from the biocache-service
                    def extension = FilenameUtils.getExtension(it.image)
                    if(extension) {
                        def smallUrl = it.image.substring(0, it.image.length() - (extension.length() +1)) + "__small." + extension
                        def largeUrl = it.image.substring(0, it.image.length() - (extension.length() +1)) + "__large." + extension
                        doc.put("smallImageUrl", smallUrl)
                        doc.put("largeImageUrl", largeUrl)
                    }
                }

                //add denormalised fields
                it.keySet().each { key ->
                    if(key.startsWith("rk_")){
                        doc.put(key.substring(3), it.get(key))
                    }
                    if(key.startsWith("rkid_")){
                        doc.put(key.substring(5)+"ID", it.get(key))
                    }
                }
                formatted << doc
            }
        }
        formatted
    }
}
