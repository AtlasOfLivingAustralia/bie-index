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

import au.org.ala.bie.indexing.IndexingInterface
import au.org.ala.bie.util.Encoder
import grails.config.Config
import grails.core.support.GrailsConfigurationAware
import org.apache.commons.lang.StringUtils
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.select.Elements

import java.util.function.Predicate
import java.util.regex.Pattern

/**
 * Service for accessing Word Press pages
 */
class WordpressService implements IndexingInterface, GrailsConfigurationAware {
    String service
    String sitemap
    String index
    int timeout
    boolean validateTLS
    String titleSelector
    String contentSelector
    String idSelector
    String shortLinkSelector
    String contentOnlyParams
    List<Predicate<String>> excludedLocations

    /**
     * Set up service with configuration
     *
     * @param config The grails configuration
     */
    @Override
    void setConfiguration(Config config) {
        this.service = config.wordPress.service
        this.sitemap = config.wordPress.sitemap
        this.index = config.wordPress.index
        this.timeout = config.getProperty("wordPress.timeout", Integer, 10000)
        this.validateTLS = config.getProperty("wordPress.validateTLS", Boolean, false)
        this.titleSelector = config.wordPress.titleSelector
        this.contentSelector = config.wordPress.contentSelector
        this.idSelector = config.wordPress.idSelector
        this.shortLinkSelector = config.wordPress.shortLinkSelector
        this.contentOnlyParams = config.wordPress.contentOnlyParams ?: ""
        this.excludedLocations = (config.wordPress.excludedLocations ?: []).collect { Pattern.compile(it).asPredicate() }
    }

    /**
     * Get a list of pages from the worpress system
     *
     * @return The list of available pages
     */
    List resources(String type = "") {
        def url = Encoder.buildServiceUrl(service, sitemap, type)
        return crawlWordPressSite([url] as Queue)
    }

    /**
     * Read WP sitemap.xml file and return a list of page URLs
     *
     * @param url The site map url
     * @return
     */
    private List crawlWordPressSite(Queue<URL> queue) throws Exception {
        Set locations = [] as Set
        Set<URL> seen = [] as Set

        while (!queue.isEmpty()) {
            URL source = queue.remove()
            if (seen.contains(source))
                continue
            seen << source
            try {
                Document doc = Jsoup.connect(source.toExternalForm()).timeout(this.timeout).validateTLSCertificates(this.validateTLS).get()
                Elements sitemaps = doc.select("sitemapindex sitemap loc")
                sitemaps.each { loc ->
                    try {
                        String sitemap = loc.text()
                        if (sitemap.endsWith('/')) {
                            sitemap = sitemap + this.index
                        }
                        URL url = new URL(sitemap)
                        queue << url
                     } catch (MalformedURLException mex) {
                        log.warn "Site map URL ${loc.text()} is malformed"
                    }
                }
                Elements pages = doc.select("urlset url loc")
                pages.each { loc ->
                    String url = loc.text()
                    if (!this.excludedLocations.any { it.test(url) }) {
                        locations << url
                    }
                }
            } catch (IOException ex) {
                log.warn "Unable to retrieve ${source}: ${ex.message}, ignoring"
            }

        }
        log.info("Sitemap contains" + locations.size() + " pages.")
        return locations.toList()
    }


    /**
     * Get the description of a page
     *
     * @param url The page url
     *
     * @return The a summary of the page contents
     */
    Map getResource(String url) {
        String fullUrl = url + contentOnlyParams
        log.info "GETing url: ${fullUrl}"
        Document document = Jsoup.connect(fullUrl).timeout(this.timeout).validateTLSCertificates(this.validateTLS).get()

        // some summary/landing pages do not work with `content-only=1`, so we don't want to index them
        if (document.select("body.ala-content") || !document.body().text()) {
            return [:]
        }

        def id = idSelector ? document.select(idSelector).attr("content") : ""
        def shortlink = shortLinkSelector ? document.select(shortLinkSelector).attr("href") : ""

        if (StringUtils.isEmpty(id) && StringUtils.isNotBlank(shortlink) && shortlink.contains("=")) {
            // Look for a secondary id source
            id = StringUtils.split(shortlink, "=")[1];
        }

        if (!id) {
            // If no embedded id can be found
            id = UUID.randomUUID().toString()
        }

        def title = document.select(titleSelector).text()
        def main = document.select(contentSelector).text()

        log.info "title = ${title} main = ${main.length() > 200 ? main.substring(0, 198) + " ..." : main}"

        return [
                title: title,
                id: id,
                shortlink: shortlink,
                body: main,
                categories: document.select("ul[class=post-categories] li > a").collect { it.text() }
        ]
    }
}
