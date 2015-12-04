# Processing - The Basics

Going from a buch of files to a shiney new DwCA tends to follow more or less the same pattern,
no matter how ropey and deranged the originating data is.

## Tools

[Talend OS](http://www.talend.com/) is used as a processing engine.
The processing scripts are in the form of a vast snarl of 

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
Generally, this involves:

* Checking to see whether identifiers pass muster
* Checking to see whether there is an name at all.
It would be nice to be able to check to see whether the name fits the established rules for
the branch of biology but this is almost always a fool's errand.
[Phrase names](glossary.md#def-phrase-name) and the like usually play merry hell with whatever rules you come up with.

### Cleaning

It may be necessary to remove notes and additional data from the supplied data.
In particular, year of publication seems to cause a desire to prevaricate in ways unknown to 

### Currency

Data that contains historical trails of information needs to be filtered so that only the current
view of names and taxonomy is used.
[Incertae Sedis](glossary.md#def-incertae-sedis) taxa of doubtful position may also be eliminated at this point.

If the data contains historical trails, it may be possible to build a trail of ancestor identifiers
that can be used to provide a service for clients not yet up to date. 


### Identification

Adding taxonIDs to the various taxa, along with notes as to whether they are to be used or not.
Minor sources of names often duplicate taxa that have already been processed.
Identification involves mapping taxa onto pre-existing identifiers, or building new identifiers for the incoming taxa.

Where necessary, the supplied identifier is left in place  and a parallel `identifier` column is used
to carry the final identifier.
This process is useful 

#### <a name="name-matching"/> Name Matching

When matching names and authors from other sources, slight variations in spelling and punctuation
mean that two names that are a match may not syntactically match.
To ease matching, a *match name* and *match author* are often built.
These are canonicalisations of the name and author with the following characteristics:

* If the name has a subgenus element (eg. *Coniopteryx (Xeroconiopteryx) occidentalis*) then the subgenus element is removed.
For example, the above name would become *Coniopteryx occidentalis*
* Any " and " part of the author is replaced by an ampersand.
* The name or author is made upper case.
* Any punctuation is removed
* Multiple spaces are normalised to a single space

### Annotation

Annotation involves adding an any extra information, such as literature references, to a taxon description.


## Parent Taxa

The data fed into bie-index needs to have a consistent tree of parent-child relationships.
The parentNameUsageID links a taxon to its parent.
By the time the data has passed though pre-processing, things may look a bit patchy.

### Identifying Parents

Ideally, the supplied data should explicitly provide the parent taxon by a linking identifier.
If it doesn't, then it probably provides the names of higher taxa.
If that is the case, then it becomes necessary to work down through the list of higher taxa, providing
the lowest rank parent taxon as the parent.


### <a name="correcting-parents"/> Correcting Parents

Some taxa may have non-current or incertae sedis parents.
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

### DwCA Packaging

Once the CSV files are constructed, the files are zipped into a DwC archive, along with
pre-built `meta.xml` and `eml.xml` files.
Once packaged, the resulting DwCA can be delivered to the BIE index.

* TODO Dynamically build the eml.xml file.
* TODO (Potentially, it may not be worth it) Dynamically build the meta.xml file.





