package au.org.ala.bie.search

/**
 * Created by mar759 on 21/07/15.
 */
class AutoCompleteDTO {
    String guid
    String name
    Integer occurrenceCount = 0
    Integer georeferencedCount = 0
    List scientificNameMatches = []
    List commonNameMatches = []
    String commonName
    List matchedNames = []
    Integer rankID =  -1
    String rankString
}
