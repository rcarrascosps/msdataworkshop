#!/bin/bash

SCRIPT_DIR=$(dirname $0)

IMAGE_NAME=frontend-helidon
IMAGE_VERSION=0.1

if [ -z "DOCKER_REGISTRY" ]; then
    echo "Error: DOCKER_REGISTRY env variable needs to be set!"
    exit 1
fi

sed -i "s|%DOCKER_REGISTRY%|${DOCKER_REGISTRY}|g" frontend-helidon-deployment.yaml

export IMAGE=${DOCKER_REGISTRY}/${IMAGE_NAME}:${IMAGE_VERSION}

if [ -z "$1" ]; then
    kubectl create -f $SCRIPT_DIR/frontend-helidon-deployment.yaml -n msdataworkshop
else
    kubectl create -f <(istioctl kube-inject -f $SCRIPT_DIR/frontend-helidon-deployment.yaml) -n msdataworkshop
fi

kubectl create -f $SCRIPT_DIR/frontend-service.yaml -n msdataworkshop
