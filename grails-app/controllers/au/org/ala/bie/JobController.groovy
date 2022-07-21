package au.org.ala.bie

import au.org.ala.web.AlaSecured
import grails.converters.JSON
import grails.converters.XML

@AlaSecured(value = "ROLE_ADMIN", redirectUri = "/")
class JobController {
    def jobService

    // Documented in openapi.yml
    def index() {
        def jobs = jobService.list().collect { it.status() }
        withFormat {
            html jobs: jobs
            json { render jobs as JSON }
            xml { render jobs as XML }
            '*' jobs: jobs
        }
    }

    // Documented in openapi.yml
    def status() {
        def id = params.id
        def status = jobService.get(id)?.status() ?: notFoundStatus(id)
        withFormat {
            html job: status
            json { render status as JSON }
            xml { render status as XML }
            '*' job: status
        }
    }

    def panel() {
        def id = params.id
        def status = jobService.get(id)?.status() ?: notFoundStatus(id)
        [job: status]
    }

    // Documented in openapi.yml
    def cancel() {
        def id = params.id
        jobService.cancel(params.id)
        def status = jobService.get(id)?.status() ?: notFoundStatus(id)
        withFormat {
            html { render view: 'status', model: [job: status] }
            json { render status as JSON }
            xml { render status as XML }
            '*' { render view: 'status', model: [job: status] }
        }
    }

    // Documented in openapi.yml
    def remove() {
        def id = params.id
        def removed = jobService.remove(params.id)
        def status = [success: removed]
        withFormat {
            html { redirect(action: 'index') }
            json { render status as JSON }
            xml { render status as XML }
            '*' { redirect(action: 'index') }
        }
    }

    private def notFoundStatus(id) {
        return [success: false, active: false, id: id, lifecycle: 'ERROR', lastUpdated: new Date(), message: 'Not found']
    }
}