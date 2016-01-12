# Name Formatting

Generally, a name has two components, the scientific name and the authorship.
Normally, the scientific name comes first, followed by the author.
If the taxon is genus-level or below, the scientific name is italicised.
For example, the taxon with the name `Phalanger mimicus` and authorship `Thomas, 1922` is
rendered as *Phalanger mimicus* Thomas, 1922.

Naturally, you should be suspicious of this by now.
It's too simple, there are probably horrible edge cases all over the place.
And you would be right.
For example, you might have *Pararchidendron pruinosum* (Benth.) I.C.Nielsen var. *pruinosum* where the authorship
is attached to the original species, rather than the variety.

To cope with these edge cases, the bie-index provides two fields:
**nameComplete** is the correctly laid out name in plain text, suitable for indexes etc.
**nameFormatted** is an HTMLised version of the name, using `<span class="...">` elements to mark the various parts of the name.
A front-end can then use CSS to correctly format the name.

## Formatted Name Structure

A complex formatted name may have a stricture of the form:

```
<span class="scientific-name rank-species"><span class="name">Ozothamnus</span> <span class="name">rosmarinifolius</span> <span class="rank">var.</span> <span class="name">purpurascens</span> (<span class="author base-author">DC.</span>) <span class="author">H.F.Comber</span></span>
```

The essential components are

* `<span class="scientific-name rank-species">` Encloses the entire name, marks it as a scientific name and provides a hint as to the rank of the name (see below)
* `<span class="name">` Encloses a scientific name party
* `<span class="author">` Encloses an author. In addition `base-author` or `ex-author` may be added for specific author types. 
* `<span class="rank">` Encloses a rank marker
* `<span class="hybrid">` Encloses a hybrid marker
* `<span class="manuscript">` Encloses a manuscript marker


### Rank classes

There is a separate `rank-<rank>` class for each level of the Linnaean hierarchy: 
rank-kingdom, rank-phylum, rank-class, rank-order, rank-family, rank-genus, rank-species and rank-subspecies.
Different levels, such as subfamily are grouped into the main ranks.
These classes can be used to specifically format name elements in rank-specific ways.

## Import

If a DWCA has either a `http://ala.org.au/terms/1.0/nameComplete` or `http://ala.org.au/terms/1.0/nameFormatted` column, then
that column will be used directly.
Otherwise, the bie-index attempts to build a nameComplete and nameFormatted from what is available.