FROM openjdk:11-jre-slim

RUN mkdir -p /usr/src/app
WORKDIR /usr/src/app

COPY build/libs ./

ENV JAVA_OPTS=""
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar ./*.jar"]
