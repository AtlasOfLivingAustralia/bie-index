FROM tomcat:9.0-jdk11-temurin

ENV TZ=Europe/Stockholm

RUN mkdir -p /data/bie-index/config

COPY sbdi/data/config/conservation-lists.json /data/bie-index/config/conservation-lists.json
COPY sbdi/data/config/image-lists.json /data/bie-index/config/image-lists.json
COPY sbdi/data/config/locality-keywords.json /data/bie-index/config/locality-keywords.json
COPY sbdi/data/config/vernacular-lists.json /data/bie-index/config/vernacular-lists.json

COPY build/libs/bie-index-*-plain.war $CATALINA_HOME/webapps/ROOT.war
