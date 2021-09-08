# Catalogue of Life

The [Catalogue of Life](http://www.catalogueoflife.org/) (CoL) is a global taxonomic catalogue.
The ALA uses CoL for viruses, bacteria, protozoa, chromista and certain fungi that are found in Australia.

## Data Source

Data is suppled in the form of Darwin Core archives by downloading from the [DCA_Export](http://www.catalogueoflife.org/DCA_Export/) link
The data comes with the following files:

| File | Used | Description |
| ---- | ---- | ----------- |
| taxa.txt | yes | Core taxonomic data with scientific names and structural information such as parentNameUsageID |
| vernacular.txt | yes | Vernacular names in multiple languages and localities |
| distribution.txt | yes (fungi only) | Distributions, identified by country or state/province. Not all taxa have distribution entries |
| reference.txt | no (oops) | Publication references. Not used at present |
| speciesprofile.txt | no | Present but not used in the downloaded data |
| description.txt | no | Present but not used in the downloaded data |
 
## Identification

Data in the DwCA is linked by an integer taxonID.
`taxa.txt` also contains an identifier column that contains a CoL LSID of the form
`urn:lsid:catalogueoflife.org:taxon:<guid>:col<date>` for example
`urn:lsid:catalogueoflife.org:taxon:0d7cd405-18fe-11e5-9774-bc764e0806fb:col20150828`
These identifiers are passed though and used when constructing the final ALA DwCA.

## Currency

The data supplied is a snapshot of the current domain.
All information is current.

Data from the CoL is filtered by a series of kingdom, dataset and locality filters.
For Fungi, only non-exitinct, Australian fungi (identified by a real distribution in Australia) are included,
along with the higher taxa for Australian species.
Higher taxa are identified by a similar process to [parent correction](processing-basics.md#correcting-parents).
Many fungi are already present in [AusFungi](ausfungi.md) and are [merged](merging.md) with the AusFungi entries.


## Parents

CoL is a complete representation of a domain.
Since parents are included by means of an Australian species being present no further parent processing is required.

Note that there are occasional glitches in the parent-child relationship with at least one ID, 
urn:lsid:catalogueoflife.org:taxon:e0016937-18fd-11e5-9774-bc764e0806fb:col20150828 referring to both the
kingdom and phylum.


## DwCA Construction

The data is already in Darwin Core form.
Construction consists of structure checking and reducing the supplied columns to the used ALA terms.
