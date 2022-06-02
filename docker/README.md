# Docker Images

Subdirectories contain docker descriptions that can be used for testing and
debugging

## Solr

The solr image contains a solr server with pre-configred `bie` and `bie-offline` cores.
To build a solr8 image for use with the bie-index run, from this directory

```shell
docker build solr8 -t bie-solr:v2
```

To run the resulting image in a fresh container, use

```shell
docker run -p 8983:8983 bie-solr:v2
```

You will then need to add the cores.
Go to http://localhost:8983 and choose the cores item.
Click on add core and set the following:

Parameter | Value (bie) | Value (bie-offline)
--- | --- | ---
name | bie | bie-offline
instanceDir | /opt/solr/server/solr/bie | /opt/solr/server/solr/bie-offline
dataDir | /opt/solr/server/solr/bie/data | /opt/solr/server/solr/bie-offline/data
config | solrconfig.xml | solrconfig.xml 
schema | schema.xml | schema.xml

(You may need to do this twice; no idea why.)

Set the solr connections in the configuration to
`http://localhost:8983/solr/bie` and `http://localhost:8983/solr/bie-offline`