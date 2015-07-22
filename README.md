# bie-index [![Build Status](https://travis-ci.org/AtlasOfLivingAustralia/bie-index.svg?branch=master)](https://travis-ci.org/AtlasOfLivingAustralia/bie-index)

A webapp that indexes taxonomic content in DwC-A and provides search web services for this content.
This includes:

- facetted taxonomic search with synonymy support
- search across other entities (regions, layers, datasets, institutions)
- autocomplete services
- download services (to be done)

## Darwin Core archive format of taxonomic information

This application currently supports the ingestion of DwC archives with the following mandatory darwin core fields in the core file:

- taxonID
- parentNameUsageID
- acceptedNameUsageID
- scientificName
- scientificNameAuthorship
- taxonRank

Additional fields added to the core file e.g. establishmentMeans or any other field will also be indexed and available for facetted searching.

In addition to this, an extension file of vernacular names is also supported.
The format support here aligns with the same format supported by the [ala-names-matching API](https://travis-ci.org/AtlasOfLivingAustralia/ala-name-matching).

### Basic example meta.xml

```xml
<archive xmlns="http://rs.tdwg.org/dwc/text/" metadata="eml.xml">
  <core encoding="UTF-8" fieldsTerminatedBy="," linesTerminatedBy="\n" fieldsEnclosedBy="&quot;" ignoreHeaderLines="1" rowType="http://rs.tdwg.org/dwc/terms/Taxon">
    <files>
      <location>taxon.csv</location>
    </files>
    <id index="0" />
    <field term="http://rs.tdwg.org/dwc/terms/taxonID"/>
    <field index="1" term="http://rs.tdwg.org/dwc/terms/parentNameUsageID"/>
    <field index="2" term="http://rs.tdwg.org/dwc/terms/acceptedNameUsageID"/>
    <field index="3" term="http://rs.tdwg.org/dwc/terms/scientificName"/>
    <field index="4" term="http://rs.tdwg.org/dwc/terms/scientificNameAuthorship"/>
    <field index="5" term="http://rs.tdwg.org/dwc/terms/taxonRank"/>
  </core>
  <extension encoding="UTF-8" fieldsTerminatedBy="," linesTerminatedBy="\n" fieldsEnclosedBy="&quot;" ignoreHeaderLines="1" rowType="http://rs.gbif.org/terms/1.0/VernacularName">
    <files>
      <location>vernacular.csv</location>
    </files>
    <coreid index="0" />
    <field term="http://rs.tdwg.org/dwc/terms/taxonID"/>
    <field index="1" term="http://rs.tdwg.org/dwc/terms/vernacularName"/>    
  </extension>
</archive>
```

## Architecture

This application makes use of the following technologies

- Apache SOLR
- Grails 2.4
- Tomcat

![Architecture image](https://raw.githubusercontent.com/AtlasOfLivingAustralia/bie-index/master/architecture.jpg)
