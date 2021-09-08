# Australian Faunal Directory

The [Australian Faunal Directory](http://www.environment.gov.au/biodiversity/abrs/online-resources/fauna/) 
(AFD for short) maintains lists of names in the kingdom Animalia.
The AFD is the main source of faunal names.

## Data Source

The data comes in the form of CSV database dumps.
The data should be encoded in UTF-8 but it is necessary to check, since the database export process can
end up wth the files having odd encodings,

The tables used are:

| Table | Essential | Description |
| ----- | --------- | ----------- |
| taxon.csv | yes | The taxonomic structure, with parent-child relationships and currency information |
| name.csv | yes | Scientific and common names for taxa, linked to the taxon table |
| afd_name_disambiguation.csv | yes | A unique ID for each name, author and year combination |
| publication.csv | no | The publication that a taxon or name appeared in |
| reference.csv | no | Links between taxa and publications |
| lookup.csv | no | Not used but contains descriptions of the various status codes used |

Every edit in the AFD produces a new taxon/name combination and a link to the parent entry.
Each row has a start- and end-date.
Current versions can be recognised by having a null end-date.

There may be more than one version of a taxon current.
If a taxon is to be modified, a new copy of that taxon is assigned to a compiler for review and given a status of *taxon.status.A*.
Once the data has been compiled, the assigned taxon is given the status *taxon.status.C* for and it then undergoes review.
Finally, the taxon is placed and the status changes to *taxon.status.P*.
During compilation and review, the old placed taxon remains current and is the authoritative version.

Since each taxon/name combination represents an edit, there may be multiple copies of the same
name, author and year combination in the name table.
The disambiguation table assigns a single ID to each name/author/year.

### Sanitization

Rows from the input are checked for correct formatting:

* Identifiers are sequences of digits.
* GUIDs are UUIDs.
* Flags are `0` (false) or `1` (true) with nothing defaulting to false.
* Dates are of the form `dd-MMM-yy`.
* Names have HTML elements removed and essential names are not empty.
* Vocabulary terms from lookup tables have the form `XXX.YYY.ZZZ` eg taxon.rank.K.

Care needs to be taken when providing input that long text, such as a reference qualification, that it is
correctly enclosed in quotes and escaped, otherwise the row will be rejected.

## Currency

The supplied AFD tables contain a complete history of all changes.
To be current, a taxon needs to be:

* Not unplaced (ie with a specific unplaced flag)
* A status of `taxon.status.P` (placed)
* No end-date

Any taxon which does not meet the currency test is sent to either an unplaced or not previous taxon file.
The previous taxon file is used to build an [ancestor trail](#ancestors) for the current taxon.

A name is current if it has no end-date.

In addition, a useful taxon needs to have a matching name in the names table.
Taxa with missing names are placed in an unnamed file.

## Identification

AFD data comes with two forms of identifiers:

* A GUID that uniquely identifies a taxon or name
* An ID, in the form of an integer, that can be used to link tables together.

The identifiers are converted into LSIDs:

* A taxon LSID has the form `urn:lsid:biodiversity.org.au:afd.taxon:<taxon guid>`
This taxon can be resolved to `http://biodiversity.org.au/afd.taxon/<taxon guid>` 
for example http://biodiversity.org.au/afd.taxon/05d742b7-34fe-4d9e-936f-91cd213c5ca9
* A name LSID has the form `urn:lsid:biodiversity.org.au:afd.name:<disambiguated name id>`
This LSID can be resolved to `http://biodiversity.org.au/afd.taxon/<disambiguated name id>` 
for example http://biodiversity.org.au/afd.name/489144

## Source Splitting

Names are split according to the following rules:

* Valid names are treated as accepted names and linked to a taxon
* Synonyms or various sorts and excluded names are treated as synonyms
* Common names, miscellaneous literature and legislative names are treated as vernacular names
* Other names, such as associated flora, associated fauna, type species, etc. are discarded

## Annotation

### Vernacular Names

The name subtype provides a priority code that can be used in the vernacular name status field.

* `name.type.C.O` is `preferred`
* `name.type.C.N` is `common`, also the default

### References

Links to literature references are added to the taxon, if a reference is available.
The reference table contains several columns dealing with parts of the reference and a reference type.
These columns are joined to build an APA-style reference.

### Ancestors

An ancestor trail of previous taxa identifiers is built iteratively.
Each taxon contains a column with the identifier of the previous version of the taxon.
The ancestor trail is traversed to build a list of ancestors for each current taxon.

### Parents

There are possibly rejected taxa in the AFD with accepted child taxa.
Once the list of current taxa has been generated an iterative process builds a table of actual parents.
On each iteration, the parent ID for a taxon is bumped up to the parent with the next highest rank if
the parent is not part of the list of accepted taxa.

## DwCA Construction

Files are generated for accepted taxa, synonyms, vernacular names and previous taxon identifiers.
Accepted taxa and synonyms are checked against parents for rank inversions.

Zoological practise is to include the accepted name for a taxon as a senior synonym.
This is ignored, since all this does is gum the index up.

Incerate sedis and species inquirenda status, for some reason, is supplied via the taxon rank.
The senior synonym also holds the actual taxon name, rather than the name "Incertae sedis".
The taxonomic status is coverted to the appropriate taxonomic status and the rank is converted to `unknown`
when this appears.


