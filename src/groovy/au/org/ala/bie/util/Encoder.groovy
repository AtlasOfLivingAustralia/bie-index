/*
 * Copyright (C) 2017 Atlas of Living Australia
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
package au.org.ala.bie.util

import groovy.util.logging.Log4j
import org.springframework.web.util.UriUtils

/**
 * Utility class to encode URLs
 * taken from http://stackoverflow.com/a/10786112/249327
 *
 * @author "Nick dos Remedios <Nick.dosRemedios@csiro.au>"
 */
@Log4j
class Encoder {
    /**
     * Encode params so Tomcat security "improvements" don't reject SOLR URL
     * taken from http://stackoverflow.com/a/10786112/249327
     *
     * @param url
     * @return encoded url
     */
    static String encodeUrl(String inputUrlStr) {
        URL url = new URL(inputUrlStr)
        URI uri = new URI(url.getProtocol(), url.getUserInfo(), url.getHost(), url.getPort(), url.getPath(), url.getQuery(), url.getRef())
        log.debug "encodeUrl uri = ${uri.toASCIIString()}"
        uri.toASCIIString()
    }
}
