#!/bin/sh
# deploy app to kube
# usage ./kube_deploy.sh

IMAGE_TAG=${CI_PIPELINE_ID:-latest}
KUBE_NAMESPACE=${KUBE_NAMESPACE:-default}
GITLAB_USER_EMAIL=${GITLAB_USER_EMAIL:-nobody@example.com}
CI_ENVIRONMENT_URL=${CI_ENVIRONMENT_URL:-network-map-dev.cordite.biz}

echo "$(date) deploying app to namespace ${KUBE_NAMESPACE} with image ${IMAGE_TAG}"

set -e

# Check if we have variables set to access docker reg
if [ -z "$CI_REGISTRY" ] ; then
   echo "CI_REGISTRY not set. Value needs to be set to docker registry"
   exit 1
fi

if [ -z "$CI_REGISTRY_USER" ] ; then
   echo "CI_REGISTRY_USER not set. Value needs to be set to docker registry user"
   exit 1
fi

if [ -z "$CI_REGISTRY_PASSWORD" ] ; then
   echo "CI_REGISTRY_PASSWORD not set. Value needs to be set to docker registry password"
   exit 1
fi

# before we start show everything
echo "all resources"
kubectl get all -n "$KUBE_NAMESPACE"

# create gitlab reg secret
kubectl create secret -n "$KUBE_NAMESPACE" \
    docker-registry gitlab-registry \
    --docker-server="$CI_REGISTRY" \
    --docker-username="${CI_DEPLOY_USER:-$CI_REGISTRY_USER}" \
    --docker-password="${CI_DEPLOY_PASSWORD:-$CI_REGISTRY_PASSWORD}" \
    --docker-email="$GITLAB_USER_EMAIL" \
    -o yaml --dry-run | kubectl replace -n "$KUBE_NAMESPACE" --force -f -

# Replace deployment
cat ./deployment.yaml \
 | sed s/:latest/:${IMAGE_TAG}/ \
 | sed s/network-map-dev.cordite.biz/${CI_ENVIRONMENT_URL}/ \
 | kubectl create -n "$KUBE_NAMESPACE" -o yaml --dry-run -f - \
 | kubectl replace -n "$KUBE_NAMESPACE" --force -f -

# once we are done show everything again
kubectl get all -n "$KUBE_NAMESPACE"