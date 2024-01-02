/*
 * Copyright (C) 2019 Atlas of Living Australia
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

import grails.converters.JSON

/**
 * Service to index Biocollect projects
 */
class BiocollectService {
    def grailsApplication

    /**
     * Get a full list of KB pages, to be used to index SOLR
     * Note "type" is not currently used here - implemented only due to interface requirements
     *
     * @param type
     * @return
     */
    List resources() throws IOException {
        List projects = []
        String baseUrl = grailsApplication.config.getProperty('biocollect.service') + grailsApplication.config.getProperty('biocollect.search')

        int max = 100
        int offset = 0

        boolean hasMore = true
        while (hasMore) {
            def url = new URL(baseUrl + "&max=" + max + "&offset=" + offset)
            offset += max

            def newProjects = JSON.parse(url.getText('UTF-8')).projects
            hasMore = newProjects.size() == max

            projects.addAll(newProjects)
        }

        projects
    }
}
