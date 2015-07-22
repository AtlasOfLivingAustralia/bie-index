package au.org.ala.bie

import grails.converters.JSON
import org.apache.commons.lang.BooleanUtils

/**
 * Controller for data import into the system.
 */
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

    private def asJson = { model ->
        response.setContentType("application/json;charset=UTF-8")
        model
    }
}
