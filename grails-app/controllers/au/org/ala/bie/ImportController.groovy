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

    def collectory(){}

    def layers(){}

    def regions(){}

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

    /**
     * Import information from the collectory into the main index.
     *
     * @return
     */
    def importCollectory(){
        if(grailsApplication.config.collectoryUrl){
            Thread.start {
                log.info("Starting import of collectory....")
                importService.importCollectory()
                log.info("Finished import of collectory.")
            }
            asJson ([success:true] )
        } else {
            asJson ([success: false, message: 'collectoryUrl not configured'] )
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
    def importRegions(){
        if(grailsApplication.config.layersServicesUrl){
            Thread.start {
                log.info("Starting import of layers....")
                importService.importRegions()
                log.info("Finished import of layers.")
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

    def ranks() {
        asJson(importService.ranks())
    }


    private def asJson = { model ->
        response.setContentType("application/json;charset=UTF-8")
        model
    }
}
