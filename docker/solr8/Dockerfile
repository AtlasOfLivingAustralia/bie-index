FROM solr:8.1
USER solr:solr
WORKDIR /var/solr/data
COPY solr.xml solr.xml
COPY zoo.cfg zoo.cfg
#COPY --chown=solr:solr lib /opt/solr/server/solr-webapp/webapp/WEB-INF/lib/
RUN mkdir -p bie bie/conf bie/data bie/suggest-infix
COPY conf bie/conf
COPY --chown=solr:solr bie.core.properties bie/core.properties
RUN mkdir -p bie-offline bie-offline/conf bie-offline/data bie-offline/suggest-infix
COPY conf bie-offline/conf
COPY --chown=solr:solr bie-offline.core.properties bie-offline/core.properties
COPY solr.in.sh /etc/default/solr.in.sh


