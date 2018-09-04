# Merging Taxonomies

The resulting set of DwCAs procuded by the processing are combined in the
Large Taxon Collider, documented [here](https://github.com/AtlasOfLivingAustralia/ala-name-matching/blob/master/doc/large-taxon-collider.md).

The ALA uses the following [configuration](https://github.com/AtlasOfLivingAustralia/ala-name-matching/blob/master/data/ala-taxon-config.json)
documented [here](https://github.com/AtlasOfLivingAustralia/ala-name-matching/blob/master/doc/merge-config.md).
The configuration has the following properties:

## General Rules

These rules are common to all sources, unless overridden.

### Forbidden

Taxa with a taxonomic status of invalid or inferred invalid are eliminated.
Previously, incertae sedis taxa were also eliminated.

### Score Adjustments

* Taxa with less than perfect taxonomic status, such as unplaced taxa, inferred status, etc. have scores adjusted downwards.
  This means that cleanly accepted and synonym taxa are chosen as principals, if available.
* Taxa with names not handled well by the current name matching algorithms, eg. hybrids, or names that imply
  problems, eg. doubtful names or names with unplaced in them have scores adjusted downwards.
* Taxa with a dodgey nomenclatural status, such as illegitimate or denied, have scores adjusted downwards.

### Key Adjustments

Note that key adjustments only affect name keys for the purposes of deciding whether two taxa from
different sources are "the same".

* Ranks are collapsed into broad families. For example, superclass, class, subclass, infraclass and subinfraclass
  are all mapped onto class.
  This means that slight differences in opinion on placement between sources do not affect merging.

### Cutoff

Taxa with scores below 500 will, generally, not be considered as principals for resolution
unless there are no other options.
This cutoff score ensures that inferred accepted taxa coming from the ALA species lists, 
which have a base score of 0, are not considered if there are other variants, perhaps excluded or invalid, 
from more authoritative sources.
  
## Sources

### Forbidden

"Root" taxon concepts, tying all top-level taxa together, are eliminated.

### Special Significance

AusMoss and AusFungi have boosted scores for Bryophyta/Bryidae and Fungi, respectively.
In these cases their taxonomy will tend to override APC.
APC has boosted scores for Plantae and AFD has boosted scores for Animalia.

### Ownership

AusFungi owns Fungi, meaning that other taxon concepts for Fungi are mapped onto the
AusFungi taxon concept by inferred synonyms.
Similarly, APC owns Plantae.

### Key Adjustments

Viruses from the Catalogue of Life is mapped onto the Virus used by everyone else.

