# Docker Images

Subdirectories contain docker descriptions that can be used for testing and
debugging

## Solr

The solr image contains a solr server with pre-configred `bie` and `bie-offline` cores.
To build a solr6 image for use with the bie-index run, from this directory

```shell
docker build solr6 -t bie-solr:v1
```

To run the resulting image in a fresh container, use

```shell
docker run -p 8983:8983 bie-solr:v1
```

Set the solr connections in the configuration to
`http://localhost:8983/solr/bie` and `http://localhost:8983/solr/bie-offline`