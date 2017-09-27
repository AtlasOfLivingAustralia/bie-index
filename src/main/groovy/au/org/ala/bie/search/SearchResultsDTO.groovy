package au.org.ala.bie.search

/**
 * DTO to represents the results from a Lucene search
 *
 * @author "Nick dos Remedios <Nick.dosRemedios@csiro.au>"
 */
public class SearchResultsDTO<T extends SearchDTO> {

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
    List<T> searchResults = new ArrayList<T>();
    /** List of facet results from search */
    Collection<FacetResult> facetResults = new ArrayList<FacetResult>();
//    /** SOLR query response following search */
    //QueryResponse qr;
    /** The query that was used */
    String query;

//    @JsonIgnore
//    public long getCurrentPage() {
//        if(pageSize>0){
//            return this.startIndex/pageSize;
//        }
//        return -1;
//    }

}
