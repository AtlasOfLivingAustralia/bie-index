# AusFMoss

The [AusMoss](http://www.rbg.vic.gov.au/dbpages/cat/index.php/mosscatalogue) dataset contains names and taxa for Australian Mosses.
Data is compiled by the Melbourne Royal Botanic Gardens.
AusMoss closely follows the [AusFungi](ausfungi.md) processing pattern.

## Data Source

Data is provided as a Darwin Core Archive.
The `taxon.txt` file contains complete information on the taxon, including hiogher-order taxon names,
parent IDs, accepted name usage IDs and other goodies.

An extension file, `distribution.txt` contains information on geographical distribution.
This file is currently unused.

## Currency

The data supplied is a snapshot of the current taxonomic tree.
All information is current.
However, incertae sedis taxa and taxa without valid ranks or names are removed.

## Identification

Taxon identifiers are supplied as GUIDs.
Since the GUIDs are unique and there is no resolvable reference, the GUIDs are used as-is.

AusMoss is part of Plantae.
Since [APNI/APC](apni.md) already provides higher-order taxa, identifers for taxa above
Equisetopsida or Streptophyta are translated into the APNI LSIDs.

## Parents

Some taxa may have been removed during currency checking.
New parents are computed via the iterative process described in [the basics](processing-basics.md#correcting-parents).

## DwCA Construction

The data is already in Darwin Core form.
Construction consists of structure checking and reducing the supplied columns to the used ALA terms.
