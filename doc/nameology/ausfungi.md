# AusFungi

The [AusFungi](http://www.rbg.vic.gov.au/dbpages/cat/index.php/fungicatalogue) dataset contains names and taxa for Australian Fungi.
Data is compiled by the Melbourne Royal Botanic Gardens 

## Data Source

Data is provided as a Darwin Core Archive.
The `taxon.csv` file contains complete information on the taxon, including hiogher-order taxon names,
parent IDs, accepted name usage IDs and other goodies.
An extension file, `identifier.csv` contains additional identifiers for some taxa.

## Currency

The data supplied is a snapshot of the current taxonomic tree.
All information is current.
However, incertae sedis taxa and taxa without valid ranks or names are removed.
There are also accepted taxa without a parent; 
these are mostly names of genera that are no longer recognised, or do not occur in Australia. 

## Identification

Supplied taxonIDs are either LSIDs referencing the [Index Fungorum](http://www.indexfungorum.org/) or GUIDs.
Both Index Fungorum LSIDs and GUIDs are used as-is.
If a taxon has a supplimentary identifier in the `identifier.csv` erxtension, then that identifier is used in preference.

## Parents

Some taxa may have been removed during currency checking.
New parents are computed via the iterative process described in [the basics](processing-basics.md#correcting-parents).

## DwCA Construction

The data is already in Darwin Core form.
Construction consists of structure checking and reducing the supplied columns to the used ALA terms.

