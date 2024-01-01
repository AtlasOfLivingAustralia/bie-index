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

    def importService, bieAuthService, indexService
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

    def all(){
        [info: indexService.info()]
    }

    def daily(){}

    def collectory(){}

    def layers(){}

    def regions(){}

    def localities(){}

    def specieslist(){}

    def wordpress(){}

    def knowledgebase(){}

    def biocollect(){}

    def links(){}

    def sitemap() {}

    def occurrences(){}

    def swap() {
        indexService.swap()
    }

    /**
     * Import a DwC-A into this system.
     *
     * @return
     */
    // Documented in openapi.yml, not migrating to annotations because it is not intended for external use.
    def importDwcA() {

        if(!params.dwca_dir || !(new File(params.dwca_dir).exists())){
            asJson([success: false, message: 'Supply a DwC-A parameter'])
            return
        }

        def clearIndex = params.getBoolean('clear_index', false)
        def dwcDir = params.dwca_dir

        if(new File(dwcDir).exists()){
            def job = execute("importDwca", "admin.button.importdwca", { importService.importDwcA(dwcDir, clearIndex, false) })
            asJson (job.status())
        } else {
            asJson ([success: false, message: 'Supplied directory path is not accessible'])
        }
    }

    def importAll(){
        boolean online = params.getBoolean('online', false)
        def job = execute(importService.importSequence.join(','),
                "admin.button.importall",
                { importService.importAll(importService.importSequence, online) })
        asJson(job.status())
    }

    def importDaily(){
        boolean online = params.getBoolean('online', false)
        def job = execute(importService.importDailySequence.join(','),
                "admin.button.daily",
                { importService.importAll(importService.importDailySequence, online) })
        asJson(job.status())
    }

    def importWeekly(){
        boolean online = params.getBoolean('online', false)
        def job = execute(importService.importWeeklySequence.join(','),
                "admin.button.weekly",
                { importService.importAll(importService.importWeeklySequence, online) })
        asJson(job.status())
    }

    /**
     * Import a DwC-A into this system.
     *
     * @return
     */
    def importAllDwcA() {
        boolean online = params.getBoolean('online', false)
        if(new File(grailsApplication.config..getProperty('import.taxonomy.dir')).exists()){
            def job = execute("importDwca", "admin.button.importalldwca", { importService.importAllDwcA(online) })
            asJson(job.status())
        } else {
            asJson ([success: false, message: 'Supplied directory path is not accessible'])
        }
    }

    // Documented in openapi.yml, not migrating to annotations because it is not intended for external use.
    def deleteDanglingSynonyms(){
        boolean online = params.getBoolean('online', false)
        def job = execute("deleteDanglingSynonyms", "admin.button.deletedanglingsynonyms", { importService.clearDanglingSynonyms(online) })
        asJson(job.status())
    }

    /**
     * Import information from the collectory into the main index.
     *
     * @return
     */
    // Documented in openapi.yml, not migrating to annotations because it is not intended for external use.
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
    // Documented in openapi.yml, not migrating to annotations because it is not intended for external use.
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
    // Documented in openapi.yml, not migrating to annotations because it is not intended for external use.
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
    // Documented in openapi.yml, not migrating to annotations because it is not intended for external use.
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
    // Documented in openapi.yml, not migrating to annotations because it is not intended for external use.
    def importHabitats(){
        boolean online = params.getBoolean('online', false)
        def job = execute("importHabitats", "admin.button.importhabitats", { importService.importHabitats(online) })
        asJson(job.status())
    }

    /**
     * Import/index CMS (WordPress) pages
     *
     * @return
     */
    // Documented in openapi.yml, not migrating to annotations because it is not intended for external use.
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
     * Import/index KB (biocollect) projects
     *
     * @return
     */
    def importBiocollect(){
        boolean online = params.getBoolean('online', false)
        def job = execute("importBiocollectProjects", "admin.button.loadbiocollect", { importService.importBiocollectProjects(online) })
        asJson(job.status())
    }

    /**
     * Build sitemap.xml from SOLR index
     *
     * @return
     */
    def buildSitemap(){
        boolean online = params.getBoolean('online', false)
        def job = execute("buildSitemap", "admin.button.buildsitemap", { importService.buildSitemap(online) })
        asJson(job.status())
    }

    /**
     * Index occurrence data
     *
     * @return
     */
    // Documented in openapi.yml, not migrating to annotations because it is not intended for external use.
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
    // Documented in openapi.yml, not migrating to annotations because it is not intended for external use.
    def importConservationSpeciesLists(){
        boolean online = params.getBoolean('online', false)
        def job = execute("importConsevationSpeciesLists", "admin.button.importlistconservatioon", { importService.importConservationSpeciesLists(online) })
        asJson(job.status())
    }

    /**
     * Import/index Vernacular Species Lists
     *
     * @return
     */
    // Documented in openapi.yml, not migrating to annotations because it is not intended for external use.
    def importVernacularSpeciesLists(){
        boolean online = params.getBoolean('online', false)
        def job = execute("buildVernacularSpeciesLists", "admin.button.importlistvernacular", { importService.importVernacularSpeciesLists(online) })
        asJson (job.status())

    }

    def importWikiUrls(){
        boolean online = params.getBoolean('online', false)
        def job = execute("buildWikiUrls", "admin.button.importlistwiki", { importService.loadWikiUrls(online) })
        asJson (job.status())
    }

    def importHiddenImages(){
        boolean online = params.getBoolean('online', false)
        def job = execute("buildHiddenImages", "admin.button.importlisthiddenimages", { importService.loadHiddenImages(online) })
        asJson (job.status())

    }

    // Documented in openapi.yml, not migrating to annotations because it is not intended for external use.
    def buildLinkIdentifiers() {
        def online = params.getBoolean('online', false)
        def job = execute("buildLinkIdentifiers", "admin.button.buildLinks", { importService.buildLinkIdentifiers(online) })
        asJson (job.status())

    }

    // Documented in openapi.yml, not migrating to annotations because it is not intended for external use.
    def denormaliseTaxa() {
        def online = params.getBoolean('online', false)
        def job = execute("denormaliseTaxa", "admin.button.denormalise", { importService.denormaliseTaxa(online) })
        asJson (job.status())

    }

    // Documented in openapi.yml, not migrating to annotations because it is not intended for external use.
    def buildFavourites() {
        def online = params.getBoolean('online', false)
        def job = execute("buildFavourites", "admin.button.buildfavourites", { importService.buildFavourites(online) })
        asJson (job.status())

    }

    // Documented in openapi.yml, not migrating to annotations because it is not intended for external use.
    def buildWeights() {
        def online = params.getBoolean('online', false)
        def job = execute("buildWeights", "admin.button.buildweights", { importService.buildWeights(online) })
        asJson (job.status())

    }

    // Documented in openapi.yml, not migrating to annotations because it is not intended for external use.
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
    // Documented in openapi.yml, not migrating to annotations because it is not intended for external use.
    def loadPreferredImages() {
        def online = params.getBoolean('online', false)
        def job = execute("loadImages", "admin.button.loadimagespref", { importService.loadPreferredImages(online) })
        asJson (job.status())
    }

    // Documented in openapi.yml, not migrating to annotations because it is not intended for external use.
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
