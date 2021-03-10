FROM hseeberger/scala-sbt:8u252_1.3.13_2.12.11 AS builder

RUN apt update && apt-get install -y apt-transport-https ca-certificates curl gnupg2 && apt clean

WORKDIR /build
ADD ./project/*.* /build/project/
ADD build.sbt /build/
RUN sbt reload
RUN sbt update
ADD src /build/src
RUN sbt assembly


FROM openjdk:8u252-slim-buster as ncm-metering-api
COPY --from=builder /build/target/scala-2.12/metering-agent-*.jar /app/
CMD ["java", "-Xms512m", "-Xmx1024m", "-cp", "/app/*", "tech.cryptonomic.nautilus.metering.MeteringApi"]

FROM openjdk:8u252-slim-buster as ncm-agent-api
COPY --from=builder /build/target/scala-2.12/metering-agent-*.jar /app/
CMD ["java", "-Xms512m", "-Xmx1024m", "-XX:+UseG1GC", "-cp", "/app/*", "tech.cryptonomic.nautilus.metering.MeteringAgent"]