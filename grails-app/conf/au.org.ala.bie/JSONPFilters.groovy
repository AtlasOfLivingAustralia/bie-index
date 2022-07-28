package au.org.ala.bie

import grails.converters.JSON

class JSONPFilters {

    def filters = {
        all(controller:'*', action:'*') {
            before = {

            }
            after = { Map model ->

                def ct = response.getContentType()
                //println("content type: "  + ct)
                if(ct?.contains("application/json") && model){
                    String resp = model as JSON
                    if(params.callback) {
                        resp = params.callback + "(" + resp + ")"
                    }
                    render (contentType: "application/json", text: resp)
                    false
                }
            }
            afterView = { Exception e ->

            }
        }
    }
}
