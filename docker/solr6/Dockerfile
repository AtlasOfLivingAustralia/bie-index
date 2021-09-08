FROM solr:6.6
WORKDIR /opt/solr/server/solr
COPY --chown=solr:solr solr.xml solr.xml
COPY --chown=solr:solr zoo.cfg zoo.cfg
#COPY --chown=solr:solr lib /opt/solr/server/solr-webapp/webapp/WEB-INF/lib/
RUN mkdir -p bie bie/conf bie/data bie/suggest-infix
COPY --chown=solr:solr conf bie/conf
COPY --chown=solr:solr bie.core.properties bie/core.properties
RUN chown -R solr.solr bie
RUN mkdir -p bie-offline bie-offline/conf bie-offline/data bie-offline/suggest-infix
COPY --chown=solr:solr conf bie-offline/conf
COPY --chown=solr:solr bie-offline.core.properties bie-offline/core.properties
RUN chown -R solr.solr bie-offline
COPY solr.in.sh /etc/default/solr.in.sh

