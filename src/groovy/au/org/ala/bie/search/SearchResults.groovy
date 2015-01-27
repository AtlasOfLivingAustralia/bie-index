package au.org.ala.bie.search

import javax.naming.directory.SearchResult


public class SearchResults {

    /** Maximum number of results returned from a query */
    long pageSize = 10;
    /** Current page of results (not currently used) */
    long startIndex = 0;
    /** Total number of results for the match (indept of resultsPerPage) */
    long totalRecords = 0;
    /** Field to sort results by */
    String sort;
    /** Direction to sort results by (asc || desc) */
    String dir = "asc";
    /** Status code to be set by Controller (e.g. OK) */
    String status;
    /** List of results from search */
    List<SearchResult> searchResults = []
    /** List of facet results from search */
    List<FacetResult> facetResults = []
    /** The query that was used */
    String query

}