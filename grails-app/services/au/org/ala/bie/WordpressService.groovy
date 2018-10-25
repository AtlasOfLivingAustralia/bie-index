package au.org.ala.bie

import au.org.ala.bie.util.Encoder
import groovy.json.JsonSlurper
import org.apache.commons.lang.StringUtils
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.select.Elements

/**
 * Interface to the collectory
 */
class WordpressService {
    def grailsApplication

    /**
     * Get a list of pages from the worpress system
     *
     * @return The list of available pages
     */
    def pages() {
        def url = Encoder.buildServiceUrl(grailsApplication.config.wordPress.service, grailsApplication.config.wordPress.sitemap)
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
    def get(String url) {
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
