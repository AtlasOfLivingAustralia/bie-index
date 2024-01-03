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

import au.com.bytecode.opencsv.CSVReader
import au.com.bytecode.opencsv.CSVWriter
import au.org.ala.bie.util.Encoder

class DownloadService {

    def grailsApplication
    def messageSource
    def indexService
    def importService

    /**
     * Generate CSV output for a search query
     *
     * @param params - only q and fq are read for this service
     * @param outputStream
     * @param locale
     * @return
     */
    def download(params, OutputStream outputStream, Locale locale){
        def q = Encoder.escapeQuery(params.q ?: "*:*")
        def fq = new ArrayList<>(params.list('fq')) // Make modifiable
        def fields = params.fields
        if (!fields) {
            fields = grailsApplication.config.getRequiredProperty('defaultDownloadFields')

            // if the defaultDownloadFields contain no rk_ fields, include all rk_ and rkid_ fields available in SOLR
            if (!fields.contains("rk_")) {
                fields += "," + rankFields().join(',')
            }
        }
        def fqs = ''

        grailsApplication.config.solr.search.fq.each { fq << it }
        if (fq) {
            fqs = '&fq=' + fq.collect({ Encoder.escapeQuery(it) }).join("&fq=")
        }
        // TODO Convert this into a SOLRJ API call
        String queryUrl = grailsApplication.config.solr.live.connection + "/select?wt=csv&defType=edismax&fl=" +
                Encoder.escapeQuery(fields) + "&csv.header=false&rows=" + grailsApplication.config.downloadMaxRows +
                "&q=${q}${fqs}"

        def connection = new URL(queryUrl).openConnection()
        CSVReader csv = new CSVReader(new InputStreamReader(connection.getInputStream()))
        String [][] all = csv.readAll()

        // Identify empty columns
        def columnCount = []
        for (int j=0;all.length && j<all[0].length;j++) {
            int count = 0
            for (int i=0;i<all.length;i++) {
                if (all[i][j]) {
                    count++
                }
            }
            columnCount.add(count)
        }

        def headers = []

        // read field labels from i18n messages
        fields.split(',').eachWithIndex { it, idx ->
            if (columnCount[idx]) {
                String code = "download." + it
                String defaultLabel
                if (it.startsWith("rkid_")) defaultLabel = it.replaceAll("^rkid_", "") + "ID"
                else defaultLabel = it.replaceAll("^rk_", "")
                headers.add(messageSource.getMessage(code, null, defaultLabel, locale))
            }
        }

        CSVWriter writer = new CSVWriter(new OutputStreamWriter(outputStream))
        writer.writeNext(headers.toArray(new String[0]))

        String [] row = new String[headers.size()]
        for (int i=0;i<all.length;i++) {
            int pos = 0
            for (int j=0;j<all[i].length;j++) {
                if (columnCount[j]) {
                    row[pos] = all[i][j]
                    pos++
                }
            }
            writer.writeNext(row)
        }

        writer.flush()
        writer.close()
    }

    def rankFields() {
        def ranks = importService.ranks()

        // find all rk_* and rkid_ fields. Sort using rankID
        indexService.getIndexFieldDetails().collect {
            [name: it.name, rankID: ranks[it.name.replaceAll("^[^_]+_", "").replaceAll("_", " ")]?.rankID ?: -1]
        }.findAll { it.name.startsWith('rk_') || it.name.startsWith('rkid_') }.sort { a, b ->
            // sort descending by taxonRankID, rk_ entries before rkid_ entries
            def sort = Integer.compare(b.rankID, a.rankID)
            if (sort == 0) {
                a.name.startsWith('rk_') ? 1 : -1
            } else {
                sort
            }
        }.collect { it.name }
    }
}
