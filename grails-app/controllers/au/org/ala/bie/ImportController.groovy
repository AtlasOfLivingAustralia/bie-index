package au.org.ala.bie

import au.com.bytecode.opencsv.CSVReader
import grails.converters.JSON
import org.apache.commons.lang.BooleanUtils
import org.apache.solr.client.solrj.impl.ConcurrentUpdateSolrServer
import org.apache.solr.common.SolrInputDocument
import org.gbif.dwc.terms.DwcTerm
import org.gbif.dwc.terms.GbifTerm
import org.gbif.dwca.record.Record
import org.gbif.dwca.io.Archive
import org.gbif.dwca.io.ArchiveFactory
import org.gbif.dwca.io.ArchiveFile

/**
 * Controller for data import into the system.
 */
class ImportController {

    def grailsApplication

    def static DYNAMIC_FIELD_EXTENSION = "_s"

    def synonymCheckingEnabled = false

    def index() {
        def filePaths = []
        def importDir = new File("/data/bie/import")
        if(importDir.exists()){
            File[] expandedDwc = new File("/data/bie/import").listFiles()
            expandedDwc.each {
                if(it.isDirectory()){
                    filePaths << it.getAbsolutePath()
                }
            }
        }
        [filePaths: filePaths]
    }

    /**
     * Return a denormalised map lookup.
     *
     * @return
     */
    private def denormalise(ArchiveFile taxaFile){

        //read inventory, creating entries in index....
        def childParentMap = [:]
        def parentLess = []
        def parents = [] as Set

        Iterator<Record> iter = taxaFile.iterator()

        while(iter.hasNext()){

            Record record = iter.next()

            def taxonID = record.id()
            def parentNameUsageID = record.value(DwcTerm.parentNameUsageID)
            def acceptedNameUsageID = record.value(DwcTerm.acceptedNameUsageID)
            def scientificName = record.value(DwcTerm.scientificName)
            def taxonRank = record.value(DwcTerm.taxonRank)

            parents << parentNameUsageID

            //if an accepted usage, add to map
            if(!synonymCheckingEnabled || (acceptedNameUsageID == "" || taxonID == acceptedNameUsageID) ){
                if(parentNameUsageID){
                    childParentMap.put(taxonID, [cn:scientificName, cr:taxonRank, p:parentNameUsageID ])
                } else {
                    parentLess << taxonID
                }
            }
        }

        log.info("Parent-less: " + parentLess.size())
        log.info("Parent-child: " + childParentMap.size())

        def taxonDenormLookup = [:]

        log.info("Starting denorm lookups")
        childParentMap.keySet().each {
            //don't bother de-normalising terminal taxa
            if(parents.contains(it)){
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
    private List denormaliseTaxon(id, currentList, childParentMap, stackLevel = 0){
        if(stackLevel > 20){
            log.warn("Infinite loop detected for ${id} " + currentList)
            return currentList
        }
        def info = childParentMap.get(id)
        if(info && info['p'] && !currentList.contains(id + '|' + info['cn'] + '|' + info['cr'] )){
            currentList << id + '|' + info['cn'] + '|' + info['cr']   //cn:scientificName, cr:taxonRank, p:parentNameUsageID
            denormaliseTaxon(info['p'], currentList, childParentMap, stackLevel + 1)
        }
        currentList
    }

    /**
     * Import a DwC-A into this system.
     *
     * @return
     */
    def importDwcA() {

        if(!params.dwca_dir || !(new File(params.dwca_dir).exists())){
            render ([success: false, message:'Supply a DwC-A parameter'] as JSON)
            return
        }

        def clearIndex = BooleanUtils.toBooleanObject(params.clear_index?:"false")
        def dwcDir =  params.dwca_dir

        Thread.start {

            //read the DwC metadata
            Archive archive = ArchiveFactory.openArchive(new File(dwcDir));
            ArchiveFile taxaArchiveFile = archive.getCore()

            //vernacular names extension?
            ArchiveFile vernacularArchiveFile = archive.getExtension(GbifTerm.VernacularName)

            //retrieve taxon rank mappings
            def taxonRanks = readTaxonRankIDs()

            //retrieve images
            def imageMap = indexImages()

            //retrieve common names
            def commonNamesMap = readCommonNames(vernacularArchiveFile)
            log.info("Common names read: " + commonNamesMap.size())

            //retrieve datasets
            def attributionMap = readAttribution(new File(dwcDir + File.separatorChar + "dataset.csv"))
            log.info("Datasets read: " + attributionMap.size())

            //compile a list of synonyms into memory....
            def synonymMap = readSynonyms(taxaArchiveFile)
            log.info("Synonyms read: " + synonymMap.size())

            //initialise SOLR connection
            def solrServer = new ConcurrentUpdateSolrServer(grailsApplication.config.solrBaseUrl, 10, 4)

            if(clearIndex){
                log.info("Deleting existing entries in index...")
                solrServer.deleteByQuery("idxtype:TAXON")
            } else {
                log.info("Skipping deleting existing entries in index...")
            }

            log.info("Index server: " + grailsApplication.config.solrBaseUrl)

            //retrieve the denormed taxon lookup
            def denormalised = denormalise(taxaArchiveFile)
            log.info("Denormalised map..." + denormalised.size())

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

                if (taxonID && !synonymCheckingEnabled || (taxonID == acceptedNameUsageID || acceptedNameUsageID == "")) {

                    def taxonRank = record.value(DwcTerm.taxonRank)
                    def scientificName = record.value(DwcTerm.scientificName)
                    def parentNameUsageID = record.value(DwcTerm.parentNameUsageID)
                    def scientificNameAuthorship = record.value(DwcTerm.scientificNameAuthorship)
                    def taxonRankID = taxonRanks.get(taxonRank.toLowerCase()) ? taxonRanks.get(taxonRank.toLowerCase()) as Integer : -1

                    //common name
                    def doc = new SolrInputDocument()
                    doc.addField("idxtype", "TAXON")

                    doc.addField("id", UUID.randomUUID().toString())
                    doc.addField("guid", taxonID)
                    doc.addField("parentGuid", parentNameUsageID)
                    doc.addField("rank", taxonRank)
                    doc.addField("rankID", taxonRankID)
                    doc.addField("scientificName", scientificName)
                    doc.addField("scientificNameAuthorship", scientificNameAuthorship)
                    if(scientificNameAuthorship){
                        doc.addField("nameComplete", scientificName + " " + scientificNameAuthorship)
                    } else {
                        doc.addField("nameComplete", scientificName)
                    }

                    def inSchema = [DwcTerm.establishmentMeans, DwcTerm.taxonomicStatus, DwcTerm.taxonConceptID]

                    record.terms().each { term ->
                        if (!alreadyIndexed.contains(term)) {
                            if(inSchema.contains(term)){
                                doc.addField(term.simpleName(), record.value(term))
                            } else {
                                //use a dynamic field extension
                                doc.addField(term.simpleName() + DYNAMIC_FIELD_EXTENSION, record.value(term))
                            }
                        }
                    }

                    def attribution = attributionMap.get(record.value(DwcTerm.datasetID))
                    if (attribution) {
                        doc.addField("dataset", attribution["name"])
                        doc.addField("dataProvider", attribution["dataProvider"])
                    }

                    //retrieve images via scientific name - FIXME should be looking up with taxonID
                    def image = imageMap.get(scientificName)
                    if (image) {
                        doc.addField("image", image)
                        doc.addField("imageAvailable", "yes")
                    } else {
                        doc.addField("imageAvailable", "no")
                    }

                    //common names
                    def commonNames = commonNamesMap.get(taxonID)
                    if (commonNames) {
                        commonNames.each {
                            doc.addField("commonName", it)
                        }
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
                                    doc.addField("rk_" + normalisedRank, name)
                                    doc.addField("rkid_" + normalisedRank, tID)
                                }
                            }
                        }
                    }

                    //synonyms - add a separate doc for each
                    def synonyms = synonymMap.get(taxonID)
                    if (synonyms) {
                        synonyms.each { synonym ->

                            def synonymDoc = new SolrInputDocument()
                            synonymDoc.addField("id", UUID.randomUUID().toString())
                            synonymDoc.addField("guid", synonym["taxonID"])
                            synonymDoc.addField("idxtype", "TAXON")
                            synonymDoc.addField("rank", taxonRank)
                            synonymDoc.addField("rankID", taxonRankID)
                            synonymDoc.addField("scientificName", synonym['name'])
                            synonymDoc.addField("nameComplete", synonym['name'])
                            synonymDoc.addField("acceptedConceptName", scientificName + ' ' + scientificNameAuthorship)
                            synonymDoc.addField("acceptedConceptID", taxonID)
                            synonymDoc.addField("taxonomicStatus", "synonym")

                            def synAttribution = attributionMap.get(synonym['dataset'])
                            if (synAttribution) {
                                synonymDoc.addField("dataset", synAttribution["name"])
                                synonymDoc.addField("dataProvider", synAttribution["dataProvider"])
                            }

                            counter++
                            buffer << synonymDoc
                        }
                    }

                    buffer << doc
                }

                if (counter > 0 && counter % 1000 == 0) {
                    if (!buffer.isEmpty()) {
                        log.info("Adding docs: ${counter}")
                        solrServer.add(buffer)
                        solrServer.commit(true, false, true)
                        buffer.clear()
                    }
                }
            }

            //commit remainder
            if (!buffer.isEmpty()) {
                solrServer.add(buffer)
                solrServer.commit(true, false, true)
                buffer.clear()
            }
            log.info "Import finished"
        }

        render ([success:true] as JSON)
    }

    /**
     * Read synonyms into taxonID -> [synonym1, synonym2]
     *
     * @param fileName
     * @return
     */
    private def readSynonyms(ArchiveFile taxaFile){

        def synonyms = [:]
        def iter = taxaFile.iterator()

        while(iter.hasNext()){

            def record = iter.next()

            def taxonID = record.id()
            def acceptedNameUsageID = record.value(DwcTerm.acceptedNameUsageID)
            def scientificName = record.value(DwcTerm.scientificName)
            def scientificNameAuthorship = record.value(DwcTerm.scientificNameAuthorship)
            def datasetID = record.value(DwcTerm.datasetID)

            if(!synonymCheckingEnabled || (acceptedNameUsageID != taxonID && acceptedNameUsageID != "")){
                //we have a synonym
                def synonymList = synonyms.get(acceptedNameUsageID)
                if(!synonymList){
                    synonymList = []
                    synonyms.put(acceptedNameUsageID, synonymList)
                }
                synonymList << [
                        taxonID: taxonID,
                        name: scientificName + ' ' + scientificNameAuthorship,
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
    private def readAttribution(file, fieldDelimiter = ","){

        def datasets = [:]
        if(!file.exists()){
            return datasets
        }

        def csvReader = new CSVReader(new FileReader(file), fieldDelimiter)
        def headers = csvReader.readNext() as List //ignore header

        def nameIdx = headers.indexOf("name")
        def datasetIDIdx = headers.indexOf("datasetID")
        def dataProviderIdx = headers.indexOf("dataProvider")

        def line = null
        while((line =  csvReader.readNext()) != null){
            def datasetID = line[datasetIDIdx]
            def name = line[nameIdx]
            def dataProvider = line[dataProviderIdx]
            datasets.put(datasetID, [name:name, dataProvider: dataProvider])
        }
        csvReader.close()
        datasets
    }

    /**
     * Read the common file, building a map of taxonID -> [commonName1, commonName2]
     *
     * @param fileName
     * @return
     */
    private def readCommonNames(ArchiveFile vernacularArchiveFile){

        def commonNames = [:]
        Iterator<Record> iter = vernacularArchiveFile.iterator()
        while(iter.hasNext()){
            Record record = iter.next()
            def taxonID = record.id()
            def vernacularName = record.value(DwcTerm.vernacularName)
            def nameList = commonNames.get(taxonID)
            if(!nameList){
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
    private def readTaxonRankIDs(){
        Properties props = new Properties()
        InputStream is =  this.class.getResourceAsStream("/taxonRanks.properties")
        props.load(is as InputStream)
        def idMap = [:]
        def iter = props.entrySet().iterator()
        while (iter.hasNext()){
            def entry = iter.next()
            idMap.put(entry.getKey().toLowerCase().trim(), entry.getValue())
        }
        idMap
    }

    /**
     * Retrieve map of scientificName -> image details
     *
     * @return
     */
    private def indexImages(){

        if(!grailsApplication.config.indexImages.toBoolean()){
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
