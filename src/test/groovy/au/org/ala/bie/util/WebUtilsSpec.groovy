package au.org.ala.bie.util

import spock.lang.Specification

class WebUtilsSpec extends Specification {

    def 'userAgent falls back to defaults when no grailsApplication is available'() {
        expect: 'the application/version format using the built-in defaults'
        WebUtils.userAgent() == "${WebUtils.DEFAULT_APP_NAME}/${WebUtils.DEFAULT_APP_VERSION}"
    }

    def 'userAgent uses application/version format (never the JVM default)'() {
        expect:
        WebUtils.userAgent() ==~ /[^\/]+\/[^\/]+/
        !WebUtils.userAgent().startsWith('Java/')
    }

    def 'userAgentHeader is a flat User-Agent header map'() {
        expect:
        WebUtils.userAgentHeader() == ['User-Agent': WebUtils.userAgent()]
    }

    def 'connectionParams nests the User-Agent under requestProperties'() {
        when:
        def params = WebUtils.getConnectionParams()

        then: 'suitable for URL.getText(Map, String) / JsonSlurper.parse(URL, Map)'
        params == [requestProperties: ['User-Agent': WebUtils.userAgent()]]
    }

    def 'withUserAgent sets the User-Agent request property on a connection'() {
        given:
        def props = [:]
        def connection = [setRequestProperty: { String k, String v -> props[k] = v }] as URLConnection

        when:
        def result = WebUtils.withUserAgent(connection)

        then:
        props['User-Agent'] == WebUtils.userAgent()
        result.is(connection)
    }
}
