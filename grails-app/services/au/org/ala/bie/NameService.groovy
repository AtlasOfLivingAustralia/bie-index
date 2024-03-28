/*
 * Copyright (C) 2022 Atlas of Living Australia
 * All Rights Reserved.
 *
 * The contents of this file are subject to the Mozilla Public
 * License Version 1.1 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of
 * the License at http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS
 * IS" basis, WITHOUT WARRANTY OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * rights and limitations under the License.
 */
package au.org.ala.bie

import au.org.ala.bie.util.Encoder
import grails.config.Config
import grails.converters.JSON
import grails.core.support.GrailsConfigurationAware
import groovy.json.JsonSlurper

class NameService implements GrailsConfigurationAware {
    String service

    @Override
    void setConfiguration(Config config) {
        this.service = config.getRequiredProperty("naming.service")
    }


    /**
     * Get a name match.
     * <p>
     * Fields other than the scientific name can be null but can be used,
     * if set, to make the match more acvcurtate.
     * </p>
     * <p>
     * Fuzzy mataches and higher order matches are excluded
     * </p>
     *
     * @param scientificName The scientific name
     * @param kingdom The kingdom
     * @param phylum The phylum
     * @param class_ The class
     * @param order The order
     * @param family The family
     * @param rank The rank
     * @param fields Additional fields to get from the list
     *
     * @return The lsid of the matched name or null for not found
     */
    def search(String scientificName, String kingdom, String phylum, String class_, String order, String family, String rank) {
        def query = "scientificName=" + Encoder.escapeQuery(scientificName);
        if (kingdom) {
            query = query + "&kingdom=" + Encoder.escapeQuery(kingdom)
        }
        if (phylum) {
            query = query + "&phylum=" + Encoder.escapeQuery(phylum)
        }
        if (class_) {
            query = query + "&class=" + Encoder.escapeQuery(class_)
        }
        if (order) {
            query = query + "&order=" + Encoder.escapeQuery(order)
        }
        if (family) {
            query = query + "&family=" + Encoder.escapeQuery(family)
        }
        if (rank) {
            query = query + "&rank=" + Encoder.escapeQuery(rank)
        }
        def url = new URL(this.service + "/api/searchByClassification?" + query)
        def slurper = new JsonSlurper()
        def json = slurper.parseText(url.getText('UTF-8'))
        if (!json.success)
            return null
        def matchType = json.matchType
        if (matchType == "fuzzyMatch" || matchType == "higherMatch")
            return null
        return json.taxonConceptID
     }

    def searchByIds(def list) {
        try {
            def query = list.collect { [taxonConceptID: it] }

            def url = new URL(this.service + "/api/searchAllByClassification")
            def bytes = (query as JSON).toString().getBytes("UTF-8")

            HttpURLConnection conn = (HttpURLConnection) url.openConnection()
            conn.setRequestMethod("POST")
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Content-Length", String.valueOf(bytes.length))
            conn.setDoOutput(true)
            conn.getOutputStream().write(bytes)

            def txt = conn.getInputStream().text
            def response = JSON.parse(txt)

            conn.disconnect()

            return response
        } catch (err) {
            log.error("Error calling " + this.service + "/api/searchByClassification, " + err.message)
        }
    }
}
