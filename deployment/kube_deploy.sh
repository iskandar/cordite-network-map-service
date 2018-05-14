#!/bin/sh
# deploy app to kube
# usage ./kube_deploy.sh

IMAGE_TAG=${CI_PIPELINE_ID:-latest}
KUBE_NAMESPACE=${KUBE_NAMESPACE:-default}
GITLAB_USER_EMAIL=${GITLAB_USER_EMAIL:-nobody@example.com}
CI_ENVIRONMENT_SLUG=${CI_ENVIRONMENT_SLUG:-network-map-dev}

echo "$(date) rebuilding environment ${CI_ENVIRONMENT_SLUG} in namespace ${KUBE_NAMESPACE} with image ${IMAGE_TAG}"

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
echo "all resources under namespace ${KUBE_NAMESPACE}"
kubectl get all,pv,pvc,sc -n "$KUBE_NAMESPACE"

# re-create gitlab reg secret
kubectl create secret -n "$KUBE_NAMESPACE" \
    docker-registry nms-registry \
    --docker-server="$CI_REGISTRY" \
    --docker-username="${CI_REGISTRY_USER}" \
    --docker-password="${CI_REGISTRY_PASSWORD}" \
    --docker-email="$GITLAB_USER_EMAIL" \
    -o yaml --dry-run | kubectl replace -n "$KUBE_NAMESPACE" --force -f -

# delete & create deployment
kubectl delete deployment,svc,pv,pvc,sc -n "$KUBE_NAMESPACE" -l app=${CI_ENVIRONMENT_SLUG}
cat ./deployment.yaml \
 | sed s/:latest/:${IMAGE_TAG}/ \
 | sed s/network-map-dev/${CI_ENVIRONMENT_SLUG}/ \
 | kubectl create -n "$KUBE_NAMESPACE" -f -

# once we are done show everything again
kubectl get all,pv,pvc,sc -n "$KUBE_NAMESPACE"

echo "to see the logs run $kubectl -n $KUBE_NAMESPACE logs deployment/${CI_ENVIRONMENT_SLUG}"
echo "to wait for the public IP to be available use $kubectl -n $KUBE_NAMESPACE -l app=${CI_ENVIRONMENT_SLUG} get services --watch"

echo "$(date) finished rebuilding environment ${CI_ENVIRONMENT_SLUG} in namespace ${KUBE_NAMESPACE} with image ${IMAGE_TAG}"