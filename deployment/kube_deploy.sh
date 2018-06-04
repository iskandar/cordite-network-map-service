#!/bin/sh
# deploy app to kube
# usage ./kube_deploy.sh

#!/bin/sh
# deploy app to kube
# usage ./kube_deploy.sh

IMAGE_TAG=${CI_PIPELINE_ID:-latest}
KUBE_NAMESPACE=${KUBE_NAMESPACE:-network-map-service}
GITLAB_USER_EMAIL=${GITLAB_USER_EMAIL:-nobody@example.com}
CI_ENVIRONMENT_SLUG=${CI_ENVIRONMENT_SLUG:-network-map-dev}
NMS_REG_URL=${NMS_REG_URL:-registry.gitlab.com}

echo \xE2\x9C\x94\033[1m Context set\033[0m
echo IMAGE_TAG=$IMAGE_TAG
echo KUBE_NAMESPACE=$KUBE_NAMESPACE
echo GITLAB_USER_EMAIL=$GITLAB_USER_EMAIL
echo CI_ENVIRONMENT_SLUG=$CI_ENVIRONMENT_SLUG
echo NMS_REG_URL=$NMS_REG_URL
echo KUBECTL CURRENT-CONTEXT=$(kubectl config current-context)

echo "\xE2\x9C\x94\033[1m $(date) starting rebuild of environment ${CI_ENVIRONMENT_SLUG} in namespace ${KUBE_NAMESPACE} with image tag ${IMAGE_TAG} in cluster $(kubectl config current-context)\033[0m"

set -e

# Check if we have variables set to access docker reg
if [ -z "$NMS_REG_URL" ] ; then
  echo "\xE2\x9D\x8C NMS_REG_URL not set. Value needs to be set to docker registry URL"
   exit 1
fi

if [ -z "$NMS_REG_USER" ] ; then
   echo "\xE2\x9D\x8C NMS_REG_USER not set. Value needs to be set to docker registry user"
   exit 1
fi

if [ -z "$NMS_REG_TOKEN" ] ; then
   echo "\xE2\x9D\x8C NMS_REG_TOKEN not set. Value needs to be set to docker registry password"
   exit 1
fi

# create namespace if we need to
if kubectl get namespaces | grep -q "${KUBE_NAMESPACE}"; then
  echo "\xE2\x9C\x94\033[1m ${KUBE_NAMESPACE} already exists.\033[0m"
else
  kubectl create namespace ${KUBE_NAMESPACE}
  echo "\xE2\x9C\x94\033[1m created namespace: ${KUBE_NAMESPACE}\033[0m"
fi

# re-create gitlab reg secret
kubectl create secret -n "$KUBE_NAMESPACE" \
    docker-registry nms-registry \
    --docker-server="${NMS_REG_URL}" \
    --docker-username="${NMS_REG_USER}" \
    --docker-password="${NMS_REG_TOKEN}" \
    --docker-email="$GITLAB_USER_EMAIL" \
    -o yaml --dry-run | kubectl replace -n "$KUBE_NAMESPACE" --force -f -
echo "\xE2\x9C\x94\033[1m recreated secret: nmsregistrykey\033[0m"

# delete deployment if it exists
kubectl delete -n "$KUBE_NAMESPACE" -f deployment.yaml --ignore-not-found=true
echo "\xE2\x9C\x94\033[1m deleted deployment in $KUBE_NAMESPACE\033[0m"

# create deployment
kubectl create -n "$KUBE_NAMESPACE" -f deployment.yaml
echo "\xE2\x9C\x94\033[1m created deployment in $KUBE_NAMESPACE\033[0m"

# use create --dry run -f - | replace pattern
# cat ./deployment.yaml \
# | sed s/:latest/:${IMAGE_TAG}/ \
# | sed s/network-map-dev/${CI_ENVIRONMENT_SLUG}/ \
# | kubectl create -n "$KUBE_NAMESPACE" -f -


echo "to see the logs run \033[1m kubectl -n $KUBE_NAMESPACE logs deployment/${CI_ENVIRONMENT_SLUG}\033[0m"
echo " to wait for the public IP to be available use \033[1m kubectl -n $KUBE_NAMESPACE -l app=${CI_ENVIRONMENT_SLUG} get services --watch\033[0m"

echo "\xE2\x9C\x94\033[1m $(date) finished rebuild of environment ${CI_ENVIRONMENT_SLUG} in namespace ${KUBE_NAMESPACE} with image tag ${IMAGE_TAG} in cluster $(kubectl config current-context)\033[0m"
