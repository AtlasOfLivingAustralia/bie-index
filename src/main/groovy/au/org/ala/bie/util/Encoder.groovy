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

import java.text.FieldPosition
import java.text.MessageFormat

/**
 * Utility class to encode URLs
 * taken from http://stackoverflow.com/a/10786112/249327
 *
 * @author "Nick dos Remedios <Nick.dosRemedios@csiro.au>"
 */
@Log4j
class Encoder {
    static SOLR_ESCAPE = ~/([+\-&|!(){}\[\]\^"~*?:\\])/

    /**
     * Encode params so Tomcat security "improvements" don't reject SOLR URL
     * This method emulates a browser in the way it automatically encodes certain param values
     * taken from http://stackoverflow.com/a/10786112/249327
     *
     * @param url
     * @return encoded url
     *
     * @deprecated This doesn't handle entries with an ampersand in it, such as "Anthocerotophyta Rothm. ex Stotler & Crand.-Stotl." at all well. Use {@link #escapeQuery} on individual query elements instead.
     */
    static String encodeUrl(String inputUrlStr) {
        URL url = new URL(inputUrlStr)
        URI uri = new URI(url.getProtocol(), url.getUserInfo(), url.getHost(), url.getPort(), url.getPath(), url.getQuery(), url.getRef())
        //log.debug "encodeUrl uri = ${uri.toASCIIString()}"
        uri.toASCIIString()
    }

    /**
     * Escape a term in a solr query that might contain special SOLR punctuation.
     *
     * @param term The term to escape
     *
     * @return The term with special characters preceeded by a backslash
     */
    static String escapeSolr(String term) {
        if (!term)
            return term
        def matches = SOLR_ESCAPE.matcher(term)
        return matches.replaceAll('\\\\$1')
    }

    /**
     * Encode a query term.
     *
     * @param term
     *
     * @return The query term with special characters escaped.
     */
    static String escapeQuery(String term) {
        return URLEncoder.encode(term, "UTF-8")
    }

    /**
     * Encode a query term, removing any "difficult" elements.
     *
     * @param term
     *
     * @return The query term with special characters escaped.
     */
    static String stripQuery(String term) {
        term = SOLR_ESCAPE.matcher(term).replaceAll(' ').trim()
        return URLEncoder.encode(term, "UTF-8")
    }

    /**
     * Build a call to a service.
     * <p>
     * The path follows the {@link MessageFormat} conventions, so that a complex URL can be built
     *
     * @param service The base service (without trailing /)
     * @param pathFormat The path (with leading /)
     * @param args Any arguments to place in the path
     *
     * @return The formatted service URL
     */
    static URL buildServiceUrl(String service, String pathFormat, Object... args) {
        StringBuffer buffer = new StringBuffer(service.length() + pathFormat.length())
        buffer.append(service)
        MessageFormat format = new MessageFormat(pathFormat)
        args = args.collect { escapeQuery(it.toString()) }
        format.format(args, buffer, new FieldPosition(0))
        return new URL(buffer.toString())
    }


    /**
     * Construct a list of language tags to try, based on a list of locales.
     * <p>
     * For example, [en-US, en-GB, fr] would produce ["en-US", "en", "eng" "en-GB", "fr", "fra"] as a set of languages
     *
     * @param locales The locale list.
     *
     * @return The language list
     */
    static List<String> buildLanguageList(List<Locale> locales) {
        def langs = new LinkedHashSet<String>()
        for (Locale locale: locales) {
            langs.add(locale.toLanguageTag())
            langs.add(locale.language)
            langs.add(locale.getISO3Language())
        }
        return langs.toList()
    }


}
