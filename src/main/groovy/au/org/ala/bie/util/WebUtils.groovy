/*
 * Copyright (C) 2025 Atlas of Living Australia
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
package au.org.ala.bie.util

import grails.util.Holders
import groovy.util.logging.Slf4j

/**
 * Utilities for outbound HTTP requests.
 * <p>
 * Provides a single source for the application User-Agent so that requests to
 * downstream services (biocache-ws, namematching-ws, collectory, etc.) are
 * identified as "&lt;app name&gt;/&lt;app version&gt;" rather than the JVM default
 * (e.g. "Java/11.0.31").
 */
@Slf4j
class WebUtils {
    /** Fallbacks used when the build-populated info.app.* config is unavailable (e.g. in unit tests). */
    static final String DEFAULT_APP_NAME = 'bie-index'
    static final String DEFAULT_APP_VERSION = '3.1'

    /**
     * The application User-Agent, e.g. "bie-index/3.3.0".
     *
     * @return The User-Agent string, derived from info.app.name / info.app.version
     */
    static String userAgent() {
        def config = Holders.grailsApplication?.config
        String name = config?.getProperty('info.app.name', String, DEFAULT_APP_NAME) ?: DEFAULT_APP_NAME
        String version = config?.getProperty('info.app.version', String, DEFAULT_APP_VERSION) ?: DEFAULT_APP_VERSION
        return "${name}/${version}" as String
    }

    /**
     * A request header map containing the application User-Agent.
     * <p>
     * Suitable for use as the {@code requestProperties} of Groovy's
     * {@code URL.getText(Map, String)} / {@code JsonSlurper.parse(URL, Map)} and
     * for ala-ws {@code WebService} calls.
     *
     * @return A map of ["User-Agent": userAgent()]
     */
    static Map<String, String> userAgentHeader() {
        return ['User-Agent': userAgent()]
    }

    /**
     * Connection configuration parameters carrying the application User-Agent,
     * for Groovy's {@code URL.getText(Map, String)} / {@code URL.getText(Map)} and
     * {@code JsonSlurper.parse(URL, Map)}.
     * <p>
     * These methods expect request headers nested under a {@code requestProperties}
     * key, so this cannot be a flat header map.
     *
     * @return A map of [requestProperties: ["User-Agent": userAgent()]]
     */
    static Map getConnectionParams() {
        return [requestProperties: userAgentHeader()]
    }

    /**
     * Set the application User-Agent on a URLConnection.
     *
     * @param connection The connection to configure
     * @return The same connection, for chaining
     */
    static URLConnection withUserAgent(URLConnection connection) {
        connection.setRequestProperty('User-Agent', userAgent())
        return connection
    }
}
