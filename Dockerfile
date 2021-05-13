FROM openjdk:8-jre-alpine

LABEL maintainer="Cichanowski Miguel <miguel.cicha@gmail.com>" 

ENV VERTICLE_FILE ml-1.0.0.jar
ENV VERTICLE_INIT API.START


ENV VERTICLE_HOME /usr/verticles

EXPOSE 8087
 

RUN mkdir $VERTICLE_HOME
RUN mkdir $VERTICLE_HOME/libs/
COPY target/libs $VERTICLE_HOME/libs/

COPY target/$VERTICLE_FILE $VERTICLE_HOME/
COPY settings.json $VERTICLE_HOME/
COPY credentials.json $VERTICLE_HOME/


# Launch the verticle
WORKDIR $VERTICLE_HOME
ENTRYPOINT ["sh", "-c"]
CMD ["java -jar $VERTICLE_FILE $VERTICLE_INIT"]
