FROM hseeberger/scala-sbt:8u252_1.3.13_2.12.11 AS builder

RUN apt update && apt-get install -y apt-transport-https ca-certificates curl gnupg2
RUN curl -fsSL https://download.docker.com/linux/debian/gpg |  apt-key add -
RUN echo "deb [arch=amd64] https://download.docker.com/linux/debian stretch stable" >> /etc/apt/sources.list
RUN apt-get update && \
    apt-get install -y \
      docker-ce-cli build-essential libjansson-dev libpcre++-dev zlib1g-dev && \
   apt clean

WORKDIR /build
ADD ./project/*.* /build/project/
ADD make.sh build.sbt /build/
RUN /build/make.sh clean
RUN /build/make.sh download
RUN /build/make.sh setup
ADD src /build/src
RUN /build/make.sh compile


FROM openjdk:8u252-slim-buster as ncm-metering-api
COPY --from=builder /build/target/scala-2.12/metering-agent-*.jar /app/
CMD ["java", "-Xms512m", "-Xmx1024m", "-cp", "/app/*", "tech.cryptonomic.nautilus.metering.MeteringApi"]


FROM nginx:1.17.8 as ncm-proxy-agent
RUN mkdir -p /usr/share/man/man1
RUN apt update -y && apt install -y libjansson4 openjdk-11-jdk-headless && apt clean
COPY --from=builder /build/target/metered_access_module.so /etc/nginx/modules/metered_access_module.so
COPY --from=builder /build/target/scala-2.12/metering-agent-*.jar /app/
COPY docker/entrypoint.sh /
ENTRYPOINT ["/entrypoint.sh"]
