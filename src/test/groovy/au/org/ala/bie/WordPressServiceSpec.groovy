/*
 * Copyright (C) 2019 Atlas of Living Australia
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

import grails.test.mixin.TestFor
import spock.lang.Specification

/**
 * See the API for {@link grails.test.mixin.services.ServiceUnitTestMixin} for usage instructions
 */
@TestFor(WordpressService)
class WordPressServiceSpec extends Specification {

    static List pages = []
    static String firstPageUrl = ""
    static String randomPageUrl = ""
    static Map firstPageMap = [:]
    static Map randomPageMap = [:]
    static int randomNum
    static Integer max = 10

    def setupSpec() {
        // call web service once only and store results in static vars
        service.setConfiguration(grailsApplication.config)
        service.sitemap = '/sitemap.xml' // Depends on wordpress implementation!
        pages = service.resources("")
        firstPageUrl = pages.get(1) // homepage has no body so use second page for testing
        firstPageMap = service.getResource(firstPageUrl)
        Random rand = new Random()
        int count = 0

        while (count < pages.size()) {
            randomNum = rand.nextInt(pages.size())
            randomPageUrl = pages.get(randomNum)
            randomPageMap = service.getResource(randomPageUrl)
            // some pages have no body so we want to skip them
            if (randomPageMap && randomPageMap.body) {
                break
            }

            count++
        }

        log.info "resources is ${pages.size()} in size"
        log.info "first page URL = ${firstPageUrl}"
        log.info "random page URL = ${randomPageUrl}"
        log.info "random page Map = ${randomPageMap}"
    }

    def cleanup() {
    }

    void "test resources (List) should not be empty"() {
        expect:
            pages.size() > 9
    }

    void "test first page URL has expected domain"() {
        expect:
            firstPageUrl?.contains("www.ala.org.au")
    }

    void "test random page URL has expected domain"() {
        expect:
            randomPageUrl?.contains("www.ala.org.au")
    }

    void "test first page has body text"() {
        expect:
            firstPageMap.containsKey("id")
            firstPageMap.containsKey("title")
            firstPageMap.containsKey("body")
            firstPageMap.get("body").size() > 50
    }

    void "test random page has body text"() {
        expect:
            randomPageMap.containsKey("id")
            randomPageMap.containsKey("title")
            randomPageMap.containsKey("body")
            randomPageMap.get("body").size() > 50
    }

}
