package au.org.ala.bie

import grails.transaction.Transactional

class DownloadService {

    def grailsApplication

    def serviceMethod() {}

    def download(queryString, q,  OutputStream outputStream){

        def fields = "guid,rank,scientificName,establishmentMeans,rk_genus,rk_family,rk_order,rk_class,rk_phylum,rk_kingdom,datasetName"

        def queryUrl = grailsApplication.config.indexLiveBaseUrl + "/select?wt=csv&fl=" +
                fields + "&csv.header=false&rows=" + Integer.MAX_VALUE +
                "&" + queryString

        if(!q){
            queryUrl  = queryUrl + "&q=*:*"
        }

        def connection = new URL(queryUrl).openConnection()

        def input = connection.getInputStream()

        outputStream << "taxonID,taxonRank,scientificName,establishmentMeans,genus,family,order,class,phylum,kingdom,datasetName\n".getBytes("UTF-8")

        outputStream << input
        outputStream.flush()
    }
}
