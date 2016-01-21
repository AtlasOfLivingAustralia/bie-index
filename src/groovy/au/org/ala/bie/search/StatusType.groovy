package au.org.ala.bie.search

/**
 * Enum class to store and retrieve status types
 *
 * @author "Nick dos Remedios <Nick.dosRemedios@csiro.au>"
 */
public enum StatusType {
    CONSERVATION("conservationStatus", "Conservation Status"),
    PEST("pestStatus", "Pest Status");

    private String value;
    private String displayName;

    private StatusType(String value, String name) {
        this.value = value;
        this.displayName = name;
    }

    /**
     * Allow reverse-lookup
     * (based on <a href="http://www.ajaxonomy.com/2007/java/making-the-most-of-java-50-enum-tricks">Enum Tricks</a>)
     */
    private static final Map<String,StatusType> statusTypeLookup = new HashMap<String,StatusType>();

    static {
        for (StatusType st : EnumSet.allOf(StatusType.class)) {
            statusTypeLookup.put(st.getValue(), st);
        }
    }

    /**
     * Lookup method for status type string
     *
     * @param statusType
     * @return StatusType the StatusType
     */
    public static StatusType getForStatusType(String statusType) {
        return statusTypeLookup.get(statusType);
    }

    @Override
    public String toString() {
        return this.value;
    }

    public String getValue() {
        return value;
    }

    public String getDisplayName() {
        return displayName;
    }
}