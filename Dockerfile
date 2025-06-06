FROM openjdk:17-jdk-alpine

# Recover full jar file name
ARG JAR_FILE

COPY target/${JAR_FILE} ${JAR_FILE}

# Create logs folders and grant permissions
RUN mkdir /logs/
RUN mkdir /logs/history/
RUN chmod -R 777 /logs/

# Create environment variable wit jar file name
ENV JAR_FILE=$JAR_FILE

ENTRYPOINT java -jar ${JAR_FILE}