# The Child's First Encyclopedia of Nameology with numerous useful annotations by a gentleman

### Or everything you wanted to know about names but were afraid to ask.

Actually, everything you never wanted to know about names and were too sensible to ask.
But here you are ...

Also known as the sequal to [Celestial Emporium of Benevolent Knowledge](https://en.wikipedia.org/wiki/Celestial_Emporium_of_Benevolent_Knowledge)

Nameology describes the process by which different sources of names are collected and transformed into
the sort of taxonomic Darwin Core Archives that the bie-index expects.

## Table of Contents

* [The Goal](#thegoal)
* [Order of Sources](#orderofsources)
* [Conventions](conventions.md)
* [Processing Basics](processing-basics.md)
* [Name Formatting](name-formatting.md)
* Data Sources
    * [Australian Faunal Directory (AFD)](afd.md)
    * [Australian Plant Name Index and Australian Plant Census (APNI/APC)](apni.md)
    * [AusFungi](ausfungi.md)
    * [AusMoss](ausmoss.md)
    * [Codes for Australian Aquatic Biota](caab.md)
    * [New Zealand Organism Register](nzor.md)
    * [Catalogue of Life](col.md)
* [Merging](merging.md)

## The Goal

*This is important!* 
The ALA uses scientific names and taxonomic trees for specific purposes.
Essentially, the ALA is matching names so that it can build an index of occurrence
records by species and we want both to join like with like and build an index tree so
that people can find records across higher taxa, so that "find me all birds" becomes
"find all records indexed to class:AVES".
We also build a search index of names, data sources, localities, etc. that can be used
by humans to find what they are looking for.

The ALA is *not* in the business of providing some sort of absolute truth.
This is because the ALA collects occurrence records from across time, space and sanity:

* Some records may come from the 19th century, or before, and contain names that no longer fit current biologial thinking. 
In this case, the ALA processing tries to make the best fist of things.
This can get complicated when splitters have been at work, since it may not be possible to deterministically
decide which of the current taxa the occurrence record shsould be assigned to.
* Some records may come from outside Australia, thanks to collecting expeditions, and refer to species that don't occur in Australia.
The ALA is really only interested in Australian species; we can't import a world-wide taxonomic tree.
The supplied name is always present, so people looking for those records will still be able to find them.
* Some occurrence records may contain only vernacular (common) names or legislated names (names that appear
in legislation to do things like identify threatened species, where taxonomy has moved on but the legislation
still uses the older names).
* The ALA doesn't exclude occurrence records and identifications just because they look incorrect.
Instead, records are marked with various quality metrics.
If someone wants to use the data for research, then they will need to decide which records pass muster
in terms of data quality.
What records pass muster depends on the nature of the research, someone mapping a species may be considerably
more conservative than someone using the records to map history.

To do this we do the following:

* Build a taxonomic tree where each taxon concept has a place in the tree.
We know that there are a least as many taxonomic trees as there are taxonomists.
The ALA, where possible, takes the "official" taxonomic trees of the 
Australian Faunal Directory (AFD), the Australian Plant Census (AFC) of the Australian Plant Name Index (APNI)
and the New Zealand Organisms Register (NZOR).
These "official" trees are essentially the state of the art for anyone who doesn't have a dog in this fight.
They have some holes in them, so they're supplemented by various other sources.
  * Where the sources double up or disagree, a [merging](merging.md) step resolves the taxonomies against
    each other.
* Build an index of names where each name is mapped onto a place in the taxonomic tree. 
These names include synonyms (names for species that now go by a different, correct name) and
vernacular names (commonly used names for species)
* Process incoming occurrence records by trying to match the supplied names against the index.
When this is successful, the occurrence record is annotated with the identifier of the matched taxon concept
and the identifiers of all the parent taxon concepts for the match. For example, if a record contains the name
*Grus rubicunda* (a Brolga, in case you're interested) then the ALA will match it to the species 
*Grus (Mathewsia) rubicunda Perry, 1810* and the subgenus *Grus (Mathewsia) Iredale, 1911* and
the genuis *Grus*, then the family *GRUIDAE*, the order *GRUIFORMES*, the class *AVES*, 
the taxon *GNATHOSTOMATA*, the subphylum *VERTEBRATA*, the phylum *CHORDATA* and the kingdom *ANIMALIA*.
This means that anyone looking for "class:AVES" will get this record.

## Order of Sources

Some sources are more equal than others.
Generally, if a name/author occurs in an earlier source, then the identifier and source for that source is
used and identifiers are mapped in following sources.
The order of priority for sources is:

1. AusFungi (for fungi only)
1. AusMoss (for mosses only)
1. Australian Faunal Directory
1. Australian Plant Name Index/Australian Plant Census
1. New Zealand Organism Register
1. Australian Plant Name Index (names only)
1. Codes for Australian Aquatic Biota
1. Catalogue of Life
1. Names generated from species lists held by the ALA, such as threatened species lists, special cases
   and lists of vernacular names.

There are some cases where source disagree on classification to the point where the same species appears
at a different rank or with different parents.
For example, the Australian Pelican is *Pelecanus conspicillatus* in the AFD and 
*Pelecanus conspicillatus conspicillatus* in NZOR.
There's not much that can be done about this, but it does make failed matches likely when using vernacular names.
