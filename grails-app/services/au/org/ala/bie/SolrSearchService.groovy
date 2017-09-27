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
import org.apache.commons.lang.StringUtils
import org.apache.solr.client.solrj.SolrQuery
import org.apache.solr.client.solrj.SolrServerException
import org.apache.solr.client.solrj.response.FacetField
import org.apache.solr.client.solrj.response.QueryResponse
import org.apache.solr.common.SolrDocument
import org.apache.solr.common.SolrDocumentList
import org.apache.solr.common.params.HighlightParams
import org.gbif.api.exception.UnparsableException
import org.gbif.api.model.checklistbank.ParsedName
import org.gbif.nameparser.PhraseNameParser

import static org.apache.solr.client.solrj.util.ClientUtils.escapeQueryChars

/**
 * a search service that uses the solr client rather than construct URLs and parse responses manually
 */
class SolrSearchService {

    static transactional = false

    def grailsApplication
    def liveSolrClient
    def conservationListsSource

    SearchResultsDTO<SearchTaxonConceptDTO> findByScientificName(
            String query, List<String> filterQuery = [], Integer startIndex = 0,
            Integer pageSize = 10, String sortField = 'score', String sortDirection = 'asc',
            boolean exactInput = false, boolean includeVernacular = true) {
        try {
            // set the query

            StringBuffer queryString = new StringBuffer()
            if (query.contains(":") && !query.startsWith("urn")) {
                String[] bits = StringUtils.split(query, ":")
                queryString.append(escapeQueryChars(bits[0]))
                queryString.append(":")
                queryString.append(escapeQueryChars(bits[1]).toLowerCase())
            } else {

                String cleanQuery = escapeQueryChars(query).toLowerCase()
                if (exactInput) {
                    cleanQuery = "\"$cleanQuery\""
                }
                //queryString.append(ClientUtils.escapeQueryChars(query))
                queryString.append("idxtype:").append(IndexedTypes.TAXON)
                queryString.append(" AND (")
//                queryString.append("scientificNameText:"+cleanQuery)
                queryString.append("text:").append(cleanQuery) // TODO determine whether scientificNameText -> text is ok...
                if (includeVernacular) {
                    queryString.append(" OR commonName:").append(cleanQuery)
                }
                // TODO these synonym fields don't appear in current index
                //fix so that synonyms are not being included with a guid search
                queryString.append(" OR guid:").append(cleanQuery) //.append(" AND -syn_guid:*) OR syn_guid:").append(cleanQuery)

                String canonicalSciName = retrieveCanonicalForm(query);
                if (canonicalSciName != null) {
                    canonicalSciName = escapeQueryChars(canonicalSciName).toLowerCase();
                } else {
                    canonicalSciName = escapeQueryChars(query).toLowerCase();
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
        PhraseNameParser np = new PhraseNameParser()
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
        SolrQuery solrQuery = initSolrQuery(
                startIndex: startIndex,
                pageSize: pageSize,
                sortField: sortField,
                sortDirection: sortDirection,
                filterQuery: filterQuery,
                facets: facets).
                setQuery(queryString)

        QueryResponse qr = getSolrQueryResponse(solrQuery)
        return createSearchResultsFromQueryResponse(solrQuery, qr, pageSize, sortField, sortDirection)
    }

    private QueryResponse getSolrQueryResponse(SolrQuery solrQuery) throws SolrServerException {
        if (log.debugEnabled) {
            log.debug("About to execute ${solrQuery.query}")
        }
        // do the Solr search
        return liveSolrClient.query(solrQuery); // can throw exception
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

    static final DEFAULT_FACETS = [
            'idxtype'
            ,'australian_s'
            ,'speciesGroup'
            ,'speciesSubgroup'
//            ,'kingdom'
            ,'rank'
//            ,'rankId'
//            ,'pestStatus'
//            ,'conservationStatus'
//            ,'conservationStatusAUS'
//            ,'conservationStatusACT'
//            ,'conservationStatusNSW'
//            ,'conservationStatusNT'
//            ,'conservationStatusQLD'
//            ,'conservationStatusSA'
//            ,'conservationStatusTAS'
//            ,'conservationStatusVIC'
//            ,'conservationStatusWA'
            ,'category_m_s'
            ,'category_NSW_m_s'
            ,'category_ACT_m_s'
            ,'category_QLD_m_s'
            ,'category_SA_m_s'
            ,'category_NT_m_s'
            ,'category_TAS_m_s'
            ,'category_WA_m_s'
            ,'category_VIC_m_s'
    ] as String[]

    /**
     * Helper method to create SolrQuery object and add facet settings and default highlight settings
     *
     * @params startIndex The search results start index, defaults to 0
     * @params pageSize the search results page size, defaults to 10
     * @params sortField the search results sort field, defaults to 'score'
     * @params sortDirection the search results sort directions, defaults to 'asc'
     * @params filterQuery the filter queries to apply, defaults to [] (empty list)
     * @params fields the fields to return, defaults to ['*', 'score']
     * @params facets the facets to return, defaults to {@link SolrSearchService#DEFAULT_FACETS}
     * @return solrQuery the SolrQuery
     */
    protected static SolrQuery initSolrQuery(Map params) {
        Integer startIndex = params.startIndex ?: 0
        Integer pageSize = params.pageSize ?: 10
        String sortField = params.sortField ?: 'score'
        String sortDirection = params.sortDirection ?: 'asc'
        List<String> filterQuery = params.filterQuery ?: []
        List<String> fields = params.fields ?: ['*', 'score']
        String[] facets = params.facets ?: DEFAULT_FACETS

        return new SolrQuery()
                .setRequestHandler("standard")
                .setFacet(facets ? true : false)
                .addFacetField(facets)
                .setFacetMinCount(10)
                .setStart(startIndex)
                .setRows(pageSize)
                .setSort(new SolrQuery.SortClause(sortField, sortDirection))
                .setFilterQueries(*filterQuery)
                .setFields(*fields)
                .setHighlight(true)
                .setHighlightFragsize(80)
                .setHighlightSnippets(2)
                .setHighlightSimplePre('<strong>')
                .setHighlightSimplePost('</strong>')
                .addHighlightField('commonName')
                .addHighlightField('scientificName')
                .addHighlightField('pestStatus')
                .addHighlightField('conservationStatus')
                .addHighlightField('simpleText')
                .addHighlightField('content')
                .setParam(HighlightParams.USE_PHRASE_HIGHLIGHTER, 'true')

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
        def clists = conservationListsSource.lists ?: []
        def conservationStatus = clists.inject([:], { map, region ->
            def cs = (String) doc.getFirstValue(region.field)
            if (cs) map.put(region.term, cs)
            map
        })
        SearchTaxonConceptDTO taxonConcept = new SearchTaxonConceptDTO(
                idxType: IndexedTypes.TAXON.toString(),
                score: (Float) doc.getFirstValue("score"),
                guid: (String) doc.getFirstValue("guid"),
                parentGuid: (String) doc.getFirstValue("parentGuid"),
                name: (String) doc.getFirstValue("scientificName"),
                acceptedConceptName: (String) doc.getFirstValue("acceptedConceptName"),
                acceptedConceptGuid: (String) doc.getFirstValue("acceptedConceptID"),
                taxonomicStatus: (String) doc.getFieldValue("taxonomicStatus"),
                commonName: (String) doc.getFirstValue("commonName"),
                image: (String) doc.getFirstValue("image"),
                rank: (String) doc.getFirstValue("rank"),
                author: (String) doc.getFirstValue("scientificNameAuthorship"),
                nameComplete: (String) doc.getFirstValue("nameComplete"),
                conservationStatus: conservationStatus,
                kingdom: (String) doc.getFirstValue("rk_kingdom"),
                phylum: (String) doc.getFirstValue("rk_phylum"),
                classs: (String) doc.getFirstValue("rk_class"),
                order: (String) doc.getFirstValue("rk_order"),
                family: (String) doc.getFirstValue("rk_family"),
                genus: (String) doc.getFirstValue("rk_genus")
        )
        try {
            taxonConcept.linkIdentifier = URLEncoder.encode((String) doc.getFirstValue("linkIdentifier"), "UTF-8")
        } catch (Exception e) {
            taxonConcept.linkIdentifier = (String) doc.getFirstValue("linkIdentifier")
        }

        try {
            Integer rankId = (Integer) doc.getFirstValue("rankID");
            if (rankId != null) {
                taxonConcept.rankId = rankId
            }
        } catch (Exception ex) {
            log.error("Error parsing rankId: ${ex.message}", ex);
        }
        //taxonConcept.pestStatus = (String) doc.getFirstValue(StatusType.PEST.toString())
        //taxonConcept.conservationStatus = (String) doc.getFirstValue(StatusType.CONSERVATION.toString())

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
