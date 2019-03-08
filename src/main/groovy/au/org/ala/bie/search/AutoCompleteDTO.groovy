package au.org.ala.bie.search

import groovy.transform.AutoClone

/**
 * Created by mar759 on 21/07/15.
 */
@AutoClone
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
