package au.org.ala.bie

import au.com.bytecode.opencsv.CSVReader
import au.org.ala.bie.search.IndexDocType
import groovy.json.JsonSlurper
import org.gbif.dwc.terms.DcTerm
import org.gbif.dwc.terms.DwcTerm
import org.gbif.dwc.terms.GbifTerm
import org.gbif.dwca.io.Archive
import org.gbif.dwca.io.ArchiveFactory
import org.gbif.dwca.io.ArchiveFile
import org.gbif.dwca.record.Record

/**
 * Services for data importing.
 */
class ImportService {

    def serviceMethod() {}

    def indexService

    def grailsApplication

    def static DYNAMIC_FIELD_EXTENSION = "_s"

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

    /**
     * Return a denormalised map lookup.
     *
     * @return
     */
    private def denormalise(ArchiveFile taxaFile) {

        //read inventory, creating entries in index....
        def childParentMap = [:]
        def parentLess = []
        def parents = [] as Set

        Iterator<Record> iter = taxaFile.iterator()

        while (iter.hasNext()) {

            Record record = iter.next()

            def taxonID = record.id()
            def parentNameUsageID = record.value(DwcTerm.parentNameUsageID)
            def acceptedNameUsageID = record.value(DwcTerm.acceptedNameUsageID)
            def scientificName = record.value(DwcTerm.scientificName)
            def taxonRank = record.value(DwcTerm.taxonRank)?:"".toLowerCase()

            parents << parentNameUsageID

            //if an accepted usage, add to map
            if (acceptedNameUsageID == null || acceptedNameUsageID == "" || taxonID == acceptedNameUsageID) {
                if (parentNameUsageID) {
                    childParentMap.put(taxonID, [cn: scientificName, cr: taxonRank, p: parentNameUsageID])
                } else {
                    parentLess << taxonID
                }
            }
        }

        log.info("Parent-less: ${parentLess.size()}, Parent-child: ${childParentMap.size()}")

        def taxonDenormLookup = [:]

        log.info("Starting denorm lookups")
        childParentMap.keySet().each {
            //don't bother de-normalising terminal taxa
            if (parents.contains(it)) {
                def list = []
                denormaliseTaxon(it, list, childParentMap)
                taxonDenormLookup.put(it, list)
            }
        }
        log.info("Finished denorm lookups")
        taxonDenormLookup
    }

    /**
     * Recursive function.
     *
     * @param id
     * @param currentList
     * @param childParentMap
     * @return
     */
    private List denormaliseTaxon(id, currentList, childParentMap, stackLevel = 0) {
        if (stackLevel > 20) {
            log.warn("Infinite loop detected for ${id} " + currentList)
            return currentList
        }
        def info = childParentMap.get(id)
        if (info && info['p'] && !currentList.contains(id + '|' + info['cn'] + '|' + info['cr'])) {
            currentList << id + '|' + info['cn'] + '|' + info['cr']
            //cn:scientificName, cr:taxonRank, p:parentNameUsageID
            denormaliseTaxon(info['p'], currentList, childParentMap, stackLevel + 1)
        }
        currentList
    }

    /**
     * Import layer information into the index.
     *
     * @return
     */
    def importLayers(){
        def js = new JsonSlurper()
        def layers = js.parseText(new URL(grailsApplication.config.layersServicesUrl + "/layers").getText("UTF-8"))
        def batch = []
        layers.each { layer ->
            def doc = [:]
            doc["id"] = layer.name
            doc["guid"] = layer.name
            doc["idxtype"] = IndexDocType.LAYER.name()
            doc["name"] = layer.displayname
            doc["description"] = layer.description
            batch << doc
        }
        indexService.indexBatch(batch)
        log.info("Finished indexing ${layers.size()} layers")
    }

    /**
     * Import collectory information into the index.
     *
     * @return
     */
    def importCollectory(){
       [
                "dataResource" : IndexDocType.DATARESOURCE,
                "dataProvider" : IndexDocType.DATAPROVIDER,
                "institution" : IndexDocType.INSTITUTION,
                "collection" : IndexDocType.COLLECTION
        ].each { entityType, indexDocType ->
           def js = new JsonSlurper()
           def entities = []
           def drLists = js.parseText(new URL(grailsApplication.config.collectoryUrl + "/${entityType}").getText("UTF-8"))
           log.info("About to import ${drLists.size()} ${entityType}")
           log.info("Clearing existing: ${entityType}")
           indexService.deleteFromIndex(indexDocType)

           drLists.each {
               def details = js.parseText(new URL(it.uri).getText("UTF-8"))
               def doc = [:]
               doc["id"] = it.uri
               doc["guid"] = details.alaPublicUrl
               doc["idxtype"] = indexDocType.name()
               doc["name"] = details.name
               doc["description"] = details.description

               entities << doc

               if(entities.size() > 10){
                   indexService.indexBatch(entities)
                   entities.clear()
               }
           }
           log.info("Cleared")
           indexService.indexBatch(entities)
           log.info("Finished indexing ${drLists.size()} ${entityType}")
       }
    }

    /**
     * Import a DwC-A into this system.
     *
     * @return
     */
    def importDwcA(dwcDir, clearIndex){

        //read the DwC metadata
        Archive archive = ArchiveFactory.openArchive(new File(dwcDir));
        ArchiveFile taxaArchiveFile = archive.getCore()

        //vernacular names extension available?
        ArchiveFile vernacularArchiveFile = archive.getExtension(GbifTerm.VernacularName)

        //dataset extension available?
        ArchiveFile datasetArchiveFile = archive.getExtension(DcTerm.rightsHolder)

        //retrieve taxon rank mappings
        def taxonRanks = readTaxonRankIDs()

        //retrieve images
        def imageMap = indexImages()

        //retrieve common names
        def commonNamesMap = readCommonNames(vernacularArchiveFile)
        log.info("Common names read: " + commonNamesMap.size())

        //retrieve datasets
        def attributionMap = readAttribution(datasetArchiveFile)
        log.info("Datasets read: " + attributionMap.size())

        //compile a list of synonyms into memory....
        def synonymMap = readSynonyms(taxaArchiveFile)
        log.info("Synonyms read: " + synonymMap.size())

        //clear
        if (clearIndex) {
            log.info("Deleting existing entries in index...")
            indexService.deleteFromIndex(IndexDocType.TAXON)
        } else {
            log.info("Skipping deleting existing entries in index...")
        }

        //retrieve the denormed taxon lookup
        def denormalised = denormalise(taxaArchiveFile)
        log.info("De-normalised map..." + denormalised.size())

        log.info("Creating entries in index...")

        //read inventory, creating entries in index....
        def alreadyIndexed = [DwcTerm.taxonID,
                              DwcTerm.datasetID,
                              DwcTerm.acceptedNameUsageID,
                              DwcTerm.parentNameUsageID,
                              DwcTerm.scientificName,
                              DwcTerm.taxonRank,
                              DwcTerm.scientificNameAuthorship
        ]

        def buffer = []
        def counter = 0

        Iterator<Record> iter = taxaArchiveFile.iterator()

        while (iter.hasNext()) {

            Record record = iter.next()

            counter++
            def taxonID = record.id()
            def acceptedNameUsageID = record.value(DwcTerm.acceptedNameUsageID)

            if (taxonID == acceptedNameUsageID || acceptedNameUsageID == "" || acceptedNameUsageID == null) {

                def taxonRank = (record.value(DwcTerm.taxonRank)?:"").toLowerCase()
                def scientificName = record.value(DwcTerm.scientificName)
                def parentNameUsageID = record.value(DwcTerm.parentNameUsageID)
                def scientificNameAuthorship = record.value(DwcTerm.scientificNameAuthorship)
                def taxonRankID = taxonRanks.get(taxonRank) ? taxonRanks.get(taxonRank) as Integer : -1

                //common name
                def doc = ["idxtype" : IndexDocType.TAXON.name()]
                doc["id"] = UUID.randomUUID().toString()
                doc["guid"] = taxonID
                doc["parentGuid"] = parentNameUsageID
                doc["rank"] = taxonRank
                doc["rankID"] = taxonRankID
                doc["scientificName"] = scientificName
                doc["scientificNameAuthorship"] = scientificNameAuthorship
                if (scientificNameAuthorship) {
                    doc["nameComplete"] = scientificName + " " + scientificNameAuthorship
                } else {
                    doc["nameComplete"] = scientificName
                }

                def inSchema = [DwcTerm.establishmentMeans, DwcTerm.taxonomicStatus, DwcTerm.taxonConceptID]

                record.terms().each { term ->
                    if (!alreadyIndexed.contains(term)) {
                        if (inSchema.contains(term)) {
                            doc[term.simpleName()] = record.value(term)
                        } else {
                            //use a dynamic field extension
                            doc[term.simpleName() + DYNAMIC_FIELD_EXTENSION] = record.value(term)
                        }
                    }
                }

                def attribution = attributionMap.get(record.value(DwcTerm.datasetID))
                if (attribution) {
                    doc["datasetName"] = attribution["datasetName"]
                    doc["rightsHolder"] = attribution["rightsHolder"]
                }

                //retrieve images via scientific name - FIXME should be looking up with taxonID
                def image = imageMap.get(scientificName)
                if (image) {
                    doc["image"] = image
                    doc["imageAvailable"] = "yes"
                } else {
                    doc["imageAvailable"] = "no"
                }

                //common names
                def commonNames = commonNamesMap.get(taxonID)
                if (commonNames) {
                    doc["commonName"] = commonNames
                    doc["commonNameExact"] = commonNames
                }

                //denormed taxonomy
                if (parentNameUsageID) {
                    def taxa = denormalised.get(parentNameUsageID)
                    def processedRanks = []
                    taxa.each { taxon ->

                        //check we have only one value for each rank...
                        def parts = taxon.split('\\|')

                        if (parts.length == 3) {
                            String tID = parts[0]
                            String name = parts[1]
                            String rank = parts[2]
                            String normalisedRank = rank.replaceAll(" ", "_").toLowerCase()
                            if (processedRanks.contains(normalisedRank)) {
                                log.info("Duplicated rank: " + normalisedRank + " - " + taxa)
                            } else {
                                processedRanks << normalisedRank
                                doc["rk_" + normalisedRank] = name
                                doc["rkid_" + normalisedRank] = tID
                            }
                        }
                    }
                }

                //synonyms - add a separate doc for each
                def synonyms = synonymMap.get(taxonID)
                if (synonyms) {
                    synonyms.each { synonym ->

                        //dont add the synonym if it is lexographically the same
                        if(!synonym['scientificName'].equalsIgnoreCase(scientificName)) {

                            def sdoc = ["idxtype": "TAXON"]
                            sdoc["id"] = UUID.randomUUID().toString()
                            sdoc["guid"] = synonym["taxonID"]
                            sdoc["rank"] = taxonRank
                            sdoc["rankID"] = taxonRankID
                            sdoc["scientificName"] = synonym['scientificName']
                            sdoc["scientificNameAuthorship"] = synonym['scientificNameAuthorship']
                            sdoc["nameComplete"] = synonym['scientificName'] + " " +  synonym['scientificNameAuthorship']
                            sdoc["acceptedConceptName"] = scientificName + ' ' + scientificNameAuthorship
                            sdoc["acceptedConceptID"] = taxonID
                            sdoc["taxonomicStatus"] = "synonym"

                            def synAttribution = attributionMap.get(synonym['dataset'])
                            if (synAttribution) {
                                sdoc["datasetName"] = synAttribution["datasetName"]
                                sdoc["rightsHolder"] = synAttribution["rightsHolder"]
                            }

                            counter++
                            buffer << sdoc
                        } else {
                            log.debug("Skipping lexographically the same synonym for " + scientificName)
                        }
                    }
                }

                buffer << doc
            }

            if (counter > 0 && counter % 1000 == 0) {
                if (!buffer.isEmpty()) {
                    log.info("Adding docs: ${counter}")
                    indexService.indexBatch(buffer)
                    buffer.clear()
                }
            }
        }

        //commit remainder
        if (!buffer.isEmpty()) {
            indexService.indexBatch(buffer)
            buffer.clear()
        }
        log.info "Import finished"
    }

    /**
     * Read synonyms into taxonID -> [synonym1, synonym2]
     *
     * @param fileName
     * @return
     */
    private def readSynonyms(ArchiveFile taxaFile) {

        def synonyms = [:]
        def iter = taxaFile.iterator()

        while (iter.hasNext()) {

            def record = iter.next()

            def taxonID = record.id()
            def acceptedNameUsageID = record.value(DwcTerm.acceptedNameUsageID)
            def scientificName = record.value(DwcTerm.scientificName)
            def scientificNameAuthorship = record.value(DwcTerm.scientificNameAuthorship)
            def datasetID = record.value(DwcTerm.datasetID)

            if (acceptedNameUsageID != taxonID && acceptedNameUsageID != "" && acceptedNameUsageID != null) {
                //we have a synonym
                def synonymList = synonyms.get(acceptedNameUsageID)
                if (!synonymList) {
                    synonymList = []
                    synonyms.put(acceptedNameUsageID, synonymList)
                }

                //lets ignore lexicographically the same names....
                synonymList << [
                        taxonID: taxonID,
                        scientificName : scientificName,
                        scientificNameAuthorship : scientificNameAuthorship,
                        datasetID: datasetID
                ]
            }
        }
        synonyms
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
     * Read the common file, building a map of taxonID -> [commonName1, commonName2]
     *
     * @param fileName
     * @return
     */
    private def readCommonNames(ArchiveFile vernacularArchiveFile) {

        def commonNames = [:]

        if(!vernacularArchiveFile){
            return commonNames
        }

        Iterator<Record> iter = vernacularArchiveFile.iterator()
        while (iter.hasNext()) {
            Record record = iter.next()
            def taxonID = record.id()
            def vernacularName = record.value(DwcTerm.vernacularName)
            def nameList = commonNames.get(taxonID)
            if (!nameList) {
                nameList = []
                commonNames.put(taxonID, nameList)
            }
            nameList << vernacularName
        }
        commonNames
    }

    /**
     * Read taxon rank IDs
     *
     * @return
     */
    private def readTaxonRankIDs() {
        Properties props = new Properties()
        InputStream is = this.class.getResourceAsStream("/taxonRanks.properties")
        props.load(is as InputStream)
        def idMap = [:]
        def iter = props.entrySet().iterator()
        while (iter.hasNext()) {
            def entry = iter.next()
            idMap.put(entry.getKey().toLowerCase().trim(), entry.getValue())
        }
        idMap
    }

    private def indexLists(){

        // http://lists.ala.org.au/ws/speciesList?isAuthoritative=eq:true&max=100
        //for each list
            // download http://lists.ala.org.au/speciesListItem/downloadList/{0}
            // read, and add to map
    }

    /**
     * Retrieve map of scientificName -> image details
     *
     * @return
     */
    private def indexImages() {

        if (!grailsApplication.config.indexImages.toBoolean()) {
            return [:]
        }

        def imageMap = [:]
        log.info("Loading images for the each of the ranks")
        //load images against scientific name
        ["taxon_name", "genus", "family", "order", "class", "phylum"].each {

            log.info("Loading images for the each of the ${it} ... total thus far ${imageMap.size()}")

            def imagesUrl = grailsApplication.config.biocache.solr.url + "/select?" +
                    "q=*%3A*" +
                    "&fq=multimedia%3AImage" +
                    "&fl=${it}%2C+image_url%2C+data_resource_uid" +
                    "&wt=csv" +
                    "&indent=true" +
                    "&rows=100000"

            //load into map, keyed (for now) on scientific name. The images *should* be keyed on GUID
            new URL(imagesUrl).readLines().each {
                def parts = it.split(",")
                if (parts.length == 3) {
                    //the regular expression removes the subgenus
                    imageMap.put(parts[0].replaceFirst(/\([A-Z]{1}[a-z]{1,}\) /, ""), parts[1])
                }
            }
        }

        log.info("Images loaded: " + imageMap.size())
        imageMap
    }
}