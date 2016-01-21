package au.org.ala.bie.search

/**
 * A DTO used for returning search results.
 *
 * @author Dave Martin (David.Martin@csiro.au)
 */
public class SearchTaxonConceptDTO extends SearchDTO implements Comparable<SearchTaxonConceptDTO>{

    protected static String bieRepoUrl = "http://bie.ala.org.au/repo/";
    protected static String bieRepoDir = "/data/bie/";

    static {
        //check the properties file for an override
        try {
            Properties p = new Properties();
            InputStream inStream = Thread.currentThread().getContextClassLoader().getResourceAsStream("bie.properties");
            if (inStream) p.load(inStream);
            if(p.getProperty("bieRepo") != null)
                bieRepoUrl = p.getProperty("bieRepo");
            if(p.getProperty("bieRepoDir") != null)
                bieRepoDir = p.getProperty("bieRepoDir");
        } catch (Exception e){
            e.printStackTrace();
        }
    }

    String parentId;
    String parentGuid;
    String commonName;
    String nameComplete;
    String commonNameSingle;
    String acceptedConceptGuid;
    String acceptedConceptName;
    String synonymRelationship;
    String synonymDescription;
    boolean hasChildren;
    String rank;
    int rankId;
    String rawRank;
    String pestStatus;
    String conservationStatus;
    String conservationStatusAUS;
    String conservationStatusACT;
    String conservationStatusQLD;
    String conservationStatusNSW;
    String conservationStatusSA;
    String conservationStatusNT;
    String conservationStatusTAS;
    String conservationStatusVIC;
    String conservationStatusWA;
    String isAustralian;
    String highlight;
    String image;
    String thumbnail;
    Integer left;
    Integer right;
    String kingdom;
    String phylum;
    String classs;
    String order;
    String family;
    String genus;
    String author;
    String linkIdentifier;
    Integer occCount;
    String imageSource;
    Integer imageCount;
    Boolean isExcluded; //whether or not the taxon concept has been marked as excluded in Australia

    //image properties
    String imageUrl;
    String largeImageUrl;
    String smallImageUrl;
    String thumbnailUrl;
    String imageMetadataUrl;

    void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    void setLargeImageUrl(String largeImageUrl) {
        this.largeImageUrl = largeImageUrl;
    }

    void setSmallImageUrl(String smallImageUrl) {
        this.smallImageUrl = smallImageUrl;
    }

    void setThumbnailUrl(String thumbnailUrl) {
        this.thumbnailUrl = thumbnailUrl;
    }

    void setImageMetadataUrl(String imageMetadataUrl) {
        this.imageMetadataUrl = imageMetadataUrl;
    }

    public String getImageUrl(){
        if(imageUrl == null){
            if(image != null && image.startsWith(bieRepoDir)){
                imageUrl= image.replace(bieRepoDir, bieRepoUrl);
            }
        }
        return imageUrl;
    }

    private static String imageUrl(String image, String imageFormatName){
        if(image !=null){
            if(image.startsWith(bieRepoDir)){
                image = image.replace(bieRepoDir, bieRepoUrl);
            }
            return image.replace("/raw.", imageFormatName);
        } else {
            return null;
        }
    }

    public String getLargeImageUrl(){
        this.largeImageUrl = imageUrl(image, "/largeRaw.");
        return this.largeImageUrl;
    }

    public String getSmallImageUrl(){
        this.smallImageUrl = imageUrl(image, "/smallRaw.");
        return smallImageUrl;
    }

    public String getThumbnailUrl(){
        this.thumbnailUrl = imageUrl(image, "/thumbnail.");
        return thumbnailUrl;
    }

    public String getImageMetadataUrl(){
        if(imageMetadataUrl ==null){
            if(image != null && image.startsWith(bieRepoDir)){
                image =  image.replace(bieRepoDir, bieRepoUrl);
            }
            if(image !=null){
                this.imageMetadataUrl = image.substring(0, image.lastIndexOf('/') +1) + "dc";
            }
        }
        return this.imageMetadataUrl;
    }

    /**
     * @see java.lang.Comparable#compareTo(java.lang.Object)
     */
    @Override
    public int compareTo(SearchTaxonConceptDTO o) {
        if(o.getName()!=null && this.name!=null){
            return this.name.compareTo(o.getName());
        }
        return 0;
    }

}