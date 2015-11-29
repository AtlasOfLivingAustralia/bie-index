# Codes for Australian Aquatic Biota

[Codes for Australian Aquatic Biota](http://www.marine.csiro.au/caab/) (CAAB) is a catalogue of codes
for established species and unidentified spieces.
The CAAB catalogue contains a number of species not included in AFD and APNI and is used to fill in
any gaps that have appeared.

In addition to scientific names, CAAB also maintains a list of commecially significant common names for species.
These names are preferred common names and should take precedence when the species information is displayed.

## Data Source

Data is supplied in the form of an XLSX spreadsheet.
Each row in the spreadsheet contains a single species.
Multiple values are separated by vertical bars (|).
 
## Identification

CAAB maintains a unique numeric *CAAB Code* for each species.
These codes are not resolvable, and a taxonID is simply `CAAB:<CAAB ID>` for example `CAAB:24462004`

Synonyms share the same identification as the parent species.
These are given a unique identifier built from a hash of the species and synonym name.
For example, `CAAB:e405ef6a:93721fae:7ec56454:41ae634c`

## Currency

Many of the species in CAAB are already in [AFD](afd.md).
Prior to processing, species are matched against the existing list of species
using [name matching](processing-basics.md#name-matching).
Pre-existing species use the pre-existing taxonID and are not included in the final CAAB data.

## Parents

CAAB does not directly supply a parent identifier for a taxon.
Instead, each taxon contains the names from each rank in the Linnaean hierarchy, along with a few additional
sub- or super- ranks.

Parents are identified by following a chain of parents, from kingdom down to subspecies.
If a parent is named and is at a superior rank to the taxon, then that parent name is found in an index of
names and the appropriate parent identifier added.
This process continues down the chain, meaning that the lowest rank parent ends up as the parent identifier.


## DwCA Construction

After pre-processing and parent identification, accepted taxa can be directly mapped onto the DwCA form.

Synonyms and vernacular names are extracted from the lists of synonyms and common names and denormalised.
separate tables are built for each type of name.

An identifier table is built with the CAAB codes for each taxon.
