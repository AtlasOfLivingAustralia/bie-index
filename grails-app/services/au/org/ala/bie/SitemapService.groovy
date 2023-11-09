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

import au.org.ala.bie.search.IndexDocType
import grails.web.servlet.mvc.GrailsParameterMap
import org.apache.solr.client.solrj.SolrQuery
import org.apache.solr.common.params.CursorMarkParams

import java.text.SimpleDateFormat

class SitemapService{

    def grailsApplication

    def searchService
    def indexService

    /** The default locale to use when choosing common names */
    Locale defaultLocale


    String URLSET_HEADER = "<?xml version='1.0' encoding='UTF-8'?><urlset xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:schemaLocation=\"http://www.sitemaps.org/schemas/sitemap/0.9 http://www.sitemaps.org/schemas/sitemap/0.9/sitemap.xsd\" xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">"
    String URLSET_FOOTER = "</urlset>"

    // Batch size for solr queries/commits and page sizes
    static BATCH_SIZE = 5000

    int MAX_URLS = 50000 // maximum number of URLs in a sitemap file
    int MAX_SIZE = 9*1024*1024 // use 9MB to keep the actual file size below 10MB (a gateway limit)

    File currentFile
    int fileCount = 0
    int countUrls = 0

    SimpleDateFormat simpleDateFormat = new SimpleDateFormat("YYYY-MM-dd")

    FileWriter fw

    def brokerMessagingTemplate

    /**
     * Index Knowledge Base pages.
     */
    def build(boolean online) throws Exception {
        // write all sitemaps
        initWriter()

        buildTaxonSitemap(online)

        closeWriter()

        buildSitemapIndex()
    }

    def buildSitemapIndex() {

        // write parent sitemap file
        fw = new FileWriter(grailsApplication.config.sitemap.dir + "/sitemap.xml")
        fw.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?><sitemapindex xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">")

        for (int i=0;i<fileCount;i++) {

            // move the tmp file
            File newFile = new File(grailsApplication.config.sitemap.dir + "/sitemap" + i + ".xml")
            if (newFile.exists()) {
                newFile.delete()
            }
            new File(grailsApplication.config.sitemap.dir + "/sitemap" + i + ".xml.tmp").renameTo(newFile)

            // add an entry for this new file
            fw.write("<sitemap><url>" + grailsApplication.config.grails.serverURL + '/sitemap' + i + ".xml" + "</url>")
            fw.write("<lastmod>" + simpleDateFormat.format(new Date()) + "</lastmod></sitemap>")
        }

        fw.write("</sitemapindex>")
        fw.flush()
        fw.close()
    }

    def initWriter() {
        currentFile = new File(grailsApplication.config.sitemap.dir + "/sitemap" + fileCount + ".xml.tmp")

        fw = new FileWriter(currentFile)

        fw.write(URLSET_HEADER)

        countUrls = 0
        fileCount++
    }

    def closeWriter() {
        fw.write(URLSET_FOOTER)
        fw.flush()
        fw.close()
    }

    def log(msg) {
        log.info(msg)
        brokerMessagingTemplate.convertAndSend "/topic/import-feedback", msg.toString()
    }

    def buildTaxonSitemap(boolean online) throws Exception {
        int pageSize = BATCH_SIZE
        int processed = 0
        def typeQuery = "idxtype:\"${IndexDocType.TAXON.name()}\" AND taxonomicStatus:accepted"
        def prevCursor
        def cursor

        log("Starting sitemap for accepted taxon records, scanning of ${online ? 'online' : 'offline'} index")
        try {
            prevCursor = ""
            cursor = CursorMarkParams.CURSOR_MARK_START
            processed = 0
            while (prevCursor != cursor) {
                def startTime = System.currentTimeMillis()
                SolrQuery query = new SolrQuery(typeQuery)
                query.setParam('cursorMark', cursor)
                query.setSort("id", SolrQuery.ORDER.asc)
                query.setRows(pageSize)
                def response = indexService.query(query, online)
                def docs = response.results
                int total = docs.numFound
                int failed = 0

                docs.each { doc ->
                    def nameString = doc.scientificName ?: doc.nameComplete
                    def commonName = doc.commonNameSingle

                    if (nameString) {
                        if (searchService.lookupTaxonByName(nameString, null)) {
                            writeUrl("monthly", grailsApplication.config.grails.serverURL + "/species/" + URLEncoder.encode(nameString))
                        } else {
                            failed++
                        }
                    }

                    if (commonName) {
                        if (searchService.lookupTaxonByName(commonName, null)) {
                            writeUrl("monthly", grailsApplication.config.grails.serverURL + "/species/" +  URLEncoder.encode(commonName))
                        } else {
                            failed++
                        }
                    }

                    processed++
                }

                def percentage = total ? Math.round(processed * 100 / total) : 100
                def speed = total ? Math.round((pageSize * 1000) / (System.currentTimeMillis() - startTime)) : 0
                log("Processed ${processed} taxa (${percentage}%) speed ${speed} records per second")
                if (total > 0) {
                    updateProgressBar(total, processed)
                }
                prevCursor = cursor
                cursor = response.nextCursorMark
            }
            log("Finished scan")
        } catch (Exception ex) {
            log.error("Unable to perform sitemap scan", ex)
            log("Error during scan: " + ex.getMessage())
        }
    }

    def writeUrl(String changefreq, String encodedUrl) {
        if (countUrls >= MAX_URLS || currentFile.size() >= MAX_SIZE) {
            closeWriter()
            initWriter()
        }

        fw.write("<url>")
        fw.write("<loc>" + encodedUrl + "</loc>")
        fw.write("<lastmod>" + simpleDateFormat.format(new Date()) + "</lastmod>")
        fw.write("<changefreq>" + changefreq + "</changefreq>")
        fw.write("</url>")

        fw.flush()

        countUrls++
    }

    private updateProgressBar(int total, int current) {
        Double percentDone = total > 0 ? current * 100.0 / total : 100.0
        brokerMessagingTemplate.convertAndSend "/topic/import-progress", percentDone.round(1).toString()
    }
}
