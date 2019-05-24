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
    List resources(String type) {
        def url = Encoder.buildServiceUrl(grailsApplication.config.wordPress.service, grailsApplication.config.wordPress.sitemap, type)
        return crawlWordPressSite(url)
    }

    /**
     * Read WP sitemap.xml file and return a list of page URLs
     *
     * @param url The site map url
     * @return
     */
    private List crawlWordPressSite(URL url) throws Exception {
        // get list of pages to crawl via Google sitemap xml file
        // Note: sitemap.xml files can be nested, so code may need to read multiple files in the future (recursive function needed)
        Document doc = Jsoup.connect(url.toExternalForm()).get()
        Elements pages = doc.select("loc")
        log.info("Sitemap file lists " + pages.size() + " pages.")
        return pages.collect { it.text() }
    }


    /**
     * Get the description of a page
     *
     * @param url The page url
     *
     * @return The a summary of the page contents
     */
    Map getResource(String url) {
        Document document = Jsoup.connect(url + grailsApplication.config.wordPress.contentOnlyParams).get()
        def id = document.select("head > meta[name=id]").attr("content")
        def shortlink = document.select("head > link[rel=shortlink]").attr("href")
        if (StringUtils.isEmpty(id) && StringUtils.isNotBlank(shortlink)) {
            // e.g. http://www.ala.org.au/?p=24241
            id = StringUtils.split(shortlink, "=")[1];
        }
        return [
                title: document.select("head > title").text(),
                id: id,
                shortlink: shortlink,
                body: document.body().text(),
                categories: document.select("ul[class=post-categories] li > a").collect { it.text() }
        ]
    }
}
