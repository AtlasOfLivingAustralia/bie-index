# Conventions

How we interpret various things when building the name indexes.

## Darwin Core

The base vocabulary is the [Darwin Core](http://rs.tdwg.org/dwc/) vocabulary (DwC for short).
This vocabulary is extended by a few terms from [Dublin Core Terms](http://dublincore.org/documents/dcmi-terms/) 
(DC Terms) and a few other ALA-specific terms.

An individual source of names is packaged into a [Darwin Core Archive](http://www.gbif.org/resource/80636) (DwCA).
This is a zip file containing a number of table files, a schema metafile `meta.xml` andan optional metadata file `eml.xml`.
The table files can be any separated-values form.
One table file is the core table, containg the main list of items.
Extension files contain additional one-to-many items linked to the core table by a common identifier.
There are a number of [registered extension types](http://tools.gbif.org/dwca-validator/extensions.do) 
The metafile is an XML document describing the columns in each table file and the relationship between the tables.
The metadata file is an XML document using the 
[Ecological Metadata Language](http://www.dcc.ac.uk/resources/metadata-standards/eml-ecological-metadata-language)
that can be used to describe the contents of the file.


## Taxon Identifiers, Name Identifiers and Taxonomic Concept Identitfiers

Some vocabulary:

* A *scientific name* is the name, including authorship.
* A *taxon* is a group of organisms that share some sort of common characteritics.
A taxon can have several names, one of which is the *valid* or *accepted* name.
* A *synonym* is a name applied to a taxon that now goes by a different name.
  Note that what the ALA regards as a synonym is somewhat looser than the use of the term in
  the taxonomic community; all the ALA wants is to be able to say "this is now referred to as that"
  so that supplied names and searches can be redirected.
* A *taxonomic concept* is the placement of a particular taxon in the taxonomic tree in relation other taxa.

The common identifier for a taxonomic DwCA is the [taxonID](http://rs.tdwg.org/dwc/terms/index.htm#taxonID).

### Use of LSIDs

[Life Sciences Identifiers](http://www.omg.org/cgi-bin/doc?dtc/04-05-01) are resolvable [URNs](https://en.wikipedia.org/wiki/Uniform_Resource_Name) that can be used to identify a taxon, name or taxon concept. An LSID has the form *urn:lsid:&lt;authority&gt;:&lt;namespace&gt;:&lt;id&gt;* for example *urn:lsid:biodiversity.org.au:afd.taxon:bcd1b9ea-1cc1-4ab5-a15c-bbca5a987fb2* is a reference to an AFD taxon controlled by biodiversity.org.au. LSIDs can be resolved by going to the service at http://lsid.tdwg.org/

LSIDs are preferred for identifiers, where possible, because they allow links to further information about the taxon.
However, many sources use URLs, instead, for identifiers; these are also perfectly acceptable.

## Taxonomic Status

Taxonomic status indicates whether a name or taxon is currently accepted, a synonym or of some other, murkier status.

Taxonomic status is drawn from the [following vocabulary](http://terms.tdwg.org/wiki/dwc:taxonomicStatus). 
Some terms in the vocabulary are *deterministic* in the sense that you can directly go from that taxon
to the currently accepted taxon.
Some terms are *non-derterministic* in the sense that there are two or more possible accepted taxa.
Non-deterministic taxa represent a problem.
An occurrence record only has one place for a taxonID and it is not possible to somehow split the
identification into two parts.

| Term | deterministic | Description |
| ---- | ------------- | ----------- |
| accepted | yes | The currently accepted taxon. The zoological term valid is not use, largely so that "accepted" in DwC terms make sense. |
| synonym | yes? | An unspecified synonym. By default, this is treated as deterministic, since we dont have any reason to suppose it's not. |
| homotypicSynonym | yes | A nomenclatural synonym, meaning that the same taxon has gone under a different name. This can occur when two people describe the same species. |
| objectiveSynonym | yes | The zoological term for a nomenclatural synonym. The two terms are kept since they should be displayed in different forms  |
| heterotypicSynonym | yes | A taxonomic synonym, meaning that a species that was originally considered to be separate has been lumped into another species. The zoological term is subjective synonym, since whether they are synonymns or not is a matter of opinion. |
| subjectiveSynonym | yes | A taxonomic synonym, meaning that a species that was originally considered to be separate has been lumped into another species. The zoological term is subjective synonym, since whether they are synonymns or not is a matter of opinion. |
| proParteSynonym | no | A synonym where part of an original taxon has been divided. This means that the original name may still be in use or may have been mapped onto several other taxa. Here be dragons. |
| incertaeSedis | yes | A name of uncertain placement. This is generally treated as an accepted name with the placement supplied. |
| speciesInquirena | yes | A species under enquiry. This is generally treated as an accepted name with the placement supplied. |
| misapplied | no | A name incorrectly applied in a publication to a different species. However, the name itself is perfectly valid and has its own taxon. |
| excluded | no | A name that shouldn't be used, since it refers to something not found in the region of the occurrence record. |
| miscellaneousLiterature | no | A name that occurs in other literature. This will now be converted to a vernacular name, since it's not really a proper name for the|
| invalid | no | A name that should not be. |
| inferredAccepted | yes | A name treated as an accepted taxon by the processing software. Inferred markers indicate that the information has been deduced by the processing sofware, rather than explicitly supplied. |
| inferredSynonym | no | A name treated as a synonym by the processing software |
| inferredExcluded | no | A name treated as excluded by the processing software |
| inferredInvalid | no | A name treated as invalid by the processing software |

Informationm about the type of name is placed in the nomenclaturalStatus term.

## <a name="taxon-rank"/> Taxon Rank

Taxonomic ranks give the level of the taxon in the taxonomic tree.
The taxonomic ranks used are

Rank | Banch | Other Names | ALA Term | Notes |
---- | ----- | ----------- | -------- | ----- |
Root | | | | Not directly used |
Domain | | Superregnum, Superkingdom, Regio | superkingdom | |
*Kingdom* | | Regnum | kingdom | | 
Subkingdom | | Subregnum | subkingdom | |
Superphylum | | | superphylum | |
*Phylum* | | Division (botany), Divisio (botany) | phylum | |
Subphylum | | Subdivision (botany), Subdivisio (botany) | subplylum | |
Superclass | | Superclassis | superclass | |
*Class* | | Classis | class | |
Subclass | | Subclassis | subclass | |
Infraclass | | Infraclassis | infraclass | |
Superdivision | zoology | | superdivison zoology | |
Division | zoology | | division zoology | |
Subdivision | zoology | | subdivision zoology | |
Supercohort | zoology | | supercohort | |
Cohort | zoology | | cohort | |
Subcohort | zoology | | subcohort | |
Superorder | | Superordo | superorder | |
*Order* | | Ordo | order | |
Suborder | | Subordo | suborder | |
Infraorder | | Infraordo | infraorder | |
Parvorder | | | parvorder | |
Superseries | zoology | | superseries zoology | |
Series | zoology | | series zoology | |
Subseries | zoology | | subseries zoology | |
Supersection | zoology | Supersection | supersection zoology | |
Section | zooology | | section zoology | |
Subsection | zoology | | subsection zoology | |
Superfamily | | Superfamilia | superfamily | |
*Family* | | Familia | family | |
Subfamily | | Subfamilia | subfamily | | 
Supertribe | | Supertribus | supertribe | |
Tribe | | Tribus | tribe | |
Subtribe | | Subtribus | subtribe | |
Supergenus | | | supergenus |
Aggregate Genera | | | genus group | A named collection of closely related genera |
*Genus* | | | genus | |
Nothogenus | | | nothogenus | A genus denoting a hybrid or cross |
Subgenus | | | subgenus | |
Supersection | botany | Supersectio | supersection botany | |
Section | botany | Sectio | section botany | |
Subsection | botany | Subsectio | subsection botany | |
Superseries | botany | | superseries botany | |
Series | botany | | series botany | |
Subseries | botany | | subseries botany |
Aggregate Species | | species group | A named collection of closely related species |
Superspecies | | | superspecies | |
Species Subgroup | | species subgroup | A named collection of very closely related species |
*Species* | | | species | |
Nothospecies | | | nothospecies | A hybrid formed by directly crossing two species |
Subspecies | | | subspecies | |
Nothosubspecies | | | nothosubspecies | A hybrid formed by directly crossing two subspecies |
Infraspecies | zoology | | infraspecies | |
Variety | | Varietas | variety | |
Nothovariety | | Nothovarietas | nothovariety | A hybrid formed by directly crossing two varieties |
Subvariety | | Subvarietas | subvariety | |
Form | | | forma | |
Nothoform | | Nothoforma | nothoform | A hybrid formed by directly crossing two forms |
Subform | | Subforma | subform | |
Biovar | | | biovar | A variant prokaryotic strain that differs physiologically and/or biochemically from other strains in a particular species |
Serovar | | | biovar | A variant prokaryotic strain that has antigenic properties that differ from other strains. |
Cultivar | | | cultivar | A plant or grouping of plants selected for desirable characteristics that can be maintained by propagation |
Pathovar | | | pathovar | A bacterial strain or set of strains with the same or similar characteristics, that is differentiated at infrasubspecific level from other strains of the same species or subspecies on the basis of distinctive pathogenicity to one or more plant hosts. |
Infraspecific | | | infraspecific | |
Abberation | | | abberation | A chromosome abnormality |
Mutation| | | mutation |  A chromosome abnormality |
Race | | | race | |
Confersubspecies | | | confersubspecies | This only seems to appear in the GBIF ranks |
Formaspecialis | | | formaspecialis | A parasite adapted to a specific host | 
Hybrid | | | hybrid | |
Breed | | | breed | |
**Un-positioned Ranks** ||||
Incertae Sedis | | | incertae sedis | Of uncertain placement |
Species Inquirenda | | | species inquirenda  | A species of doubtful identity, requiring further investigation |
Higher Taxon | | | higher taxon | Generic higher taxon |
Unranked | | | unknown | |



## ALA Term Conventions

To avoid too much parsing back and forth, the following conventions are used for terms:

* **scientificName** Contains the name only, without authorship details. This convention is chosen to avoid parsing
difficulties and ending up with the author mysteriously repeated ad-nauseum when things don't quite match.
* **scientificNameAuthorship** Contains the authors names, without parentheses, quotation marks or ballet shoes.
Keeping with the conventions, faunal authorship is *author, year* and floral (and other) authorship is just *author*.
We do not used parenthesised names, since it makes name matching somewhat difficult; this may need to change.
* **nameComplete** A non-DwC term used to contain the correct combination of scientific name and author.
In some cases, the author may not be tacked onto the end, for example *Senecio productus subsp. productus* with
author *I.Thomps.* has a nameComplete of *Senecio productus I.Thomps. subsp. productus* This is because the
species ended up being revised.
* **nameFormatted** A non-DwC term used to contain the correct combination of scientific name and author,
formatted with HTML span elements and CSS classes.
This term can be used to ensure that names are correctly presented 
* **namePublishedIn** If assembled from indivudal reference fields, then the resulting reference roughly follows
the APA reference style.
* **dcterms:source** Contains a resolvable URL that provides a specific linked-data source for the name

## ALA Taxon DwCA

The ALA taxon DwCA is structured around taxon concepts.
The basic list of taxa provides the accepted name for a taxon and provides its position in the ALA taxononmic tree.
Synonyms, vernacular names, identifiers etc. all point back to the core taxon concept.
The ALA version of the taxon datafile consists of the following parts:

| File | Type | Required | Description |
| ---- | ---- | -------- | ----------- |
| taxon.csv taxon.txt | core | yes | The scientific taxonomic information |
| taxonvariant.txt | extension | no | Alternative sources of information on a specific taxon |
| vernacularNames.csv vernacularname.txt | extension | no | Additional vernacular names for each taxon |
| identifiers.csv identifier.txt | extension | no | Additional identifiers for each taxon (via some other controlled vocabulary) |
| rightsHolder.csv rightsholder.txt | extension | no | Licence owners for various data sources |
| meta.xml | schema | yes | The file and column descriptions |
| eml.xml | metadata | no | The what-who-why-when |

Files with a .csv extension are comma-separated values (UTF-8, escape character of \ and enclosing quotes of ").
Files with a .txt extension are tab-separated values (UTF-8, enclosing quotes of " and doubled quotes to escape).

### taxon.csv and taxonvariant.csv

The produced taxon.csv contains scientific names (accepted, synonyms and odd fish like excluded) and information about the
taxonomic tree.
The taxonomic variant file contains the information from different sources that was used to make a specific taxon.
It can contain the following columns

| Term | Essential (Required column for processing) | Required (Must not be empty) | Usage |
| ---- | --------- | -------- | ----- |
| [taxonID](http://rs.tdwg.org/dwc/terms/taxonID) | yes | yes | The main taxon identifier. If not supplied, this is the taxon concept ID for a taxon and the scientific name ID for a synonym |
| [parentNameUsageID](http://rs.tdwg.org/dwc/terms/parentNameUsageID) | yes | no | If this is an accepted name then the taxonID of the parent in the taxonomic tree. Empty if a synonym. |
| [acceptedNameUsageID](http://rs.tdwg.org/dwc/terms/acceptedNameUsageID) | yes | no | If this is an synonym then the taxonID of the accepted name. Empty if an accepted name. |
| [nomenclaturalCode](http://rs.tdwg.org/dwc/terms/nomenclaturalCode) | yes | yes | The nomenclatural code the name was published under. Names from a single nomenclatural code should be unique. |
| [scientificName](http://rs.tdwg.org/dwc/terms/scientificName) | yes | yes | The accepted taxon name or synonym |
| [scientificNameAuthorship](http://rs.tdwg.org/dwc/terms/scientificNameAuthorship) | yes | yes | The accepted taxon author |
| [taxonomicStatus](http://rs.tdwg.org/dwc/terms/taxonomicStatus) | no | no | Information about how the taxon should be treated.  |
| [taxonRank](http://rs.tdwg.org/dwc/terms/scientificNameAuthorship) | yes | yes | If this is an accepted name then the taxonID of the parent in the taxonomic tree. Empty if a synonym. |
| [datasetID](http://rs.tdwg.org/dwc/terms/datasetID) | no | no | The UID of the source collectory dataset for this taxon |
| [taxonConceptID](http://rs.tdwg.org/dwc/terms/taxonConceptID) | no | no | The taxon concept ID that this taxon maps onto.  |
| [scientificNameID](http://rs.tdwg.org/dwc/terms/scientficNameID) | no | no | The scientific name ID that this name maps onto.  |
| [nomenclaturalStatus](http://rs.tdwg.org/dwc/terms/nomenclaturalStatus) | no | no | Information about how the name should be treated. This term does not draw from an established vocabulary but contains any information about the name from the source. |
| [nameComplete](http://ala.org.au/terms/1.0/nameComplete) | no | no | The name with authorship information in the correct position  |
| [nameFormatted](http://ala.org.au/terms/1.0/nameComplete) | no | no | The name with authorship information in the correct position, formatted with HTML/CSS spans  |
| [nameAccordingToID](http://rs.tdwg.org/dwc/terms/nameAccordingToID) | no | no | A unique ID referencing the person who placed the name in this location |
| [nameAccordingTo](http://rs.tdwg.org/dwc/terms/nameAcordingTo) | no | no | The person who placed the name in this location. This may be different to the publisher of the name. |
| [namePublishedInID](http://rs.tdwg.org/dwc/terms/namePublishedInID) | no | no | A unique ID, preferably a DOI, referencing the publication of the name |
| [namePublishedIn](http://rs.tdwg.org/dwc/terms/namePublishedIn) | no | no | A reference to the publication of the name  |
| [namePublishedInYear](http://rs.tdwg.org/dwc/terms/namePublishedInYear) | no | no | The year of publication. Note that if this is not included there may be no indication of when the name was published, since the authorship column may not contain a year  |
| [taxonRemarks](http://rs.tdwg.org/dwc/terms/taxonRemarks) | no | no | Any additional remarks  |
| [source](http://purl.org/dc/terms/language) | no | no | An optional linked-data URL for the name  |
| [establishmentMeans](http://rs.tdwg.org/dwc/terms/establishmentMeans) | no | no | Hints on whether the taxon is native or introduced, if available |
| [datasetName](http://rs.tdwg.org/dwc/terms/datasetName) | no | no | The name of the source dataset |
| [priority](http://ala.org.au/terms/1.0/priority) | no | no | A score giving the relative priority of the variant |
| [verbatimNomenclaturalCode](http://ala.org.au/terms/1.0/verbatimNomenclaturalCode) | no | no | The supplied nomenclaural code |
| [verbatimTaxonomicStatus](http://ala.org.au/terms/1.0/verbatimTaxonomicStatus) | no | no | The supplied taxonomic status |
| [verbatimNomenclaturalStatus](http://ala.org.au/terms/1.0/verbatimNomenclaturalStatus) | no | no | The supplied nomenclaural status |
| [provenance](http://purl.org/dc/terms/provenance) | no | no | Information about how a taxon has been processed or modified. Multiple provenance statements are separated by vertical bars  |


senior- or self-synonyms are not included in taxon.csv.
Zoological convention is to regard the current name as a senior synonym.
However, this just doubles the index to no purpose.

A source may have a taxon ID (the current information about the taxon) 
a taxon concept ID (the more stable representation of the taxon) 
and a scientific nane ID (the stable name for the taxon).

### vernacularNames.csv

If present, the vernacular names file contains common names for the taxa in taxon.csv, keyed by taxonID.
It is patterned after the []http://rs.gbif.org/terms/1.0/VernacularName](http://rs.gbif.org/extension/gbif/1.0/vernacularname.xml) row type.
It contains the following columns

| Term | Required | Usage |
| ---- | -------- | ----- |
| [taxonID](http://rs.tdwg.org/dwc/terms/taxonID) | yes | The reference link to the taxon in taxon.csv |
| [vernacularName](http://rs.tdwg.org/dwc/terms/vernacularName) | yes | The actual common name |
| [datasetID](http://rs.tdwg.org/dwc/terms/datasetID) | no | The source collectory dataset for this name |
| [nameID](http://ala.org.au/terms/1.0/nameID) | no | A unique identifier for the vernacular name |
| [language](http://purl.org/dc/terms/language) | no | The language of the common name  |
| [status](http://ala.org.au/terms/1.0/status) | no | The relative importance of the name |
| [source](http://purl.org/dc/terms/language) | no | no | An optional linked-data URL for the name  |
| [datasetName](http://rs.tdwg.org/dwc/terms/datasetName) | no | no | The name of the source dataset |
| [temporal](http://purl.org/dc/terms/temporal) | no | Any temporal restructions.  |
| [locationID](http://rs.tdwg.org/dwc/terms/locationID) | no | The ID of the location where this name is used |
| [locality](http://rs.tdwg.org/dwc/terms/locality) | no | The locality where this name is used |
| [countryCode](http://rs.tdwg.org/dwc/terms/countryCode) | no | The country where this name is used |
| [sex](http://rs.tdwg.org/dwc/terms/sex) | no | The sex of the organism to which this name applies |
| [lifeStage](http://rs.tdwg.org/dwc/terms/lifeStage) | no | The life stage (adult, young, larvae, etc.) of the organism to which this name applies |
| [isPlural](http://rs.gbif.org/terms/1.0/isPlural) | no | Is this the plural form of a name (eg. cattle) |
| [isPreferredName](http://rs.gbif.org/terms/1.0/isPreferredName) | input only | Mapped onto the status vocabulary |
| [organismPart](http://rs.gbif.org/terms/1.0/organismPart) | no | The part of the organism this name applies to (eg tail) |
| [labels](http://ala.org.au/terms/1.0/labels) | no | Context labels, separated by a vertical bar. See http://localcontexts.org/  |
| [taxonRemarks](http://rs.tdwg.org/dwc/terms/taxonRemarks) | no | Any additional remarks. Multiple remarks are separated by vertical bars  |
| [provenance](http://purl.org/dc/terms/provenance) | no | Information about how a name has been processed or modified. Multiple provenance statements are separated by vertical bars  |

Note that a vernacular name, eg. "Blue Gum", can be linked to any number of scientific names.

Some vernacular names are more equal than others.
The vernacular name status is a hint as to whether there is some sort of preferred common name for a species.
It uses the following vocabulary (highest priority first):

| Term | Description |
| ---- | ----------- |
| legislated | The name appears in legislation as a name for the species. This might include "scientific" names that are no longer considered valid but which are lying about in legislation??? |
| standard | The name appears in a standards document as a standardised common name |
| preferred | A preferred common name for some reason |
| common | A widely used common name |
| traditionalKnowledge | A name used by traditional knowledge holders. These names may carry additional restrictions, identified by the labels term. |
| local | A locally used common name |

### identfiers.csv

If present, the identifiers file contains additional identifiers for taxa other than the taxonID actually used.
These may be references to other labelling vocabularies, such as CAAB codes.
It is patterned after the http://rs.gbif.org/terms/1.0/Identifier row type.
It contains the following columns

| Term | Required | Usage |
| ---- | -------- | ----- |
| [taxonID](http://rs.tdwg.org/dwc/terms/taxonID) | yes | The reference link to the taxon in taxon.csv |
| [identifier](http://purl.org/dc/terms/identifier) | yes | The additional identifier  |
| [title](http://purl.org/dc/terms/title) | no | A human-readable title for the identifier, eg. "CAAB Number"  |
| [subject](http://purl.org/dc/terms/subject) | no | Information on how the identifier is to be used  |
| [format](http://purl.org/dc/terms/format) | no | A term referencing how the identifier is to be interpreted, eg. "LSID" |
| [datasetID](http://rs.tdwg.org/dwc/terms/datasetID) | no | The source collectory dataset for this identifier |
| [source](http://purl.org/dc/terms/source) | no | The linked-data source of the identifier  |
| [status](http://ala.org.au/terms/1.0/status) | no | The status of the identifier |
| [provenance](http://purl.org/dc/terms/provenance) | no | Information about how an identifier has been processed or modified. Multiple provenance statements are separated by vertical bars  |

Identifier status gives a hint as to how additional identifiers should be interpreted.
They use the following vocabulary:


| Term | Description |
| ---- | ----------- |
| current | An alternative identifier that is currently used |
| replaced | An taxonID for the taxon that has been replaced by the current taxonID. This can happen during edits or when taxon concepts go wandering. |
| historical | An identifier for the taxon that is not in current use |
| unknown | An identifier with unknown status |



## Applying the Name Index

Misapplied names can be a problem, since something is misapplied in a single publication or in a specific region.
If there is a non-misapplied taxon matching a name, then it is assumed that the name refers to the non-misapplied taxon.

**excluded** is not really a taxonomic status term. 
It just means that this species isn't expected at a particular location. 



