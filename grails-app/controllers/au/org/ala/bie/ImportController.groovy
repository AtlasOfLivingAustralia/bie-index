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

import grails.converters.JSON
import org.apache.commons.lang.BooleanUtils
import au.org.ala.web.AlaSecured
/**
 * Controller for data import into the system.
 */
@AlaSecured(value = "ROLE_ADMIN", redirectUri = "/")
class ImportController {

    def importService

    /**
     * Load import index page.
     */
    def index() {
        def filePaths = importService.retrieveAvailableDwCAPaths()
        [filePaths: filePaths]
    }

    def all(){}

    def collectory(){}

    def layers(){}

    def regions(){}

    def localities(){}

    def specieslist(){}

    def wordpress(){}

    def links(){}

    def occurrences(){}

    /**
     * Import a DwC-A into this system.
     *
     * @return
     */
    def importDwcA() {

        if(!params.dwca_dir || !(new File(params.dwca_dir).exists())){
            render ([success: false, message: 'Supply a DwC-A parameter'] as JSON)
            return
        }

        def clearIndex = BooleanUtils.toBooleanObject(params.clear_index ?: "false")
        def dwcDir = params.dwca_dir

        if(new File(dwcDir).exists()){
            Thread.start {
                log.info("Starting import of ${dwcDir}....")
                importService.importDwcA(dwcDir, clearIndex)
                log.info("Finished import of ${dwcDir}.")
            }
            asJson ([success:true])
        } else {
            asJson ([success: false, message: 'Supplied directory path is not accessible'])
        }
    }

    def importAll(){
        try {
            importService.importAll()
        } catch (Exception e) {
            log.error("Problem loading taxa: " + e.getMessage(), e)
        }
    }

    /**
     * Import a DwC-A into this system.
     *
     * @return
     */
    def importAllDwcA() {
        if(new File(grailsApplication.config.importDir).exists()){
            Thread.start {
                log.info("Starting import of all archives....")
                importService.importAllDwcA()
                log.info("Finished import of all archives.")
            }
            asJson ([success:true])
        } else {
            asJson ([success: false, message: 'Supplied directory path is not accessible'])
        }
    }

    /**
     * Import information from the collectory into the main index.
     *
     * @return
     */
    def importCollectory(){
        if(grailsApplication.config.collectoryServicesUrl){
            Thread.start {
                log.info("Starting import of collectory....")
                importService.importCollectory()
                log.info("Finished import of collectory.")
            }
            asJson ([success:true] )
        } else {
            asJson ([success: false, message: 'collectoryServicesUrl not configured'] )
        }
    }

    /**
     * Import information from layers.
     *
     * @return
     */
    def importLayers(){
        if(grailsApplication.config.layersServicesUrl){
            Thread.start {
                log.info("Starting import of layers....")
                importService.importLayers()
                log.info("Finished import of layers.")
            }
            asJson ([success:true] )
        } else {
            asJson ([success: false, message: 'layersServicesUrl not configured'] )
        }
    }

    /**
     * Import information from layers.
     *
     * @return
     */
    def importLocalities(){
        if(grailsApplication.config.layersServicesUrl && grailsApplication.config.gazetteerLayerId){
            Thread.start {
                log.info("Starting import of localities from gazetteer....")
                importService.importLocalities()
                log.info("Finished import of localities from gazetteer.")
            }
            asJson ([success:true] )
        } else {
            asJson ([success: false, message: 'layersServicesUrl not configured or gazetteerLayerId not configured'] )
        }
    }

    /**
     * Import information from layers.
     *
     * @return
     */
    def importRegions(){
        if(grailsApplication.config.layersServicesUrl){
            Thread.start {
                log.info("Starting import of regions....")
                importService.importRegions()
                log.info("Finished import of regions.")
            }
            asJson ([success:true] )
        } else {
            asJson ([success: false, message: 'layersServicesUrl not configured'] )
        }
    }

    /**
     * Import habitat information.
     *
     * @return
     */
    def importHabitats(){
            Thread.start {
                log.info("Starting import of habitats....")
                importService.importHabitats()
                log.info("Finished import of habitats.")
            }
            asJson ([success:true] )
    }

    /**
     * Import/index CMS (WordPress) pages
     *
     * @return
     */
    def importWordPress(){
            Thread.start {
                log.info("Starting import of CMS pages....")
                importService.importWordPressPages()
                log.info("Finished import of CMS pages.")
            }
            asJson ([success:true] )

    }

    /**
     * Index occurrence data
     *
     * @return
     */
    def importOccurrences(){
            Thread.start {
                log.info("Starting import of occurrences data....")
                importService.importOccurrenceData()
                log.info("Finished import of occurrences data.")
            }
            asJson ([success:true] )

    }

     /**
     * Import/index Conservation Species Lists
     *
     * @return
     */
    def importConservationSpeciesLists(){
            Thread.start {
                log.info("Starting import of Conservation Species Lists....")
                importService.importConservationSpeciesLists()
                log.info("Finished import of Conservation Species Lists.")
            }
            asJson ([success:true] )

    }

    /**
     * Import/index Vernacular Species Lists
     *
     * @return
     */
    def importVernacularSpeciesLists(){
        Thread.start {
            log.info("Starting import of Vernacular Species Lists....")
            importService.importVernacularSpeciesLists()
            log.info("Finished import of Vernacular Species Lists.")
        }
        asJson ([success:true] )

    }

    def buildLinkIdentifiers() {
        def online = BooleanUtils.toBooleanObject(params.online ?: "false")
        Thread.start {
            log.info("Starting build of link identifiers....")
            importService.buildLinkIdentifiers(online)
            log.info("Finished build of link identifiers.")
        }
        asJson ([success:true] )

    }

    def denormaliseTaxa() {
        def online = BooleanUtils.toBooleanObject(params.online ?: "false")
        Thread.start {
            log.info("Starting taxon denormalisaion....")
            importService.denormaliseTaxa(online)
            log.info("Finished taxon denormalisaion.")
        }
        asJson ([success:true] )

    }

    def loadImages() {
        def online = BooleanUtils.toBooleanObject(params.online ?: "false")
        Thread.start {
            log.info("Starting image load....")
            importService.loadImages(online)
            log.info("Finished image load.")
        }
        asJson ([success:true] )
    }

    def ranks() {
        asJson(importService.ranks())
    }


    private def asJson = { model ->
        response.setContentType("application/json;charset=UTF-8")
        model
    }
}
