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
These codes are not resolvable, and a taxonID is simply `<CAAB ID>` for example `24462004`

Synonyms share the same identification as the parent species.
These are given a unique identifier built from a hash of the species and synonym name.
For example, `CAAB:e405ef6a:93721fae:7ec56454:41ae634c`

## Currency

CAAB codes with the following identifier styles represent placeholder entries in CAAB
for groupings of organisms that have not yet been analysed.
These codes have a prefix of `8` or `99` and are marked as placeholders.
Placeholder CAAB codes are not included in the output unless, by some fluke they have ended up as the parent of
another taxon.

## Parents

CAAB does not directly supply a parent identifier for a taxon.
Instead, each taxon contains the names from each rank in the Linnaean hierarchy, along with a few additional
sub- or super- ranks.
These names are passed through to the resulting file, for use by the [taxonomy builder](merging.md).

## DwCA Construction

After pre-processing, accepted taxa can be directly mapped onto the DwCA form.

Synonyms and vernacular names are extracted from the lists of synonyms and common names and denormalised.
separate tables are built for each type of name.

