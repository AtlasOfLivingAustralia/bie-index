package bie.index

import grails.boot.GrailsApp
import grails.boot.config.GrailsAutoConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.autoconfigure.solr.SolrAutoConfiguration
import org.springframework.scheduling.annotation.EnableScheduling

@EnableAutoConfiguration(exclude = [SolrAutoConfiguration])
@EnableScheduling
class Application extends GrailsAutoConfiguration {
    static void main(String[] args) {
        GrailsApp.run(Application, args)
    }
}
