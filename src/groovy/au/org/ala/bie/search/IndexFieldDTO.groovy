package au.org.ala.bie.search

import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString

@EqualsAndHashCode(includes = "name")
@ToString
public class IndexFieldDTO implements Comparable<IndexFieldDTO> {
    String name
    String dataType
    boolean indexed
    boolean stored
    Integer numberDistinctValues

    public int compareTo(IndexFieldDTO other) {
        return name.compareTo(other.name);
    }
}
