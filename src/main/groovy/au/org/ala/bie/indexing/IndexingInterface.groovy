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

package au.org.ala.bie.indexing

/**
 * Interface for indexing the different content types (JSON, web pages, etc.)
 */
interface IndexingInterface {
    /**
     * Get a list of pages or resources from external URI
     *
     * @return List
     */
    List resources(String type)

    /**
     * For a given resources (URI) return a Map with data needed to populate the SOLR index
     * TODO: change return type be a bean not Map (type safe)
     *
     * @return Map with keys: id, title, shortlink, body, categories
     */
    Map getResource(String url)
}
