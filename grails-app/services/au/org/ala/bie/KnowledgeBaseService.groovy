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
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.select.Elements

/**
 * Service to index Knowledge Base pages in FreshDesk system
 */
class KnowledgeBaseService implements IndexingInterface {
    def grailsApplication

    /**
     * Get a full ist of KB pages, to be used to index SOLR
     *
     * @param type
     * @return
     */
    List resources(String type) throws IOException {
        // note type is not used here - implemented only due to interface requirements
        def url = Encoder.buildServiceUrl(grailsApplication.config.getProperty('knowledgeBase.service'), grailsApplication.config.getProperty('knowledgeBase.sitemap'), type)
        return crawlKnowledgeBaseSite(url)
    }

    /**
     * Scraping the FD KB site requires navigating 2 levels of pages in order to get a full list of pages.
     * This is because only the first 5 articles are listed under each section.
     *
     * @param url
     * @return
     */
    private List crawlKnowledgeBaseSite(url) throws IOException {
        List pages = []
        String baseUrl = grailsApplication.config.getProperty('knowledgeBase.service')
        String sectionCssSelector = grailsApplication.config.getProperty('knowledgeBase.sectionSelector')
        // do the first level scraping
        Document doc = Jsoup.connect("${url}").timeout(10000).validateTLSCertificates(false).get()
        Elements sections = doc.select(sectionCssSelector) // link to sections

        if (sections.size() > 0) {
            // we're on a page with sub-albums (e.g. family or subfamily) so need to go one level deeper
            for (Element section : sections) {
                String pageUrl = section.attr("href")
                log.debug ""

                if (pageUrl) {
                    // do the second level scraping
                    Document sectionDoc = Jsoup.connect(baseUrl + pageUrl).timeout(10000).validateTLSCertificates(false).get()
                    String articleCssSelector = grailsApplication.config.getProperty('knowledgeBase.articleCssSelector')
                    Elements articles = sectionDoc.select(articleCssSelector) // link to KB pages

                    if (articles.size() > 0) {
                        // we're on a page with sub-albums (e.g. family or subfamily) so need to go one level deeper
                        for (Element article : articles) {
                            String articleUrl = article.attr("href")

                            if (articleUrl) {
                                String fullUrl = baseUrl + articleUrl
                                pages.add(fullUrl) // add to list for output
                                log.info "${pages.size()}. Adding URL: ${fullUrl}"
                            }
                        }
                    } else {
                        log.warn "No links found for ${articleCssSelector} selector on ${pageUrl}"
                    }
                } else {
                    log.warn "No links found for ${sectionCssSelector} selector on ${url}"
                }
            }
        } else {
            log.warn "No sections found for ${sectionCssSelector} selector on ${url}. KB page source may have changed!"
        }

        pages
    }

    /**
     * Scrape a KB page and return the structured data (Map) required for SOLR indexing
     * (See {@link au.org.ala.bie.indexing.IndexingInterface#getResource(String)} for definition)
     *
     * @param url
     * @return Map
     */
    Map getResource(String url) throws IOException {
        Map doc = [:]
        Document page = Jsoup.connect(url).timeout(10000).validateTLSCertificates(false).get()

        if (page) {
            doc["id"] = page.select("p.article-vote").attr("data-article-id")
            doc["title"] = page.select(".content h2.heading").text()
            doc["body"] = page.select("article.article-body").text()
        } else {
            log.warn "No document detected for ${url}"
        }

        doc
    }
}
