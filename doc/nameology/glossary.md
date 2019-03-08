#Glossary

* <a name="def-accepted-name"/> **accepted name** The "official" name for a taxon.
  It's only official in the sense that some authority says that's the right one.
  We accept authority from various places (eg. APC or AFD)
* <a name="def-autonym"/> **autonym** An automatically generated name.
  Autonyms appear when a subspecies is first described and are a subspecies
  containing all the 'other' members of the species described by the subspieces.
  Because they don't have a person deliberately choosing to create them,
  autonyms do not have an author. Instead the original author for the species
  is kept, embedded in the name, and the species epithet is repeated.
  E.g. *Gonocarpus micranthus* Thunb. has a subspecies *Gonocarpus micranthus* subsp. *ramosissimus* Orchard and an
  autonym *Gonocarpus micranthus* Thunb. subsp. *micranthus*
* <a name="def-canonical-name"/> **canonical name** A name represented with a standard layout, all ligature characters replaced with their split versions, standard punctuation, etc.
  Canonical in this case means the same name has the same form so that simple string equality will work.
* <a name="def-excluded"/> **excluded** A name accepted elsewhere that doesn't occur in this region, so finding this name generally means an error
* <a name="def-homonym"/> **homonym** A name spelled exactly like another name published for a taxon of the same rank based on a different type.
  <http://www.iapt-taxon.org/nomen/main.php?page=art53#53.1>
* <a name="def-incertae-sedis"/> **incertae sedis** "uncertain placement" A taxon that doesn't have a well-defined position in the taxonomic hierarchy
* <a name="def-lsid"/> **lsid** Life-sciences ID.
  A URN that uniquely identifies a life-sciences concept e.g. `urn:lsid:biodiversity.org.au:afd.name:269127`
  The lsid is always formatted as `urn:lsid:<authority>:<namespace>:<id>` and can be generally resolved as
  `http://<authority>/<namespace>/<id>` eg <http://biodiversity.org.au/afd.name/269127>
* <a name="def-phrase-name"/> **phrase name** Generally, a *handle* for potential new species yet to be formally described.
  A phrase name has the parts generic name, rank, identifier (usually a geographical or descriptive label) and a collectors name/identifier or [voucher](#def-voucher).
  The generic name is the name of the parent taxon, the rank labels the rank of the actual taxon.
  Eg. *Acacia mutabilis Maslin subsp. Young River (G.F. Craig 2052)* has a generic name of *Acacia mutabilis Maslin*
  (genus Acacia, species mutablis, author Maslin), a rank of *subsp.*, an identifier of *Young River* and a voucher
  of *G.F. Craig 2052*. <https://florabase.dpaw.wa.gov.au/help/names#phrase>
* <a name="def-rank-marker"/> **rank marker** A term within a name indicating a vague match to a rank.
  (Eg. "sp." for species of some sort, "var" for variety etc.)
* <a name="def-species-inquirenda"/> **species inquirenda** A species of doubtful identity requiring further investigation.
* <a name="def-taxon"/> **taxon** A group of organisms with some sort of similar chataracteristic
* <a name="def-voucher"/> **voucher** The name/identifier of the person vouching for the new taxon in a [phrase name](#def-phrase-name)


