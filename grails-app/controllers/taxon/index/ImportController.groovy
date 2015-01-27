package taxon.index

import au.com.bytecode.opencsv.CSVReader
import au.com.bytecode.opencsv.CSVWriter
import grails.converters.JSON
import org.apache.solr.client.solrj.impl.ConcurrentUpdateSolrServer
import org.apache.solr.common.SolrInputDocument

class ImportController {

    def solrBaseUrl = "http://130.56.248.115/solr/bie_denormed"

    def index() {}

    /**
     *
     * @return
     */
    def denormalise(){

        def dwcDir = "/data/uk/dwca"

        //read inventory, creating entries in index....
        def csvReader = new CSVReader(new FileReader(new File(dwcDir + File.separatorChar + "taxa.csv")))

        def headers = csvReader.readNext() as List //ignore header

        //get field indicies
        def taxonIDIdx = headers.indexOf("taxonID")
        def parentNameUsageIDIdx = headers.indexOf("parentNameUsageID")
        def scientificNameIdx = headers.indexOf("scientificName")
        def acceptedNameUsageIDIdx = headers.indexOf("acceptedNameUsageID")
        def taxonRankIdx = headers.indexOf("taxonRank")

        def childParentMap = [:]
        def parentLess = []
        def parents = [] as Set

        def record = null
        while( (record = csvReader.readNext()) != null){

            def taxonID = record[taxonIDIdx]
            def parentNameUsageID = record[parentNameUsageIDIdx]
            def acceptedNameUsageID = record[acceptedNameUsageIDIdx]
            def scientificName = record[scientificNameIdx]
            def taxonRank = record[taxonRankIdx]

            parents << parentNameUsageID

            //ignore synonyms
            if(acceptedNameUsageID == "" || taxonID == acceptedNameUsageID){
                if(parentNameUsageID){
                    childParentMap.put(taxonID, [cn:scientificName, cr:taxonRank, p:parentNameUsageID ])
                } else {
                    parentLess << taxonID
                }
            }
        }

        println("Parent-less: " + parentLess.size())
        println("Parent-child: " + childParentMap.size())

//        def denormOutput = new CSVWriter(new FileWriter("/tmp/denormed"))

        def taxonDenormLookup = [:]


        childParentMap.keySet().each {
            //dont bother denormalising terminal taxa
            if(parents.contains(it)){
                def list = []
                denormaliseTaxon(it, list, childParentMap)
                taxonDenormLookup.put(it, list)
            }
        }
//        denormOutput.flush()
//        denormOutput.close()

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
    List denormaliseTaxon(id, currentList, childParentMap){
        def info = childParentMap.get(id)
        if(info && info['p']){
            currentList << id + '|' + info['cn'] + '|' + info['cr']
            denormaliseTaxon(info['p'], currentList, childParentMap)
        }
        currentList
    }



    def importDwcA() {

        def dwcDir = "/data/uk/dwca"

        //todo validate the DWC-A
        //retrieve taxon rank mappings
        def taxonRanks = readTaxonRankIDs()

        //retrieve images
        def imageMap = indexImages()

        //retrieve common names
        def commonNamesMap = readCommonNames(new File(dwcDir + File.separatorChar + "vernacular.csv"))
        println("Common names read: " + commonNamesMap.size())

        //retrieve datasets
        def attributionMap = readAttribution(new File(dwcDir + File.separatorChar + "dataset.csv"))
        println("Datasets read: " + attributionMap.size())

        //compile a list of synonyms into memory....
        def synonymMap = readSynonyms(new File(dwcDir + File.separatorChar + "taxa.csv"))
        println("Synonyms read: " + synonymMap.size())

        //initialise SOLR connection
        def solrServer = new ConcurrentUpdateSolrServer(solrBaseUrl, 10, 4)
        println("Deleting existing entries in index...")
        solrServer.deleteByQuery("idxtype:TAXON")

        //retrieve the denormed taxon lookup
        def denormalised =  denormalise()

        //read inventory, creating entries in index....
        def csvReader = new CSVReader(new FileReader(new File(dwcDir + File.separatorChar + "taxa.csv")))

        def headers = csvReader.readNext() as List //ignore header

        //get field indicies
        def taxonIDIdx = headers.indexOf("taxonID")
        def datasetIDIdx = headers.indexOf("datasetID")
        def acceptedNameUsageIDIdx = headers.indexOf("acceptedNameUsageID")
        def parentNameUsageIDIdx = headers.indexOf("parentNameUsageID")
        def scientificNameIdx = headers.indexOf("scientificName")
        def scientificNameAuthorshipIdx = headers.indexOf("scientificNameAuthorship")
        def taxonRankIdx = headers.indexOf("taxonRank")

        def alreadyIndexed = [taxonIDIdx, datasetIDIdx,
                              acceptedNameUsageIDIdx, parentNameUsageIDIdx, scientificNameIdx, taxonRankIdx]

        def record = null
        def buffer = []
        def counter = 0
        while( (record = csvReader.readNext()) != null){
            counter ++

            def taxonID = record[taxonIDIdx]
            def acceptedNameUsageID = record[acceptedNameUsageIDIdx]

            if(taxonID == acceptedNameUsageID || acceptedNameUsageID == "" ){

                def taxonRank = record[taxonRankIdx]
                def scientificName = record[scientificNameIdx]
                def parentNameUsageID = record[parentNameUsageIDIdx]
                def taxonRankID = taxonRanks.get(taxonRank.toLowerCase()) ? taxonRanks.get(taxonRank.toLowerCase()) as Integer : -1
                //index everything in taxa.csv, duplicate some fields for backwards compatibility

                //common name
                def doc = new SolrInputDocument()
                doc.addField("idxtype", "TAXON")

                doc.addField("id", UUID.randomUUID().toString())
                doc.addField("guid", taxonID)
                doc.addField("parentGuid", record[parentNameUsageIDIdx])
                doc.addField("rank", taxonRank)
                doc.addField("rankId", taxonRankID)
                doc.addField("scientificName", scientificName)
                doc.addField("nameComplete", scientificName + ' ' + record[scientificNameAuthorshipIdx])
                doc.addField("author", record[scientificNameAuthorshipIdx])

                headers.eachWithIndex { header, idx ->
                    if(idx > 0 && !alreadyIndexed.contains(idx) && record.length > idx){
                        doc.addField(header + "_s", record[idx])
                    }
                }

                def attribution = attributionMap.get(record[datasetIDIdx])
                if(attribution){
                    doc.addField("dataset", attribution["name"])
                    doc.addField("dataProvider_s", attribution["dataProvider"])
                }

                //retrieve images via scientific name - FIXME should be looking up with taxonID
                def image = imageMap.get(scientificName)
                if(image) {
                    doc.addField("image", image)
                }

                //common names
                def commonNames = commonNamesMap.get(taxonID)
                if(commonNames){
                    commonNames.each {
                        doc.addField("commonName", it)
                    }
                }

                //denormed taxonomy
                if(parentNameUsageID){
                    def taxa = denormalised.get(parentNameUsageID)
                    def processedRanks = []
                    taxa.each { taxon ->

                        //check we have only one value for each rank...
                        def parts = taxon.split('\\|')

                        if(parts.length==3){
                            String tID = parts[0]
                            String name = parts[1]
                            String rank = parts[2]
                            String normalisedRank = rank.replaceAll(" ", "_").toLowerCase()
                            if(processedRanks.contains(normalisedRank)){
                                println("Duplicated rank: " + normalisedRank + " - " + taxa)
                            } else {
                                processedRanks << normalisedRank
                                doc.addField("rk_"+normalisedRank, name)
                                doc.addField("rkid_"+normalisedRank, tID)
                            }
                        }
                    }
                }

                //synonyms - add a separate doc for each
                def synonyms = synonymMap.get(taxonID)
                if(synonyms){
                    synonyms.each { synonym ->

                        def synonymDoc = new SolrInputDocument()
                        synonymDoc.addField("id", UUID.randomUUID().toString())
                        synonymDoc.addField("guid", synonym["taxonID"])
                        synonymDoc.addField("idxtype", "TAXON")
                        synonymDoc.addField("rank", taxonRank)
                        synonymDoc.addField("rankId", taxonRankID)
                        synonymDoc.addField("scientificName", synonym['name'])
                        synonymDoc.addField("nameComplete", synonym['name'])
                        synonymDoc.addField("acceptedConceptName", scientificName + ' ' + record[scientificNameAuthorshipIdx])
                        synonymDoc.addField("acceptedConceptID", taxonID)
                        synonymDoc.addField("taxonomicStatus_s", "synonym")

                        def synAttribution = attributionMap.get(synonym['dataset'])
                        if(synAttribution){
                            synonymDoc.addField("dataset", synAttribution["name"])
                            synonymDoc.addField("dataProvider_s", synAttribution["dataProvider"])
                        }

                        counter++
                        buffer << synonymDoc
                    }
                }

                buffer << doc
            }

            if(counter > 0 && counter  % 1000 == 0){
                if(!buffer.isEmpty()){
                    println("Adding docs: ${counter}")
                    solrServer.add(buffer)
                    solrServer.commit(true, false, true)
                    buffer.clear()
                }
            }
        }

        //commit remainder
        if(!buffer.isEmpty()) {
            solrServer.add(buffer)
            solrServer.commit(true, false, true)
            buffer.clear()
        }
        csvReader.close()

        render ([indexCount: counter] as JSON)
    }

    /**
     * Read synonyms into taxonID -> [synonym1, synonym2]
     *
     * @param fileName
     * @return
     */
    private def readSynonyms(fileName){

        def csvReader = new CSVReader(new FileReader(fileName))
        def headers = csvReader.readNext() as List //ignore header

        def taxonIDIdx = headers.indexOf("taxonID")
        def datasetIDIdx = headers.indexOf("datasetID")
        def scientificNameIdx = headers.indexOf("scientificName")
        def acceptedNameUsageIDIdx = headers.indexOf("acceptedNameUsageID")
        def scientificNameAuthorshipIdx = headers.indexOf("scientificNameAuthorship")

        def synonyms = [:]
        def line = null
        while((line =  csvReader.readNext()) != null){
            def taxonID = line[taxonIDIdx]
            def acceptedNameUsageID = line[acceptedNameUsageIDIdx]
            if(acceptedNameUsageID != taxonID && acceptedNameUsageID != ""){
                //we have a synonym
                def synonymList = synonyms.get(acceptedNameUsageID)
                if(!synonymList){
                    synonymList = []
                    synonyms.put(acceptedNameUsageID, synonymList)
                }
                synonymList << [taxonID: taxonID, name: line[scientificNameIdx] + ' ' + line[scientificNameAuthorshipIdx], datasetID: line[datasetIDIdx]]
            }
        }
        csvReader.close()
        synonyms
    }

    /**
     * Read the attribution file, building a map of ID -> name, dataProvider
     *
     * @param fileName
     * @return
     */
    private def readAttribution(fileName){

        def csvReader = new CSVReader(new FileReader(fileName))
        def headers = csvReader.readNext() as List //ignore header

        def nameIdx = headers.indexOf("name")
        def datasetIDIdx = headers.indexOf("datasetID")
        def dataProviderIdx = headers.indexOf("dataProvider")

        def datasets = [:]
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
    private def readCommonNames(fileName){

        def csvReader = new CSVReader(new FileReader(fileName))
        def headers = csvReader.readNext() as List //ignore header

        def taxonIDIdx = headers.indexOf("taxonID")
        def nameIdx = headers.indexOf("vernacularName")

        def commonNames = [:]
        def line = null
        while((line =  csvReader.readNext()) != null){
            def taxonKey = line[taxonIDIdx]
            def name = line[nameIdx]

            def nameList = commonNames.get(taxonKey)
            if(!nameList){
                nameList = []
                commonNames.put(taxonKey, nameList)
            }

            nameList << name
        }
        csvReader.close()
        commonNames
    }

    private def readTaxonRankIDs(){
        Properties props = new Properties()
        InputStream is =  this.class.getResourceAsStream("/taxonRanks.properties")
        props.load(is as InputStream)
        def idMap = [:]
        def iter = props.entrySet().iterator()
        while(iter.hasNext()){
            def entry = iter.next()
            idMap.put(entry.getKey().toLowerCase(), entry.getValue())
        }
        idMap
    }

    /**
     * Retrieve map of scientificName -> image details
     *
     * @return
     */
    private def indexImages(){

        //def imageUrls = "http://ala-macropus.it.csiro.au/solr/biocache/select?q=*%3A*&fq=multimedia%3AImage&fl=taxon_concept_lsid%2C+image_url&wt=csv&indent=true&rows=500000"
        //http://ala-macropus.it.csiro.au/solr/biocache/select?q=*%3A*&fq=multimedia%3AImage&fl=taxon_name%2C+image_url&wt=csv&indent=true&rows=500
        def imagesUrl = "http://ala-demo.gbif.org/solr/biocache/select?" +
                "q=*%3A*" +
                "&fq=multimedia%3AImage" +
                "&fl=taxon_name%2C+image_url" +
                "&wt=csv" +
                "&indent=true" +
                "&rows=100000"

        //load into map, keyed (for now) on scientific name. The images *should* be keyed on GUID
        def imageMap = [:]
        new URL(imagesUrl).readLines().each {
            def parts = it.split(",")
            if(parts.length == 2){
                imageMap.put(parts[0].replaceFirst(/\([A-Z]{1}[a-z]{1,}\) /, ""), parts[1].replaceFirst("/data/", "http://ala-demo.gbif.org/"))
            }
        }

        println("Images loaded: " + imageMap.size())

//        imageMap.each { name, url -> println("${name} ${url}")}

        imageMap
    }
}
