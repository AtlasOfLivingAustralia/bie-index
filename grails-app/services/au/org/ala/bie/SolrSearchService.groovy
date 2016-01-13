package au.org.ala.bie

import au.org.ala.bie.search.FacetResult
import au.org.ala.bie.search.FieldResult
import au.org.ala.bie.search.IndexedTypes
import au.org.ala.bie.search.SearchCollectionDTO
import au.org.ala.bie.search.SearchDTO
import au.org.ala.bie.search.SearchDataProviderDTO
import au.org.ala.bie.search.SearchDatasetDTO
import au.org.ala.bie.search.SearchInstitutionDTO
import au.org.ala.bie.search.SearchLayerDTO
import au.org.ala.bie.search.SearchRegionDTO
import au.org.ala.bie.search.SearchResultsDTO
import au.org.ala.bie.search.SearchTaxonConceptDTO
import au.org.ala.bie.search.SearchWordpressDTO
import au.org.ala.bie.search.StatusType
import org.apache.commons.lang.StringUtils
import org.apache.solr.client.solrj.SolrQuery
import org.apache.solr.client.solrj.SolrServerException
import org.apache.solr.client.solrj.response.FacetField
import org.apache.solr.client.solrj.response.QueryResponse
import org.apache.solr.client.solrj.util.ClientUtils
import org.apache.solr.common.SolrDocument
import org.apache.solr.common.SolrDocumentList
import org.gbif.ecat.model.ParsedName
import org.gbif.ecat.parser.NameParser
import org.gbif.ecat.parser.UnparsableException

/**
 * a search service that uses the solr client rather than construct URLs and parse responses manually
 */
class SolrSearchService {

    static transactional = false

    def liveSolrServer

    SearchResultsDTO<SearchTaxonConceptDTO> findByScientificName(
            String query, List<String> filterQuery = [], Integer startIndex = 0,
            Integer pageSize = 10, String sortField = 'score', String sortDirection = 'asc',
            boolean exactInput = false, boolean includeVernacular = true) {
        try {
            // set the query

            StringBuffer queryString = new StringBuffer()
            if (query.contains(":") && !query.startsWith("urn")) {
                String[] bits = StringUtils.split(query, ":")
                queryString.append(ClientUtils.escapeQueryChars(bits[0]))
                queryString.append(":")
                queryString.append(ClientUtils.escapeQueryChars(bits[1]).toLowerCase())
            } else {

                String cleanQuery = ClientUtils.escapeQueryChars(query).toLowerCase()
                if (exactInput) {
                    cleanQuery = "\"$cleanQuery\""
                }
                //queryString.append(ClientUtils.escapeQueryChars(query))
                queryString.append("idxtype:").append(IndexedTypes.TAXON)
                queryString.append(" AND (")
//                queryString.append("scientificNameText:"+cleanQuery)
                queryString.append("text:").append(cleanQuery) // TODO determine whether this is ok...
                if (includeVernacular) {
                    queryString.append(" OR commonName:").append(cleanQuery)
                }
                //fix so that synonyms are not being included with a guid search
                //queryString.append(" OR (guid:"+cleanQuery).append(" AND -syn_guid:*) OR syn_guid:").append(cleanQuery)
                queryString.append(" OR guid:").append(cleanQuery)
//.append(" AND -syn_guid:*) OR syn_guid:").append(cleanQuery)

                String canonicalSciName = retrieveCanonicalForm(query);
                if (canonicalSciName != null) {
                    canonicalSciName = ClientUtils.escapeQueryChars(canonicalSciName).toLowerCase();
                } else {
                    canonicalSciName = ClientUtils.escapeQueryChars(query).toLowerCase();
                }
                if (exactInput) {
                    canonicalSciName = "\"$canonicalSciName\""
                    queryString.append(" OR scientificName:")
                    queryString.append(canonicalSciName)
                }
                if (includeVernacular) {
                    queryString.append(" OR ")
                    queryString.append(" text:").append(canonicalSciName)
                }

//                queryString.append(" OR simpleText:"+cleanQuery)  //commented out for now as this gives confusing results to users
                queryString.append(")")
            }
            log.debug("search query: " + queryString.toString())
            return doSolrSearch(queryString.toString(), filterQuery, (String[]) null, pageSize, startIndex, sortField, sortDirection)
        } catch (SolrServerException ex) {
            SearchResultsDTO searchResults = new SearchResultsDTO()
            log.error("Problem communicating with SOLR server.", ex)
            searchResults.setStatus("ERROR") // TODO also set a message field on this bean with the error message(?)
            return searchResults
        }
    }

    /**
     * Retrieve a canonical form of the name to search with.
     * @param query
     * @return
     */
    private String retrieveCanonicalForm(String query) {
        NameParser np = new NameParser()
        try {
            ParsedName pn = np.parse(query)
            if (pn != null) {
                return pn.canonicalName()
            }
        }
        catch (UnparsableException e) {
            //do nothing a null name will be returned
            log.debug("Unable to parse name $query. ${e.message}")
        }
        return null;
    }

    /**
     * Re-usable method for performing SOLR searches - takes query string input
     *
     * @param queryString
     * @param filterQuery
     * @param pageSize
     * @param startIndex
     * @param sortField
     * @param sortDirection
     * @return
     * @throws org.apache.solr.client.solrj.SolrServerException
     */
    private SearchResultsDTO doSolrSearch(String queryString, List<String> filterQuery, String[] facets, Integer pageSize,
                                          Integer startIndex, String sortField, String sortDirection) throws SolrServerException {
        SolrQuery solrQuery = initSolrQuery(facets).with {
            // general search settings
            fields = ["*", "score"]
            query = queryString
            delegate
        }

        return doSolrQuery(solrQuery, filterQuery, pageSize, startIndex, sortField, sortDirection)
    }


    private QueryResponse getSolrQueryResponse(SolrQuery solrQuery, List<String> filterQuery, Integer pageSize,
                                               Integer startIndex, String sortField, String sortDirection) throws SolrServerException {
        if (log.debugEnabled) {
            log.debug("About to execute ${solrQuery.query}")
        }

        // set the facet query if set
        addFqs(solrQuery, filterQuery)

        solrQuery.rows = pageSize
        solrQuery.start = startIndex
        solrQuery.sort = new SolrQuery.SortClause(sortField, SolrQuery.ORDER.valueOf(sortDirection))

        // do the Solr search
        return liveSolrServer.query(solrQuery); // can throw exception
    }

    /**
     * Re-usable method for performing SOLR searches - takes SolrQuery input
     *
     * @param solrQuery
     * @param filterQuery
     * @param pageSize
     * @param startIndex
     * @param sortField
     * @param sortDirection
     * @return
     * @throws org.apache.solr.client.solrj.SolrServerException
     */
    private SearchResultsDTO doSolrQuery(SolrQuery solrQuery, List<String> filterQuery, Integer pageSize,
                                         Integer startIndex, String sortField, String sortDirection) throws Exception {
        QueryResponse qr = getSolrQueryResponse(solrQuery, filterQuery, pageSize, startIndex, sortField, sortDirection);
        return createSearchResultsFromQueryResponse(solrQuery, qr, pageSize, sortField, sortDirection)
    }

    private SearchResultsDTO createSearchResultsFromQueryResponse(SolrQuery solrQuery, QueryResponse qr, Integer pageSize,
                                                                  String sortField, String sortDirection) {
        //process results
        SolrDocumentList sdl = qr.getResults()
        List<FacetField> facets = qr.getFacetFields()
        List<SearchDTO> results = new ArrayList<SearchDTO>()
        List<FacetResult> facetResults = new ArrayList<FacetResult>()
        SearchResultsDTO searchResults = new SearchResultsDTO(
            totalRecords: sdl.numFound,
            startIndex: sdl.start,
            pageSize: pageSize,
            status: "OK",
            sort: sortField,
            dir: sortDirection,
            query: solrQuery.query
        )
        // populate SOLR search results
        if (!sdl.isEmpty()) {
            for (SolrDocument doc : sdl) {
                if (IndexedTypes.TAXON.toString().equalsIgnoreCase((String) doc.getFieldValue("idxtype"))) {
                    results.add(createTaxonConceptFromIndex(qr, doc))
                } else if (IndexedTypes.COLLECTION.toString().equalsIgnoreCase((String) doc.getFieldValue("idxtype"))) {
                    results.add(createCollectionFromIndex(qr, doc))
                } else if (IndexedTypes.INSTITUTION.toString().equalsIgnoreCase((String) doc.getFieldValue("idxtype"))) {
                    results.add(createInstitutionFromIndex(qr, doc))
                } else if (IndexedTypes.DATAPROVIDER.toString().equalsIgnoreCase((String) doc.getFieldValue("idxtype"))) {
                    results.add(createDataProviderFromIndex(qr, doc))
                } else if (IndexedTypes.DATASET.toString().equalsIgnoreCase((String) doc.getFieldValue("idxtype"))) {
                    results.add(createDatasetFromIndex(qr, doc))
                } else if (IndexedTypes.REGION.toString().equalsIgnoreCase((String) doc.getFieldValue("idxtype"))) {
                    results.add(createRegionFromIndex(qr, doc))
                } else if (IndexedTypes.WORDPRESS.toString().equalsIgnoreCase((String) doc.getFieldValue("idxtype"))) {
                    results.add(createWordpressFromIndex(qr, doc))
                } else if (IndexedTypes.LAYERS.toString().equalsIgnoreCase((String) doc.getFieldValue("idxtype"))) {
                    results.add(createLayerFromIndex(qr, doc))
                } else {
                    results.add(createSearchDTOFromIndex(qr, doc))
                }
            }
        } else {
            log.debug("No results for query.")
        }
        searchResults.searchResults = results
        // populate SOLR facet results
        if (facets != null) {
            for (FacetField facet : facets) {
                List<FacetField.Count> facetEntries = facet.getValues()
                if ((facetEntries != null) && (facetEntries.size() > 0)) {
                    ArrayList<FieldResult> r = new ArrayList<FieldResult>()
                    for (FacetField.Count fcount : facetEntries) {
                        r.add(new FieldResult(label: fcount.getName(), count: fcount.getCount()))
                    }
                    FacetResult fr = new FacetResult(fieldName: facet.getName(), fieldResult: r);
                    facetResults.add(fr);
                }
            }
        }
        searchResults.setFacetResults(facetResults);
        // The query result is stored in its original format so that all the information
        // returned is available later on if needed
        //searchResults.setQr(qr);
        return searchResults
    }

    /**
     * Helper method to create SolrQuery object and add facet settings
     *
     * @return solrQuery the SolrQuery
     */
    protected SolrQuery initSolrQuery(String[] facets) {
        SolrQuery solrQuery = new SolrQuery()
        solrQuery.setRequestHandler("standard")
        if (facets == null) {
            //use the default set
            solrQuery.facet = true
            solrQuery.addFacetField("idxtype")
            solrQuery.addFacetField("australian_s")
            solrQuery.addFacetField("speciesGroup")
            solrQuery.addFacetField("speciesSubgroup")
            //solrQuery.addFacetField("kingdom")
            solrQuery.addFacetField("rank")
            //solrQuery.addFacetField("rankId")
            //solrQuery.addFacetField("pestStatus")
            //        solrQuery.addFacetField("conservationStatus")
            //solrQuery.addFacetField("conservationStatusAUS")
            //solrQuery.addFacetField("conservationStatusACT")
            //solrQuery.addFacetField("conservationStatusNSW")
            //solrQuery.addFacetField("conservationStatusNT")
            //solrQuery.addFacetField("conservationStatusQLD")
            //solrQuery.addFacetField("conservationStatusSA")
            //solrQuery.addFacetField("conservationStatusTAS")
            //solrQuery.addFacetField("conservationStatusVIC")
            //solrQuery.addFacetField("conservationStatusWA")
            solrQuery.addFacetField("category_m_s")
            solrQuery.addFacetField("category_NSW_m_s")
            solrQuery.addFacetField("category_ACT_m_s")
            solrQuery.addFacetField("category_QLD_m_s")
            solrQuery.addFacetField("category_SA_m_s")
            solrQuery.addFacetField("category_NT_m_s")
            solrQuery.addFacetField("category_TAS_m_s")
            solrQuery.addFacetField("category_WA_m_s")
            solrQuery.addFacetField("category_VIC_m_s")
        } else {
            solrQuery.addFacetField(facets)
        }

        solrQuery.setFacetMinCount(1)
        solrQuery.setRows(10)
        solrQuery.setStart(0)

        //add highlights
        solrQuery.highlight = true
        solrQuery.highlightFragsize = 80
        solrQuery.highlightSnippets = 2
        solrQuery.highlightSimplePre = "<strong>"
        solrQuery.highlightSimplePost = "</strong>"
        solrQuery.add("hl.usePhraseHighlighter", "true")
        solrQuery.addHighlightField("commonName")
        solrQuery.addHighlightField("scientificName")
        solrQuery.addHighlightField("pestStatus")
        solrQuery.addHighlightField("conservationStatus")
        solrQuery.addHighlightField("simpleText")
        solrQuery.addHighlightField("content")

        return solrQuery;
    }

    private void addFqs(SolrQuery solrQuery, List<String> filterQuery) {
        if (filterQuery != null) {
            for (String fq : filterQuery) {
                // pull apart fq. E.g. Rank:species and then sanitize the string parts
                // so that special characters are escaped appropriately
                if (fq != null && !fq.isEmpty()) {
                    String[] parts = fq.split(":", 2); // separate query field from query text
                    log.debug("fq split into: " + parts.length + " parts: " + parts[0] + " & " + parts[1])
                    String prefix
                    String suffix
                    // don't escape range queries
                    if (parts[1].contains(" TO ")) {
                        prefix = parts[0]
                        suffix = parts[1]
                    } else if (parts[1].contains(" OR ") || (parts[1].startsWith("(") && parts[1].endsWith(")"))) {
                        prefix = parts[0]
                        suffix = parts[1]
                    } else {
                        prefix = parts[0].startsWith("-") ? "-" + ClientUtils.escapeQueryChars(parts[0].substring(1)) : ClientUtils.escapeQueryChars(parts[0])
                        suffix = ClientUtils.escapeQueryChars(parts[1])
                    }
                    solrQuery.addFilterQuery(prefix + ":" + suffix) // solrQuery.addFacetQuery(facetQuery)
                    log.debug("adding filter query: " + prefix + ":" + suffix)
                }
            }
        }
    }

    /// Search DTO factory methods

    /**
     * Populate a Collection from the data in the lucene index.
     *
     * @param doc
     * @return
     */
    private SearchCollectionDTO createCollectionFromIndex(QueryResponse qr, SolrDocument doc) {
        new SearchCollectionDTO(
                score: (Float) doc.getFirstValue("score"),
                idxType: IndexedTypes.COLLECTION.toString(),
                guid: (String) doc.getFirstValue("guid"),
                institutionName: (String) doc.getFirstValue("institutionName"),
                name: (String) doc.getFirstValue("name")
        )
    }

    /**
     * Populate a Collection from the data in the lucene index.
     *
     * @param doc
     * @return
     */
    private SearchRegionDTO createRegionFromIndex(QueryResponse qr, SolrDocument doc) {
        new SearchRegionDTO(
                score: (Float) doc.getFirstValue("score"),
                idxType: IndexedTypes.REGION.toString(),
                guid: (String) doc.getFirstValue("guid"),
                name: (String) doc.getFirstValue("name"),
                regionTypeName: (String) doc.getFirstValue("regionType"),
        )
    }

    /**
     * Populate a Collection from the data in the lucene index.
     *
     * @param doc
     * @return
     */
    private SearchInstitutionDTO createInstitutionFromIndex(QueryResponse qr, SolrDocument doc) {
        new SearchInstitutionDTO(
                score: (Float) doc.getFirstValue("score"),
                idxType: IndexedTypes.INSTITUTION.toString(),
                guid: (String) doc.getFirstValue("guid"),
                name: (String) doc.getFirstValue("name")
        )
    }

    /**
     * Populate a Wordpress from the data in the lucene index.
     *
     * @param doc
     * @return
     */
    private SearchWordpressDTO createWordpressFromIndex(QueryResponse qr, SolrDocument doc) {
        SearchWordpressDTO wordpress = new SearchWordpressDTO(
                idxType: IndexedTypes.WORDPRESS.toString(),
                score: (Float) doc.getFirstValue("score"),
                guid: (String) doc.getFirstValue("guid"),
                name: (String) doc.getFirstValue("name"),
        )

        // add SOLR generated highlights
        String id = (String) doc.getFirstValue("id")
        if (qr.getHighlighting() != null && qr.getHighlighting().get(id) != null) {
            log.debug("Got highlighting (" + id + "): " + qr.getHighlighting().get(id).size())
            Map<String, List<String>> highlightVal = qr.getHighlighting().get(id)
            for (Map.Entry<String, List<String>> entry : highlightVal.entrySet()) {
                List<String> newHls = new ArrayList<String>()

                for (String hl : entry.getValue()) {
                    // Strip leading punctuation twice (which SOLR tends to include)
                    String punctuation = ".,;:)]}>!?%-_"
                    String hl2 = StringUtils.stripStart(hl, punctuation)
                    hl2 = StringUtils.stripStart(hl2, punctuation)
                    newHls.add(hl2.trim() + " ...")
                }

                wordpress.setHighlight(StringUtils.join(newHls, "<br/>"))
            }
        }

        return wordpress;
    }

    /**
     * Populate a Dataset DTO from the data in the lucene index.
     *
     * @param doc
     * @return
     */
    private SearchDatasetDTO createDatasetFromIndex(QueryResponse qr, SolrDocument doc) {
        new SearchDatasetDTO(
                idxType: IndexedTypes.DATASET.toString(),
                score: (Float) doc.getFirstValue("score"),
                guid: (String) doc.getFirstValue("guid"),
                name: (String) doc.getFirstValue("name"),
                description: (String) doc.getFirstValue("description"),
                dataProviderName: (String) doc.getFirstValue("dataProviderName")
        )
    }

    private SearchLayerDTO createLayerFromIndex(QueryResponse qr, SolrDocument doc) {
        new SearchLayerDTO(
                idxType: IndexedTypes.LAYERS.toString(),
                score: (Float) doc.getFirstValue("score"),
                guid: (String) doc.getFirstValue("guid"),
                name: (String) doc.getFirstValue("name"),
                description: (String) doc.getFirstValue("description"),
                dataProviderName: (String) doc.getFirstValue("dataProviderName")
        )
    }


    private SearchDTO createSearchDTOFromIndex(QueryResponse qr, SolrDocument doc) {
        new SearchDTO(
                idxType: IndexedTypes.SEARCH.toString(),
                score: (Float) doc.getFirstValue("score"),
                guid: (String) doc.getFirstValue("guid"),
                name: (String) doc.getFirstValue("name"))
    }

    /**
     * Populate a Collection from the data in the lucene index.
     *
     * @param doc
     * @return
     */
    private SearchDataProviderDTO createDataProviderFromIndex(QueryResponse qr, SolrDocument doc) {
        new SearchDataProviderDTO(
                idxType: IndexedTypes.DATAPROVIDER.toString(),
                score: (Float) doc.getFirstValue("score"),
                guid: (String) doc.getFirstValue("guid"),
                name: (String) doc.getFirstValue("name"),
                description: (String) doc.getFirstValue("description"),
        )
    }

    /**
     * Populate a TaxonConcept from the data in the lucene index.
     *
     * @param doc
     * @return
     */
    private SearchTaxonConceptDTO createTaxonConceptFromIndex(QueryResponse qr, SolrDocument doc) {
        SearchTaxonConceptDTO taxonConcept = new SearchTaxonConceptDTO(
                idxType: IndexedTypes.TAXON.toString(),
                score: (Float) doc.getFirstValue("score"),
                guid: (String) doc.getFirstValue("guid"),
                parentGuid: (String) doc.getFirstValue("parentGuid"),
                name: (String) doc.getFirstValue("scientificNameRaw"),
                acceptedConceptName: (String) doc.getFirstValue("acceptedConceptName"),
                synonymRelationship: (String) doc.getFieldValue("synonymRelationship_s"),
                synonymDescription: (String) doc.getFirstValue("synonymDescription_s"),
                commonName: (String) doc.getFirstValue("commonNameDisplay"),
                commonNameSingle: (String) doc.getFirstValue("commonNameSingle"),
                image: (String) doc.getFirstValue("image"),
                imageSource: (String) doc.getFirstValue("imageSource"),
                imageCount: (Integer) doc.getFirstValue("imageCount"),
                thumbnail: (String) doc.getFirstValue("thumbnail"),
                rank: (String) doc.getFirstValue("rank"),
                rawRank: (String) doc.getFirstValue("rawRank"),
                left: (Integer) doc.getFirstValue("left"),
                right: (Integer) doc.getFirstValue("right"),
                kingdom: (String) doc.getFirstValue("kingdom"),
                author: (String) doc.getFirstValue("author"),
                nameComplete: (String) doc.getFirstValue("nameComplete"),
                conservationStatusAUS: (String) doc.getFirstValue("conservationStatusAUS"),
                conservationStatusACT: (String) doc.getFirstValue("conservationStatusACT"),
                conservationStatusNSW: (String) doc.getFirstValue("conservationStatusNSW"),
                conservationStatusNT: (String) doc.getFirstValue("conservationStatusNT"),
                conservationStatusQLD: (String) doc.getFirstValue("conservationStatusQLD"),
                conservationStatusSA: (String) doc.getFirstValue("conservationStatusSA"),
                conservationStatusTAS: (String) doc.getFirstValue("conservationStatusTAS"),
                conservationStatusVIC: (String) doc.getFirstValue("conservationStatusVIC"),
                conservationStatusWA: (String) doc.getFirstValue("conservationStatusWA"),
                isAustralian: (String) doc.getFirstValue("australian_s"),
                isExcluded: (Boolean) doc.getFirstValue("is_excluded_b"),
                phylum: (String) doc.getFirstValue("phylum"),
                classs: (String) doc.getFirstValue("class"),
                order: (String) doc.getFirstValue("bioOrder"),
                family: (String) doc.getFirstValue("family"),
                genus: (String) doc.getFirstValue("genus"),
                hasChildren: Boolean.parseBoolean((String) doc.getFirstValue("hasChildren"))
        )
        try {
            taxonConcept.linkIdentifier = URLEncoder.encode((String) doc.getFirstValue("linkIdentifier"), "UTF-8")
        } catch (Exception e) {
            taxonConcept.linkIdentifier = (String) doc.getFirstValue("linkIdentifier")
        }

        try {
            Integer rankId = (Integer) doc.getFirstValue("rankId");
            if (rankId != null) {
                taxonConcept.rankId = rankId
            }
        } catch (Exception ex) {
            log.error("Error parsing rankId: ${ex.message}", ex);
        }
        taxonConcept.pestStatus = (String) doc.getFirstValue(StatusType.PEST.toString())
        taxonConcept.conservationStatus = (String) doc.getFirstValue(StatusType.CONSERVATION.toString())

        // highlights
        if (qr.getHighlighting() != null && qr.getHighlighting().get(taxonConcept.getGuid()) != null) {

            Map<String, List<String>> highlightVal = qr.getHighlighting().get(taxonConcept.getGuid());
            for (Map.Entry<String, List<String>> entry : highlightVal.entrySet()) {

                taxonConcept.highlight = StringUtils.join(entry.getValue(), " ")
            }
        }
        return taxonConcept
    }

}
