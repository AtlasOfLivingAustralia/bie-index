# Australian Plant Name Index and Australian Plant Census

The [APNI](https://www.anbg.gov.au/cpbr/databases/apni-search-full.html) dataset contains names for Australian plants.
The [APC](https://www.anbg.gov.au/chah/apc/) is an evolving, consensus view of plant taxonomy, based on APNI.
Data is compiled by the National Botanic Gardens 

## Data Source

Data is provided in the form of a pair of CSV files from https://www.anbg.gov.au/ibis25/pages/viewpage.action?pageId=11960657 
The `APNInames.csv` file contains the complete list of names in APNI and contains names and publication references.
The `APCdata.csv` file contains the current APC view and contains taxonomic structure information.
Both files contains Darwin Core terms, along with additional flags and terms.

## Currency

The data supplied is a snapshot of the current taxonomic tree.
All information is current.

## Identification

Supplied taxonIDs are URLs that provide a reference to the name or taxonomic concept.
Names have the structure http://id.biodiversity.org.au/name/apni/id
Taxonomic concepts have the structure http://id.biodiversity.org.au/node/apni/id
These URLs are not resolvable by lsid.tdwg.org and are used as-is.

## DwCA Construction

The data is already in Darwin Core form.
Construction consists of splitting accepted taxa, synonyms and commonm names and linking names to the current taxa.

### Name formats

The names file contains the following:

* **scientificName** The correctly formatted scientific name, including authorship. Botanic scientific names may not have the author at the end of the name and may also intersperse rank markers and other codes. This is mapped onto **nameComplete**
* **canonicalName** The scientific name without authorship. This is mapped onto **scientificName**
* **scientificNameAuthorship** The author. This is mapped onto **scientificNameAuthorship**
* **scientificNameHTML** An XML version of the scientific name with parts of the name marked by XML tags. These are transformed into HTML spans with appropriate class markers and then mapped onto **nameFormatted**

### Names not in APC

Some names in APNI have not yet been placed in the APC.
These orphan names are treated as inferred accepted taxa and placed under Plantae; 
we at least know which kingdom they come from.
These are produced as a separate DwCA with a slightly different structure, containing
kingdom, phylum, class, order, family and genus names.
The name information generally contains genus and family.
[Merging](merging.md) can use this structural information to partially place the resulting names.

