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
import org.apache.commons.lang.StringUtils
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.select.Elements

/**
 * Service for accessing Word Press pages
 */
class WordpressService implements IndexingInterface {
    def grailsApplication

    /**
     * Get a list of pages from the worpress system
     *
     * @return The list of available pages
     */
    List resources(String type = "") {
        def url = Encoder.buildServiceUrl(grailsApplication.config.wordPress.service, grailsApplication.config.wordPress.sitemap, type)
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
        // get list of pages to crawl via Google sitemap xml file
        // Note: sitemap.xml files can be nested, so code may need to read multiple files in the future (recursive function needed)
        while (!queue.isEmpty()) {
            URL map = queue.remove()
            if (seen.contains(map))
                continue
            seen << map
            try {
                Document doc = Jsoup.connect(map.toExternalForm()).timeout(10000).validateTLSCertificates(false).get()
                Elements sitemaps = doc.select("sitemapindex sitemap loc")
                sitemaps.each { loc ->
                    try {
                        URL url = new URL(loc.text())
                        queue << url
                    } catch (MalformedURLException mex) {
                        log.warn "Site map URL ${loc.text()} is malformed"
                    }
                }
                Elements pages = doc.select("urlset url loc")
                pages.each { loc ->
                    locations << loc.text()
                }
            } catch (IOException ex) {
                log.warn "Unable to retrieve ${map}: ${ex.message}, ignoring"
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
        String fullUrl = url + grailsApplication.config.wordPress.contentOnlyParams
        log.info "GETing url: ${fullUrl}"
        Document document = Jsoup.connect(fullUrl).timeout(10000).validateTLSCertificates(false).get()

        // some summary/landing pages do not work with `content-only=1`, so we don't want to index them
        if (document.select("body.ala-content") || !document.body().text()) {
            return [:]
        }

        def id = document.select("head > meta[name=id]").attr("content")
        def shortlink = document.select("head > link[rel=shortlink]").attr("href")

        if (StringUtils.isEmpty(id) && StringUtils.isNotBlank(shortlink) && shortlink.contains("=")) {
            // Look for a secondary id source
            id = StringUtils.split(shortlink, "=")[1];
        }

        if (!id) {
            // If no embedded id can be found
            id = UUID.randomUUID().toString()
        }

        def title = document.select("head > title").text()
        def main = document.select("body main").text()

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
