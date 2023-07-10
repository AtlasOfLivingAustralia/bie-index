FROM openjdk:11-jre-slim
COPY ./build/libs/bie-index-1.9-SNAPSHOT.war app.war
CMD ["java", "-jar", "-Dserver.port=8080", "app.war"]