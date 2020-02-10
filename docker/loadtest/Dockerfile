FROM ubuntu:18.04
ARG NGINIX_VERSION=1.17.8
ARG NGINX_VERSION_PATCH=-1

RUN apt-get update && \
    apt-get install -y wget gnupg2 libjansson4 openjdk-8-jdk-headless && \
    wget -q -O - https://nginx.org/keys/nginx_signing.key | apt-key add - && \
    echo deb [arch=amd64] https://nginx.org/packages/mainline/ubuntu/ bionic nginx > /etc/apt/sources.list.d/nginx.list && \
    apt-get update && \
    apt-get install -y nginx=${NGINIX_VERSION}${NGINX_VERSION_PATCH}~bionic
COPY docker/loadtest/nginx.conf /etc/nginx/
COPY target/metered_access_module.so /etc/nginx/modules/
COPY target/scala-2.12/metering-agent-0.1.jar /
COPY docker/loadtest/entrypoint.sh /

ENTRYPOINT ["/entrypoint.sh"]