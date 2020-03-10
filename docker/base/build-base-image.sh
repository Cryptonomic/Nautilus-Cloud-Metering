#!/bin/bash


export IMAGE_NAME=${IMAGE_NAME:-"nautilus-cloud-metering"}
export IMAGE_VERSION=${IMAGE_VERSION:-"base-latest"}
export DOCKER_REPO=${DOCKER_REPO:-"cryptonomic"}

docker build \
--build-arg BASE_IMAGE_TAG="8u222-jdk-stretch" \
--build-arg SBT_VERSION="1.3.4" \
--build-arg SCALA_VERSION="2.13.1" \
--build-arg USER_ID=1001 \
--build-arg GROUP_ID=1001 \
-t $IMAGE_NAME:base-tmp \
github.com/hseeberger/scala-sbt.git#:debian

docker build -f dockerfile-base -t $DOCKER_REPO/$IMAGE_NAME:$IMAGE_VERSION .
docker tag $DOCKER_REPO/$IMAGE_NAME:$IMAGE_VERSION $IMAGE_NAME:$IMAGE_VERSION
#docker push $DOCKER_REPO/$IMAGE_NAME:$IMAGE_VERSION
