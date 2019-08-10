FROM adoptopenjdk/openjdk12-openj9:alpine-slim

RUN adduser -S dabka
USER dabka

RUN mkdir -p /home/dabka/runtime/config
ADD app/build/libs/dabka_server-0.0.2.jar /home/dabka/runtime
WORKDIR /home/dabka/runtime

ENTRYPOINT [ "java", "-jar", "/home/dabka/runtime/dabka_server-0.0.2.jar" ]
