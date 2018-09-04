# Processing - The Basics

Going from a bunch of files to a shiney new DwCA tends to follow more or less the same pattern,
no matter how ropey and deranged the originating data is.

The general principle is to identify current data, reformat it into DwCA form and pass it on.
The [merging](merging.md) step decides which of the resulting taxa is used

## Tools

[Talend OS](http://www.talend.com/) is used as a processing engine.
The processing scripts are in the form of a vast snarl of Talend jobs.
However, each job generally follows the same sequence, outlined below.
Each data source has a job that sequences pre-processing, parent identification, DwCA construction and DwCA packaging.
A job sequences processing for each data source, since some sources depend on previous, higher-priority sources
being processed to provide names for 

## Source Data

Source data is usually in the form of comma separated values (CSV) tables.

Encoding is important, since authors can come from anywhere and can have all kinds of characters in their names.
The expected encoding is [UTF-8](http://www.unicode.org).
Various systems export procedures may cause this to be a problem.
In particular, files exported via Macs may end up in [Mac OS Roman](https://en.wikipedia.org/wiki/Mac_OS_Roman)
with the software silently replacing any character it can't encode, such as Ž (Z-caron) with an underscore.
This causes problems when trying to compare names and authors.

## Pre-Processing

The business of getting a set of files that contain only the taxa that you're interested in,
structured so that further processing steps can proceed.

### Sanitisation

Before use, source files should be sanitized.
This means checking that the fields you use contain data that looks like it should be there.
For example, for an AFD taxon file, sanitization consists of:

* The TAXON_ID is present and is a sequence of digits
* The PARENT_ID is absent or is a sequence of digits
* The TAXON_GUID is present and is formatted as a UUID
* The PRIMARY_RANK is present and is of the form `taxon.rank.XXXX`
* The UNPLACED field is absent or one of `0` or `1`
* The UNPUBLISHED field is absent or one of `0` or `1`
* The RESTRICTED field is absent or one of `0` or `1`
* The RANK_PREFIX field is absent or is a sequence of letters
* The ORDER_INDEX field is absent of is a sequence of digits
* The LEGACY_TAXON_ID field is absent of is a sequence of digits
* The DRAFT field is absent or one of `0` or `1`
* The START_DATE field is present and is a date of the form `dd-MMM-yy` (eg 19-AUG-09)
* The END_DATE field is absent or is a date of the form `dd-MMM-yy`
* The CREATED_FROM_ID field is absent of is a sequence of digits
* The STATUS is present and is of the form `taxon.status.XXXX`
* The DRAFT_NAME_ONLY field is absent or one of `0` or `1`
* The ASSIGNED_TAXON_ID field is absent of is a sequence of digits

Sanitization at least consists of:

* Checking to see whether identifiers pass muster
* Checking to see whether there is a name at all.
It would be nice to be able to check to see whether the name fits the established rules for
the branch of biology but this is almost always a fool's errand.
[Phrase names](glossary.md#def-phrase-name) and the like usually play merry hell with whatever rules you come up with.

Rows that do not pass sanitization tests are placed in a rejected file, for further examination.

### Cleaning

It may be necessary to remove notes and additional data from the supplied data.
In particular, year of publication seems to cause a desire to prevaricate in ways unknown to machines.

### Currency

Data that contains historical trails of information needs to be filtered so that only the current
view of names and taxonomy is used.

If the data contains historical trails, it may be possible to build a trail of ancestor identifiers
that can be used to provide a service for clients not yet up to date. 


### Identification

Adding taxonIDs to the various taxa, along with notes as to whether they are to be used or not.
In some cases, there are internal identifiers (eg. a row number in a database or UUID) and external identifiers
(eg. an LSID or URL) that are linked to the internal identifiers.
The output should use the external identifiers.

Where necessary, the supplied identifier is left in place  and a parallel `identifier` column is used
to carry the final identifier.
This process is useful in ensuring that additional identifiers and names can be passed through for taxa
that have a higher-priority predecessor but carry additional information.


### Annotation

Annotation involves adding an any extra information, such as literature references, to a taxon description.


## Parent Taxa

The data fed into bie-index needs to have a consistent tree of parent-child relationships.
The parentNameUsageID links a taxon to its parent.
By the time the data has passed though pre-processing, things may look a bit patchy.

### Identifying Parents

Ideally, the supplied data should explicitly provide the parent taxon by a linking identifier.
If it doesn't, then it probably provides the names of higher taxa.
These are proceessed during [merging](#combining-sources).


### <a name="correcting-parents"/> Correcting Parents

Some taxa may have non-current or otherwise eliminated parents.
If these have been eliminated during pre-processing, then the parent taxon needs to be bumped up to the 
current taxon of the next highest rank.

Generally this is handled via iterative processing.
A current list of taxa and parent identifiers is built.
The parent identifiers are then checked against the list of accepted taxa.
If the parent isn't in the list, then the parent of that parent is introduced as a candidate.
This continues until no new parents are introduced, either because a valid parent has been found
or because there isn't anything left in the tree.


## DwCA Construction

The pre-processed data and parent maps can then be used to construct the
files and Darwin Core Archive described in [conventions](conventions.md).
This is usually a straight-forward operation where terms are simply mapped onto the matching DwC terms.
Accepted taxa and synonyms are bundled together into `taxon.csv`.
Vernacular names are placed in `vernacularNames.csv` and additional identifiers are placed in `identifiers.csv`.

### Validity Checking

The data being supplied needs to be consistent.
Validity checking consists of ensuring that:

* A taxon with a parent that is expected to be in the same dataset actually has that parent
* A taxon with a parent respects the rank structure.
There are a few complications with the rank structure, since hybrids are effectively of the same rank
as the parent species.

### Taxonomic Status Mapping

Taxonomic status mapping involves translating the way the taxa are represented in the source data
into the standard vocabulary described in [the conventions](conventions.md#taxonomic-status).


### Taxon Rank Mapping

Each data source tends to have its own taxon rank vocabulary.
These need to be translated into the ALA vocabulary described in [the conventions]((conventions.md#taxon-rank).

### Generating the eml.xml file

The `eml.xml` file contains metadata about the DwCA.
The file is constructed by running an [XSLT script](eml.xsl) over a `metadata.xml` file.
The `metadata.xml` file contains basic information about the data set, such as the source organisation, the
data date (as opposed to the archive production date, which is included automatically), coverage, etc.
The script extends this data with information about the ALA.

### DwCA Packaging

Once the CSV files are constructed, the files are zipped into a DwC archive, along with
pre-built `meta.xml` and `eml.xml` files.
Once packaged, the resulting DwCA can be delivered to the BIE index.
.
* TODO (Potentially, it may not be worth it) Dynamically build the meta.xml file.

## Combining Sources

Once constructed, the resulting data is fed into the taxonomy builder,
implemented in [ala-name-matching](https://github.com/AtlasOfLivingAustralia/ala-name-matching).
Also known as the Large Taxon Collider.
The taxonomy builder merges taxonomic trees in DwCA form to provide a single, 
consistent(ish) taxonomy.
A scoring system is used to choose between different sources of information when
there are conflicts.

The combined index can be cleanly fed into the index builders for the name matching librariues
and the BIE.






