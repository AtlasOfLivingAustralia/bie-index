package au.org.ala.bie.search

import org.gbif.dwc.terms.Term

/**
 * Created by mar759 on 7/11/15.
 */
public enum BIETerms implements Term {

    habitatID("Habitat"),
    parentHabitatID("Habitat"),
    habitatName("Habitat");

    private final String groupName;
    public final String[] normAlts;
    static final String[] PREFIXES;
    public static final String[] GROUPS;

    private BIETerms(String groupName, String... alternatives) {
        this.groupName = groupName;
        this.normAlts = alternatives;
    }

    public String qualifiedName() {
        return  "http://ala.org.au/terms/1.0/" + this.simpleName();
    }

    public String simpleName() {
        return this.name();
    }

    public String[] alternativeNames() {
        return this.normAlts;
    }

    public String getGroup() {
        return this.groupName;
    }

    public String toString() {
        return "ala:" + this.name();
    }

    public boolean isClass() {
        return Character.isUpperCase(this.simpleName().charAt(0));
    }



    static {
        PREFIXES = ["http://ala.org.au/terms/1.0/", "ala:" ] ;
        GROUPS = [
            "HABITAT"
        ];
    }
}