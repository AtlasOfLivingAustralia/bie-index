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
import au.org.ala.bie.indexing.RankedName
import au.org.ala.bie.indexing.WeightBuilder
import au.org.ala.bie.search.IndexDocType
import au.org.ala.bie.util.Encoder
import au.org.ala.bie.util.TitleCapitaliser
import au.org.ala.names.model.ALAParsedName
import au.org.ala.names.model.RankType
import au.org.ala.names.model.TaxonomicType
import au.org.ala.vocab.ALATerm
import grails.config.Config
import grails.core.support.GrailsConfigurationAware
import groovy.json.JsonParserType
import groovy.json.JsonSlurper
import org.apache.commons.io.IOUtils
import org.apache.commons.lang.StringEscapeUtils
import org.apache.commons.lang.StringUtils
import org.apache.solr.client.solrj.SolrQuery
import org.apache.solr.common.params.CursorMarkParams
import org.apache.solr.common.params.MapSolrParams
import org.gbif.api.exception.UnparsableException
import org.gbif.dwc.terms.*
import org.gbif.dwca.io.Archive
import org.gbif.dwca.io.ArchiveFactory
import org.gbif.dwca.io.ArchiveFile
import org.gbif.dwca.record.Record
import org.gbif.dwca.record.StarRecord
import org.gbif.nameparser.PhraseNameParser
import org.springframework.context.MessageSource
import org.springframework.context.i18n.LocaleContextHolder

import java.text.MessageFormat
import java.text.SimpleDateFormat
import java.util.regex.Pattern
import java.util.zip.GZIPInputStream

/**
 * Services for data importing.
 */
class ImportService implements GrailsConfigurationAware {
    static IN_SCHEMA = [
            DwcTerm.taxonID, DwcTerm.nomenclaturalCode, DwcTerm.establishmentMeans, DwcTerm.taxonomicStatus,
            DwcTerm.taxonConceptID, DwcTerm.scientificNameID, DwcTerm.nomenclaturalStatus, DwcTerm.nameAccordingTo, DwcTerm.nameAccordingToID,
            DwcTerm.scientificNameID, DwcTerm.namePublishedIn, DwcTerm.namePublishedInID, DwcTerm.namePublishedInYear,
            DwcTerm.taxonRemarks, DwcTerm.lifeStage, DwcTerm.sex, DwcTerm.locationID, DwcTerm.locality, DwcTerm.countryCode,
            DcTerm.source, DcTerm.language, DcTerm.license, DcTerm.format, DcTerm.rights, DcTerm.rightsHolder, DcTerm.temporal,
            ALATerm.status, ALATerm.nameID, ALATerm.nameFormatted, ALATerm.nameComplete, ALATerm.priority,
            ALATerm.verbatimNomenclaturalCode, ALATerm.verbatimNomenclaturalStatus, ALATerm.verbatimTaxonomicStatus,
            DwcTerm.datasetName, DcTerm.provenance,
            GbifTerm.isPlural, GbifTerm.isPreferredName, GbifTerm.organismPart, ALATerm.labels,
            GbifTerm.nameType
    ]
    // Terms that have been algorithmically added so needn't be added as extras
    static TAXON_ALREADY_INDEXED = [
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
            DwcTerm.taxonRemarks,
            DcTerm.provenance,
            GbifTerm.nameType
    ] as Set
    // Terms that get special treatment in vernacular additions
    static VERNACULAR_ALLREADY_INDEXED = [
            DwcTerm.vernacularName,
            ALATerm.nameID,
            DwcTerm.kingdom,
            DwcTerm.phylum,
            DwcTerm.class_,
            DwcTerm.order,
            DwcTerm.family,
            DwcTerm.taxonRank,
            ALATerm.status,
            DcTerm.language,
            DcTerm.source,
            DwcTerm.taxonRemarks,
            DcTerm.provenance
    ] as Set

    // Count report interval
    static REPORT_INTERVAL = 100000
    // Batch size for solr queries/commits and page sizes
    static BATCH_SIZE = 5000
    // Buffer size for commits
    static BUFFER_SIZE = 1000
    // The count size for count requests
    static COUNT_SIZE = 20
    // Accepted status
    static ACCEPTED_STATUS = TaxonomicType.values().findAll({ it.accepted }).collect({ "taxonomicStatus:${it.term}" }).join(' OR ')
    // Synonym status
    static SYNONYM_STATUS = TaxonomicType.values().findAll({ it.synonym }).collect({ "taxonomicStatus:${it.term}" }).join(' OR ')
    // A pattern indicating that we have a URL embedded in an anchor. Yuk
    static SOURCE_IN_ANCHOR = Pattern.compile(/<[Aa] [^>]*[Hh][Rr][Ee][Ff]\s*=\s*"([^"]+)"[^>]*>.*<\/[Aa]>/)

    def indexService, searchService, biocacheService, nameService, sitemapService
    def listService, layerService, collectoryService, wordpressService, knowledgeBaseService, biocollectService

    def speciesGroupService
    def conservationListsSource
    def jobService
    def grailsApplication
    MessageSource messageSource

    def brokerMessagingTemplate

    def static DYNAMIC_FIELD_EXTENSION = "_s"
    def static IMAGE_FIELDS = "taxon_concept_lsid, kingdom, phylum, class, order, family, genus, species, taxon_name, image_url, data_resource_uid"
    def isKeepIndexing = true // so we can cancel indexing thread (single thread only so field is OK)

    def nameParser = new PhraseNameParser()

    // Configuration fields
    File importDir
    String[] importSequence
    String[] importDailySequence
    String[] importWeeklySequence
    String gazetteerId
    List<String> localityKeywords
    String wordPressSitemap
    String wordPressBaseUrl
    String wordPressPageFormat
    List wordPressExcludedCategories
    String vernacularListsUrl
    Set<String> nationalSpeciesDatasets
    List<String> occurrenceCountFilter
    String commonNameDefaultLanguage
    String imageConfiguration
    String hiddenImageConfiguration
    String wikiConfiguration
    Set<String> commonNameLanguages
    double weightMin
    double weightMax
    double weightNorm
    WeightBuilder weightBuilder
    Map vernacularNameStatus
    Object commonStatus
    Object legislatedStatus
    Object preferredStatus
    Object deprecatedStatus
    Object favouritesConfiguration


    static {
        TermFactory tf = TermFactory.instance()
        for (Term term : ALATerm.values())
            tf.addTerm(term.qualifiedName(), term)
    }

    /**
     * Set configuration parameters from the grails config
     *
     * @param config The configuration
     */
    @Override
    void setConfiguration(Config config) {
        importDir = new File(config.import.taxonomy.dir)
        importSequence = config.import.sequence?.split(',')
        importDailySequence = config.import.sequenceDaily?.split(',')
        importWeeklySequence = config.import.sequenceWeekly?.split(',')
        gazetteerId = config.layers.gazetteerId
        localityKeywords = getConfigFile(config.localityKeywordsUrl)
        wordPressSitemap = config.wordPress.sitemap
        wordPressBaseUrl = config.wordPress.service
        wordPressPageFormat = config.wordPress.page
        wordPressExcludedCategories = config.wordPress.excludedCategories
        vernacularListsUrl = config.vernacularListsUrl
        nationalSpeciesDatasets = config.collectory.nationalSpeciesDatasets as Set
        occurrenceCountFilter = config.biocache.occurrenceCount.filterQuery as List
        commonNameDefaultLanguage = config.commonName.defaultLanguage
        imageConfiguration = config.images.config
        hiddenImageConfiguration = config.hiddenImages.config
        wikiConfiguration = config.wiki.config
        commonNameLanguages = config.commonName.languages ? config.commonName.languages.split(',') as Set : null
        weightMin = config.getProperty("import.priority.min", Double, 0.25)
        weightMax = config.getProperty("import.priority.max", Double, 5.0)
        weightNorm = config.getProperty("import.priority.norm", Double, 4000.0)
        weightBuilder = new WeightBuilder(getConfigFile(config.import.weightConfigUrl))
        def vernacularNameConfig = getConfigFile(config.import.vernacularName.statusUrl)
        vernacularNameStatus = vernacularNameConfig.collectEntries { e -> [(e.status): e] }
        vernacularNameConfig.each { e -> e.alias.each { a -> vernacularNameStatus.put(a, e) } }
        commonStatus = vernacularNameStatus.get(config.import.vernacularName.common)
        legislatedStatus = vernacularNameStatus.get(config.import.vernacularName.legislated)
        preferredStatus = vernacularNameStatus.get(config.import.vernacularName.preferred)
        deprecatedStatus = vernacularNameStatus.get(config.import.vernacularName.deprecated)
        favouritesConfiguration = getConfigFile(config.import.favouritesConfigUrl)
    }

    /**
     * Retrieve a set of file paths from the import directory.
     */
    def retrieveAvailableDwCAPaths() {

        def filePaths = []
        if (importDir.exists()) {
            File[] expandedDwc = importDir.listFiles()
            expandedDwc.each {
                if (it.isDirectory()) {
                    filePaths << it.getAbsolutePath()
                }
            }
        }
        filePaths
    }

    def importAll(String [] sequence, boolean online) {
        log "Starting import of all data"
        for (String step : sequence) {
            if (!jobService.current || jobService.current.cancelled) {
                log "Cancelled"
                return
            }
            step = step.trim().toLowerCase()
            log("Step ${step}")
            try {
                switch (step) {
                    case 'collectory':
                        importCollectory(online)
                        break
                    case 'conservation-lists':
                        importConservationSpeciesLists(online)
                        break
                    case 'denormalise':
                    case 'denormalize':
                        denormaliseTaxa(online)
                        break
                    case 'favourites':
                        buildFavourites(online)
                        break
                    case 'images':
                        loadImages(online)
                        break
                    case 'hidden-images':
                        loadHiddenImages(online)
                        break
                    case 'wiki-urls':
                        loadWikiUrls(online)
                        break
                    case 'layers':
                        importLayers(online)
                        break
                    case 'link-identifiers':
                        buildLinkIdentifiers(online)
                        break
                    case 'localities':
                        importLocalities(online)
                        break
                    case 'occurrences':
                        importOccurrenceData(online)
                        break
                    case 'regions':
                        importRegions(online)
                        break
                    case 'suggest-index':
                        buildSuggestIndex(online)
                        break
                    case 'taxonomy-all':
                        importAllDwcA(online)
                        break
                    case 'vernacular':
                        importVernacularSpeciesLists(online)
                        break
                    case 'weights':
                        buildWeights(online)
                        break
                    case 'wordpress':
                        importWordPressPages(online)
                        break
                    case 'knowledgebase':
                        importKnowledgeBasePages(online)
                        break
                    case 'biocollect':
                        importBiocollectProjects(online)
                        break
                    case 'species-lists':
                        importSpeciesLists(online)
                        break
                    case 'swap':
                        indexService.swap()
                        break
                    case 'sitemap':
                        buildSitemap(online)
                        break
                    default:
                        log("Unknown step ${step}")
                        log.error("Unknown step ${step}")
                }
            } catch (Exception ex) {
                def message = "Problem in step ${step}: ${ex.getMessage()}"
                log(message)
                log.error(message, ex)
            }
        }
        log "Finished import of all data"
    }

    def importAllDwcA(boolean online) {
        log "Starting import of all taxon field"
        clearTaxaIndex(online)
        def filePaths = retrieveAvailableDwCAPaths()
        filePaths.each {
            importDwcA(it, false, online)
        }
        log "Finished import of all taxon field"
    }

    /**
     * Import layer information into the index.
     *
     * @return
     */
    def importLayers(boolean online) {
        log "Starting layer import"
        if (online) {
            log "Layers may be temporarily unavailable"
        }
        def layers = layerService.layers()
        def batch = []
        indexService.deleteFromIndex(IndexDocType.LAYER, online)
        layers.each { layer ->
            def doc = [:]
            doc["id"] = layer.name
            doc["guid"] = layer.name
            doc["idxtype"] = IndexDocType.LAYER.name()
            doc["name"] = layer.displayname
            doc["description"] = layer.description
            doc["distribution"] = "N/A"
            log "Importing layer ${layer.displayname}"
            batch << doc
        }
        indexService.indexBatch(batch, online)
        log "Finished indexing ${layers.size()} layers"
        log "Finsihed layer import"
    }

    def importLocalities(boolean online) {
        log "Starting localities import"
        if (online) {
            log "Localities may be temporarily unavailable"
        }
        indexService.deleteFromIndex(IndexDocType.LOCALITY, online)
        if (gazetteerId) {
            log("Starting indexing ${gazetteerId}")
            log("Getting metadata for layer: ${gazetteerId}")
            def layer = layerService.get(gazetteerId)
            log("Starting indexing ${layer.id} - ${layer.name} gazetteer layer")
            importLayer(layer, online)
            log("Finished indexing ${layer.id} - ${layer.name} gazetteer layer")
        } else {
            log("Skipping localities, no gazetteer layer ID configured")
        }
        log "Finished localities import"
    }

    def importRegions(boolean online) {
        log "Starting regions import"
        if (online) {
            log "Regions may be temporarily unavailable"
        }
        indexService.deleteFromIndex(IndexDocType.REGION, online)
        def layers = layerService.layers()
        layers.each { layer ->
            if (layer.type == "Contextual") {
                importLayer(layer, online)
            }
        }
        log "Finished indexing ${layers.size()} region layers"
        log "Finished regions import"

    }

    /**
     * Import layer into index.
     *
     * @param layer
     * @return
     */
    private def importLayer(layer, boolean online) {
        log("Loading regions from layer " + layer.name)

        def file = layerService.getRegions(layer.id)

        if (file.exists() && file.length() > 0) {

            def gzipInput = new GZIPInputStream(new FileInputStream(file))

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
                    localityKeywords.each {
                        if (doc["description"].contains(it)) {
                            doc["distribution"] = it
                        }
                    }

                    batch << doc

                    if (batch.size() >= BATCH_SIZE) {
                        indexService.indexBatch(batch, online)
                        batch.clear()
                    }
                }
            }
            if (batch) {
                indexService.indexBatch(batch, online)
                batch.clear()
            }
        }
    }

    def importHabitats(boolean online) {

        def batch = []
        indexService.deleteFromIndex(IndexDocType.HABITAT, false)

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
        indexService.indexBatch(batch, online)
    }

    /**
     * Import collectory information into the index.
     *
     * @return
     */
    def importCollectory(boolean online) {
        log "Starting collectory import"
        [
                "dataResource": IndexDocType.DATARESOURCE,
                "dataProvider": IndexDocType.DATAPROVIDER,
                "institution" : IndexDocType.INSTITUTION,
                "collection"  : IndexDocType.COLLECTION
        ].each { entityType, indexDocType ->
            def entities = []
            def drLists = collectoryService.resources(entityType)
            log("About to import ${drLists.size()} ${entityType}")
            log("Clearing existing: ${entityType}")
            if (online) {
                log "Search may be temporarily unavailable for ${entityType}"
            }
            indexService.deleteFromIndex(indexDocType, online)
            log("Cleared")

            def maxSize = 100
            def batch = []
            def uriMap = [:]

            drLists.each { dr ->
                batch.add(dr)
                uriMap[dr.uid] = dr.uri

                if (batch.size() == maxSize) {
                    collectoryBatch(batch, entities, online, entityType, indexDocType, uriMap)
                    batch.clear()
                }
            }

            if (batch) {
                collectoryBatch(batch, entities, online, entityType, indexDocType, uriMap)
            }

            if (entities) {
                indexService.indexBatch(entities, online)
            }

            log("Finished indexing ${drLists.size()} ${entityType}")
        }
        log "Finished collectory import"
    }

    def collectoryBatch(batch, entities, online, entityType, indexDocType, uriMap) {
        def drs = collectoryService.getBatch(batch, entityType)

        drs.each { details ->
            def doc = [:]
            doc["id"] = uriMap[details.uid]
            doc["datasetID"] = details.uid
            doc["guid"] = details.alaPublicUrl
            doc["idxtype"] = indexDocType.name()
            doc["name"] = details.name?.trim()
            doc["description"] = details.pubDescription?.trim()
            doc["distribution"] = "N/A"

            if (details.rights)
                doc["rights"] = details.rights
            if (details.licenseType)
                doc["license"] = (details.licenseType + " " + details.licenseVersion ?: "").trim()
            if (details.acronym)
                doc["acronym"] = details.acronym
            if (details.logoRef?.uri)
                doc["image"] = details.logoRef.uri

            entities << doc

            if (entities.size() > BUFFER_SIZE) {
                indexService.indexBatch(entities, online)
                entities.clear()
            }
        }
    }

    /**
     * Index WordPress pages
     */
    def importWordPressPages(boolean online) throws Exception {
        log "Starting wordpress import"
        def buffer = []
        def pages = wordPressSitemap ? wordpressService.resources() : []
        def documentCount = 0
        def totalDocs = pages.size()
        log("WordPress pages found: ${totalDocs}") // update user via socket

        // slurp and build each SOLR doc (add to buffer)
        // We're expecting less than 1000 documents here so we can delete and update in one batch
        pages.each { pageUrl ->
            log "indexing url: ${pageUrl}"
            try {
                // Extract text from WP pages
                def document = wordpressService.getResource(pageUrl)
                List<String> categories = document.categories;
                boolean excludePost = categories.any { wordPressExcludedCategories.contains(it) }
                if (excludePost) {
                    log("Excluding post (id: ${document.id} with categories: ${categories}")
                } else {
                    categories = categories.findAll({ it != null }).collect({ it.replaceAll('\\s+', '_') })
                    documentCount++;
                    // create SOLR doc
                    log.debug documentCount + ". Indexing WP page - id: " + document.id + " | title: " + document.title + " | text: " + StringUtils.substring(document.body, 0, 100) + "... ";
                    def doc = [:]
                    doc["idxtype"] = IndexDocType.WORDPRESS.name()
                    doc["id"] = "wp" + document.id // probably not needed but safer to leave in
                    doc["guid"] = pageUrl
                    doc["name"] = document.title
                    doc["content"] = document.body
                    doc["linkIdentifier"] = pageUrl
                    //doc["australian_s"] = "recorded" // so they appear in default QF search
                    doc["categories"] = categories
                    // add to doc to buffer (List)
                    buffer << doc
                    // update progress bar (number output only)
                    if (documentCount > 0) {
                        updateProgressBar(totalDocs, documentCount)
                    }
                }
            } catch (IOException ex) {
                // catch it so we don't stop indexing other pages
                log("Problem accessing/reading WP page <${pageUrl}>: " + ex.getMessage() + " - document skipped")
                log.warn(ex.getMessage(), ex);
            }
        }
        log("Committing to ${buffer.size()} documents to SOLR...")
        if (online) {
            log "Search for wordpress pages may be temporarily unavailable"
        }
        indexService.deleteFromIndex(IndexDocType.WORDPRESS, online)
        indexService.indexBatch(buffer, online)
        updateProgressBar(100, 100) // complete progress bar
        log "Finished wordpress import"
    }

    /**
     * Index Knowledge Base pages.
     */
    def importKnowledgeBasePages(boolean online) throws Exception {
        log "Starting knowledge base import."

        // get List of Knowledge Base document URLs (each page's URL)
        def pages = knowledgeBaseService.resources()
        def documentCount = 0
        def totalDocs = pages.size()
        def buffer = []
        log("Knowledge base pages found: ${totalDocs}") // update user via socket

        // slurp and build each SOLR doc (add to buffer)
        pages.each { pageUrl ->
            log "indexing url: ${pageUrl}"
            try {
                Map docMap = knowledgeBaseService.getResource(pageUrl)

                if (docMap) {
                    documentCount++
                    // create SOLR doc
                    log.debug documentCount + ". Indexing KB page - id: " + docMap.id + " | title: " + docMap.title + " | text: " + StringUtils.substring(docMap.body, 0, 100) + "... ";
                    def doc = [:]
                    doc["idxtype"] = IndexDocType.KNOWLEDGEBASE.name()
                    doc["guid"] = pageUrl
                    doc["id"] = "kb" + docMap.id // guid required
                    doc["name"] = docMap.title
                    doc["content"] = docMap.body
                    doc["linkIdentifier"] = pageUrl
                    // add to doc to buffer (List)
                    buffer << doc
                    // update progress bar (number output only)
                    if (documentCount > 0) {
                        updateProgressBar(totalDocs, documentCount)
                    }
                } else {
                    log.warn("No page data retrieved for ${pageUrl}")
                }
            } catch (IOException ex) {
                // catch it so we don't stop indexing other pages
                log("Problem accessing/reading KB page <${pageUrl}>: " + ex.getMessage() + " - document skipped")
                log.warn(ex.getMessage(), ex)
            }
        }
        log("Committing to ${buffer.size()} documents to SOLR...")
        if (online) {
            log "Search for wordpress pages may be temporarily unavailable"
        }
        indexService.deleteFromIndex(IndexDocType.KNOWLEDGEBASE, online)
        indexService.indexBatch(buffer, online)
        updateProgressBar(100, 100) // complete progress bar
        log "Finished knowledge base import"
    }

    /**
     * Index Biocollect projects.
     */
    def importBiocollectProjects(boolean online) throws Exception {
        log "Starting biocollect import."

        // get List of Biocollect document URLs (each page's URL)
        def projects = biocollectService.resources()
        def documentCount = 0
        def totalDocs = projects.size()
        def buffer = []
        log("Biocollect projects found: ${totalDocs}") // update user via socket

        // slurp and build each SOLR doc (add to buffer)
        projects.each { project ->
            log "indexing url: ${project.url}"
            try {
                documentCount++
                // create SOLR doc
                log.debug documentCount + ". Indexing Biocollect project - id: " + project.projectId + " | title: " + project.name + " | text: " + StringUtils.substring(project.description?:"", 0, 100) + "... ";
                def doc = [:]
                doc["idxtype"] = IndexDocType.BIOCOLLECT.name()
                doc["guid"] = project.url
                doc["id"] = "bc" + project.projectId // guid required
                doc["name"] = project.name
                doc["content"] = project.description?:""
                doc["linkIdentifier"] = project.url

                doc["projectType_s"] = project.projectType
                if (project.urlImage) doc["image"] = project.urlImage
                doc["containsActivity_s"] = project.containsActivity
                doc["dateCreated_s"] = project.dateCreated
                if (project.keywords) doc["keywords_s"] = project.keywords

                // add to doc to buffer (List)
                buffer << doc
                // update progress bar (number output only)
                if (documentCount > 0) {
                    updateProgressBar(totalDocs, documentCount)
                }
            } catch (IOException ex) {
                // catch it so we don't stop indexing other pages
                log("Problem accessing/reading Biocollect project <${project.url}>: " + ex.getMessage() + " - document skipped")
                log.warn(ex.getMessage(), ex)
            }
        }
        log("Committing to ${buffer.size()} documents to SOLR...")
        if (online) {
            log "Search for biocollect projects may be temporarily unavailable"
        }
        indexService.deleteFromIndex(IndexDocType.BIOCOLLECT, online)
        indexService.indexBatch(buffer, online)
        updateProgressBar(100, 100) // complete progress bar
        log "Finished biocollect import"
    }

    /**
     * Index Species lists
     */
    def importSpeciesLists(boolean online) throws Exception {
        log "Starting species lists import."

        // get List of species lists
        def lists = listService.resources()
        def documentCount = 0
        def totalDocs = lists.size()
        def buffer = []
        log("Species lists found: ${totalDocs}") // update user via socket

        // slurp and build each SOLR doc (add to buffer)
        lists.each { list ->
            def url = MessageFormat.format(grailsApplication.config.lists.ui + grailsApplication.config.lists.show, list.dataResourceUid)
            log "indexing url: ${url}"
            try {
                documentCount++

                // create SOLR doc
                log.debug documentCount + ". Indexing Species lists - id: " + list.dataResourceUid + " | title: " + list.listName + "... ";
                def doc = [:]
                doc["idxtype"] = IndexDocType.SPECIESLIST.name()
                doc["guid"] = url
                doc["id"] = list.dataResourceUid // guid required
                doc["name"] = list.listName
                doc["linkIdentifier"] = url

                doc["listType_s"] = list.listType
                def content = messageSource.getMessage('list.content.listType', null, LocaleContextHolder.locale) + ": " +
                        messageSource.getMessage("list." + list.listType, null, LocaleContextHolder.locale)

                ['dateCreated', 'itemCount', 'isAuthoritative', 'isInvasive', 'isThreatened', 'region'].each {item ->
                    def label = messageSource.getMessage('list.content.' + item, null, LocaleContextHolder.locale)
                    if (label && list[item]) {
                        if ("true" == list[item].toString()) {
                            content += ', ' + label
                        } else {
                            content += ', ' + label + ": " + list[item]
                        }
                        doc[item + "_s"] = list[item]
                    }
                }

                doc["content"] = content
                // add to doc to buffer (List)
                buffer << doc
                // update progress bar (number output only)
                if (documentCount > 0) {
                    updateProgressBar(totalDocs, documentCount)
                }
            } catch (IOException ex) {
                // catch it so we don't stop indexing other pages
                log("Problem accessing/reading Species lists <${project.url}>: " + ex.getMessage() + " - document skipped")
                log.warn(ex.getMessage(), ex)
            }
        }
        log("Committing to ${buffer.size()} documents to SOLR...")
        if (online) {
            log "Search for species lists may be temporarily unavailable"
        }
        indexService.deleteFromIndex(IndexDocType.SPECIESLIST, online)
        indexService.indexBatch(buffer, online)
        updateProgressBar(100, 100) // complete progress bar
        log "Finished species lists import"
    }

    /**
     * Index Knowledge Base pages.
     */
    def buildSitemap(boolean online) throws Exception {
        sitemapService.build(online)
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
    def importConservationSpeciesLists(boolean online) throws Exception {
        def defaultSourceField = conservationListsSource.defaultSourceField ?: 'status'
        def defaultYearSourceField = conservationListsSource.defaultYearSourceField ?: 'eventDate'
        def defaultKingdomField = conservationListsSource.defaultKingdomField
        def defaultPhylumField = conservationListsSource.defaultPhylumField
        def defaultClassField = conservationListsSource.defaultClassField
        def defaultOrderField = conservationListsSource.defaultOrderField
        def defaultFamilyField = conservationListsSource.defaultFamilyField
        def defaultRankField = conservationListsSource.defaultRankField
        def lists = listService.conservationLists()

        Integer listNum = 0

        lists.each { resource ->
            listNum++
            this.updateProgressBar(lists.size(), listNum)
            String uid = resource.uid
            String solrField = resource.field ?: "conservationStatus_s"
            String sourceField = resource.sourceField ?: defaultSourceField
            String yearSourceField = resource.yearSourceField ?: defaultYearSourceField
            String kingdomField = resource.kingdomField ?: defaultKingdomField
            String phylumField = resource.phylumField ?: defaultPhylumField
            String classField = resource.classField ?: defaultClassField
            String orderField = resource.orderField ?: defaultOrderField
            String familyField = resource.familyField ?: defaultFamilyField
            String rankField = resource.rankField ?: defaultRankField
             if (uid && solrField) {
                log("Loading list from: " + uid)
                try {
                    def list = listService.get(uid, [sourceField, kingdomField, phylumField, classField, orderField, familyField, rankField])
                    updateDocsWithConservationStatus(list, sourceField, solrField, yearSourceField, uid, kingdomField, phylumField, classField, orderField, familyField, rankField, online)
                } catch (Exception ex) {
                    def msg = "Error calling webservice: ${ex.message}"
                    log(msg)
                    log.warn(msg, ex) // send to user via http socket
                }
            }
        }
    }

    def importVernacularSpeciesLists(boolean online) throws Exception {
        def config = this.getConfigFile(vernacularListsUrl)
        def lists = config.lists
        Integer listNum = 0

        lists.each { resource ->
            listNum++
            this.updateProgressBar(lists.size(), listNum)
            String uid = resource.uid
            String vernacularNameField = resource.vernacularNameField ?: config.defaultVernacularNameField
            String nameIdField = resource.nameIdField ?: config.defaultNameIdField
            String kingdomField = resource.kingdomField ?: config.defaultKingdomField
            String phylumField = resource.phylumField ?: config.defaultPhylumField
            String classField = resource.classField ?: config.defaultClassField
            String orderField = resource.orderField ?: config.defaultOrderField
            String familyField = resource.familyField ?: config.defaultFamilyField
            String rankField = resource.rankField ?: config.defaultRankField
            String statusField = resource.statusField ?: config.defaultStatusField
            String languageField = resource.languageField ?: config.defaultLanguageField
            String sourceField = resource.sourceField ?: config.defaultSourceField
            String temporalField = resource.temporalField ?: config.defaultTemporalField
            String locationIdField = resource.locationIdField ?: config.defaultLocationIdField
            String localityField = resource.localityField ?: config.defaultLocalitylField
            String countryCodeField = resource.countryCodeField ?: config.defaultCountryCodeField
            String sexField = resource.sexField ?: config.defaultSexField
            String lifeStageField = resource.lifeStageField ?: config.defaultLifeStageField
            String isPluralField = resource.isPluralField ?: config.defaultIsPluralField
            String isPreferredNameField = resource.isPreferredNameField ?: config.defaultIsPreferredNameField
            String organismPartField = resource.organismPartField ?: config.defaultOrganismPartField
            String labelsField = resource.labelsField ?: config.defaultLabelsField
            String taxonRemarksField = resource.taxonRemarksField ?: config.defaultTaxonRemarksField
            String provenanceField = resource.provenanceField ?: config.defaultProvenanceField
            String defaultLanguage = resource.defaultLanguage ?: config.defaultLanguage
            String defaultStatus = resource.defaultStatus ?: config.defaultStatus
            Map<String, Term> mapping = [
                    (vernacularNameField) : DwcTerm.vernacularName,
                    (nameIdField)         : ALATerm.nameID,
                    (kingdomField)        : DwcTerm.kingdom,
                    (phylumField)         : DwcTerm.phylum,
                    (classField)          : DwcTerm.class_,
                    (orderField)          : DwcTerm.order,
                    (familyField)         : DwcTerm.family,
                    (rankField)           : DwcTerm.taxonRank,
                    (statusField)         : ALATerm.status,
                    (languageField)       : DcTerm.language,
                    (sourceField)         : DcTerm.source,
                    (temporalField)       : DcTerm.temporal,
                    (locationIdField)     : DwcTerm.locationID,
                    (localityField)       : DwcTerm.locality,
                    (countryCodeField)    : DwcTerm.countryCode,
                    (sexField)            : DwcTerm.sex,
                    (lifeStageField)      : DwcTerm.lifeStage,
                    (isPluralField)       : GbifTerm.isPlural,
                    (isPreferredNameField): GbifTerm.isPreferredName,
                    (organismPartField)   : GbifTerm.organismPart,
                    (labelsField)         : ALATerm.labels,
                    (taxonRemarksField)   : DwcTerm.taxonRemarks,
                    (provenanceField)     : DcTerm.provenance
            ]
            if (uid && vernacularNameField) {
                log("Deleting entries for: " + uid)
                indexService.deleteFromIndexByQuery("idxtype:\"${IndexDocType.COMMON.name()}\" AND datasetID:\"${uid}\"", online)
                log("Loading list from: " + uid)
                try {
                    def list = listService.get(uid, mapping.keySet() as List)
                    importAdditionalVernacularNames(list, mapping, defaultLanguage, defaultStatus, uid, online)
                } catch (Exception ex) {
                    def msg = "Error calling webservice: ${ex.message}"
                    log(msg)
                    log.warn(msg, ex) // send to user via http socket
                }
            }
        }
    }

    /**
     * Go through the index and get occurrence counts for accepted taxa
     */
    def importOccurrenceData(online) {
        int pageSize = BATCH_SIZE
        int processed = 0
        def typeQuery = "idxtype:\"${IndexDocType.TAXON.name()}\" AND (${ACCEPTED_STATUS})"
        def prevCursor
        def cursor

        log("Starting occurrence count scan for ${online ? 'online' : 'offline'} index")
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
                def buffer = []
                def guids = []
                def updates = [:]

                docs.each { doc ->
                    def taxonID = doc.guid
                    def update = [id: doc.id, idxtype: [set: doc.idxtype], guid: [set: taxonID], occurrenceCount: [set: 0]]
                    if (nationalSpeciesDatasets && nationalSpeciesDatasets.contains(doc.datasetID)) {
                        update.locatedInHubCountry = ["set": true]
                    }
                    guids << taxonID
                    updates[taxonID] = update

                    if (guids.size() >= COUNT_SIZE) {
                        def cts = biocacheService.counts(guids, occurrenceCountFilter)
                        guids.each { guid ->
                            def val = cts[guid]
                            def upd = updates[guid]
                            if (val && upd)
                                upd.occurrenceCount = [set: val]
                            buffer << upd
                        }
                        guids = []
                        updates = [:]
                    }
                    if (buffer.size() >= BUFFER_SIZE) {
                        indexService.indexBatch(buffer, online)
                        buffer = []
                    }
                    processed++
                }
                if (guids.size() > 0) {
                    def cts = biocacheService.counts(guids, occurrenceCountFilter)
                    guids.each { guid ->
                        def val = cts[guid]
                        def update = updates[guid]
                        if (val && update)
                            update.occurrenceCount = [set: val]
                        buffer.addAll(updates.values())
                    }
                }
                if (!buffer.isEmpty())
                    indexService.indexBatch(buffer, online)
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
            log.error("Unable to perform occurrence scan", ex)
            log("Error during scan: " + ex.getMessage())
        }
    }

    /**
     * Update TAXON SOLR doc with conservation status info
     *
     * @param list
     * @param jsonFieldName
     * @param solrFieldName
     * @return
     */
    private updateDocsWithConservationStatus(List list, String jsonFieldName, String solrFieldName, String yearJsonFieldName, String drUid, String kingdomField, String phylumField, String classField, String orderField, String familyField, String rankField, boolean online) {
        if (list.size() > 0) {
            def totalDocs = list.size()
            def buffer = []
            def unmatchedTaxaCount = 0
            HashMap<String, Integer> conservationStatusYearMap = new HashMap<>();

            updateProgressBar2(100, 0)
            log("Updating taxa with ${solrFieldName}")
            list.eachWithIndex { item, i ->
                log.debug "item = ${item}"
                def taxonDoc

                if (item.lsid) {
                    taxonDoc = searchService.lookupTaxon(item.lsid, !online)
                }
                if (!taxonDoc && item.lsid) {
                    taxonDoc = searchService.lookupTaxonByPreviousIdentifier(item.lsid, !online)
                }
                if (!taxonDoc && item.name) {
                    def kingdom = kingdomField ? item.getAt(kingdomField) : null
                    def phylum = phylumField ? item.getAt(phylumField) : null
                    def class_ = classField ? item.getAt(classField) : null
                    def order = orderField ? item.getAt(orderField) : null
                    def family = familyField ? item.getAt(familyField) : null
                    def rank = rankField ? item.getAt(rankField) : null
                    def lsid = nameService.search(item.name, kingdom, phylum, class_, order, family, rank)
                    if (lsid) {
                        taxonDoc = searchService.lookupTaxon(lsid, !online)
                    }
                }

                def currentConservationStatusYear = item[yearJsonFieldName]
                if (currentConservationStatusYear) {
                    Integer lastUpdatedYear = conservationStatusYearMap.get(taxonDoc.guid)
                    if ((lastUpdatedYear && (currentConservationStatusYear > lastUpdatedYear)) || !lastUpdatedYear) {
                        updateSolrDoc(taxonDoc, item, jsonFieldName, solrFieldName, buffer, drUid, unmatchedTaxaCount)
                        conservationStatusYearMap.put((String) taxonDoc.guid, currentConservationStatusYear)
                    }
                } else {
                    updateSolrDoc(taxonDoc, item, jsonFieldName, solrFieldName, buffer, drUid, unmatchedTaxaCount)
                }

                if (i > 0) {
                    updateProgressBar2(totalDocs, i)
                }
            }

            log("Committing to SOLR...")
            indexService.indexBatch(buffer, online)
            updateProgressBar2(100, 100)
            log("Number of taxa unmatched: ${unmatchedTaxaCount}")
            log("Import finished.")
        } else {
            log("JSON not an array or has no elements - exiting")
        }
    }

    private void updateSolrDoc(taxonDoc, item, String jsonFieldName, String solrFieldName, ArrayList buffer, String drUid, int unmatchedTaxaCount) {
        if (taxonDoc) {
            // do a SOLR doc (atomic) update
            def doc = [:]
            doc["id"] = taxonDoc.id // doc key
            doc["idxtype"] = ["set": taxonDoc.idxtype] // required field
            doc["guid"] = ["set": taxonDoc.guid] // required field
            def fieldValue = item[jsonFieldName]
            doc[solrFieldName] = ["set": fieldValue] // "set" lets SOLR know to update record
            log.debug "adding to doc = ${doc}"
            buffer << doc
        } else {
            // No match so add it as a vernacular name
            def capitaliser = TitleCapitaliser.create(commonNameDefaultLanguage)
            def doc = [:]
            doc["id"] = UUID.randomUUID().toString() // doc key
            doc["idxtype"] = IndexDocType.TAXON.name() // required field
            doc["guid"] = "ALA_${item.name?.replaceAll("[^A-Za-z0-9]+", "_")}"
            // replace non alpha-numeric chars with '_' - required field
            doc["datasetID"] = drUid
            doc["datasetName"] = "Conservation list for ${solrFieldName}"
            doc["name"] = capitaliser.capitalise(item.name)
            doc["status"] = legislatedStatus?.status ?: "legislated"
            doc["priority"] = legislatedStatus?.priority ?: 500
            // set conservationStatus facet
            def fieldValue = item[jsonFieldName]
            doc[solrFieldName] = fieldValue
            log.info "New name doc = ${doc}"
            buffer << doc
            log("No existing taxon found for ${item.name}, so has been added as ${doc["guid"]}")
            unmatchedTaxaCount++
        }
    }

    private void importAdditionalVernacularNames(List list, Map<String, Term> mapping, String defaultLanguage, String defaultStatus, String uid, boolean online) {
        if (list.size() > 0) {
            def totalDocs = list.size()
            def buffer = []
            def unmatchedTaxaCount = 0
            def loaded = [] as Set

            updateProgressBar2(100, 0)
            log("Updating vernacular names from ${uid}")
            def getField = { Map<String, Term> m, Term t -> m.find({ it.value == t })?.key }
            def vernacularNameField = getField(mapping, DwcTerm.vernacularName)
            def nameIdField = getField(mapping, ALATerm.nameID)
            def kingdomField = getField(mapping, DwcTerm.kingdom)
            def phylumField = getField(mapping, DwcTerm.phylum)
            def classField = getField(mapping, DwcTerm.class_)
            def orderField = getField(mapping, DwcTerm.order)
            def familyField = getField(mapping, DwcTerm.family)
            def rankField = getField(mapping, DwcTerm.taxonRank)
            def statusField = getField(mapping, ALATerm.status)
            def languageField = getField(mapping, DcTerm.language)
            def sourceField = getField(mapping, DcTerm.source)
            def taxonRemarksField = getField(mapping, DwcTerm.taxonRemarks)
            def provenanceField = getField(mapping, DcTerm.provenance)

            list.eachWithIndex { item, i ->
                log.debug "item = ${item}"
                def vernacularName = item[vernacularNameField]
                def nameId = item[nameIdField]
                def kingdom = kingdomField ? item[kingdomField] : null
                def phylum = phylumField ? item[phylumField] : null
                def class_ = classField ? item[classField] : null
                def order = orderField ? item[orderField] : null
                def family = familyField ? item[familyField] : null
                def rank = rankField ? item[rankField] : null
                def status = statusField ? item[statusField] : null
                status = status ? vernacularNameStatus[status] : null
                def language = item[languageField] ?: defaultLanguage
                def source = item[sourceField]
                def taxonRemarks = item[taxonRemarksField]
                def provenance = item[provenanceField]
                def additional = mapping.inject([:], { a, t ->
                    if (t.key && !VERNACULAR_ALLREADY_INDEXED.contains(t.value)) {
                        def v = item[t.key]
                        if (v)
                            a[t.value.simpleName()] = v
                    }
                    a
                })

                if (!addVernacularName(
                        item.lsid,
                        item.name,
                        kingdom,
                        phylum,
                        class_,
                        order,
                        family,
                        rank,
                        vernacularName,
                        nameId,
                        status,
                        language,
                        source, uid,
                        taxonRemarks,
                        provenance,
                        additional,
                        buffer,
                        loaded,
                        commonStatus,
                        online)) {
                    unmatchedTaxaCount++
                }

                if (i > 0) {
                    updateProgressBar2(totalDocs, i)
                }
            }
            log("Committing names to SOLR...")
            if (!buffer.isEmpty())
                indexService.indexBatch(buffer, online)
            log("Unmatched names for " + uid + ": " + unmatchedTaxaCount)
            log("Ensure denormalisation is re-run")
        } else {
            log("JSON not an array or has no elements - exiting")
        }
    }


    private boolean addVernacularName(String taxonID, String name, String kingdom, String phylum, String class_, String order, String family, String rank, String vernacularName, String nameId, Object status, String language, String source, String datasetID, String taxonRemarks, String provenance, Map additional, List buffer, Set loaded, Object defaultStatus, boolean online) {
        def taxonDoc = null
        if (source) { // Extract URL from anchor if needed
            def sia = SOURCE_IN_ANCHOR.matcher(source)
            if (sia.matches())
                source = sia.group(1)
        }
        if (taxonID)
            taxonDoc = searchService.lookupTaxon(taxonID, !online)
        if (!taxonDoc && name) {
            taxonID = nameService.search(name, kingdom, phylum, class_, order, family, rank)
            if (taxonID)
                taxonDoc = searchService.lookupTaxon(taxonID, !online)
        }
        if (!taxonDoc) {
            log.warn("Can't find matching taxon document for ${taxonID} for ${vernacularName}, skipping")
            return false
        }
        def capitaliser = TitleCapitaliser.create(language ?: commonNameDefaultLanguage)
        vernacularName = capitaliser.capitalise(vernacularName)
        def key = taxonDoc.guid + "|" + vernacularName + "|" + language
        if (loaded.contains(key)) {
            log "Duplicate name for " + taxonID + ", " + name + ": " + vernacularName + " in " + language
            return true
        }
        loaded.add(key)
        def remarksList = taxonRemarks?.split("\\|").collect({ it.trim() })
        def provenanceList = provenance?.split("\\|").collect({ it.trim() })
        def vernacularDoc = searchService.lookupVernacular(taxonDoc.guid, vernacularName, language, !online)
        def priority = status?.priority ?: defaultStatus.priority
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
                doc["priority"] = ["set": priority]
            }
            if (source)
                doc["source"] = ["set": source]
            if (remarksList)
                doc["taxonRemarks"] = ["set": remarksList]
            if (provenanceList)
                doc["provenance"] = ["set": provenanceList]
            additional.each { k, v -> doc[k] = ["set": v] }
            log.debug "adding to doc = ${doc}"
            buffer << doc
        } else {
            // No match so add it as a vernacular name
            def doc = [:]
            doc["id"] = UUID.randomUUID().toString() // doc key
            doc["idxtype"] = IndexDocType.COMMON.name() // required field
            doc["guid"] = doc.id
            doc["taxonGuid"] = taxonDoc.guid
            doc["datasetID"] = datasetID
            doc["name"] = vernacularName
            doc["status"] = status?.status ?: defaultStatus.status
            doc["priority"] = priority
            doc["nameID"] = nameId
            doc["language"] = language
            if (source)
                doc["source"] = source
            if (remarksList)
                doc["taxonRemarks"] = remarksList
            if (provenanceList)
                doc["provenance"] = provenanceList
            additional.each { k, v -> doc[k] = v }
            log.debug "new name doc = ${doc} for ${vernacularName}"
            buffer << doc
        }
        return true
    }

    def clearDanglingSynonyms(boolean online) {
        log("Starting clear dangling synonyms")
        indexService.deleteFromIndexByQuery("(${SYNONYM_STATUS}) AND -acceptedConceptName:*", online)
        log("Finished clear dangling synonyms")
    }

    def clearTaxaIndex(boolean online) {
        log("Deleting existing taxon entries in index...")
        indexService.deleteFromIndex(IndexDocType.TAXON, online)
        indexService.deleteFromIndex(IndexDocType.COMMON, online)
        indexService.deleteFromIndex(IndexDocType.IDENTIFIER, online)
        indexService.deleteFromIndex(IndexDocType.TAXONVARIANT, online)
        log("Cleared.")
    }

    def importDwcA(dwcDir, clearIndex, online) {
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
                clearTaxaIndex(online)
            } else {
                log("Skipping deleting existing entries in index...")
            }
            if (rowType == DwcTerm.Taxon)
                importTaxonDwcA(archive, attributionMap, datasetMap, defaultDatasetName, online)
            else if (rowType == GbifTerm.VernacularName)
                importVernacularDwcA(archive.core, attributionMap, datasetMap, defaultDatasetName, online)
            else if (rowType == GbifTerm.Identifier)
                importIdentifierDwcA(archive.core, attributionMap, datasetMap, defaultDatasetName, online)
            else if (rowType == ALATerm.TaxonVariant)
                importTaxonVariantDwcA(archive.core, attributionMap, datasetMap, defaultDatasetName, online)
            else
                log("Unable to import an archive of type " + rowType)
            def variantExtension = archive.getExtension(ALATerm.TaxonVariant)
            if (variantExtension)
                importTaxonVariantDwcA(variantExtension, attributionMap, datasetMap, defaultDatasetName, online)
            def vernacularExtension = archive.getExtension(GbifTerm.VernacularName)
            if (vernacularExtension)
                importVernacularDwcA(vernacularExtension, attributionMap, datasetMap, defaultDatasetName, online)
            def identifierExtension = archive.getExtension(GbifTerm.Identifier)
            if (identifierExtension)
                importIdentifierDwcA(identifierExtension, attributionMap, datasetMap, defaultDatasetName, online)
            log("Import finished.")
        } catch (Exception ex) {
            log("There was problem with the import: " + ex.getMessage())
            log("See server logs for more details.")
            log.error(ex.getMessage(), ex)
        }

    }

    /**
     * Import a taxon DwC-A into this system.
     *
     * @return
     */
    def importTaxonDwcA(Archive archive, Map attributionMap, Map datasetMap, String defaultDatasetName, boolean online) {
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


        def buffer = []
        def counter = 0

        Iterator<StarRecord> iter = archive.iterator()

        while (iter.hasNext()) {

            StarRecord record = iter.next()
            Record core = record.core()

            def taxonID = core.id()
            def acceptedNameUsageID = core.value(DwcTerm.acceptedNameUsageID)
            def synonym = taxonID != acceptedNameUsageID && acceptedNameUsageID != "" && acceptedNameUsageID != null
            def parentNameUsageID = core.value(DwcTerm.parentNameUsageID)
            def defaultTaxonomicStatus = synonym ? "inferredSynonym" : "inferredAccepted"

            def doc = ["idxtype": IndexDocType.TAXON.name()]
            doc["id"] = UUID.randomUUID().toString()
            doc["guid"] = taxonID
            doc["parentGuid"] = parentNameUsageID
            if (synonym) {
                doc["acceptedConceptID"] = acceptedNameUsageID
            } else {
                // Filled out during denormalisation
                doc['speciesGroup'] = []
                doc['speciesSubgroup'] = []
            }
            buildTaxonRecord(core, doc, attributionMap, datasetMap, taxonRanks, defaultTaxonomicStatus, defaultDatasetName)

            if (record.hasExtension(GbifTerm.Distribution)) {
                record.extension(GbifTerm.Distribution).each {
                    def distribution = it.value(DwcTerm.stateProvince)
                    if (distribution)
                        doc["distribution"] = distribution
                }
            }

            buffer << doc
            counter++
            if (buffer.size() >= BUFFER_SIZE) {
                if (counter % REPORT_INTERVAL == 0)
                    log("Adding taxa: ${counter}")
                indexService.indexBatch(buffer, online)
                buffer.clear()
            }
        }

        if (!buffer.isEmpty()) {
            log("Adding taxa: ${counter}")
            indexService.indexBatch(buffer, online)
        }
    }


    def importVernacularDwcA(ArchiveFile archiveFile, Map attributionMap, Map datasetMap, String defaultDatasetName, boolean online) throws Exception {
        if (archiveFile.rowType != GbifTerm.VernacularName)
            throw new IllegalArgumentException("Vernacular import only works for files of type " + GbifTerm.VernacularName + " got " + archiveFile.rowType)
        log("Importing vernacular names")
        def buffer = []
        def count = 0
        for (Record record : archiveFile) {
            String taxonID = record.id()
            String vernacularName = record.value(DwcTerm.vernacularName)
            if (StringUtils.isBlank(vernacularName)) {
                log("Invalid vernacular name for taxon " + taxonID + " ...skipping")
                continue
            }
            String nameID = record.value(ALATerm.nameID)
            Object status = vernacularNameStatus.get(record.value(ALATerm.status))
            String language = record.value(DcTerm.language) ?: commonNameDefaultLanguage
            String source = record.value(DcTerm.source)
            String datasetID = record.value(DwcTerm.datasetID)
            String temporal = record.value(DcTerm.temporal)
            String locationID = record.value(DwcTerm.locationID)
            String locality = record.value(DwcTerm.locality)
            String countryCode = record.value(DwcTerm.countryCode)
            String sex = record.value(DwcTerm.sex)
            String lifeStage = record.value(DwcTerm.lifeStage)
            String isPlural = record.value(GbifTerm.isPlural)
            String isPreferred = record.value(GbifTerm.isPreferredName)
            if (!status && isPreferred && isPreferred.toBoolean())
                status = preferredStatus
            String organismPart = record.value(GbifTerm.organismPart)
            String taxonRemarks = record.value(DwcTerm.taxonRemarks)
            def remarksList = taxonRemarks?.split("\\|").collect({ it.trim() })
            String provenance = record.value(DcTerm.provenance)
            def provenanceList = provenance?.split("\\|").collect({ it.trim() })
            String labels = record.value(ALATerm.labels)
            def priority = status?.priority ?: commonStatus.priority
            def capitaliser = TitleCapitaliser.create(language ?: commonNameDefaultLanguage)
            vernacularName = capitaliser.capitalise(vernacularName)
            def doc = [:]
            doc["id"] = UUID.randomUUID().toString() // doc key
            doc["idxtype"] = IndexDocType.COMMON.name() // required field
            doc["guid"] = doc.id
            doc["taxonGuid"] = taxonID
            doc["datasetID"] = datasetID
            doc["name"] = vernacularName
            doc["status"] = status?.status ?: commonStatus.status
            doc["priority"] = priority
            doc["nameID"] = nameID
            doc["language"] = language ?: commonNameDefaultLanguage
            doc["source"] = source
            doc["temporal"] = temporal
            doc["locationID"] = locationID
            doc["locality"] = locality
            doc["countryCode"] = countryCode
            doc["sex"] = sex
            doc["lifeStage"] = lifeStage
            doc["isPlural"] = isPlural
            doc["organismPart"] = organismPart
            doc["taxonRemarks"] = remarksList
            doc["provenance"] = provenanceList
            doc["labels"] = labels
            doc["distribution"] = "N/A"
            def attribution = findAttribution(datasetID, attributionMap, datasetMap)
            if (attribution) {
                doc["datasetName"] = attribution["datasetName"]
                doc["rightsHolder"] = attribution["rightsHolder"]
            } else if (defaultDatasetName) {
                doc["datasetName"] = defaultDatasetName
            }
            buffer << doc
            count++
            if (buffer.size() >= BUFFER_SIZE) {
                indexService.indexBatch(buffer, online)
                buffer.clear()
                if (count % REPORT_INTERVAL == 0)
                    log("Processed ${count} records")
            }
        }
        if (buffer.size() > 0) {
            indexService.indexBatch(buffer, online)
            log("Processed ${count} records")
        }
    }


    def importIdentifierDwcA(ArchiveFile archiveFile, Map attributionMap, Map datasetMap, String defaultDatasetName, boolean online) throws Exception {
        if (archiveFile.rowType != GbifTerm.Identifier)
            throw new IllegalArgumentException("Identifier import only works for files of type " + GbifTerm.Identifier + " got " + archiveFile.rowType)
        log("Importing identifiers")
        def statusMap = identifierStatus()
        def defaultStatus = statusMap.get("unknown")
        def buffer = []
        def count = 0
        for (Record record : archiveFile) {
            def taxonID = record.id()
            def identifier = record.value(DcTerm.identifier)
            def title = record.value(DcTerm.title)
            def subject = record.value(DcTerm.subject)
            def format = record.value(DcTerm.format)
            def source = record.value(DcTerm.source)
            def datasetID = record.value(DwcTerm.datasetID)
            def idStatus = record.value(ALATerm.status)
            def status = idStatus ? statusMap.get(idStatus.toLowerCase()) : null
            String provenance = record.value(DcTerm.provenance)
            def provenanceList = provenance?.split("\\|").collect({ it.trim() })

            def doc = [:]
            doc["id"] = UUID.randomUUID().toString() // doc key
            doc["idxtype"] = IndexDocType.IDENTIFIER.name() // required field
            doc["guid"] = identifier
            doc["taxonGuid"] = taxonID
            doc["datasetID"] = datasetID
            doc["status"] = status?.status ?: defaultStatus.status
            doc["priority"] = status?.priority ?: defaultStatus.priority
            doc["name"] = title
            doc["subject"] = subject
            doc["format"] = format
            doc["source"] = source
            doc["provenance"] = provenanceList
            def attribution = findAttribution(datasetID, attributionMap, datasetMap)
            if (attribution) {
                doc["datasetName"] = attribution["datasetName"]
                doc["rightsHolder"] = attribution["rightsHolder"]
            } else if (defaultDatasetName) {
                doc["datasetName"] = defaultDatasetName
            }
            buffer << doc
            count++
            if (buffer.size() >= BUFFER_SIZE) {
                indexService.indexBatch(buffer, online)
                buffer.clear()
                if (count % REPORT_INTERVAL == 0)
                    log("Processed ${count} records")
            }
        }
        if (buffer.size() > 0) {
            indexService.indexBatch(buffer, online)
            log("Processed ${count} records")
        }
    }

    def importTaxonVariantDwcA(ArchiveFile archiveFile, Map attributionMap, Map datasetMap, String defaultDatasetName, boolean online) throws Exception {
        if (archiveFile.rowType != ALATerm.TaxonVariant)
            throw new IllegalArgumentException("Taxon variant import only works for files of type " + ALATerm.TaxonVariant + " got " + archiveFile.rowType)
        log("Importing taxon variants")
        def taxonRanks = ranks()
        def buffer = []
        def count = 0
        for (Record record : archiveFile) {
            def doc = [:]
            doc["id"] = UUID.randomUUID().toString() // doc key
            doc["idxtype"] = IndexDocType.TAXONVARIANT.name() // required field
            doc["taxonGuid"] = record.id()
            doc["guid"] = record.value(DwcTerm.taxonID)
            buildTaxonRecord(record, doc, attributionMap, datasetMap, taxonRanks, "inferredAccepted", defaultDatasetName)
            buffer << doc
            count++
            if (buffer.size() >= BUFFER_SIZE) {
                indexService.indexBatch(buffer, online)
                buffer.clear()
                if (count % REPORT_INTERVAL == 0)
                    log("Processed ${count} records")
            }
        }
        if (buffer.size() > 0) {
            indexService.indexBatch(buffer, online)
            log("Processed ${count} records")
        }
    }

    def getTaxonRankID(taxonRanks, taxonRank) {
        def tr = taxonRanks.get(taxonRank)
        if (!tr) {
            tr = taxonRanks.find { it.value.otherNames?.contains(taxonRank) }
        }

        return tr ? tr.rankID : -1
    }

    def buildTaxonRecord(Record record, Map doc, Map attributionMap, Map datasetMap, Map taxonRanks, String defaultTaxonomicStatus, String defaultDatasetName) {
        def datasetID = record.value(DwcTerm.datasetID)
        def taxonRank = (record.value(DwcTerm.taxonRank) ?: "").toLowerCase()
        def scientificName = record.value(DwcTerm.scientificName)
        def parentNameUsageID = record.value(DwcTerm.parentNameUsageID)
        def scientificNameAuthorship = record.value(DwcTerm.scientificNameAuthorship)
        def nameComplete = record.value(ALATerm.nameComplete)
        def nameFormatted = record.value(ALATerm.nameFormatted)
        // TODO: use ALATerm.taxonRankID, or the alternative, when it exists in ala-name-matching-model
        //def taxonRankID = record.value(ALATerm.taxonRankID) ?: getTaxonRankID(taxonRanks, taxonRank)
        def taxonRankID = getTaxonRankID(taxonRanks, taxonRank)
        def taxonomicStatus = record.value(DwcTerm.taxonomicStatus) ?: defaultTaxonomicStatus
        def nameType = record.value(GbifTerm.nameType)
        String taxonRemarks = record.value(DwcTerm.taxonRemarks)
        String provenance = record.value(DcTerm.provenance)
        def remarksList = taxonRemarks?.split("\\|").collect({ it.trim() })
        def provenanceList = provenance?.split("\\|").collect({ it.trim() })


        doc["datasetID"] = datasetID
        doc["parentGuid"] = parentNameUsageID
        doc["rank"] = taxonRank
        //only add the ID if we have a recognised rank
        if (taxonRankID > 0) {
            doc["rankID"] = taxonRankID
        }
        doc["scientificName"] = scientificName
        doc["scientificNameAuthorship"] = scientificNameAuthorship
        doc["nameComplete"] = buildNameComplete(nameComplete, scientificName, scientificNameAuthorship)
        doc["nameFormatted"] = buildNameFormatted(nameFormatted, nameComplete, scientificName, scientificNameAuthorship, taxonRank, taxonRanks)
        doc["taxonomicStatus"] = taxonomicStatus
        doc["taxonRemarks"] = remarksList
        doc["provenance"] = provenanceList

        // See if we can get a name type
        if (!nameType) {
            try {
                def parseRank = taxonRankID > 0 ? RankType.getForId(taxonRankID)?.cbRank : null
                def pn = nameParser.parse(scientificName, parseRank)
                nameType = pn?.type?.name()?.toLowerCase()
                if (pn in ALAParsedName && pn.phraseVoucher)
                    nameType = 'phraseName'
            } catch (UnparsableException ex) {
                nameType = ex.type?.name()?.toLowerCase() ?: 'unknown'
            } catch (Exception ex) {
                nameType = 'unknown'
            }
        }
        doc["nameType"] = nameType

        //index additional fields that are supplied in the record
        record.terms().each { term ->
            if (!TAXON_ALREADY_INDEXED.contains(term)) {
                if (IN_SCHEMA.contains(term)) {
                    doc[term.simpleName()] = record.value(term)
                } else {
                    //use a dynamic field extension
                    doc[term.simpleName() + DYNAMIC_FIELD_EXTENSION] = record.value(term)
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
        int pageSize = BUFFER_SIZE
        int page
        def cursor
        def prevCursor
        def typeQuery = "idxtype:\"${IndexDocType.TAXON.name()}\""

        try {
            log("Clearing link identifiers")
            clearField("linkIdentifier", null, online)
            log("Making link text")
            page = 0
            cursor = CursorMarkParams.CURSOR_MARK_START
            prevCursor = ""
            while (cursor != prevCursor) {
                def startTime = System.currentTimeMillis()
                prevCursor = cursor
                def response = indexService.query(online, typeQuery, [], pageSize, null, null, 'id', 'asc', cursor)
                int total = response.results.numFound
                def buffer = []

                if (response.results.isEmpty())
                    break
                cursor = response.nextCursorMark
                response.results.each { doc ->
                    def name = (doc.scientificName ?: doc.name)?.trim()
                    if (name) {
                        name = name.replaceAll(/[^\w\s]/, "")
                        name = name.replaceAll(/\s+/, "_")
                    }
                    def update = [:]
                    update["id"] = doc.id // doc key
                    update["idxtype"] = ["set": doc.idxtype] // required field
                    update["guid"] = ["set": doc.guid] // required field
                    update["linkText"] = ["set": name]
                    buffer << update
                }
                if (!buffer.isEmpty())
                    indexService.indexBatch(buffer, online)
                page++
                if (page % 10 == 0) {
                    def progress = page * pageSize
                    def percentage = Math.round(progress * 100 / total)
                    def speed = Math.round((page * 1000) / (System.currentTimeMillis() - startTime))
                    log("Processed ${page * pageSize} names (${percentage}%), ${speed} names per second")
                }
            }
            log("Starting link identifier scan")
            int added = 0
            page = 0
            cursor = CursorMarkParams.CURSOR_MARK_START
            prevCursor = ""
            while (cursor != prevCursor) {
                def startTime = System.currentTimeMillis()
                prevCursor = cursor
                def response = indexService.query(online, "linkText:*", [], pageSize, null, null, 'id', 'asc', cursor)
                int total = response.results.numFound
                def buffer = []

                if (response.results.isEmpty())
                    break
                cursor = response.nextCursorMark
                response.results.each { doc ->
                    try {
                        def nameQuery = "linkText:\"${Encoder.escapeSolr(doc.linkText)}\""

                        // query for taxonomicStatus:accepted first as it is the most likely taxon for the linkIdentifier
                        def nameResponse = indexService.query(online, nameQuery, ["taxonomicStatus:accepted"], 1)

                        // query for taxonomicStatus:inferredAccepted second as it is the second most likely taxon for the linkIdentifier
                        if (nameResponse.results.numFound == 0) {
                            nameResponse = indexService.query(online, nameQuery, ["taxonomicStatus:inferredAccepted"], 1)
                        }

                        // query for other taxonomicStatuses last
                        if (nameResponse.results.numFound == 0) {
                            nameResponse = indexService.query(online, nameQuery, [], 1)
                        }

                        int found = nameResponse.results.numFound
                        if (found == 1) {
                            def currentDoc = nameResponse.results[0]
                            //log.debug("Adding link identifier for ${name} to ${doc.id}")
                            def update = [:]
                            update["id"] = currentDoc.id // doc key
                            update["idxtype"] = ["set": currentDoc.idxtype] // required field
                            update["guid"] = ["set": currentDoc.guid] // required field
                            update["linkIdentifier"] = ["set": currentDoc.linkText]
                            buffer << update
                            added++
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

    def getImagesLists() {
        this.getConfigFile(imageConfiguration).lists
    }

    def getHiddenImagesLists() {
        this.getConfigFile(hiddenImageConfiguration).lists
    }

    def getWikiUrlLists() {
        this.getConfigFile(wikiConfiguration).lists
    }

    /**
     * Go through the index and build image links for taxa
     */
    def loadImages(online) {
        int pageSize = BATCH_SIZE
        int processed = 0
        int added = 0
        def typeQuery = "idxtype:\"${IndexDocType.TAXON.name()}\" AND (${ACCEPTED_STATUS})"
        def clearQuery = "imageAvailable:true"
        def prevCursor
        def cursor
        def listConfig = this.getConfigFile(imageConfiguration)
        def imageMap = collectImageLists(listConfig.lists)
        def rankMap = listConfig.ranks.collectEntries { r -> [(r.rank): r] }
        def requiredFilters = listConfig.required ?: []
        def preferredFilters = (listConfig.preferred ?: []) + requiredFilters
        def imageFields = Encoder.escapeQuery(listConfig.imageFields ?: IMAGE_FIELDS)
        log.debug "listConfig = ${listConfig} || imageFields = ${imageFields}"
        def lastImage = [imageId: "none", taxonID: "none", name: "none"]
        def addImageSearch = { query, field, value, boost ->
            if (field && value) {
                query = query ? query + " OR " : ""
                query = query + "${field}:\"${value}\"^${boost}"
            }
            query
        }

        log("Clearing images for ${online ? 'online' : 'offline'} index")
        try {
            prevCursor = ""
            cursor = CursorMarkParams.CURSOR_MARK_START
            processed = 0
            while (cursor != prevCursor) {
                SolrQuery query = new SolrQuery(clearQuery)
                query.setParam('cursorMark', cursor)
                query.setSort("id", SolrQuery.ORDER.asc)
                query.setRows(pageSize)
                def response = indexService.query(query, online)
                def docs = response.results
                def buffer = []

                docs.each { doc ->
                    if (imageMap[doc.guid] == null) {
                        def update = [:]
                        update["id"] = doc.id // doc key
                        update["idxtype"] = ["set": doc.idxtype] // required field
                        update["guid"] = ["set": doc.guid] // required field
                        update["image"] = ["set": null]
                        update["imageAvailable"] = ["set": null]
                        buffer << update
                        processed++
                    }
                }
                if (!buffer.isEmpty())
                    indexService.indexBatch(buffer, online)
                log("Cleared ${processed} images")
                prevCursor = cursor
                cursor = response.nextCursorMark
            }
        } catch (Exception ex) {
            log.error("Unable to clear images", ex)
            log("Error during image clear: " + ex.getMessage())
        }
        log("Loading preferred images")
        updatePreferredImages(online, imageMap)
        log("Starting image load scan for ${online ? 'online' : 'offline'} index")
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
                def buffer = []

                docs.each { doc ->
                    def taxonID = doc.guid
                    def name = doc.scientificName ?: doc.name
                    def rank = rankMap[doc.rank]
                    def image = null

                    if (rank != null && !imageMap[taxonID]) {
                        try {
                            def biocacheQuery = null
                            biocacheQuery = addImageSearch(biocacheQuery, "lsid", taxonID, 100)
                            biocacheQuery = addImageSearch(biocacheQuery, rank.idField, taxonID, 20)
                            if (biocacheQuery) {
                                biocacheQuery = "(${biocacheQuery}) AND multimedia:Image"
                                def occurrences
                                // iterate over preferredFilters instead using them all in a single query
                                if (preferredFilters) {
                                    for (String fq : preferredFilters) {
                                        occurrences = biocacheService.search(biocacheQuery, [fq])
                                        if (occurrences.totalRecords > 0) {
                                            break;
                                        }
                                    }
                                }

                                if (occurrences.totalRecords < 1 && preferredFilters) {
                                    // Try without preferred filters
                                    occurrences = biocacheService.search(biocacheQuery, requiredFilters)
                                }
                                if (occurrences.totalRecords > 0) {
                                    // Case does not necessarily match between bie and biocache
                                    def occurrence = occurrences.occurrences[0]
                                    if (occurrence)
                                        image = [taxonID: taxonID, name: name, imageId: occurrence.image]
                                }
                            }
                        } catch (Exception ex) {
                            log.warn "Unable to search for name ${name}: ${ex.message}"
                        }
                    }
                    if (image) {
                        updateImage(doc, image.imageId, buffer, online)
                        added++
                        lastImage = image
                    }
                    processed++
                }
                if (!buffer.isEmpty())
                    indexService.indexBatch(buffer, online)
                def percentage = total ? Math.round(processed * 100 / total) : 100
                def speed = total ? Math.round((pageSize * 1000) / (System.currentTimeMillis() - startTime)) : 0
                log("Processed ${processed} names (${percentage}%), added ${added} images, ${speed} taxa per second. Last image ${lastImage.imageId} for ${lastImage.name}, ${lastImage.taxonID}")
                prevCursor = cursor
                cursor = response.nextCursorMark
            }
            log("Finished scan")
        } catch (Exception ex) {
            log.error("Unable to perform image scan", ex)
            log("Error during scan: " + ex.getMessage())
        }
    }

    /**
     * Go through the index and build hiddenImage field
     */
    def loadHiddenImages(online) {
        int pageSize = BATCH_SIZE
        int processed = 0
        def clearQuery = "hiddenImages_s:*"
        def prevCursor
        def cursor
        def listConfig = this.getConfigFile(hiddenImageConfiguration)
        def imageMap = collectHiddenImageLists(listConfig.lists)
        log.debug "listConfig = ${listConfig} "

        log("Clearing hiddenImages for ${online ? 'online' : 'offline'} index")
        try {
            prevCursor = ""
            cursor = CursorMarkParams.CURSOR_MARK_START
            processed = 0
            while (cursor != prevCursor) {
                SolrQuery query = new SolrQuery(clearQuery)
                query.setParam('cursorMark', cursor)
                query.setSort("id", SolrQuery.ORDER.asc)
                query.setRows(pageSize)
                def response = indexService.query(query, online)
                def docs = response.results
                def buffer = []

                docs.each { doc ->
                    // only remove hidden images that will not be updated below
                    if (imageMap[doc.guid] == null) {
                        def update = [:]
                        update["id"] = doc.id // doc key
                        update["idxtype"] = ["set": doc.idxtype] // required field
                        update["guid"] = ["set": doc.guid] // required field
                        update["hiddenImages_s"] = ["set": null]
                        buffer << update
                        processed++
                    }
                }
                if (!buffer.isEmpty())
                    indexService.indexBatch(buffer, online)
                log("Cleared ${processed} hiddenImages")
                prevCursor = cursor
                cursor = response.nextCursorMark
            }
        } catch (Exception ex) {
            log.error("Unable to clear hiddenImages", ex)
            log("Error during hiddenImages clear: " + ex.getMessage())
        }
        log("Loading hiddenImages")
        updateHiddenImages(online, imageMap)
    }

    /**
     * Go through the index and build hiddenImage field
     */
    def loadWikiUrls(online) {
        int pageSize = BATCH_SIZE
        int processed = 0
        def clearQuery = "wikiUrl_s:*"
        def prevCursor
        def cursor
        def listConfig = this.getConfigFile(wikiConfiguration)
        def imageMap = collectHiddenImageLists(listConfig.lists)
        log.debug "listConfig = ${listConfig} "

        log("Clearing wikiUrls for ${online ? 'online' : 'offline'} index")
        try {
            prevCursor = ""
            cursor = CursorMarkParams.CURSOR_MARK_START
            processed = 0
            while (cursor != prevCursor) {
                SolrQuery query = new SolrQuery(clearQuery)
                query.setParam('cursorMark', cursor)
                query.setSort("id", SolrQuery.ORDER.asc)
                query.setRows(pageSize)
                def response = indexService.query(query, online)
                def docs = response.results
                def buffer = []

                docs.each { doc ->
                    // only remove hidden images that will not be updated below
                    if (imageMap[doc.guid] == null) {
                        def update = [:]
                        update["id"] = doc.id // doc key
                        update["idxtype"] = ["set": doc.idxtype] // required field
                        update["guid"] = ["set": doc.guid] // required field
                        update["wikiUrl_s"] = ["set": null]
                        buffer << update
                        processed++
                    }
                }
                if (!buffer.isEmpty())
                    indexService.indexBatch(buffer, online)
                log("Cleared ${processed} wikiUrls")
                prevCursor = cursor
                cursor = response.nextCursorMark
            }
        } catch (Exception ex) {
            log.error("Unable to clear wikiUrls", ex)
            log("Error during wikiUrls clear: " + ex.getMessage())
        }
        log("Loading wikiUrls")
        updateWikiUrls(online, imageMap)
    }

    /**
     * Collect the list where images are specifically listed
     */
    def collectImageLists(List lists) {
        def imageMap = [:]
        log("Loading image lists")
        lists.each { list ->
            String drUid = list.uid
            String imageIdName = list.imageId
            String imageUrlName = list.imageUrl
            if (drUid && (imageIdName || imageUrlName)) {
                try {
                    def images = listService.get(drUid, [imageIdName, imageUrlName])
                    images.each { item ->
                        def taxonID = item.lsid
                        def name = item.name
                        def imageId = imageIdName ? item[imageIdName] : null
                        def imageUrl = imageUrlName ? item[imageUrlName] : null
                        if (imageId || imageUrl) {
                            def image = [taxonID: taxonID, name: name, imageId: imageId, imageUrl: imageUrl]
                            if (taxonID && !imageMap.containsKey(taxonID))
                                imageMap[taxonID] = image
                        }
                    }
                } catch (Exception ex) {
                    log("Unable to load image list at ${url}: ${ex.getMessage()} ... ignoring")
                }
            }
        }
        log("Loaded image lists (${imageMap.size()} taxa)")
        return imageMap
    }

    /**
     * Collect the list where images are specifically listed
     */
    def collectHiddenImageLists(List lists) {
        def imageMap = [:]
        log("Loading image lists")
        lists.each { list ->
            String drUid = list.uid
            String imageIdName = list.imageId
            if (drUid && (imageIdName)) {
                try {
                    def images = listService.get(drUid, [imageIdName])
                    images.each { item ->
                        def taxonID = item.lsid
                        def name = item.name
                        def imageId = imageIdName ? item[imageIdName] : null
                        if (imageId) {
                            def image = [taxonID: taxonID, name: name, imageId: imageId]
                            if (taxonID && !imageMap.containsKey(taxonID))
                                imageMap[taxonID] = image
                        }
                    }
                } catch (Exception ex) {
                    log("Unable to load image list at ${url}: ${ex.getMessage()} ... ignoring")
                }
            }
        }
        log("Loaded image lists (${imageMap.size()} taxa)")
        return imageMap
    }

    /**
     * Updates preferred image ID via input list of (map of) GUIDs and image IDs
     *
     * @param preferredImagesList
     * @return
     */
    def updateDocsWithPreferredImage(List<Map> preferredImagesList){

        List<String> guidList = []

        preferredImagesList.each {Map guidImageMap ->
            guidList.push(guidImageMap.guid)
        }

        String guids = guidList.join(",")

        log.info ("guid List to update: " + guids)

        def paramsMap = [
                q: "guid:\"" + guids +"\"",
                fq: "idxtype:${IndexDocType.TAXON.name()}",
                wt: "json"
        ]
        def buffer = []
        MapSolrParams solrParams = new MapSolrParams(paramsMap)
        def searchResults = searchService.getCursorSearchResults(solrParams, false)
        def resultsDocs = searchResults?.results ?: []
        def totalDocumentsUpdated = 0
        resultsDocs.each { Map doc ->
            if (doc.containsKey("id") && doc.containsKey("guid") && doc.containsKey("idxtype")) {
                String imageId = getImageFromParamList(preferredImagesList, doc.guid)
                log.info ("Updating: guid " + doc.guid + " with imageId " + imageId)
                if (!doc.containsKey("image") || (doc.containsKey("image") && doc.image != imageId)) {
                    updateImage(doc, imageId, buffer, true)
                    totalDocumentsUpdated ++
                }
            } else {
                log.warn "Updating doc error: missing keys ${doc}"
            }
        }

        def updatedTaxa = []

        if (buffer.size() > 0) {
            log.info ("Committing to SOLR..." + guidList)
            indexService.indexBatch(buffer, true)
            updatedTaxa = searchService.getTaxa(guidList)
        } else {
            log.info "Nothing to update for guidList: " + guidList
        }

        updatedTaxa
    }

    private String getImageFromParamList (List<Map> preferredImagesList, String guid) {
        return preferredImagesList.grep{it.guid == guid}.image[0]
    }

    /**
     * Triggered from admin -> links import page. Runs on separate thread and send async message back to page via log() method
     *
     * @param online
     */
    def loadPreferredImages(online) {
        Map listConfig = this.getConfigFile(imageConfiguration) // config defined JSON file
        Map imagesMap = collectImageLists(listConfig.lists) // reads preferred images list via WS

        log "Loadng preferred images"
        return updatePreferredImages(online, imagesMap)
    }

    /**
     * Triggered from admin -> links import page. Runs on separate thread and send async message back to page via log() method
     *
     * @param online Use online rather than offline store
     * @param imagesMap The images to update
     * @param guidList The list to update from the map
     */
    def updatePreferredImages(online, imagesMap) {
        def updatedTaxa = []
        Integer batchSize = 20

        List guidList = imagesMap.values().collect { image -> image.taxonID }
        int totalDocs = guidList.size()
        int totalPages = ((totalDocs + batchSize - 1) / batchSize) - 1
        def totalDocumentsUpdated = 0
        def lastTaxon = null
        def lastImage = null
        def buffer = []
        log "${totalDocs} taxa to update in ${totalPages} pages"

        if (totalDocs > 0) {
            (0..totalPages).each { page ->
                def startInd = page * batchSize
                def endInd = (startInd + batchSize - 1) < totalDocs ? (startInd + batchSize - 1) : totalDocs - 1
                log.debug "GUID batch = ${startInd} to ${endInd}"
                String guids = '"' + guidList[startInd..endInd].join('" "') + '"'
                updateProgressBar(totalPages, page)
                def paramsMap = [
                        q: "guid:(" + guids + ")",
                        fq: "idxtype:${IndexDocType.TAXON.name()}",
                        rows: "${batchSize}",
                        wt: "json"
                ]
                MapSolrParams solrParams = new MapSolrParams(paramsMap)
                def searchResults = searchService.getCursorSearchResults(solrParams, !online)
                def resultsDocs = searchResults?.results ?: []
                log.debug( "SOLR query returned ${resultsDocs.size()} docs")
                resultsDocs.each { Map doc ->
                    if (doc.containsKey("id") && doc.containsKey("guid") && doc.containsKey("idxtype")) {
                        //String imageId = getImageFromParamList(preferredImagesList, doc.guid)
                        def listEntry = imagesMap[doc.guid]
                        String imageId = listEntry?.imageId
                        if (!doc.containsKey("image") || (doc.containsKey("image") && doc.image != imageId)) {
                            lastTaxon = doc.guid
                            lastImage = imageId
                            updateImage(doc, imageId, buffer, online)
                            totalDocumentsUpdated ++
                        }
                    } else {
                        log.warn "Updating doc error: missing keys ${doc}"
                    }
                }
            }

            if (buffer.size() > 0) {
                log "Updating ${buffer.size()} docs, last taxon ${lastTaxon} with image ${lastImage}"
                indexService.indexBatch(buffer, online)
                updatedTaxa = searchService.getTaxa(guidList)
            } else {
                log "No documents to update"
            }
        }
        log "Updated ${totalDocumentsUpdated} out of ${totalDocs} with images"

        updatedTaxa

    }

    /**
     * Triggered from admin -> links import page. Runs on separate thread and send async message back to page via log() method
     *
     * @param online Use online rather than offline store
     * @param imagesMap The images to update
     * @param guidList The list to update from the map
     */
    def updateHiddenImages(online, imagesMap) {
        def updatedTaxa = []
        Integer batchSize = 20

        List guidList = imagesMap.values().collect { image -> image.taxonID }
        int totalDocs = guidList.size()
        int totalPages = ((totalDocs + batchSize - 1) / batchSize) - 1
        def totalDocumentsUpdated = 0
        def lastTaxon = null
        def lastImage = null
        def buffer = []
        log "${totalDocs} taxa to update in ${totalPages} pages"

        if (totalDocs > 0) {
            (0..totalPages).each { page ->
                def startInd = page * batchSize
                def endInd = (startInd + batchSize - 1) < totalDocs ? (startInd + batchSize - 1) : totalDocs - 1
                log.debug "GUID batch = ${startInd} to ${endInd}"
                String guids = '"' + guidList[startInd..endInd].join('" "') + '"'
                updateProgressBar(totalPages, page)
                def paramsMap = [
                        q: "guid:(" + guids + ")",
                        fq: "idxtype:${IndexDocType.TAXON.name()}",
                        rows: "${batchSize}",
                        wt: "json"
                ]
                MapSolrParams solrParams = new MapSolrParams(paramsMap)
                def searchResults = searchService.getCursorSearchResults(solrParams, !online)
                def resultsDocs = searchResults?.results ?: []
                log.debug( "SOLR query returned ${resultsDocs.size()} docs")
                resultsDocs.each { Map doc ->
                    if (doc.containsKey("id") && doc.containsKey("guid") && doc.containsKey("idxtype")) {
                        //String imageId = getImageFromParamList(preferredImagesList, doc.guid)
                        def listEntry = imagesMap[doc.guid]
                        String imageId = listEntry?.imageId
                        if (!doc.containsKey("hiddenImages") || (doc.containsKey("hiddenImages") && doc.hiddenImages != imageId)) {
                            lastTaxon = doc.guid
                            lastImage = imageId
                            updateHiddenImage(doc, imageId, buffer, online)
                            totalDocumentsUpdated ++
                        }
                    } else {
                        log.warn "Updating doc error: missing keys ${doc}"
                    }
                }
            }

            if (buffer.size() > 0) {
                log "Updating ${buffer.size()} docs, last taxon ${lastTaxon} with image ${lastImage}"
                indexService.indexBatch(buffer, online)
                updatedTaxa = searchService.getTaxa(guidList)
            } else {
                log "No documents to update"
            }
        }
        log "Updated ${totalDocumentsUpdated} out of ${totalDocs} with images"

        updatedTaxa

    }

    def updateWikiUrls(online, wikiMap) {
        def updatedTaxa = []
        Integer batchSize = 20

        List guidList = wikiMap.values().collect { item -> item.taxonID }
        int totalDocs = guidList.size()
        int totalPages = ((totalDocs + batchSize - 1) / batchSize) - 1
        def totalDocumentsUpdated = 0
        def lastTaxon = null
        def lastUrl = null
        def buffer = []
        log "${totalDocs} taxa to update in ${totalPages} pages"

        if (totalDocs > 0) {
            (0..totalPages).each { page ->
                def startInd = page * batchSize
                def endInd = (startInd + batchSize - 1) < totalDocs ? (startInd + batchSize - 1) : totalDocs - 1
                log.debug "GUID batch = ${startInd} to ${endInd}"
                String guids = '"' + guidList[startInd..endInd].join('" "') + '"'
                updateProgressBar(totalPages, page)
                def paramsMap = [
                        q: "guid:(" + guids + ")",
                        fq: "idxtype:${IndexDocType.TAXON.name()}",
                        rows: "${batchSize}",
                        wt: "json"
                ]
                MapSolrParams solrParams = new MapSolrParams(paramsMap)
                def searchResults = searchService.getCursorSearchResults(solrParams, !online)
                def resultsDocs = searchResults?.results ?: []
                log.debug( "SOLR query returned ${resultsDocs.size()} docs")
                resultsDocs.each { Map doc ->
                    if (doc.containsKey("id") && doc.containsKey("guid") && doc.containsKey("idxtype")) {
                        //String imageId = getImageFromParamList(preferredImagesList, doc.guid)
                        def listEntry = wikiMap[doc.guid]
                        String url = listEntry?.url
                        if (!doc.containsKey("wikiUrl") || (doc.containsKey("wikiUrl") && doc.wikiUrl != url)) {
                            lastTaxon = doc.guid
                            lastUrl = url
                            updateWikiUrl(doc, url, buffer, online)
                            totalDocumentsUpdated ++
                        }
                    } else {
                        log.warn "Updating doc error: missing keys ${doc}"
                    }
                }
            }

            if (buffer.size() > 0) {
                log "Updating ${buffer.size()} docs, last taxon ${lastTaxon} with wikiUrl ${lastUrl}"
                indexService.indexBatch(buffer, online)
                updatedTaxa = searchService.getTaxa(guidList)
            } else {
                log "No documents to update"
            }
        }
        log "Updated ${totalDocumentsUpdated} out of ${totalDocs} with wikiUrls"

        updatedTaxa

    }

    /**
     * Update a taxon with an image, along with any common nmames
     *
     * @param doc The taxon document
     * @param imageId The image identifier
     * @param buffer The update buffer
     * @param online True to use the online index
     */
    private updateImage(Map doc, String imageId, List buffer, boolean online) {
        def update = { d ->
            [
                    id: d.id,
                    idxtype: [set: d.idxtype],
                    guid: [set: d.guid],
                    image: ["set": imageId],
                    imageAvailable: ["set": true]
            ]
        }
        buffer << update(doc)
        def commonNames = searchService.lookupVernacular(doc.guid, !online)
        commonNames.each { common ->
            buffer << update(common)
        }
    }

    def updateHiddenImage(Map doc, String imageId, List buffer, boolean online) {
        def update = { d ->
            [
                    id: d.id,
                    idxtype: [set: d.idxtype],
                    guid: [set: d.guid],
                    hiddenImages_s: ["set": imageId]
            ]
        }
        buffer << update(doc)
        def commonNames = searchService.lookupVernacular(doc.guid, !online)
        commonNames.each { common ->
            buffer << update(common)
        }
    }

    /**
     * Update a taxon with an image, along with any common nmames
     *
     * @param doc The taxon document
     * @param imageId The image identifier
     * @param buffer The update buffer
     * @param online True to use the online index
     */
    private updateWikiUrl(Map doc, String wikiUrl, List buffer, boolean online) {
        def update = { d ->
            [
                    id: d.id,
                    idxtype: [set: d.idxtype],
                    guid: [set: d.guid],
                    wikiUrl_s: ["set": wikiUrl]
            ]
        }
        buffer << update(doc)
        def commonNames = searchService.lookupVernacular(doc.guid, !online)
        commonNames.each { common ->
            buffer << update(common)
        }
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
        int pageSize = BATCH_SIZE
        int bufferLimit = BUFFER_SIZE
        int pages = 0
        int processed = 0
        def js = new JsonSlurper()
        def prevCursor = ""
        def cursor = CursorMarkParams.CURSOR_MARK_START
        def startTime, endTime
        def capitalisers = [:]

        log("Starting dernomalisation")
        js.setType(JsonParserType.INDEX_OVERLAY)
        log("Getting species groups")
        def speciesGroupMapper = speciesGroupService.invertedSpeciesGroups
        log("Starting denormalisation scan for ${online ? 'online' : 'offline'} index")
        log("Clearing existing denormalisations")
        try {
            startTime = System.currentTimeMillis()
            def response = indexService.query(online, "denormalised:true", [], 1)
            int total = response.results.numFound
            while (total > 0 && prevCursor != cursor) {
                response = indexService.query(online, "denormalised:true", [], pageSize, null, null, "id", "asc", cursor)
                def buffer = []

                response.results.each { Map doc ->
                    def update = [:]
                    update["id"] = doc.id // doc key
                    update["idxtype"] = [set: doc.idxtype] // required field
                    update["guid"] = [set: doc.guid] // required field
                    update["denormalised"] = [set: false ]
                    doc.each { entry ->
                        def key = entry.key
                        if (key.startsWith("rk_") || key.startsWith("rkid_") || key.startsWith("commonName"))
                            update[key] = [set: null]
                    }
                    update["nameVariant"] = [set: null]
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
                    def percentage = Math.round((processed / total) * 100 )
                    log("Cleared ${processed} taxa (${percentage}%)")
                }
                prevCursor = cursor
                cursor = response.nextCursorMark
            }
        } catch (Exception ex) {
            log("Exception clearing denormalisation entries: ${ex.getMessage()}")
            log.error("Unable to clear denormalisations", ex)
        }
        log("Denormalising top-level taxa")
        try {
            pages = 0
            processed = 0
            prevCursor = ""
            cursor = CursorMarkParams.CURSOR_MARK_START
            def typeQuery = "idxtype:\"${ IndexDocType.TAXON.name() }\" AND -acceptedConceptID:* AND -parentGuid:*"
            def response = indexService.query(online, typeQuery, [], 1)
            int total = response.results.numFound
            while (prevCursor != cursor) {
                //startTime = System.currentTimeMillis()
                response = indexService.query(online, typeQuery, [], pageSize, null, null, 'id', 'asc', cursor)
                pages++
                def buffer = []
                log "1. Paging over ${total} docs - page ${pages}"

                response.results.each { doc ->
                    denormaliseEntry(doc, [:], [], [], [], buffer, bufferLimit, pageSize, online, js, speciesGroupMapper, commonNameLanguages, capitalisers)
                    processed++
                }
                if (!buffer.isEmpty())
                    indexService.indexBatch(buffer, online)
                if (total > 0) {
                    def percentage = Math.round(processed * 100 / total)
                    log("Denormalised ${processed} top-level taxa (${percentage}%)")
                }
                prevCursor = cursor
                cursor = response.nextCursorMark
            }
        } catch (Exception ex) {
            log("Exception denormalising top-level: ${ex.getMessage()}")
            log.error("Unable to denormalise", ex)
        }
        log("Denormalising dangling taxa")
        try {
            pages = 0
            processed = 0
            prevCursor = ""
            cursor = CursorMarkParams.CURSOR_MARK_START
            def danglingQuery = "idxtype:\"${IndexDocType.TAXON.name()}\" AND -acceptedConceptID:* AND -denormalised:true"
            def response = indexService.query(online, danglingQuery, [], 1)
            int total = response.results.numFound
            while (prevCursor != cursor) {
                //startTime = System.currentTimeMillis()
                response = indexService.query(online, danglingQuery, [], pageSize, null, null, 'id', 'asc', cursor)
                def buffer = []
                pages++
                log "2. Paging over ${total} docs - page ${pages}"

                response.results.each { doc ->
                    denormaliseEntry(doc, [:], [], [], [], buffer, bufferLimit, pageSize, online, js, speciesGroupMapper, commonNameLanguages, capitalisers)
                    processed++
                }
                if (!buffer.isEmpty())
                    indexService.indexBatch(buffer, online)
                if (total > 0) {
                    def percentage = Math.round(processed * 100 / total)
                    log("Denormalised ${processed} dangling taxa (${percentage}%)")
                }
                prevCursor = cursor
                cursor = response.nextCursorMark
            }
        } catch (Exception ex) {
            log("Exception denormalising dangling taxa: ${ex.getMessage()}")
            log.error("Unable to denormalise", ex)
        }
        log("Denormalising synonyms")
        try {
            pages = 0
            processed = 0
            prevCursor = ""
            cursor = CursorMarkParams.CURSOR_MARK_START
            def synonymQuery = "idxtype:\"${IndexDocType.TAXON.name()}\" AND acceptedConceptID:*"
            def response = indexService.query(online, synonymQuery, [], 1)
            int total = response.results.numFound
            while (prevCursor != cursor) {
                //startTime = System.currentTimeMillis()
                response = indexService.query(online, synonymQuery, [], pageSize, null, null, 'id', 'asc', cursor)
                def buffer = []
                pages++
                log "3. Paging over ${total} docs - page ${pages}"

                response.results.each { doc ->
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
                    if (total > 0 && processed % BUFFER_SIZE == 0) {
                        def percentage = Math.round(processed * 100 / total)
                        log("Denormalised ${processed} synonyms (${percentage}%)")
                    }
                }
                if (!buffer.isEmpty())
                    indexService.indexBatch(buffer, online)
                prevCursor = cursor
                cursor = response.nextCursorMark
            }
        } catch (Exception ex) {
            log("Exception denormalising synonyms: ${ex.getMessage()}")
            log.error("Unable to denormalise", ex)
        }
        log("Denormalising common names")
        try {
            pages = 0
            processed = 0
            prevCursor = ""
            cursor = CursorMarkParams.CURSOR_MARK_START
            def commonQuery = "idxtype:\"${IndexDocType.COMMON.name()}\""
            def response = indexService.query(online, commonQuery, [], 1)
            int total = response.results.numFound
            while (prevCursor != cursor) {
                //startTime = System.currentTimeMillis()
                response = indexService.query(online, commonQuery, [], pageSize, null, null, 'id', 'asc', cursor)
                def buffer = []
                pages++
                log "4. Paging over ${total} docs - page ${pages}"

                response.results.each { doc ->
                    def accepted = searchService.lookupTaxon(doc.taxonGuid, !online)
                    if (accepted) {
                        def update = [:]
                        update["id"] = doc.id // doc key
                        update["idxtype"] = [set: doc.idxtype] // required field
                        update["guid"] = [set: doc.guid ] // required field
                        update["acceptedConceptName"] = [set: accepted.scientificName ]

                        buffer << update
                    }
                    processed++
                    if (buffer.size() >= bufferLimit) {
                        indexService.indexBatch(buffer, online)
                        buffer.clear()
                    }
                    if (total > 0 && processed % BUFFER_SIZE == 0) {
                        def percentage = Math.round(processed * 100 / total)
                        log("Denormalised ${processed} common names (${percentage}%)")
                    }
                }
                if (!buffer.isEmpty())
                    indexService.indexBatch(buffer, online)
                prevCursor = cursor
                cursor = response.nextCursorMark
            }
        } catch (Exception ex) {
            log("Exception denormalising synonyms: ${ex.getMessage()}")
            log.error("Unable to denormalise", ex)
        }
        endTime = System.currentTimeMillis()
        log("Finished taxon denormalisaion. Duration: ${(new SimpleDateFormat("mm:ss:SSS")).format(new Date(endTime - startTime))}")
    }

    private denormaliseEntry(doc, Map trace, List stack, List speciesGroups, List speciesSubGroups, List buffer, int bufferLimit, int pageSize, boolean online, JsonSlurper js, Map speciesGroupMapping, Set commonLanguages, Map capitalisers) {
        def currentDistribution = (doc['distribution'] ?: []) as Set
        if (doc.denormalised)
            return currentDistribution
        def update = [:]
        def distribution = [] as Set
        def guid = doc.guid
        def scientificName = doc.scientificName
        def nameComplete = doc.nameComplete
        if (stack.contains(guid)) {
            log "Loop in parent-child relationship for ${guid} - ${stack}"
            return currentDistribution
        }
        stack << guid
        update["id"] = doc.id // doc key
        update["idxtype"] = [set: doc.idxtype] // required field
        update["guid"] = [set: guid] // required field
        update["denormalised"] = [set: true ]
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
                log("Adding group ${speciesGroup.group} and subgroup ${speciesGroup.subGroup} to ${scientificName}")
                speciesGroups = speciesGroups.clone()
                speciesGroups << speciesGroup.group
                speciesSubGroups = speciesSubGroups.clone()
                speciesSubGroups << speciesGroup.subGroup
            }
            update["speciesGroup"] = [set: speciesGroups]
            update["speciesSubgroup"] = [set: speciesSubGroups]
        }
        double priority = weightNorm;
        def variants = searchService.lookupVariant(guid, !online)
        if (variants) {
            priority = variants.collect({ (double) it.priority ?: weightNorm }).max()
            def names = (variants.collect { it.scientificName }) as Set
            names.addAll(variants.collect { it.nameComplete })
            names.remove(null)
            names.remove(scientificName)
            names.remove(nameComplete)
            if (names)
                update["nameVariant"] = [set: names]
            update["scientificName"] = [set: scientificName]
            if (nameComplete)
                update["nameComplete"] = [set: nameComplete]
        }
        update['priority'] = [set: (int) Math.round(priority)]
        def commonNames = searchService.lookupVernacular(guid, !online)
        if (commonNames && !commonNames.isEmpty()) {
            commonNames = commonNames.sort { n1, n2 ->
                def s = n2.priority - n1.priority
                if (s == 0 && commonLanguages) {
                    def s1 = commonLanguages.contains(n1.language) ? 1 : 0
                    def s2 = commonLanguages.contains(n2.language) ? 1 : 0
                    s = s1 - s2
                }
                s
            }
            def single = commonNames.find({ it.status != deprecatedStatus.status && (!commonLanguages || commonLanguages.contains(it.language))})?.name
            def names = new LinkedHashSet(commonNames.collect { it.name })
            update["commonName"] = [set: names]
            update["commonNameExact"] = [set: names]
            update["commonNameSingle"] = [set: single ]
        }
        def identifiers = searchService.lookupIdentifier(guid, !online)
        if (identifiers) {
            update["additionalIdentifiers"] = [set: identifiers.collect { it.guid }]
        }
        def names = searchService.lookupNames(guid, !online)
        if (names) {
            def filteredNames = names.collect { it.scientificName }.unique()
            filteredNames.remove(scientificName)
            if (filteredNames) {
                update["additionalNames_m_s"] = [set: filteredNames]
            }
        }
        def prevCursor = ""
        def cursor = CursorMarkParams.CURSOR_MARK_START
        while (cursor != prevCursor) {
            def response = indexService.query(online, "parentGuid:\"${doc.guid}\"", [ "idxtype:\"${IndexDocType.TAXON.name()}\"", ACCEPTED_STATUS ], pageSize, null, null, "id", "asc", cursor)
            response.results.each { child ->
                distribution.addAll(denormaliseEntry(child, trace, stack, speciesGroups, speciesSubGroups, buffer, bufferLimit, pageSize, online, js, speciesGroupMapping, commonLanguages, capitalisers))
            }
            prevCursor = cursor
            cursor = response.nextCursorMark
        }
        def additionalDistribtion = distribution.findAll { !currentDistribution.contains(it) }
        if (!additionalDistribtion.isEmpty())
            update['distribution'] = [add: additionalDistribtion]
        buffer << update

        if (buffer.size() >= bufferLimit) {
            indexService.indexBatch(buffer, online)
            buffer.clear()
        }
        stack.pop()
        distribution.addAll(currentDistribution)
        return distribution
    }

    def buildFavourites(boolean online) throws Exception {
        log("Clearing favourites")
        this.clearField("favourite", null, online)
        log("Finished clearing favourites")

        log("Loading favourites")
        favouritesConfiguration.lists.each { resource ->
            String uid = resource.uid
            String termField = resource.termField
            String defaultTerm = resource.defaultTerm ?: favouritesConfiguration.defaultTerm
            if (uid && defaultTerm) {
                log("Loading list from: " + uid)
                try {
                    def list = listService.get(uid, termField ? [ termField ] : [])
                    buildFavouritesList(list, termField, defaultTerm, online)
                } catch (Exception ex) {
                    def msg = "Error calling webservice: ${ex.message}"
                    log(msg)
                    log.warn(msg, ex) // send to user via http socket
                }
            }
        }
        log("Finished loading favourites")

    }

    private buildFavouritesList(List list, String termField, defaultTerm, online) throws Exception {
        int bufferLimit = BUFFER_SIZE
        int processed = 0
        def buffer = []
        def update

        list.each { entry ->
            def doc = searchService.lookupTaxon(entry.lsid, !online)
            def term = (termField ? entry[termField] : defaultTerm) ?: defaultTerm
            if (doc && term) {
                update = [id: doc.id, idxtype: doc.idxtype, guid: doc.guid ]
                update['favourite'] = ['set': term ]
                buffer << update
                processed++
                searchService.lookupVernacular(entry.lsid, !online).each { vdoc ->
                    update = [id: vdoc.id, idxtype: vdoc.idxtype, guid: vdoc.guid ]
                    update['favourite'] = ['set': term ]
                    buffer << update
                    processed++
                }
            }
            if (buffer.size() > bufferLimit) {
                log "Updated ${processed} records"
                indexService.indexBatch(buffer, online)
                buffer = []
            }
        }
        if (!buffer.isEmpty()) {
            log "Updated ${processed} records"
            indexService.indexBatch(buffer, online)
        }
    }


    /**
     * Build (or rebuild) the weights assigned to various entities.
     *
     * @param online Use the online index
     */
    def buildWeights(boolean online) {
        int pageSize = BATCH_SIZE
        int bufferLimit = BUFFER_SIZE
        int pages = 0
        int processed = 0
        int lastReported = 0
        def js = new JsonSlurper()
        def prevCursor = ""
        def cursor = CursorMarkParams.CURSOR_MARK_START
        def startTime, endTime

        log("Building search weights")
        try {
            startTime = System.currentTimeMillis()
            def response = indexService.query(online, "idxtype:*", [], 1)
            int total = response.results.numFound
            while (total > 0 && prevCursor != cursor) {
                response = indexService.query(online, "idxtype:*", [], pageSize, null, null, "id", "asc", cursor)
                def buffer = []

                response.results.each { doc ->
                    def update = [:]
                    def weight = 1.0
                    update["id"] = doc.id // doc key
                    update["idxtype"] = [set: doc.idxtype] // required field
                    update["guid"] = [set: doc.guid] // required field
                    switch (IndexDocType.valueOf(doc.idxtype)) {
                        case IndexDocType.TAXON:
                            if (doc.priority)
                                weight = Math.max(weightMin, Math.min(weightMax, ((double) doc.priority) / weightNorm))
                            break;
                        case IndexDocType.COMMON:
                            if (doc.priority)
                                weight = ((double) doc.priority) / commonStatus.priority;
                            break;
                        default:
                            weight = 1.0;
                    }
                    def weights = weightBuilder.apply(weight, doc)
                    weights.each { k, v -> update[k] = [set: v]}
                    processed++
                    buffer << update
                }
                if (!buffer.isEmpty())
                    indexService.indexBatch(buffer, online)
                if (total > 0 && (processed - lastReported) >= REPORT_INTERVAL) {
                    lastReported = processed
                    def percentage = Math.round((processed / total) * 100 )
                    log("Weighted ${processed} items (${percentage}%)")
                }
                prevCursor = cursor
                cursor = response.nextCursorMark
            }
        } catch (Exception ex) {
            log("Exception weighting entries: ${ex.getMessage()}")
            log.error("Unable to weight entries", ex)
        }
        log("Finished building search weights, ${processed} items")

    }

    /**
     * Build (or rebuild) the suggestion index
     *
     * @param online Use the online index
     */
    def buildSuggestIndex(boolean online) {
         log("Building suggestion index")
        try {
            def response = indexService.buildSuggestIndex(online)
            log.info(response.toString())
        } catch (Exception ex) {
            log("Exception building suggestion index: ${ex.getMessage()}")
            log.error("Unable to build suggestion index", ex)
        }
        log("Finished building suggestion index")

    }

    /**
     * Get the taxon rank structure
     *
     * @return
     */
    def ranks() {
        String path = grailsApplication.config.taxonRanksFile
        File taxonRanksFile = new File(path)
        if (path && taxonRanksFile.exists()) {
            JsonSlurper slurper = new JsonSlurper()
            def ranks = slurper.parse(taxonRanksFile.text)
            def idMap = [:]
            def iter = ranks.iterator()
            while (iter.hasNext()) {
                def entry = iter.next()
                idMap.put(entry.rank, entry)
            }
            idMap
        } else {
            def idMap = [:]
            RankType.values().each { rankType ->
                idMap.put(rankType.field, [
                        branch: null,
                        notes: null,
                        otherNames: [],
                        rank: rankType.field,
                        rankGroup: rankType.cbRank?.name()?.toLowerCase(),
                        rankID: rankType.sortOrder
                ])
            }
            idMap
        }
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
            if (authorIndex <= 0)
                return "<span class=\"${formattedCssClass}\">${StringEscapeUtils.escapeHtml(nameComplete)}</span>"
            def preAuthor = nameComplete.substring(0, authorIndex).trim()
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
    private String getStringForUrl(URL url) throws IOException {
        String output = ""
        def inStm = url.openStream()
        try {
            output = IOUtils.toString(inStm)
        } finally {
            IOUtils.closeQuietly(inStm)
        }
        output
    }

    /**
     * Helper method to do a HTTP GET and return String content
     *
     * @param url
     * @return
     */
    private String getStringForUrl(String url) throws IOException {
        return getStringForUrl(new URL(url))
    }

    def log(msg) {
        log.info(msg)
        brokerMessagingTemplate.convertAndSend "/topic/import-feedback", msg.toString()
    }

    private updateProgressBar(int total, int current) {
        Double percentDone = total > 0 ? current * 100.0 / total : 100.0
        brokerMessagingTemplate.convertAndSend "/topic/import-progress", percentDone.round(1).toString()
    }

    private updateProgressBar2(int total, int current) {
        Double percentDone = total > 0 ? current * 100.0 / total : 100.0
        brokerMessagingTemplate.convertAndSend "/topic/import-progress2", percentDone.round(1).toString()
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

    private getConfigFile(String url) {
        //url = URLEncoder.encode(url, "UTF-8")
        URL source = this.class.getResource(url)
        if (source == null)
            source = new URL(url)
        JsonSlurper slurper = new JsonSlurper()
        return slurper.parse(source)
     }

    /**
     * Clear a field in the index.
     *
     * @param field The field name
     * @param value The value to set it to (usually null)
     * @param online True if the online index is to be used
     */
    private clearField(String field, Object value, boolean online) {
        int pageSize = BATCH_SIZE
        int processed = 0
        int lastReported = 0
        def prevCursor = ""
        def cursor = CursorMarkParams.CURSOR_MARK_START

        try {
            def response = indexService.query(online, "${field}:*", [], 1)
            int total = response.results.numFound
            while (total > 0 && prevCursor != cursor) {
                response = indexService.query(online, "${field}:*", [], pageSize, null, null, "id", "asc", cursor)
                def buffer = []

                response.results.each { doc ->
                    def update = [:]
                    update["id"] = doc.id // doc key
                    update["idxtype"] = [set: doc.idxtype] // required field
                    update["guid"] = [set: doc.guid] // required field
                    update[field] = [set: value]
                    processed++
                    buffer << update
                }
                if (!buffer.isEmpty())
                    indexService.indexBatch(buffer, online)
                if (total > 0 && (processed - lastReported) >= REPORT_INTERVAL) {
                    lastReported = processed
                    def percentage = Math.round((processed / total) * 100 )
                    log("Cleared ${processed} items (${percentage}%)")
                }
                prevCursor = cursor
                cursor = response.nextCursorMark
            }
            log("Cleared ${processed} items")
        } catch (Exception ex) {
            log("Exception setting ${field} to ${value}: ${ex.getMessage()}")
            log.error("Unable to setting ${field} to ${value}", ex)
        }
    }
}
