/*
 * Copyright (C) 2016 Atlas of Living Australia
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

import au.com.bytecode.opencsv.CSVReader
import au.org.ala.bie.search.IndexDocType
import au.org.ala.bie.indexing.RankedName
import au.org.ala.vocab.ALATerm
import grails.async.PromiseList
import grails.converters.JSON
import groovy.json.JsonParserType
import groovy.json.JsonSlurper
import org.apache.commons.io.IOUtils
import org.apache.commons.lang.StringEscapeUtils
import org.apache.commons.lang.StringUtils
import org.apache.solr.common.params.MapSolrParams
import org.codehaus.groovy.grails.web.json.JSONElement
import org.codehaus.groovy.grails.web.json.JSONObject
import org.gbif.dwc.terms.DcTerm
import org.gbif.dwc.terms.DwcTerm
import org.gbif.dwc.terms.GbifTerm
import org.gbif.dwc.terms.Term
import org.gbif.dwc.terms.TermFactory
import org.gbif.dwca.io.Archive
import org.gbif.dwca.io.ArchiveFactory
import org.gbif.dwca.io.ArchiveFile
import org.gbif.dwca.record.Record
import org.gbif.dwca.record.StarRecord
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.select.Elements

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.zip.GZIPInputStream

/**
 * Services for data importing.
 */
class ImportService {
    static IN_SCHEMA = [
            DwcTerm.establishmentMeans, DwcTerm.taxonomicStatus, DwcTerm.taxonConceptID, DwcTerm.nomenclaturalStatus,
            DwcTerm.scientificNameID, DwcTerm.namePublishedIn, DwcTerm.namePublishedInID, DwcTerm.namePublishedInYear,
            DcTerm.source, DcTerm.language, DcTerm.license, DcTerm.format, DcTerm.rights, DcTerm.rightsHolder,
            ALATerm.status, ALATerm.nameID
    ]

    def indexService, searchService

    def grailsApplication
    def speciesGroupService
    def conservationListsSource

    def brokerMessagingTemplate

    def static DYNAMIC_FIELD_EXTENSION = "_s"
    def static IMAGE_FIELDS = URLEncoder.encode("taxon_concept_lsid, kingdom, phylum, class, order, family, genus, species, taxon_name, image_url, data_resource_uid", "UTF-8")
    def isKeepIndexing = true // so we can cancel indexing thread (single thread only so field is OK)

    static {
        TermFactory tf = TermFactory.instance()
        for (Term term: ALATerm.values())
            tf.addTerm(term.qualifiedName(), term)
    }

    /**
     * Retrieve a set of file paths from the import directory.
     */
    def retrieveAvailableDwCAPaths() {

        def filePaths = []
        def importDir = new File(grailsApplication.config.importDir)
        if (importDir.exists()) {
            File[] expandedDwc = new File(grailsApplication.config.importDir).listFiles()
            expandedDwc.each {
                if (it.isDirectory()) {
                    filePaths << it.getAbsolutePath()
                }
            }
        }
        filePaths
    }

    def importAll() {
        try {
            importCollectory()
        } catch (Exception e) {
            log("Problem loading collectory: " + e.getMessage())
        }
        try {
            importAllDwcA()
        } catch (Exception e) {
            log("Problem loading taxa: " + e.getMessage())
        }
        try {
            importVernacularSpeciesLists()
        } catch (Exception e) {
            log("Problem loading vernacular species lists: " + e.getMessage())
        }
        try {
            denormaliseTaxa(false)
        } catch (Exception e) {
            log("Problem loading vernacular species lists: " + e.getMessage())
        }
        try {
            importLayers()
        } catch (Exception e) {
            log("Problem loading layers: " + e.getMessage())
        }
        try {
            importRegions()
        } catch (Exception e) {
            log("Problem loading regions: " + e.getMessage())
        }
        try {
            importLocalities()
        } catch (Exception e) {
            log("Problem loading localities: " + e.getMessage())
        }
        try {
            importConservationSpeciesLists()
        } catch (Exception e) {
            log("Problem loading conservation species lists: " + e.getMessage())
        }
        try {
            importWordPressPages()
        } catch (Exception e) {
            log("Problem loading wordpress pages: " + e.getMessage())
        }
        try {
            buildLinkIdentifiers(false)
        } catch (Exception e) {
            log("Problem building link identifiers: " + e.getMessage())
        }
    }

    def importAllDwcA() {
        clearTaxaIndex()
        def filePaths = retrieveAvailableDwCAPaths()
        filePaths.each {
            importDwcA(it, false)
        }
    }

    /**
     * Import layer information into the index.
     *
     * @return
     */
    def importLayers() {
        def js = new JsonSlurper()
        def url = grailsApplication.config.layersServicesUrl + "/layers"
        log("Requesting layer list from : " + url)
        def layers = js.parseText(new URL(url).getText("UTF-8"))
        def batch = []
        indexService.deleteFromIndex(IndexDocType.LAYER)
        layers.each { layer ->
            def doc = [:]
            doc["id"] = layer.name
            doc["guid"] = layer.name
            doc["idxtype"] = IndexDocType.LAYER.name()
            doc["name"] = layer.displayname
            doc["description"] = layer.description
            doc["distribution"] = "N/A"
            log("Importing layer: " + layer.displayname)
            batch << doc
        }
        indexService.indexBatch(batch)
        log("Finished indexing ${layers.size()} layers")
    }

    def importLocalities() {
        if(grailsApplication.config.gazetteerLayerId) {
            indexService.deleteFromIndex(IndexDocType.LOCALITY)
            log("Starting indexing ${grailsApplication.config.gazetteerLayerId}")
            def metadataUrl = grailsApplication.config.layersServicesUrl + "/layer/" + grailsApplication.config.gazetteerLayerId + "?enabledOnly=false"
            log("Getting metadata for layer: ${metadataUrl}")
            def js = new JsonSlurper()
            def layer = js.parseText(new URL(metadataUrl).getText("UTF-8"))
            log("Starting indexing ${layer.id} - ${layer.name} gazetteer layer")
            importLayer(layer)
            log("Finished indexing ${layer.id} - ${layer.name} gazetteer layer")
        } else {
            log("Skipping localities, no gazetteer layer ID configured")
        }
    }

    def importRegions() {
        def js = new JsonSlurper()
        def layers = js.parseText(new URL(grailsApplication.config.layersServicesUrl + "/layers").getText("UTF-8"))
        indexService.deleteFromIndex(IndexDocType.REGION)
        layers.each { layer ->
            if (layer.type == "Contextual") {
                importLayer(layer)
            }
        }
        log("Finished indexing ${layers.size()} region layers")
    }

    /**
     * Import layer into index.
     *
     * @param layer
     * @return
     */
    private def importLayer(layer) {
        log("Loading regions from layer " + layer.name)

        def tempFilePath = "/tmp/objects_${layer.id}.csv.gz"
        def url = grailsApplication.config.layersServicesUrl + "/objects/csv/cl" + layer.id
        def file = new File(tempFilePath).newOutputStream()
        file << new URL(url).openStream()
        file.flush()
        file.close()

        if (new File(tempFilePath).exists() && new File(tempFilePath).length() > 0) {

            def gzipInput = new GZIPInputStream(new FileInputStream(tempFilePath))

            //read file and index
            def csvReader = new CSVReader(new InputStreamReader(gzipInput))

            def expectedHeaders = ["pid", "id", "name", "description", "centroid", "featuretype"]

            def headers = csvReader.readNext()
            def currentLine = []
            def batch = []
            while ((currentLine = csvReader.readNext()) != null) {

                if (currentLine.length >= expectedHeaders.size()) {

                    def doc = [:]
                    doc["id"] = currentLine[0]
                    doc["guid"] = currentLine[0]

                    if (currentLine[5] == "POINT") {
                        doc["idxtype"] = IndexDocType.LOCALITY.name()
                    } else {
                        doc["idxtype"] = IndexDocType.REGION.name()
                    }

                    doc["name"] = currentLine[2]

                    if (currentLine[3] && currentLine[2] != currentLine[3]) {
                        doc["description"] = currentLine[3]
                    } else {
                        doc["description"] = layer.displayname
                    }

                    doc["centroid"] = currentLine[4]
                    doc["distribution"] = "N/A"
                    batch << doc

                    if (batch.size() > 10000) {
                        indexService.indexBatch(batch)
                        batch.clear()
                    }
                }
            }
            if (batch) {
                indexService.indexBatch(batch)
                batch.clear()
            }
        }
    }

    def importHabitats() {

        def batch = []
        indexService.deleteFromIndex(IndexDocType.HABITAT)

        //read the DwC metadata
        Archive archive = ArchiveFactory.openArchive(new File("/data/habitat/"));
        ArchiveFile habitatArchiveFile = archive.getCore()

        Iterator<Record> iter = habitatArchiveFile.iterator()

        //get terms
        Term parentHabitatIDTerm = habitatArchiveFile.getField("http://ala.org.au/terms/1.0/parentHabitatID").getTerm()
        Term habitatNameTerm = habitatArchiveFile.getField("http://ala.org.au/terms/1.0/habitatName").getTerm()

        while (iter.hasNext()) {
            Record record = iter.next()
            def habitatID = record.id()
            def parentHabitatID = record.value(parentHabitatIDTerm)
            def habitatName = record.value(habitatNameTerm)
            def doc = [:]
            if (habitatID) {
                doc["id"] = habitatID
                doc["guid"] = habitatID
                if (parentHabitatID) {
                    doc["parentGuid"] = parentHabitatID
                }
                doc["idxtype"] = IndexDocType.HABITAT.name()
                doc["name"] = habitatName
                batch << doc
            }
        }
        indexService.indexBatch(batch)
    }

    /**
     * Import collectory information into the index.
     *
     * @return
     */
    def importCollectory() {
        [
                "dataResource": IndexDocType.DATARESOURCE,
                "dataProvider": IndexDocType.DATAPROVIDER,
                "institution" : IndexDocType.INSTITUTION,
                "collection"  : IndexDocType.COLLECTION
        ].each { entityType, indexDocType ->
            def js = new JsonSlurper()
            def entities = []
            def drLists = js.parseText(new URL(grailsApplication.config.collectoryServicesUrl + "/${entityType}").getText("UTF-8"))
            log("About to import ${drLists.size()} ${entityType}")
            log("Clearing existing: ${entityType}")
            indexService.deleteFromIndex(indexDocType)

            drLists.each {
                def details = js.parseText(new URL(it.uri).getText("UTF-8"))
                def doc = [:]
                doc["id"] = it.uri
                doc["datasetID"] = details.uid
                doc["guid"] = details.alaPublicUrl
                doc["idxtype"] = indexDocType.name()
                doc["name"] = details.name
                doc["description"] = details.pubDescription
                doc["distribution"] = "N/A"

                if (details.rights)
                    doc["rights"] = details.rights
                if (details.licenseType)
                    doc["license"] = (details.licenseType + " " + details.licenseVersion ?: "").trim()
                if (details.acronym)
                    doc["acronym"] = details.acronym

                entities << doc

                if (entities.size() > 10) {
                    indexService.indexBatch(entities)
                    entities.clear()
                }
            }
            log("Cleared")
            if (entities) {
                indexService.indexBatch(entities)
            }
            log("Finished indexing ${drLists.size()} ${entityType}")
        }
    }

    /**
     * Index WordPress pages
     */
    def importWordPressPages() throws Exception {
        // clear the existing WP index
        indexService.deleteFromIndex(IndexDocType.WORDPRESS)
        if (!grailsApplication.config.wordPress.sitemapUrl) {
            return
        }

        // WordPress variables
        String wordPressSitemapUrl = grailsApplication.config.wordPress.sitemapUrl
        String wordPressBaseUrl = grailsApplication.config.wordPress.baseUrl
        List excludedCategories = grailsApplication.config.wordPress.excludedCategories
        String contentOnlyParams = grailsApplication.config.wordPress.contentOnlyParams
        // get List of WordPress document URLs (each page's URL)
        List docUrls = crawlWordPressSite(wordPressSitemapUrl)
        def documentCount = 0
        def totalDocs = docUrls.size()
        def buffer = []
        log("WordPress pages found: ${totalDocs}") // update user via socket

        // slurp and build each SOLR doc (add to buffer)
        docUrls.each { pageUrl ->
            log.debug "indexing url: ${pageUrl}"
            try {
                // Crawl and extract text from WP pages
                Document document = Jsoup.connect(pageUrl + contentOnlyParams).get();
                String title = document.select("head > title").text();
                String id = document.select("head > meta[name=id]").attr("content");
                String shortlink = document.select("head > link[rel=shortlink]").attr("href");
                String bodyText = document.body().text();
                Elements postCategories = document.select("ul[class=post-categories]");
                List categoriesOut = []
                Boolean excludePost = false;

                if (StringUtils.isEmpty(id) && StringUtils.isNotBlank(shortlink)) {
                    // e.g. http://www.ala.org.au/?p=24241
                    id = StringUtils.split(shortlink, "=")[1];
                }

                if (!postCategories.isEmpty()) {
                    // Is a WP post (not page)
                    Elements categoriesIn = postCategories.select("li > a"); // get list of li elements

                    for (Element cat : categoriesIn) {
                        String thisCat = cat.text();

                        if (thisCat != null && excludedCategories.contains(thisCat)) {
                            // exclude category "button" posts
                            excludePost = true;
                        }
                        if (thisCat != null) {
                            // add category to list
                            categoriesOut.add(thisCat.replaceAll(" ", "_"));
                        }
                    }
                }

                if (excludePost) {
                    log("Excluding post (id: ${id} with category: ${categoriesOut.join('|')}")
                    return
                }

                documentCount++;
                // create SOLR doc
                log.debug(documentCount + ". Indexing WP page - id: " + id + " | title: " + title + " | text: " + StringUtils.substring(bodyText, 0, 100) + "... ");
                def doc = [:]
                doc["idxtype"] = IndexDocType.WORDPRESS.name()

                if (StringUtils.isNotBlank(shortlink)) {
                    doc["guid"] = shortlink
                } else if (StringUtils.isNotEmpty(id)) {
                    doc["guid"] = wordPressBaseUrl + id
                    // use page_id based URI instead of permalink in case permalink is too long for id field
                } else {
                    // fallback
                    doc["guid"] = pageUrl
                }

                doc["id"] = "wp" + id // probably not needed but safer to leave in
                doc["name"] = title // , 1.2f
                doc["content"] = bodyText
                doc["linkIdentifier"] = pageUrl
                //doc["australian_s"] = "recorded" // so they appear in default QF search
                doc["categories"] = categoriesOut
                // add to doc to buffer (List)
                buffer << doc
                // update progress bar (number output only)
                if (documentCount > 0) {
                    Double percentDone = (documentCount / totalDocs) * 100
                    log("${percentDone.round(1)}") // progress bar output
                }
            } catch (IOException ex) {
                // catch it so we don't stop indexing other pages
                log("Problem accessing/reading WP page <${pageUrl}>: " + ex.getMessage() + " - document skipped")
                log.warn(ex.getMessage(), ex);
            }
        }
        log("Committing to SOLR...")
        indexService.indexBatch(buffer)
        log("100") // complete progress bar
        log("Import finished.")
    }

    /**
     * Read WP sitemap.xml file and return a list of page URLs
     *
     * @param siteMapUrl
     * @return
     */
    private List crawlWordPressSite(String siteMapUrl) throws Exception {

        List pageUrls = []
        // get list of pages to crawl via Google sitemap xml file
        // Note: sitemap.xml files can be nested, so code may need to read multiple files in the future (recursive function needed)
        Document doc = Jsoup.connect(siteMapUrl).get();
        Elements pages = doc.select("loc");
        log.info("Sitemap file lists " + pages.size() + " pages.");

        for (Element page : pages) {
            // add it to list of page urls Field
            pageUrls.add(page.text());
        }

        pageUrls
    }

    /**
     * Import and index species lists for:
     * <ul>
     *   <li> conservation status </li>
     *   <li> sensitive ? </li>
     *   <li> host-pest interactions ? </li>
     * </ul>
     * For each taxon in each list, update that taxon's SOLR doc with additional fields
     */
    def importConservationSpeciesLists() throws Exception {
        def speciesListUrl = grailsApplication.config.speciesList.url
        def speciesListParams = grailsApplication.config.speciesList.params
        def defaultSourceField = conservationListsSource.defaultSourceField
        def lists =conservationListsSource.lists
        Integer listNum = 0

        lists.each { resource ->
            listNum++
            Integer listProgress = (listNum / lists.size()) * 100 // percentage as int
            String uid = resource.uid
            String solrField = resource.field ?: "conservationStatus_s"
            String sourceField = resource.sourceField ?: defaultSourceField
            if (uid && solrField) {
                def url = "${speciesListUrl}${uid}${speciesListParams}"
                log("Loading list from: " + url)
                try {
                    JSONElement json = JSON.parse(getStringForUrl(url))
                    updateDocsWithConservationStatus(json, sourceField, solrField, uid, listProgress)
                } catch (Exception ex) {
                    def msg = "Error calling webservice: ${ex.message}"
                    log(msg)
                    log.warn(msg, ex) // send to user via http socket
                }
            }
        }
    }

    def importVernacularSpeciesLists() throws Exception {
        def speciesListUrl = grailsApplication.config.speciesList.url
        def speciesListParams = grailsApplication.config.speciesList.params
        JsonSlurper slurper = new JsonSlurper()
        def config = slurper.parse(new URL(grailsApplication.config.vernacularListsUrl))
        def lists = config.lists
        Integer listNum = 0

        lists.each { resource ->
            listNum++
            Integer listProgress = (listNum / lists.size()) * 100 // percentage as int
            String uid = resource.uid
            String vernacularNameField = resource.vernacularNameField ?: config.defaultVernacularNameField
            String nameIdField = resource.nameIdField ?: config.defaultNameIdField
            String statusField = resource.statusField ?: config.defaultStatusField
            String languageField = resource.languageField ?: config.defaultLanguageField
            String sourceField = resource.sourceField ?: config.defaultSourceField
            String resourceLanguage = resource.language ?: config.defaultLanguage
            if (uid && vernacularNameField) {
                def url = "${speciesListUrl}${resource.uid}${speciesListParams}"
                log("Loading list from: " + url)
                try {
                    JSONElement json = JSON.parse(getStringForUrl(url))
                    importAdditionalVernacularNames(json, vernacularNameField, nameIdField, statusField, languageField, sourceField, resourceLanguage, uid, listProgress)
                } catch (Exception ex) {
                    def msg = "Error calling webservice: ${ex.message}"
                    log(msg)
                    log.warn(msg, ex) // send to user via http socket
                }
            }
        }
    }

    /**
     * Paginate through taxon docs in SOLR and update their occurrence status via either:
     * - checking against a list of datasetID codes (config)
     * OR
     * - searching for occurrence records with the (taxon concept) GUID
     *
     * Example cursor search
     * http://bie-dev.ala.org.au/solr/bie/select?q=idxtype:TAXON+AND+taxonomicStatus:accepted&wt=json&rows=100&indent=true&sort=id+asc&cursorMark=*
     * Pagination via cursor: https://cwiki.apache.org/confluence/display/solr/Pagination+of+Results
     **/
    def importOccurrenceData() throws Exception {
        String nationalSpeciesDatasets = grailsApplication.config.nationalSpeciesDatasets // comma separated String
        def pageSize = 10000
        def paramsMap = [
                q: "taxonomicStatus:accepted", // "taxonomicStatus:accepted",
                //fq: "datasetID:dr2699", // testing only with AFD
                cursorMark: "*", // gets updated by subsequent searches
                fl: "id,idxtype,guid,scientificName,datasetID", // will restrict results to dos with these fields (bit like fq)
                rows: pageSize,
                sort: "id asc", // needed for cursor searching
                wt: "json"
        ]

        // first get a count of results so we can determine number of pages to process
        Map countMap = paramsMap.clone(); // shallow clone is OK
        countMap.rows = 0
        countMap.remove("cursorMark")
        def searchCount = searchService.getCursorSearchResults(new MapSolrParams(countMap), true) // could throw exception
        def totalDocs = searchCount?.response?.numFound?:0
        int totalPages = (totalDocs + pageSize - 1) / pageSize
        log.debug "totalDocs = ${totalDocs} || totalPages = ${totalPages}"
        log("Processing " + String.format("%,d", totalDocs) + " taxa (via ${paramsMap.q})...<br>") // send to browser

        def promiseList = new PromiseList() // for biocaceh queries
        Queue commitQueue = new ConcurrentLinkedQueue()  // queue to put docs to be indexes
        ExecutorService executor = Executors.newSingleThreadExecutor() // consumer of queue - single blocking thread
        executor.execute {
            indexDocInQueue(commitQueue, "initialised") // will keep polling the queue until terminated via cancel()
        }

        // iterate over pages
        (1..totalPages).each { page ->
            try {
                MapSolrParams solrParams = new MapSolrParams(paramsMap)
                log.debug "${page}. paramsMap = ${paramsMap}"
                def searchResults = searchService.getCursorSearchResults(solrParams, true) // use offline index to search
                def resultsDocs = searchResults?.response?.docs?:[]

                // buckets to group results into
                def taxaLocatedInHubCountry = []  // automatically get included
                def taxaToSearchOccurrences = []  // need to search biocache to see if they are located in hub country

                // iterate over the result set
                resultsDocs.each { doc ->
                    if (nationalSpeciesDatasets.contains(doc.datasetID)) {
                        taxaLocatedInHubCountry.add(doc) // in national list so _assume_ it is located in host/hub county
                    } else {
                        taxaToSearchOccurrences.add(doc) // search occurrence records to determine if it is located in host/hub county
                    }
                }

                // update national list without occurrence record lookup
                updateTaxaWithLocationInfo(taxaLocatedInHubCountry, commitQueue)
                // update the rest via occurrence search (non blocking via promiseList)
                promiseList << { searchOccurrencesWithGuids(resultsDocs, commitQueue) }
                // update cursor
                paramsMap.cursorMark = searchResults?.nextCursorMark?:""
                // update view via via JS
                updateProgressBar(totalPages, page)
                log("${page}. taxaLocatedInHubCountry = ${taxaLocatedInHubCountry.size()} | taxaToSearchOccurrences = ${taxaToSearchOccurrences.size()}")
            } catch (Exception ex) {
                log.warn "Error calling BIE SOLR: ${ex.message}", ex
                log("ERROR calling SOLR: ${ex.message}")
            }
        }

        log("Waiting for all occurrence searches and SOLR commits to finish (could take some time)")

        //promiseList.get() // block until all promises are complete
        promiseList.onComplete { List results ->
            //executor.shutdownNow()
            isKeepIndexing = false // stop indexing thread
            executor.shutdown()
            log("Total taxa found with occurrence records = ${results.sum()}")
            log("waiting for indexing to finish...")
        }
    }

    /**
     * Batch update of SOLR docs for occurrence/location info
     * TODO extract field name into config: "locatedInHubCountry"
     *
     * @param docs
     * @param commitQueue
     * @return
     */
    def updateTaxaWithLocationInfo(List docs, Queue commitQueue) {
        def totalDocumentsUpdated = 0

        docs.each { Map doc ->
            if (doc.containsKey("id") && doc.containsKey("guid") && doc.containsKey("idxtype")) {
                Map updateDoc = [:]
                updateDoc["id"] = doc.id // doc key
                updateDoc["idxtype"] = ["set": doc.idxtype] // required field
                updateDoc["guid"] = ["set": doc.guid] // required field
                updateDoc["locatedInHubCountry"] = ["set": true]
                commitQueue.offer(updateDoc) // throw it on the queue
                totalDocumentsUpdated++
            } else {
                log.warn "Updating doc error: missing keys ${doc}"
            }
        }

        totalDocumentsUpdated
    }

    /**
     * Poll the queue of docs and index in batches
     *
     * @param updateDocs
     * @return
     */
    def indexDocInQueue(Queue updateDocs, msg) {
        int batchSize = 1000

        while (isKeepIndexing || updateDocs.size() > 0) {
            if (updateDocs.size() > 0) {
                log.info "Starting indexing of ${updateDocs.size()} docs"
                try {
                    // batch index docs
                    List batchDocs = []
                    int end = (batchSize < updateDocs.size()) ? batchSize : updateDocs.size()

                    (1..end).each {
                        if (updateDocs.peek()) {
                            batchDocs.add(updateDocs.poll())
                        }
                    }

                    indexService.indexBatch(batchDocs) // index
                } catch (Exception ex) {
                    log.warn "Error batch indexing: ${ex.message}", ex
                    log.warn "updateDocs = ${updateDocs}"
                    log("ERROR batch indexing: ${ex.message} <br><code>${ex.stackTrace}</code>")
                }
            } else {
                sleep(500)
            }
        }

        log("Indexing thread is done: ${msg}")
    }

    /**
     * Extract a list of GUIDs from input list of docs and do paginated/batch search of occurrence records,
     * updating index with occurrence status info (could be presence or record counts, etc)
     *
     * @param docs
     * @return
     */
    def searchOccurrencesWithGuids(List docs, Queue commitQueue) {
        int batchSize = 20 // even with POST SOLR throws 400 code is batchSize is more than 100
        List guids = docs.collect { it.guid }
        int totalPages = ((guids.size() + batchSize - 1) / batchSize) -1
        log.debug "total = ${guids.size()} || batchSize = ${batchSize} || totalPages = ${totalPages}"
        List docsWithRecs = [] // docs to index
        //log("Getting occurrence data for ${docs.size()} docs")

        (0..totalPages).each { index ->
            int start = index * batchSize
            int end = (start + batchSize < guids.size()) ? start + batchSize - 1 : guids.size()
            log.debug "paging biocache search - ${start} to ${end}"
            def guidSubset = guids.subList(start,end)
            def guidParamList = guidSubset.collect { String guid -> guid.encodeAsURL() } // URL encode guids
            def query = "taxon_concept_lsid:\"" + guidParamList.join("\"+OR+taxon_concept_lsid:\"") + "\""
            def filterQuery = "country:Australia+OR+cl21:*&fq=geospatial_kosher:true" // filter on Aust. terristrial and IMCRA marine areas
            //  def postBody = [ q: query, rows: 0, facet: true, "facet.field": "taxon_concept_lsid", "facet.mincount": 1, wt: "json" ] // will be url-encoded

            try {
                // def json = searchService.doPostWithParamsExc(grailsApplication.config.biocache.solr.url +  "/select", postBody)
                // log.debug "results = ${json?.resp?.response?.numFound}"
                def url = grailsApplication.config.biocache.solr.url + "/select?q=${query}&fq=${filterQuery}&" +
                        "wt=json&indent=true&rows=0&facet=true&facet.field=taxon_concept_lsid&facet.mincount=1"
                def queryResponse = new URL(url).getText("UTF-8")
                JSONObject jsonObj = JSON.parse(queryResponse)

                if (jsonObj.containsKey("facet_counts")) {
                    jsonObj?.facet_counts?.facet_fields?.taxon_concept_lsid?.eachWithIndex { val, idx ->
                        // facets results are a list with key, value, key, value, etc
                        if (idx % 2 == 0) {
                            docsWithRecs.add(docs.find { it.guid == val } )
                        }
                    }
                }
            } catch (Exception ex) {
                log.warn "Error calling biocache SOLR: ${ex.message}", ex
                log("ERROR calling biocache SOLR: ${ex.message}")
            }
        }

        if (docsWithRecs.size() > 0) {
            log.debug "docsWithRecs size = ${docsWithRecs.size()} vs docs size = ${docs.size()}"
            updateTaxaWithLocationInfo(docsWithRecs, commitQueue)
        }

    }

    /**
     * Update TAXON SOLR doc with conservation status info
     *
     * @param json
     * @param jsonFieldName
     * @param SolrFieldName
     * @return
     */
    private updateDocsWithConservationStatus(JSONElement json, String jsonFieldName, String SolrFieldName, String drUid, Integer listProgress) {
        if (json.size() > 0) {
            def totalDocs = json.size()
            def buffer = []
            def statusMap = vernacularNameStatus()
            def legistatedStatusType = statusMap.get("legislated")
            def unmatchedTaxaCount = 0

            log("${listProgress}||0") // reset progress bar
            log("Updating taxa with ${SolrFieldName}")
            json.eachWithIndex { item, i ->
                log.debug "item = ${item}"
                def taxonDoc

                if (item.lsid) {
                    taxonDoc = searchService.lookupTaxon(item.lsid, true) // TODO cache call
                }

                if (!taxonDoc && item.name) {
                    taxonDoc = searchService.lookupTaxonByName(item.name, true) // TODO cache call
                }

                if (taxonDoc) {
                    // do a SOLR doc (atomic) update
                    def doc = [:]
                    doc["id"] = taxonDoc.id // doc key
                    doc["idxtype"] = ["set": taxonDoc.idxtype] // required field
                    doc["guid"] = ["set": taxonDoc.guid] // required field
                    def fieldVale = item.kvpValues.find { it.key == jsonFieldName }?.get("value")
                    doc[SolrFieldName] = ["set": fieldVale] // "set" lets SOLR know to update record
                    log.debug "adding to doc = ${doc}"
                    buffer << doc
                } else {
                    // No match so add it as a vernacular name
                    def doc = [:]
                    doc["id"] = UUID.randomUUID().toString() // doc key
                    doc["idxtype"] = IndexDocType.TAXON // required field
                    doc["guid"] = "ALA_${item.name?.replaceAll(" ", "_")}" // required field
                    doc["datasetID"] = drUid
                    doc["datasetName"] = "Conservation list for ${SolrFieldName}"
                    doc["name"] = item.name
                    doc["status"] = legistatedStatusType?.status ?: "legistated"
                    doc["priority"] = legistatedStatusType?.priority ?: 500
                    // set conservationStatus facet
                    def fieldVale = item.kvpValues.find { it.key == jsonFieldName }?.get("value")
                    doc[SolrFieldName] = fieldVale
                    log.debug "new name doc = ${doc}"
                    buffer << doc
                    log("No existing taxon found for ${item.name}, so has been added as ${doc["guid"]}")
                }

                if (i > 0) {
                    Double percentDone = (i / totalDocs) * 100
                    log("${listProgress}||${percentDone.round(1)}") // progress bar output
                }
            }

            log("Committing to SOLR...")
            indexService.indexBatch(buffer)
            log("${listProgress}||1000") // complete progress bar
            log("Number of taxa unmatched: ${unmatchedTaxaCount}")
            log("Import finished.")
        } else {
            log("JSON not an array or has no elements - exiting")
        }
    }

    private void importAdditionalVernacularNames(JSONElement json, String vernacularNameField, String nameIdField, String statusField, String languageField, String sourceField, String resourceLanguage, String uid, Integer listProgress) {
        if (json.size() > 0) {
            def totalDocs = json.size()
            def buffer = []
            def statusMap = vernacularNameStatus()
            def commonStatus = statusMap.get("common")
            def unmatchedTaxaCount = 0

            log("${listProgress}||0") // reset progress bar
            log("Updating vernacular names from ${uid}")
            json.eachWithIndex { item, i ->
                log.debug "item = ${item}"
                def vernacularName = item.kvpValues.find { it.key == vernacularNameField }?.get("value")
                def nameId = item.kvpValues.find { it.key == nameIdField }?.get("value")
                def status = statusMap[item.kvpValues.find { it.key == statusField }?.get("value")]
                def language = item.kvpValues.find { it.key == languageField }?.get("value") ?: resourceLanguage
                def source = item.kvpValues.find { it.key == sourceField }?.get("value")

                if (!addVernacularName(item.lsid, item.name, vernacularName, nameId, status, language, source, uid, buffer, commonStatus))
                    unmatchedTaxaCount++

                if (i > 0) {
                    Double percentDone = (i / totalDocs) * 100
                    log("${listProgress}||${percentDone.round(1)}") // progress bar output
                }
            }
            log("Committing names to SOLR...")
            if (!buffer.isEmpty())
                indexService.indexBatch(buffer)
            log("Ensure denormalisation is re-run")
        } else {
            log("JSON not an array or has no elements - exiting")
        }

    }


    private boolean addVernacularName(String taxonID, String name, String vernacularName, String nameId, Object status, String language, String source, String datasetID, List buffer, Object defaultStatus) {
        def taxonDoc = null

        if (taxonID)
            taxonDoc = searchService.lookupTaxon(taxonID, true)
        if (!taxonDoc && name)
            taxonDoc = searchService.lookupTaxonByName(name, true)
        if (!taxonDoc) {
            log.warn("Can't find matching taxon document for ${taxonID} for ${vernacularName}, skipping")
            return false
        }
        def vernacularDoc = searchService.lookupVernacular(taxonDoc.guid, vernacularName, true)
        if (vernacularDoc) {
            // do a SOLR doc (atomic) update
            def doc = [:]
            doc["id"] = vernacularDoc.id // doc key
            doc["idxtype"] = ["set": vernacularDoc.idxtype] // required field
            doc["guid"] = ["set": vernacularDoc.guid] // required field
            doc["taxonGuid"] = ["set": taxonDoc.guid]
            doc["name"] = ["set": vernacularName]
            doc["datasetID"] = ["set": datasetID]
            doc["language"] = ["set": language]
            if (nameId)
                doc["nameID"] = ["set": nameId]
            if (status) {
                doc["status"] = ["set": status.status]
                doc["priority"] = ["set": status.priority]
            }
            if (source)
                doc["source"] = ["set": source]
            log.debug "adding to doc = ${doc}"
            buffer << doc
        } else {
            // No match so add it as a vernacular name
            def doc = [:]
            doc["id"] = UUID.randomUUID().toString() // doc key
            doc["idxtype"] = IndexDocType.COMMON // required field
            doc["guid"] = doc.id
            doc["taxonGuid"] = taxonDoc.guid
            doc["datasetID"] = datasetID
            doc["name"] = vernacularName
            doc["status"] = status?.status ?: defaultStatus.status
            doc["priority"] = status?.priority ?: defaultStatus.priority
            doc["nameID"] = nameId
            doc["language"] = language
            doc["source"] = source
            log.debug "new name doc = ${doc} for ${vernacularName}"
            buffer << doc
        }
        return true
    }

    private void commitVernacularNames(List buffer, Set updateTaxa) {
        if (!buffer.isEmpty())
            indexService.indexBatch(buffer)
        buffer = []
        updateTaxa.each {
            def taxonDoc = searchService.lookupTaxon(it, true)
            if (!taxonDoc)
                return
            def commonNames = searchService.lookupVernacular(it, true)
            if (!commonNames || commonNames.isEmpty())
                return
            commonNames = commonNames.sort { n1, n2 -> n2.priority - n1.priority }
            def doc = [:]
            doc["id"] = taxonDoc.id // doc key
            doc["idxtype"] = ["set": taxonDoc.idxtype] // required field
            doc["guid"] = ["set": taxonDoc.guid] // required field
            doc["commonName"] = ["set": commonNames.collect { it.name } ]
            doc["commonNameExact"] = ["set": commonNames.collect { it.name } ]
            doc["commonNameSingle"] = ["set": commonNames.first().name ]
            buffer << doc
        }
        if (!buffer.isEmpty())
            indexService.indexBatch(buffer)
    }

    private void commitIdentifiers(List buffer, Set updateTaxa) {
        if (!buffer.isEmpty())
            indexService.indexBatch(buffer)
        buffer = []
        updateTaxa.each {
            def taxonDoc = searchService.lookupTaxon(it, true)
            if (!taxonDoc)
                return
            def identifiers = searchService.lookupIdentifier(it, true)
            if (!identifiers || identifiers.isEmpty())
                return
            def doc = [:]
            doc["id"] = taxonDoc.id // doc key
            doc["idxtype"] = ["set": taxonDoc.idxtype] // required field
            doc["guid"] = ["set": taxonDoc.guid] // required field
            doc["additionalIdentifiers"] = ["set": identifiers.collect { it.guid } ]
            buffer << doc
        }
        if (!buffer.isEmpty())
            indexService.indexBatch(buffer)
    }

    def clearTaxaIndex() {
        log("Deleting existing taxon entries in index...")
        indexService.deleteFromIndex(IndexDocType.TAXON)
        indexService.deleteFromIndex(IndexDocType.COMMON)
        indexService.deleteFromIndex(IndexDocType.IDENTIFIER)
        log("Cleared.")
    }

    def importDwcA(dwcDir, clearIndex) {
        try {
            log("Importing archive from path.." + dwcDir)
            //read the DwC metadata
            Archive archive = ArchiveFactory.openArchive(new File(dwcDir));
            // Archive metadata available
            log("Archive metadata detected: " + (archive.metadataLocation != null))
            def defaultDatasetName = null
            if (archive.metadataLocation) {
                defaultDatasetName = archive.metadata.title?.trim()
                log("Default dataset name from metadata: " + defaultDatasetName)
            }
            //retrieve datasets
            def datasetMap = [:]
            def attributionMap = [:]
            //dataset extension available?
            ArchiveFile datasetArchiveFile = archive.getExtension(DcTerm.rightsHolder)
            log("Dataset extension detected: " + (datasetArchiveFile != null))
            if (datasetArchiveFile) {
                attributionMap = readAttribution(datasetArchiveFile)
                log("Datasets read: " + attributionMap.size())
            }
            def rowType = archive.core.rowType
            //clear
            if (clearIndex) {
                clearTaxaIndex()
            } else {
                log("Skipping deleting existing entries in index...")
            }
            if (rowType == DwcTerm.Taxon)
                importTaxonDwcA(archive, attributionMap, datasetMap, defaultDatasetName)
            else if (rowType == GbifTerm.VernacularName)
                importVernacularDwcA(archive.core, attributionMap, datasetMap, defaultDatasetName)
            else if (rowType == GbifTerm.Identifier)
                importIdentifierDwcA(archive.core, attributionMap, datasetMap, defaultDatasetName)
            else
                log("Unable to import an archive of type " + rowType)
            def vernacularExtension = archive.getExtension(GbifTerm.VernacularName)
            if (vernacularExtension)
                importVernacularDwcA(vernacularExtension, attributionMap, datasetMap, defaultDatasetName)
            def identifierExtension = archive.getExtension(GbifTerm.Identifier)
            if (identifierExtension)
                importIdentifierDwcA(identifierExtension, attributionMap, datasetMap, defaultDatasetName)
            log("Import finished.")
        } catch (Exception ex) {
            log("There was problem with the import: " + ex.getMessage())
            log("See server logs for more details.")
            log.error(e.getMessage(), ex)
        }

    }

    /**
     * Import a taxon DwC-A into this system.
     *
     * @return
     */
    def importTaxonDwcA(Archive archive, Map attributionMap, Map datasetMap, String defaultDatasetName) {
        /*
        log.info("Loading Species Group mappings for DwcA import")
        def speciesGroupMapping = speciesGroupService.invertedSpeciesGroups
        log.info("Finished loading Species Group mappings")
        */
        log("Importing taxa")

        //read the DwC metadata
        ArchiveFile taxaArchiveFile = archive.getCore()

        //retrieve taxon rank mappings
        log("Reading taxon ranks..")
        def taxonRanks = ranks()
        log("Reading taxon ranks.." + taxonRanks.size() + " read.")

        //compile a list of synonyms into memory....
        //def synonymMap = readSynonyms(taxaArchiveFile, taxonRanks)
        //log("Synonyms read for " + synonymMap.size() + " taxa")

        log("Creating entries in index...")

        //read inventory, creating entries in index....
        def alreadyIndexed = [
                DwcTerm.taxonID,
                DwcTerm.datasetID,
                DwcTerm.acceptedNameUsageID,
                DwcTerm.parentNameUsageID,
                DwcTerm.scientificName,
                DwcTerm.taxonRank,
                DwcTerm.scientificNameAuthorship,
                DwcTerm.taxonomicStatus,
                ALATerm.nameComplete,
                ALATerm.nameFormatted,
        ]

        def buffer = []
        def counter = 0

        Iterator<StarRecord> iter = archive.iterator()

        while (iter.hasNext()) {

            StarRecord record = iter.next()
            Record core = record.core()

            def taxonID = core.id()
            def acceptedNameUsageID = core.value(DwcTerm.acceptedNameUsageID)
            def synonym = taxonID != acceptedNameUsageID && acceptedNameUsageID != "" && acceptedNameUsageID != null

            def datasetID = (core.value(DwcTerm.datasetID))
            def taxonRank = (core.value(DwcTerm.taxonRank) ?: "").toLowerCase()
            def scientificName = core.value(DwcTerm.scientificName)
            def parentNameUsageID = core.value(DwcTerm.parentNameUsageID)
            def scientificNameAuthorship = core.value(DwcTerm.scientificNameAuthorship)
            def nameComplete = core.value(ALATerm.nameComplete)
            def nameFormatted = core.value(ALATerm.nameFormatted)
            def taxonRankID = taxonRanks.get(taxonRank) ? taxonRanks.get(taxonRank).rankID : -1
            def taxonomicStatus = core.value(DwcTerm.taxonomicStatus) ?: (synonym ? "synonym" : "accepted")

            def doc = ["idxtype": IndexDocType.TAXON.name()]
            doc["id"] = UUID.randomUUID().toString()
            doc["guid"] = taxonID
            doc["datasetID"] = datasetID
            doc["parentGuid"] = parentNameUsageID
            doc["rank"] = taxonRank
            //only add the ID if we have a recognised rank
            if(taxonRankID > 0){
                doc["rankID"] = taxonRankID
            }
            doc["scientificName"] = scientificName
            doc["scientificNameAuthorship"] = scientificNameAuthorship
            doc["nameComplete"] = buildNameComplete(nameComplete, scientificName, scientificNameAuthorship)
            doc["nameFormatted"] = buildNameFormatted(nameFormatted, nameComplete, scientificName, scientificNameAuthorship, taxonRank, taxonRanks)
            doc["taxonomicStatus"] = taxonomicStatus

            //index additional fields that are supplied in the core record
            core.terms().each { term ->
                if (!alreadyIndexed.contains(term)) {
                    if (IN_SCHEMA.contains(term)) {
                        doc[term.simpleName()] = core.value(term)
                    } else {
                        //use a dynamic field extension
                        doc[term.simpleName() + DYNAMIC_FIELD_EXTENSION] = core.value(term)
                    }
                }
            }

            def attribution = findAttribution(datasetID, attributionMap, datasetMap)
            if (attribution) {
                doc["datasetName"] = attribution["datasetName"]
                doc["rightsHolder"] = attribution["rightsHolder"]
            } else if (defaultDatasetName) {
                doc["datasetName"] = defaultDatasetName
            }

            if (record.hasExtension(GbifTerm.Distribution)) {
                record.extension(GbifTerm.Distribution).each {
                    def distribution = it.value(DwcTerm.stateProvince)
                    if (distribution)
                        doc["distribution"] = distribution
                }
            }

            if (synonym) {
                doc["acceptedConceptID"] = acceptedNameUsageID
            } else {
                // Filled out during denomalisation
                doc['speciesGroup'] = []
                doc['speciesSubgroup'] = []
            }
            buffer << doc
            counter++
            if (buffer.size() >= 1000) {
                log("Adding taxa: ${counter}")
                indexService.indexBatch(buffer)
                buffer.clear()
            }
        }

        if (!buffer.isEmpty()) {
            log("Adding taxa: ${counter}")
            indexService.indexBatch(buffer)
        }
    }


    def importVernacularDwcA(ArchiveFile archiveFile, Map attributionMap, Map datasetMap, String defaultDatasetName) throws Exception {
        if (archiveFile.rowType != GbifTerm.VernacularName)
            throw new IllegalArgumentException("Vernacular import only works for files of type " + GbifTerm.VernacularName + " got " + archiveFile.rowType)
        log("Importing verncaular names")
        def statusMap = vernacularNameStatus()
        def defaultStatus = statusMap.get("common")
        String defaultLanguage = grailsApplication.config.commonNameDefaultLanguage
        def buffer = []
        def count = 0
        for (Record record: archiveFile) {
            String taxonID = record.id()
            String vernacularName = record.value(DwcTerm.vernacularName)
            String nameID = record.value(ALATerm.nameID)
            Object status = statusMap.get(record.value(ALATerm.status))
            String language = record.value(DcTerm.language) ?: defaultLanguage
            String source = record.value(DcTerm.source)
            String datasetID = record.value(DwcTerm.datasetID)

            def doc = [:]
            doc["id"] = UUID.randomUUID().toString() // doc key
            doc["idxtype"] = IndexDocType.COMMON // required field
            doc["guid"] = doc.id
            doc["taxonGuid"] = taxonID
            doc["datasetID"] = datasetID
            doc["name"] = vernacularName
            doc["status"] = status?.status ?: defaultStatus.status
            doc["priority"] = status?.priority ?: defaultStatus.priority
            doc["nameID"] = nameID
            doc["language"] = language ?: defaultLanguage
            doc["source"] = source
            def attribution = findAttribution(datasetID, attributionMap, datasetMap)
            if (attribution) {
                doc["datasetName"] = attribution["datasetName"]
                doc["rightsHolder"] = attribution["rightsHolder"]
            } else if (defaultDatasetName) {
                doc["datasetName"] = defaultDatasetName
            }
            buffer << doc
            count++
            if (buffer.size() >= 1000) {
                indexService.indexBatch(buffer)
                buffer.clear()
                log("Processed ${count} records")
            }
        }
        if (buffer.size() > 0) {
            indexService.indexBatch(buffer)
            log("Processed ${count} records")
        }
    }


    def importIdentifierDwcA(ArchiveFile archiveFile, Map attributionMap, Map datasetMap, String defaultDatasetName) throws Exception {
        if (archiveFile.rowType != GbifTerm.Identifier)
            throw new IllegalArgumentException("Identifier import only works for files of type " + GbifTerm.Identifier + " got " + archiveFile.rowType)
        log("Importing identifiers")
        def statusMap = identifierStatus()
        def defaultStatus = statusMap.get("unknown")
        def buffer = []
        def count = 0
        for (Record record: archiveFile) {
            def taxonID = record.id()
            def identifier = record.value(DcTerm.identifier)
            def title = record.value(DcTerm.title)
            def subject = record.value(DcTerm.subject)
            def format = record.value(DcTerm.format)
            def source = record.value(DcTerm.source)
            def datasetID = record.value(DwcTerm.datasetID)
            def idStatus = record.value(ALATerm.status)
            def status = idStatus ? statusMap.get(idStatus.toLowerCase()) : null

            def doc = [:]
            doc["id"] = UUID.randomUUID().toString() // doc key
            doc["idxtype"] = IndexDocType.IDENTIFIER // required field
            doc["guid"] = identifier
            doc["taxonGuid"] = taxonID
            doc["datasetID"] = datasetID
            doc["status"] = status?.status ?: defaultStatus.status
            doc["priority"] = status?.priority ?: defaultStatus.priority
            doc["name"] = title
            doc["subject"] = subject
            doc["format"] = format
            doc["source"] = source
            def attribution = findAttribution(datasetID, attributionMap, datasetMap)
            if (attribution) {
                doc["datasetName"] = attribution["datasetName"]
                doc["rightsHolder"] = attribution["rightsHolder"]
            } else if (defaultDatasetName) {
                doc["datasetName"] = defaultDatasetName
            }
            buffer << doc
            count++
            if (buffer.size() >= 1000) {
                indexService.indexBatch(buffer)
                buffer.clear()
                log("Processed ${count} records")
            }
        }
        if (buffer.size() > 0) {
            indexService.indexBatch(buffer)
            log("Processed ${count} records")
        }
    }


    static String normaliseRank(String rank) {
        return rank?.toLowerCase()?.replaceAll("[^a-z]", "_")
    }

    /**
     * Read the attribution file, building a map of ID -> name, dataProvider
     *
     * @param fileName
     * @return
     */
    private def readAttribution(ArchiveFile datasetArchiveFile) {

        def datasets = [:]
        if (!datasetArchiveFile) {
            return datasets
        }

        Iterator<Record> iter = datasetArchiveFile.iterator()
        while (iter.hasNext()) {
            Record record = iter.next()
            def datasetID = record.id()
            def datasetName = record.value(DwcTerm.datasetName)
            def rightsHolder = record.value(DcTerm.rightsHolder)
            datasets.put(datasetID, [datasetName: datasetName, rightsHolder: rightsHolder])
        }
        datasets
    }

    /**
     * Go through the index and build link identifiers for unique names.
     */
    def buildLinkIdentifiers(online) {
        int pageSize = 1000
        int page = 0
        int added = 0
        def js = new JsonSlurper()
        def baseUrl = online ? grailsApplication.config.indexLiveBaseUrl : grailsApplication.config.indexOfflineBaseUrl
        def typeQuery = "idxtype:\"" + IndexDocType.TAXON.name() + "\"+OR+idxtype:\"" + IndexDocType.COMMON.name() + "\""

        js.setType(JsonParserType.INDEX_OVERLAY)
        log("Starting link identifier scan")
        try {
            while (true) {
                def startTime = System.currentTimeMillis()
                def solrServerUrl = baseUrl + "/select?wt=json&q=" + typeQuery + "&start=" + (pageSize * page) + "&rows=" + pageSize
                def queryResponse = solrServerUrl.toURL().getText("UTF-8")
                def json = js.parseText(queryResponse)
                int total = json.response.numFound
                def docs = json.response.docs
                def buffer = []

                if (docs.isEmpty())
                    break
                docs.each { doc ->
                    def name = doc.scientificName ?: doc.name
                    try {
                        if (name) {
                            def encName = URLEncoder.encode(name, "UTF-8")
                            def nameSearchUrl = baseUrl + "/select?wt=json&q=name:\"" + encName + "\"+OR+scientificName:\"" + encName + "\"&fq=" + typeQuery + "&rows=0"
                            def nameResponse = nameSearchUrl.toURL().getText("UTF-8")
                            def nameJson = js.parseText(nameResponse)
                            int found = nameJson.response.numFound
                            if (found == 1) {
                                //log.debug("Adding link identifier for ${name} to ${doc.id}")
                                def update = [:]
                                update["id"] = doc.id // doc key
                                update["idxtype"] = ["set": doc.idxtype] // required field
                                update["guid"] = ["set": doc.guid] // required field
                                update["linkIdentifier"] = ["set": name]
                                buffer << update
                                added++
                            }
                        }
                    } catch (Exception ex) {
                        log.warn "Unable to search for name ${name}: ${ex.message}"
                    }
                }
                if (!buffer.isEmpty())
                    indexService.indexBatch(buffer, online)
                page++
                if (page % 10 == 0) {
                    def progress = page * pageSize
                    def percentage = Math.round(progress * 100 / total)
                    def speed = Math.round((page * 1000) / (System.currentTimeMillis() - startTime))
                    log("Processed ${page * pageSize} names (${percentage}%), added ${added} unique links, ${speed} names per second")
                }
            }
            log("Finished scan")
        } catch (Exception ex) {
            log.error("Unable to perform link identifier scan", ex)
            log("Error during scan: " + ex.getMessage())
        }
    }

    /**
     * Go through the index and build image links for taxa
     */
    def loadImages(online) {
        int pageSize = 5000
        int processed = 0
        int added = 0
        def js = new JsonSlurper()
        def baseUrl = online ? grailsApplication.config.indexLiveBaseUrl : grailsApplication.config.indexOfflineBaseUrl
        def biocacheSolrUrl = grailsApplication.config.biocache.solr.url
        def typeQuery = "idxtype:\"" + IndexDocType.TAXON.name() + "\"+AND+taxonomicStatus:accepted"
        def prevCursor = ""
        def cursor = "*"
        JsonSlurper slurper = new JsonSlurper()
        def config = slurper.parse(new URL(grailsApplication.config.imageListsUrl))
        def imageMap = collectImageLists(config.lists)
        def rankMap = config.ranks.collectEntries { r -> [(r.rank): r] }
        def boosts = config.boosts.collect({"bq=" + it}).join("&")
        def lastImage = [imageId: "none", taxonID: "none", name: "none"]
        def addImageSearch = { query, field, value, boost ->
            if (field && value) {
                query = query ? query + "+OR+" : ""
                query = query + "${field}:\"${URLEncoder.encode(value, "UTF-8")}\"^${boost}"
            }
            query
        }

        js.setType(JsonParserType.INDEX_OVERLAY)
        log("Starting image load scan for ${online ? 'online' : 'offline'} index")
        try {
            while (prevCursor != cursor) {
                def startTime = System.currentTimeMillis()
                def solrServerUrl = baseUrl + "/select?wt=json&q=" + typeQuery + "&cursorMark=" + cursor + "&sort=id+asc&rows=" + pageSize
                def queryResponse = solrServerUrl.toURL().getText("UTF-8")
                def json = js.parseText(queryResponse)
                int total = json.response.numFound
                def docs = json.response.docs
                def buffer = []

                docs.each { doc ->
                    def taxonID = doc.guid
                    def kingdom = doc.rk_kingdom
                    def name = doc.scientificName ?: doc.name
                    def rank = rankMap[doc.rank]
                    def image = null

                    if (rank != null) {
                        try {
                            image = imageMap[taxonID] ?: imageMap[name]
                            if (!image) {
                                def query = null
                                query = addImageSearch(query, "lsid", taxonID, 100)
                                query = addImageSearch(query, rank.nameField, name, 50)
                                query = addImageSearch(query, rank.idField, taxonID, 20)
                                if (query) {
                                    def taxonSearchUrl = biocacheSolrUrl + "/select?q=(${query})+AND+multimedia:Image&${boosts}&rows=5&wt=json&fl=${IMAGE_FIELDS}"
                                    def taxonResponse = taxonSearchUrl.toURL().getText("UTF-8")
                                    def taxonJson = js.parseText(taxonResponse)
                                    if (taxonJson.response.numFound > 0) {
                                        // Case does not necessarily match between bie and biocache
                                        def occurrence = taxonJson.response.docs.find { !kingdom || kingdom.equalsIgnoreCase(it.kingdom) }
                                        if (occurrence)
                                            image = [taxonID: taxonID, name: name, imageId: occurrence.image_url]
                                    }
                                }
                            }
                        } catch (Exception ex) {
                            log.warn "Unable to search for name ${name}: ${ex.message}"
                        }
                    }
                    if (image) {
                        def update = [:]
                        update["id"] = doc.id // doc key
                        update["idxtype"] = ["set": doc.idxtype] // required field
                        update["guid"] = ["set": doc.guid] // required field
                        update["image"] = ["set": image.imageId]
                        update["imageAvailable"] = ["set": true]
                        added++
                        buffer << update
                        lastImage = image
                    }
                    processed++
                }
                if (!buffer.isEmpty())
                    indexService.indexBatch(buffer, online)
                def percentage = Math.round(processed * 100 / total)
                def speed = Math.round((pageSize * 1000) / (System.currentTimeMillis() - startTime))
                log("Processed ${processed} names (${percentage}%), added ${added} images, ${speed} taxa per second. Last image ${lastImage.imageId} for ${lastImage.name}, ${lastImage.taxonID}")
                prevCursor = cursor
                cursor = json.nextCursorMark
            }
            log("Finished scan")
        } catch (Exception ex) {
            log.error("Unable to perform image scan", ex)
            log("Error during scan: " + ex.getMessage())
        }
    }

    /**
     * Collect the list where images are specifically listed
     */
    def collectImageLists(List lists) {
        def speciesListUrl = grailsApplication.config.speciesList.url
        def speciesListParams = grailsApplication.config.speciesList.params
        def imageMap = [:]
        log("Loading image lists")
        lists.each { list ->
            String drUid = list.uid
            String imageIdName = list.imageId
            String imageUrlName = list.imageUrl
            if (drUid && (imageIdName || imageUrlName)) {
                def url = "${speciesListUrl}${drUid}${speciesListParams}"
                try {
                    JSONElement json = JSON.parse(getStringForUrl(url))
                    json.each { item ->
                        def taxonID = item.lsid
                        def name = item.name
                        def imageId = imageIdName ? item.kvpValues.find { it.key == imageIdName }?.get("value") : null
                        def imageUrl = imageUrlName ? item.kvpValues.find {
                            it.key == imageUrlName
                        }?.get("value") : null
                        if (imageId || imageUrl) {
                            def image = [taxonID: taxonID, name: name, imageId: imageId, imageUrl: imageUrl]
                            if (taxonID && !imageMap.containsKey(taxonID))
                                imageMap[taxonID] = image
                            if (name && !imageMap.containsKey(name))
                                imageMap[name] = image
                        }
                    }
                } catch (Exception ex) {
                    log("Unable to load image list at ${url}: ${ex.getMessage()} ... ignoring")
                }
            }
        }
        log("Loaded image lists")
        return imageMap
    }

    /**
     * Denormalise the accepted taxa.
     * <p>
     * Do this by working down the hierarchy, Going from parent to child with a new set of parent taxa.
     * This is complicated if there are any accepted taxa with a dangling parent. We treat these as
     * root taxa, anyway.
     *
     * @param online Use the online index
     */
    def denormaliseTaxa(online) {
        int pageSize = 5000
        int bufferLimit = 1000
        int processed = 0
        def js = new JsonSlurper()
        def baseUrl = online ? grailsApplication.config.indexLiveBaseUrl : grailsApplication.config.indexOfflineBaseUrl
        def prevCursor = ""
        def cursor = "*"
        def startTime

        js.setType(JsonParserType.INDEX_OVERLAY)
        log("Getting species groups")
        def speciesGroupMapper = speciesGroupService.invertedSpeciesGroups
        log("Starting denormalisation scan for ${online ? 'online' : 'offline'} index")
        log("Clearing existing denormalisations")
        try {
            startTime = System.currentTimeMillis()
            while (prevCursor != cursor) {
                def solrServerUrl = baseUrl + "/select?wt=json&q=denormalised_b:true&cursorMark=${cursor}&sort=id+asc&rows=${pageSize}"
                def queryResponse = solrServerUrl.toURL().getText("UTF-8")
                def json = js.parseText(queryResponse)
                int total = json.response.numFound
                def docs = json.response.docs
                def buffer = []

                docs.each { doc ->
                    def update = [:]
                    update["id"] = doc.id // doc key
                    update["idxtype"] = [set: doc.idxtype] // required field
                    update["guid"] = [set: doc.guid] // required field
                    update["denormalised_b"] = [set: false ]
                    doc.each { k, v -> if (k.startsWith("rk_") || k.startsWith("rkid_")) update[k] = [set: null] }
                    doc.each { k, v -> if (k.startsWith("commonName")) update[k] = [set: null] }
                    update["commonName"] = [set: null]
                    update["commonNameExact"] = [set: null]
                    update["commonNameSingle"] = [set: null]
                    update["speciesGroup"] = [set: null]
                    update["speciesSubgroup"] = [set: null]
                    processed++
                    buffer << update
                }
                if (!buffer.isEmpty())
                    indexService.indexBatch(buffer, online)
                if (total > 0) {
                    def percentage = Math.round(processed * 100 / total)
                    log("Cleared ${processed} taxa (${percentage}%)")
                }
                prevCursor = cursor
                cursor = json.nextCursorMark
            }
        } catch (Exception ex) {
            log("Exception clearing denormalisation entries: ${ex.getMessage()}")
            log.error("Unable to clear denormalisations", ex)
        }
        log("Denormalising top-level taxa")
        try {
            processed = 0
            prevCursor = ""
            cursor = "*"
            while (prevCursor != cursor) {
                startTime = System.currentTimeMillis()
                def typeQuery = "idxtype:\"" + IndexDocType.TAXON.name() + "\"+AND+-acceptedConceptID:*+AND+-parentGuid:*"
                def solrServerUrl = baseUrl + "/select?wt=json&q=${typeQuery}&cursorMark=${cursor}&sort=id+asc&rows=${pageSize}"
                def queryResponse = solrServerUrl.toURL().getText("UTF-8")
                def json = js.parseText(queryResponse)
                int total = json.response.numFound
                def docs = json.response.docs
                def buffer = []

                docs.each { doc ->
                    denormaliseEntry(doc, [:], [], [], buffer, bufferLimit, pageSize, online, js, speciesGroupMapper)
                }
                processed++
                if (!buffer.isEmpty())
                    indexService.indexBatch(buffer, online)
                if (total > 0 && processed % 1000 == 0) {
                    def percentage = Math.round(processed * 100 / total)
                    log("Denormalised ${processed} top-level taxa (${percentage}%)")
                }
                prevCursor = cursor
                cursor = json.nextCursorMark
            }
        } catch (Exception ex) {
            log("Exception denormalising top-level: ${ex.getMessage()}")
            log.error("Unable to denormalise", ex)
        }
        log("Denormalising dangling taxa")
        try {
            processed++
            prevCursor = ""
            cursor = "*"
            while (prevCursor != cursor) {
                startTime = System.currentTimeMillis()
                def danglingQuery = "idxtype:\"${IndexDocType.TAXON.name()}\"+AND++-acceptedConceptID:*+AND+-denormalised_s:yes"
                def solrServerUrl = baseUrl + "/select?wt=json&q=${danglingQuery}&cursorMark=${cursor}&sort=id+asc&rows=${pageSize}"
                def queryResponse = solrServerUrl.toURL().getText("UTF-8")
                def json = js.parseText(queryResponse)
                int total = json.response.numFound
                def docs = json.response.docs
                def buffer = []

                docs.each { doc ->
                    denormaliseEntry(doc, [:], [], [], buffer, bufferLimit, pageSize, online, js, speciesGroupMapper)
                }
                processed++
                if (!buffer.isEmpty())
                    indexService.indexBatch(buffer, online)
                if (total > 0 && processed % 1000 == 0) {
                    def percentage = Math.round(processed * 100 / total)
                    log("Denormalised ${processed} dangling taxa (${percentage}%)")
                }
                prevCursor = cursor
                cursor = json.nextCursorMark
            }
        } catch (Exception ex) {
            log("Exception denormalising dangling taxa: ${ex.getMessage()}")
            log.error("Unable to denormalise", ex)
        }
        log("Denormalising synonyms")
        try {
            processed = 0
            prevCursor = ""
            cursor = "*"
            while (prevCursor != cursor) {
                startTime = System.currentTimeMillis()
                def synonymQuery = "idxtype:\"${IndexDocType.TAXON.name()}\"+AND+acceptedConceptID:*"
                def solrServerUrl = baseUrl + "/select?wt=json&q=${synonymQuery}&cursorMark=${cursor}&sort=id+asc&rows=${pageSize}"
                def queryResponse = solrServerUrl.toURL().getText("UTF-8")
                def json = js.parseText(queryResponse)
                int total = json.response.numFound
                def docs = json.response.docs
                def buffer = []

                docs.each { doc ->
                    def accepted = searchService.lookupTaxon(doc.acceptedConceptID, !online)
                    if (accepted) {
                        def update = [:]
                        update["id"] = doc.id // doc key
                        update["idxtype"] = [set: doc.idxtype] // required field
                        update["guid"] = [set: doc.guid ] // required field
                        update["acceptedConceptName"] = [set: accepted.nameComplete ?: accepted.scientificName ]
                        buffer << update
                    }
                    processed++
                     if (buffer.size() >= bufferLimit) {
                        indexService.indexBatch(buffer, online)
                        buffer.clear()
                    }
                    if (total > 0 && processed % 1000 == 0) {
                        def percentage = Math.round(processed * 100 / total)
                        log("Denormalised ${processed} synonyms (${percentage}%)")
                    }
                }
                if (!buffer.isEmpty())
                    indexService.indexBatch(buffer, online)
                prevCursor = cursor
                cursor = json.nextCursorMark
            }
        } catch (Exception ex) {
            log("Exception denormalising dangling taxa: ${ex.getMessage()}")
            log.error("Unable to denormalise", ex)
        }
        log("Finished taxon denormalisaion.")
    }

    private denormaliseEntry(doc, Map trace, List speciesGroups, List speciesSubGroups, List buffer, int bufferLimit, int pageSize, boolean online, JsonSlurper js, Map speciesGroupMapping) {
        def currentDistribution = (doc['distribution'] ?: []) as Set
        if (doc.denormalised_b)
            return currentDistribution
        def baseUrl = online ? grailsApplication.config.indexLiveBaseUrl : grailsApplication.config.indexOfflineBaseUrl
        def update = [:]
        def distribution = [] as Set
        def guid = doc.guid
        def scientificName = doc.scientificName
        update["id"] = doc.id // doc key
        update["idxtype"] = [set: doc.idxtype] // required field
        update["guid"] = [set: guid] // required field
        update["denormalised_b"] = [set: true ]
        update << trace

        if (doc.rank && doc.rankID && doc.rankID != 0) {
            def normalisedRank = normaliseRank(doc.rank)
            trace = trace.clone()
            trace << [("rk_" + normalisedRank): [set: scientificName]]
            trace << [("rkid_" + normalisedRank): [set: doc.guid]]
            // we have a unique rank name and value, check if it's in the species group list
            def rn = new RankedName(name: scientificName.toLowerCase(), rank: normalisedRank)
            def speciesGroup = speciesGroupMapping[rn]
            if (speciesGroup) {
                log.debug("Adding group ${speciesGroup.group} and subgroup ${speciesGroup.subGroup} to $scientificName")
                speciesGroups = speciesGroups.clone()
                speciesGroups << speciesGroup.group
                speciesSubGroups = speciesSubGroups.clone()
                speciesSubGroups << speciesGroup.subGroup
            }
            update["speciesGroup"] = [set: speciesGroups]
            update["speciesSubgroup"] = [set: speciesSubGroups]
        }
        def commonNames = searchService.lookupVernacular(guid, !online)
        if (commonNames && !commonNames.isEmpty()) {
            commonNames = commonNames.sort { n1, n2 -> n2.priority - n1.priority }
            update["commonName"] = [set: commonNames.collect { it.name }]
            update["commonNameExact"] = [set: commonNames.collect { it.name }]
            update["commonNameSingle"] = [set: commonNames.first().name]
        }
        def identifiers = searchService.lookupIdentifier(guid, !online)
        if (identifiers && !identifiers.isEmpty()) {
            update["additionalIdentifiers"] = [set: identifiers.collect { it.guid }]
        }

        def prevCursor = ""
        def cursor = "*"
        while (cursor != prevCursor) {
            def encGuid = URLEncoder.encode(doc.guid, "UTF-8")
            def parentQuery = "idxtype:\"${IndexDocType.TAXON.name()}\"+AND+taxonomicStatus:accepted+AND+parentGuid:\"${encGuid}\""
            def solrServerUrl = baseUrl + "/select?wt=json&q=${parentQuery}&cursorMark=${cursor}&sort=id+asc&rows=${pageSize}"
            def queryResponse = solrServerUrl.toURL().getText("UTF-8")
            def json = js.parseText(queryResponse)
            int total = json.response.numFound
            def docs = json.response.docs
            docs.each { child ->
                distribution.addAll(denormaliseEntry(child, trace, speciesGroups, speciesSubGroups, buffer, bufferLimit, pageSize, online, js, speciesGroupMapping))
            }
            prevCursor = cursor
            cursor = json.nextCursorMark
        }
        def additionalDistribtion = distribution.findAll { !currentDistribution.contains(it) }
        if (!additionalDistribtion.isEmpty())
            buffer['distrubution'] = [add: additionalDistribtion]
        buffer << update

        if (buffer.size() >= bufferLimit) {
            indexService.indexBatch(buffer, online)
            buffer.clear()
        }
        distribution.addAll(currentDistribution)
        return distribution
    }

    /**
     * Get the taxon rank structure
     *
     * @return
     */
    def ranks() {
        JsonSlurper slurper = new JsonSlurper()
        def ranks = slurper.parse(this.class.getResource("/taxonRanks.json"))
        def idMap = [:]
        def iter = ranks.iterator()
        while (iter.hasNext()) {
            def entry = iter.next()
            idMap.put(entry.rank, entry)
        }
        idMap
    }

    /**
     * Get vernacular name information
     */
    def vernacularNameStatus() {
        JsonSlurper slurper = new JsonSlurper()
        def ranks = slurper.parse(this.class.getResource("/vernacularNameStatus.json"))
        def idMap = [:]
        def iter = ranks.iterator()
        while (iter.hasNext()) {
            def entry = iter.next()
            idMap.put(entry.status, entry)
        }
        idMap
    }

    /**
     * Get identifier information
     */
    def identifierStatus() {
        JsonSlurper slurper = new JsonSlurper()
        def ranks = slurper.parse(this.class.getResource("/identifierStatus.json"))
        def idMap = [:]
        def iter = ranks.iterator()
        while (iter.hasNext()) {
            def entry = iter.next()
            idMap.put(entry.status, entry)
        }
        idMap
    }

    private def indexLists() {

        // http://lists.ala.org.au/ws/speciesList?isAuthoritative=eq:true&max=100
        //for each list
        // download http://lists.ala.org.au/speciesListItem/downloadList/{0}
        // read, and add to map
    }

    /**
     * Build a complete name + author
     * <p>
     * Some names are funny. So if there is a name supplied used that.
     * Otherwise try to build the name from scientific name + authorship
     *
     * @param nameComplete The supplied complete name, if available
     * @param scientificName The scientific name
     * @param scientificNameAuthorship The authorship
     * @return
     */
    String buildNameComplete(String nameComplete, String scientificName, String scientificNameAuthorship) {
        if (nameComplete)
            return nameComplete
        if (scientificNameAuthorship)
            return scientificName + " " + scientificNameAuthorship
        return scientificName
    }

    /**
     * Build an HTML formatted name
     * <p>
     * If a properly formatted name is supplied, then use that.
     * Otherwise, try yo build the name from the supplied information.
     * The HTMLised name is escaped and uses spans to encode formatting information.
     *
     *
     * @param nameFormatted The formatted name, if available
     * @param nameComplete The complete name, if available
     * @param scientificName The scientific name
     * @param scientificNameAuthorship The name authorship
     * @param rank The taxon rank
     * @param rankMap The lookup table for ranks
     *
     * @return The formatted name
     */
    String buildNameFormatted(String nameFormatted, String nameComplete, String scientificName, String scientificNameAuthorship, String rank, Map rankMap) {
        def rankGroup = rankMap.get(rank)?.rankGroup ?: "unknown"
        def formattedCssClass = rank ? "scientific-name rank-${rankGroup}" : "scientific-name";

        if (nameFormatted)
            return nameFormatted
        if (nameComplete) {
            def authorIndex = scientificNameAuthorship ? nameComplete.indexOf(scientificNameAuthorship) : -1
            if (authorIndex < 0)
                return "<span class=\"${formattedCssClass}\">${StringEscapeUtils.escapeHtml(nameComplete)}</span>"
            def preAuthor = nameComplete.substring(0, authorIndex - 1).trim()
            def postAuthor = nameComplete.substring(authorIndex + scientificNameAuthorship.length()).trim()
            def name = "<span class=\"${formattedCssClass}\">"
            if (preAuthor && !preAuthor.isEmpty())
                name = name + "<span class=\"name\">${StringEscapeUtils.escapeHtml(preAuthor)}</span> "
            name = name + "<span class=\"author\">${StringEscapeUtils.escapeHtml(scientificNameAuthorship)}</span>"
            if (postAuthor && !postAuthor.isEmpty())
                name = name + " <span class=\"name\">${StringEscapeUtils.escapeHtml(postAuthor)}</span>"
            name = name + "</span>"
            return name
        }
        if (scientificNameAuthorship)
            return "<span class=\"${formattedCssClass}\"><span class=\"name\">${StringEscapeUtils.escapeHtml(scientificName)}</span> <span class=\"author\">${StringEscapeUtils.escapeHtml(scientificNameAuthorship)}</span></span>"
        return "<span class=\"${formattedCssClass}\"><span class=\"name\">${StringEscapeUtils.escapeHtml(scientificName)}</span></span>"
    }


    /**
     * Helper method to do a HTTP GET and return String content
     *
     * @param url
     * @return
     */
    private String getStringForUrl(String url) throws IOException {
        String output = ""
        def inStm = new URL(url).openStream()
        try {
            output = IOUtils.toString(inStm)
        } finally {
            IOUtils.closeQuietly(inStm)
        }
        output
    }

    def log(msg) {
        log.info(msg)
        brokerMessagingTemplate.convertAndSend "/topic/import-feedback", msg.toString()
    }

    private updateProgressBar(int total, int current) {
        Double percentDone = (current / total) * 100
        log("${percentDone.round(1)}") // progress bar output (JS code detects numeric input)
    }

    private findAttribution(datasetID, attributionMap, datasetMap) {
        if (attributionMap.containsKey(datasetID))
            return attributionMap.get(datasetID)
        def attribution = null
        def dataset = searchService.getDataset(datasetID, datasetMap, true)
        if (dataset && dataset["name"]) {
            attribution = [datasetName: dataset["name"]]
        }
        attributionMap.put(datasetID, attribution)
        return attribution
    }
}