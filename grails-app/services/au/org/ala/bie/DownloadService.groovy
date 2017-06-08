/*
 * Copyright (C) 2016 Atlas of Living Australia
 * All Rights Reserved.
 * The contents of this file are subject to the Mozilla Public
 * License Version 1.1 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of
 * the License at http://www.mozilla.org/MPL/
 * Software distributed under the License is distributed on an "AS
 * IS" basis, WITHOUT WARRANTY OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * rights and limitations under the License.
 */

package au.org.ala.bie

import au.org.ala.bie.util.Encoder

class DownloadService {

    def grailsApplication
    def messageSource

    /**
     * Generate CSV output for a search query
     *
     * @param params - only q and fq are read for this service
     * @param outputStream
     * @param locale
     * @return
     */
    def download(params, OutputStream outputStream, Locale locale){
        def q = params.q ?: "*:*"
        def fq = ""
        def fields = grailsApplication.config.defaultDownloadFields?:"guid,rank,scientificName,rk_genus,rk_family,rk_order,rk_class,rk_phylum,rk_kingdom,datasetName"

        if (params.fields) {
            fields = params.fields
        }

        if (params.fq) {
            String fqs = params.list("fq").join("&fq=")
            fq = "&fq=${fqs}"
        }

        String queryUrl = grailsApplication.config.indexLiveBaseUrl + "/select?wt=csv&defType=edismax&fl=" +
                fields + "&csv.header=false&rows=" + Integer.MAX_VALUE +
                "&q=${q}${fq}"

        def connection = new URL(Encoder.encodeUrl(queryUrl)).openConnection()
        def input = connection.getInputStream()
        def headers = []

        // read field labels from i18n messages
        fields.split(',').each {
            String code = "download." + it
            headers.add(messageSource.getMessage(code, null, it, locale))
        }

        String headerLine = headers.join(",") + "\n"
        outputStream << headerLine.getBytes("UTF-8")
        outputStream << input
        outputStream.flush()
        outputStream.close()
    }
}
