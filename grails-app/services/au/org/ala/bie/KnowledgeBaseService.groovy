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
     * Get a full list of KB pages, to be used to index SOLR
     * Note "type" is not currently used here - implemented only due to interface requirements
     *
     * @param type
     * @return
     */
    List resources(String type = "", Integer max = -1) throws IOException {
        crawlKnowledgeBaseSite(getEncodedUrl(type), max)
    }

    /**
     * Get the encoded URL of the starting page for crawling the site
     *
     * @param type
     * @return
     */
    private String getEncodedUrl(String type) {
        Encoder.buildServiceUrl(grailsApplication.config.getProperty('knowledgeBase.service'),
                grailsApplication.config.getProperty('knowledgeBase.sitemap'), type)
    }

    /**
     * Scraping the FD KB site requires navigating 2 levels of pages in order to get a full list of pages.
     * This is because only the first 5 articles are listed under each section on starting page.
     *
     * @param url
     * @return List the list of URLs to scrape and index
     */
    private List crawlKnowledgeBaseSite(String url, Integer max = -1) throws IOException {
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
                        for (Element article : articles) {
                            String articleUrl = article.attr("href")

                            // exit inner loop if max is set
                            if (max > 0 && pages.size() >= max) {
                                break
                            }

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

                // exit outer loop if max is set
                if (max > 0 && pages.size() >= max) {
                    break
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
            // unlikely to trigger as IOException should be thrown back up stack
            log.warn "No document detected for ${url}"
        }

        doc
    }
}
