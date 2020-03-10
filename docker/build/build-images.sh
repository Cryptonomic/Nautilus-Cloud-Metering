#!/bin/bash


set -e
set -x

# build agent and nignx module
docker build -t ncm-build -f ./docker/build/dockerfile-build .
# create nginx docker image
docker build -t ncm-nginx -f ./docker/build/dockerfile-nginx .
# create agent docker image
docker build -t ncm-agent -f ./docker/build/dockerfile-ncm-agent .
