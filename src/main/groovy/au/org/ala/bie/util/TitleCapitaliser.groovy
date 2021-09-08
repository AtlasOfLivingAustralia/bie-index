package au.org.ala.bie.util

/**
 * A title capitaliser.
 * <p>
 * Titles are capitalised according to the following rules:
 * </p>
 * <ol>
 *     <li>Certain initial letters with an apostrophe (d', O') are replaced with the correct case and the following letter capitalised</li>
 *     <li>The first and last words are always capitalised</li>
 *     <li>Conjunctions (and, or, nor, but, for) are lower case</li>
 *     <li>Articles (a, an, the) are lower case</li>
 *     <li>Prepositions (to, from, by, on, at) are lower case</li>
 * </ul>
 * <p>
 * Capitalisation capitalises anything that occurs after a punctuation symbol.
 * So 'a.j.p.' becomes 'A.J.P.' and 'indo-pacific' becomes 'Indo-Pacific'
 * <p>
 * The terms used for articles, etc. are language-specific and can be specified in the <code>messages</code>
 * resource bundle.
 * </p>
 * <p>
 * Constructing a capitaliser can be a little expensive, so the
 * {@link #create} factory method can be used to get a capitaliser for a language.
 * </p>
 *
 * @author Doug Palmer &lt;Doug.Palmer@csiro.au&gt;
 * @copyright Copyright &copy; 2017 Atlas of Living Australia
 */
class TitleCapitaliser {
    /** The resource bundle to use for word lookups */
    static RESOURCE_BUNDLE = "messages"
    /** The resource bundle entry for conjunctions */
    static CONJUNCTION_RESOURCE = "title.conjunctions"
    /** The resource bundle entry for articless */
    static ARTICLE_RESOURCE = "title.articles"
    /** The resource bundle entry for initial */
    static INITIALS_RESOURCE = "title.initials"
    /** The resource bundle entry for prepositions */
    static PREPOSITION_RESOURCE = "title.prepositions"
    /** The action to take */
    private static ACTION_CAPITALISE = 1
    private static ACTION_LOWERCASE = 2
    private static ACTION_ASIS = 3
    /** Patterns */
    private static LETTER_APOSTROPHE = /\p{L}'\p{L}.*/

    /** The capitaliser list */
    private static capitializers = [:]

    Locale locale
    Set<String> lowercase
    Set<String> initials

    /**
     * Construct a capitaliser for a locale.
     *
     * @param locale The locale
     */
    TitleCapitaliser(Locale locale) {
        this.locale = locale
        ResourceBundle rb = ResourceBundle.getBundle(RESOURCE_BUNDLE, locale)
        this.lowercase = (rb.getString(CONJUNCTION_RESOURCE).split(',').collect { it.trim().toLowerCase() })
        this.lowercase.addAll(rb.getString(ARTICLE_RESOURCE).split(',').collect { it.trim().toLowerCase() }) as Set
        this.lowercase.addAll(rb.getString(PREPOSITION_RESOURCE).split(',').collect { it.trim().toLowerCase() }) as Set
        this.initials = (rb.getString(INITIALS_RESOURCE).split(',').collect { it.trim() }) as Set
    }

    /**
     * Construct a capitaliser for a language
     *
     * @param language The language (must map onto a locale)
     */
    TitleCapitaliser(String language) {
        this(Locale.forLanguageTag(language))
    }

    /**
     * Capitalise a title according to the capitaliser rules.
     *
     * @param title The title
     *
     * @return The capitalised title
     */
    String capitalise(String title) {
        List<String> tokens = title.split('\\s+')
        StringBuffer capitalised = new StringBuffer(title.length())
        for (int i = 0; i < tokens.size(); i++) {
            String token = tokens.get(i)
            String lc = token.toLowerCase()
            int action = ACTION_CAPITALISE
            if (token ==~ LETTER_APOSTROPHE) {
                String pre = token.substring(0, 2)
                String puc = pre.toUpperCase()
                String plc = pre.toLowerCase()
                boolean cap = true
                if (this.initials.contains(puc))
                    pre = puc
                else if (this.initials.contains(plc))
                    pre = plc
                else
                    cap = false
                if (cap) {
                    token = pre + token.substring(2, 3).toUpperCase() + token.substring(3)
                    action = ACTION_ASIS
                }
            } else if (i == 0 || i == tokens.size() - 1)
                action = ACTION_CAPITALISE
            else if (this.lowercase.contains(lc))
                action = ACTION_LOWERCASE
            if (capitalised.length() > 0)
                capitalised.append(' ')
            switch (action) {
                case ACTION_CAPITALISE:
                    boolean cap = true
                    for (int j = 0; j < token.length(); j++) {
                        int ch = token.codePointAt(j)
                        capitalised.appendCodePoint(cap ? Character.toUpperCase(ch) : Character.toLowerCase(ch))
                        cap = !Character.isLetterOrDigit(ch) && ch != 0x27 // 0x27 == '
                    }
                    break
                case ACTION_LOWERCASE:
                    capitalised.append(token.toLowerCase())
                    break
                default:
                    capitalised.append(token)
            }
        }
        return capitalised.toString()
    }

    /**
     * Create a capitaliser.
     *
     * @param lang The language code
     *
     * @return The capitaliser
     */
    synchronized static TitleCapitaliser create(String lang) {
        TitleCapitaliser capitaliser = capitializers.get(lang)
        if (!capitaliser) {
            capitaliser = new TitleCapitaliser(lang)
            capitializers.put(lang, capitaliser)
        }
        return capitaliser
    }
}
