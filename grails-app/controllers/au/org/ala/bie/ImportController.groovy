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

    def importService, bieAuthService
    def brokerMessagingTemplate
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
            asJson([success: false, message: 'Supply a DwC-A parameter'])
            return
        }

        def clearIndex = BooleanUtils.toBooleanObject(params.clear_index ?: "false")
        def dwcDir = params.dwca_dir

        if(new File(dwcDir).exists()){
            execute { importService.importDwcA(dwcDir, clearIndex) }
            asJson ([success:true])
        } else {
            asJson ([success: false, message: 'Supplied directory path is not accessible'])
        }
    }

    def importAll(){
        execute { importService.importAll() }
        asJson([success: true])
    }

    /**
     * Import a DwC-A into this system.
     *
     * @return
     */
    def importAllDwcA() {
        if(new File(grailsApplication.config..getProperty('import.taxonomy.dir')).exists()){
            execute { importService.importAllDwcA() }
            asJson ([success:true])
        } else {
            asJson ([success: false, message: 'Supplied directory path is not accessible'])
        }
    }

    def deleteDanglingSynonyms(){
        execute { importService.clearDanglingSynonyms() }
        asJson([success: true])
    }

    /**
     * Import information from the collectory into the main index.
     *
     * @return
     */
    def importCollectory(){
        if(grailsApplication.config.collectoryServicesUrl){
            execute { importService.importCollectory() }
            asJson([success:true])
        } else {
            asJson([success: false, message: 'collectoryServicesUrl not configured'])
        }
    }

    /**
     * Import information from layers.
     *
     * @return
     */
    def importLayers(){
        if(grailsApplication.config.layersServicesUrl){
            execute { importService.importLayers() }
            asJson([success:true])
        } else {
            asJson([success: false, message: 'layersServicesUrl not configured'])
        }
    }

    /**
     * Import information from layers.
     *
     * @return
     */
    def importLocalities(){
        if(grailsApplication.config.layersServicesUrl && grailsApplication.config.gazetteerLayerId){
            execute { importService.importLocalities() }
            asJson([success:true])
        } else {
            asJson([success: false, message: 'layersServicesUrl not configured or gazetteerLayerId not configured'])
        }
    }

    /**
     * Import information from layers.
     *
     * @return
     */
    def importRegions(){
        if(grailsApplication.config.layersServicesUrl){
            execute { importService.importRegions() }
            asJson([success:true])
        } else {
            asJson([success: false, message: 'layersServicesUrl not configured'])
        }
    }

    /**
     * Import habitat information.
     *
     * @return
     */
    def importHabitats(){
        execute { importService.importHabitats() }
        asJson([success:true])
    }

    /**
     * Import/index CMS (WordPress) pages
     *
     * @return
     */
    def importWordPress(){
        execute { importService.importWordPressPages() }
        asJson([success:true])
    }

    /**
     * Index occurrence data
     *
     * @return
     */
    def importOccurrences(){
        execute { importService.importOccurrenceData() }
        asJson ([success:true] )

    }

     /**
     * Import/index Conservation Species Lists
     *
     * @return
     */
    def importConservationSpeciesLists(){
        execute { importService.importConservationSpeciesLists() }
        asJson ([success:true] )
    }

    /**
     * Import/index Vernacular Species Lists
     *
     * @return
     */
    def importVernacularSpeciesLists(){
        execute { importService.importVernacularSpeciesLists() }
        asJson ([success:true] )

    }

    def buildLinkIdentifiers() {
        def online = BooleanUtils.toBooleanObject(params.online ?: "false")
        execute { importService.buildLinkIdentifiers(online) }
        asJson ([success:true] )

    }

    def denormaliseTaxa() {
        def online = BooleanUtils.toBooleanObject(params.online ?: "false")
        execute { importService.denormaliseTaxa(online) }
        asJson ([success:true] )

    }

    /**
     * Reads preferred images list in list tool and updates imageId if values have changed
     * list DR is defined by config var ${imagesListsUrl} - property {lists}
     *
     * @return
     */
    def loadPreferredImages() {
        def online = BooleanUtils.toBooleanObject(params.online ?: "false")
        execute { importService.loadPreferredImages(online) }
        asJson ([success:true] )
    }

    def loadImages() {
        def online = BooleanUtils.toBooleanObject(params.online ?: "false")
        execute { importService.loadImages(online) }
        asJson ([success:true] )
    }

    def ranks() {
        asJson(importService.ranks())
    }


    private def asJson = { model ->
        response.setContentType("application/json;charset=UTF-8")
        render (model as JSON)
    }

    private def execute = { Closure task ->
        Thread.start {
            try {
                brokerMessagingTemplate.convertAndSend "/topic/import-control", "STARTED"
                task.call()
                brokerMessagingTemplate.convertAndSend "/topic/import-control", "FINISHED"
            } catch (Exception ex) {
                log.error(ex.message, ex)
                brokerMessagingTemplate.convertAndSend "/topic/import-control", "ERROR"
            }
        }
    }
}
