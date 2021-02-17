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

import au.org.ala.bie.util.Job
import grails.converters.JSON
import au.org.ala.web.AlaSecured
/**
 * Controller for data import into the system.
 */
@AlaSecured(value = "ROLE_ADMIN", redirectUri = "/")
class ImportController {

    def importService, bieAuthService
    def brokerMessagingTemplate
    def jobService

    Map<String, Job> jobStatus = [:]

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

    def knowledgebase(){}

    def links(){}

    def occurrences(){}

    /**
     * Import a DwC-A into this system.
     *
     * @return
     */
    // Dcoumented in openapi.yml
    def importDwcA() {

        if(!params.dwca_dir || !(new File(params.dwca_dir).exists())){
            asJson([success: false, message: 'Supply a DwC-A parameter'])
            return
        }

        def clearIndex = params.getBoolean('clear_index', false)
        def dwcDir = params.dwca_dir

        if(new File(dwcDir).exists()){
            def job = execute("importDwca", "admin.button.importdwca", { importService.importDwcA(dwcDir, clearIndex) })
            asJson (job.status())
        } else {
            asJson ([success: false, message: 'Supplied directory path is not accessible'])
        }
    }

    // Dcoumented in openapi.yml
    def importAll(){
        def job = execute(
                "importDwca,importCollectory,deleteDanglingSynonyms,importLayers,importLocalities,importRegions,importHabitats,importHabitats," +
                    "importWordPressPages,importOccurrences,importConsevationSpeciesLists,buildVernacularSpeciesLists,buildLinkIdentifiers" +
                    "denormaliseTaxa,loadImages,importKnowledgeBase",
                "admin.button.importall",
                { importService.importAll() })
        asJson(job.status())
    }

    /**
     * Import a DwC-A into this system.
     *
     * @return
     */
    // Documented in openapi.yml
    def importAllDwcA() {
        if(new File(grailsApplication.config..getProperty('import.taxonomy.dir')).exists()){
            def job = execute("importDwca", "admin.button.importalldwca", { importService.importAllDwcA() })
            asJson(job.status())
        } else {
            asJson ([success: false, message: 'Supplied directory path is not accessible'])
        }
    }

    // Documented in openapi.yml
    def deleteDanglingSynonyms(){
        def job = execute("deleteDanglingSynonyms", "admin.button.deletedanglingsynonyms", { importService.clearDanglingSynonyms() })
        asJson(job.status())
    }

    /**
     * Import information from the collectory into the main index.
     *
     * @return
     */
    // Documented in openapi.yml
    def importCollectory(){
        boolean online = params.getBoolean('online', false)
        if(grailsApplication.config.collectory.service){
            def job = execute("importCollectory", "admin.button.importcollectory", { importService.importCollectory(online) })
            asJson(job.status())
        } else {
            asJson([success: false, message: 'collectoryServicesUrl not configured'])
        }
    }

    /**
     * Import information from layers.
     *
     * @return
     */
    // Documented in openapi.yml
    def importLayers(){
        boolean online = params.getBoolean('online', false)
        if(grailsApplication.config.layers.service){
            def job = execute("importLayers", "admin.button.importlayer", { importService.importLayers(online) })
            asJson(job.status())
        } else {
            asJson([success: false, message: 'layers.service not configured'])
        }
    }

    /**
     * Import information from layers.
     *
     * @return
     */
    // Documented in openapi.yml
    def importLocalities(){
        boolean online = params.getBoolean('online', false)
        if(grailsApplication.config.layers.service && grailsApplication.config.layers.gazetteerId){
            def job = execute("importLocalities", "admin.button.importlocalities", { importService.importLocalities(online) })
            asJson(job.status())
        } else {
            asJson([success: false, message: 'layers.services not configured or layers.gazetteerId not configured'])
        }
    }

    /**
     * Import information from layers.
     *
     * @return
     */
    // Documented in openapi.yml
    def importRegions(){
        boolean online = params.getBoolean('online', false)
        if(grailsApplication.config.layers.service){
            def job = execute("importRegions", "admin.button.importregions", { importService.importRegions(online) })
            asJson(job.status())
        } else {
            asJson([success: false, message: 'layers.service not configured'])
        }
    }

    /**
     * Import habitat information.
     *
     * @return
     */
    // Documented in openapi.yml
    def importHabitats(){
        def job = execute("importHabitats", "admin.button.importhabitats", { importService.importHabitats() })
        asJson(job.status())
    }

    /**
     * Import/index CMS (WordPress) pages
     *
     * @return
     */
    // Documented in openapi.yml
    def importWordPress(){
        boolean online = params.getBoolean('online', false)
        def job = execute("importWordPressPages", "admin.button.loadwordpress", { importService.importWordPressPages(online) })
        asJson(job.status())
    }

    /**
     * Import/index KB (Fresh Desk) pages
     *
     * @return
     */
    def importKnowledgeBase(){
        boolean online = params.getBoolean('online', false)
        def job = execute("importKnowledgeBasePages", "admin.button.loadknowledgebase", { importService.importKnowledgeBasePages(online) })
        asJson(job.status())
    }

    /**
     * Index occurrence data
     *
     * @return
     */
    // Documented in openapi.yml
    def importOccurrences(){
        def online = params.getBoolean('online', false)
        def job = execute("importOccurrences", "admin.button.loadoccurrence", { importService.importOccurrenceData(online) })
        asJson (job.status())

    }

     /**
     * Import/index Conservation Species Lists
     *
     * @return
     */
    // Documented in openapi.yml
    def importConservationSpeciesLists(){
        def job = execute("importConsevationSpeciesLists", "admin.button.importlistconservatioon", { importService.importConservationSpeciesLists() })
        asJson(job.status())
    }

    /**
     * Import/index Vernacular Species Lists
     *
     * @return
     */
    // Documented in openapi.yml
    def importVernacularSpeciesLists(){
        def job = execute("buildVernacularSpeciesLists", "admin.button.importlistvernacular", { importService.importVernacularSpeciesLists() })
        asJson (job.status())

    }

    // Documented in openapi.yml
    def buildLinkIdentifiers() {
        def online = params.getBoolean('online', false)
        def job = execute("buildLinkIdentifiers", "admin.button.buildLinks", { importService.buildLinkIdentifiers(online) })
        asJson (job.status())

    }

    // Documented in openapi.yml
    def denormaliseTaxa() {
        def online = params.getBoolean('online', false)
        def job = execute("denormaliseTaxa", "admin.button.denormalise", { importService.denormaliseTaxa(online) })
        asJson (job.status())

    }

    // Documented in openapi.yml
    def buildFavourites() {
        def online = params.getBoolean('online', false)
        def job = execute("buildFavourites", "admin.button.buildfavourites", { importService.buildFavourites(online) })
        asJson (job.status())

    }

    // Documented in openapi.yml
    def buildWeights() {
        def online = params.getBoolean('online', false)
        def job = execute("buildWeights", "admin.button.buildweights", { importService.buildWeights(online) })
        asJson (job.status())

    }

    // Documented in openapi.yml
    def buildSuggestIndex() {
        def online = params.getBoolean('online', false)
        def job = execute("buildSuggestIndex", "admin.button.buildsuggestindex", { importService.buildSuggestIndex(online) })
        asJson (job.status())

    }

    /**
     * Reads preferred images list in list tool and updates imageId if values have changed
     * list DR is defined by config var ${images.config} - property {lists}
     *
     * @return
     */
    // Documented in openapi.yml
    def loadPreferredImages() {
        def online = params.getBoolean('online', false)
        def job = execute("loadImages", "admin.button.loadimagespref", { importService.loadPreferredImages(online) })
        asJson (job.status())
    }

    // Documented in openapi.yml
    def loadImages() {
        def online = params.getBoolean('online', false)
        def job = execute("loadImages", "admin.button.loadimagesall", { importService.loadImages(online) })
        asJson (job.status())
    }

    private def asJson = { model ->
        response.setContentType("application/json;charset=UTF-8")
        render (model as JSON)
    }

    private def execute(String type, String titleCode, Closure task) {
        def title = message(code: titleCode)
        def types = type.split(',') as Set
        def job = jobService.existing(types)
        if (job) {
            return job
        }
        job = jobService.create(types, title, {
            try {
                brokerMessagingTemplate.convertAndSend "/topic/import-control", "STARTED"
                task.call()
                brokerMessagingTemplate.convertAndSend "/topic/import-control", "FINISHED"
            } catch (Exception ex) {
                log.error(ex.message, ex)
                brokerMessagingTemplate.convertAndSend "/topic/import-control", "ERROR"
                throw ex
            }
        })
    }
}
