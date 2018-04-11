#!/bin/sh

IMAGE=registry.gitlab.com/cordite/network-map-service/network-map-service:latest

PORT=80

status() {
  echo $(az container show --resource-group cordite-network-map --name cordite-network-map --query "{FQDN:ipAddress.fqdn,ProvisioningState:provisioningState}" --out table)
}

isContainerStillBeingDeployed() {
  stat=$(status)
  count=$(echo ${stat} | grep "Creating" | wc -l)
  return $((count > 0))
}

echo "logging into gitlab docker registry"
echo "registry username:"
read -r USERNAME
echo "registry password:"
read -s PASSWORD

az login
echo "deleting the existing container if it exists"
az container delete --resource-group cordite-network-map --name cordite-network-map
echo "creating the new container"
az container create --resource-group cordite-network-map --name cordite-network-map --image ${IMAGE} --ports ${PORT} --registry-login-server registry.gitlab.com --registry-username ${USERNAME} --registry-password ${PASSWORD} --dns-name-label cordite-network-map -e port=80
echo "awaiting for completion of deployment"
while ! isContainerStillBeingDeployed
do
printf "."
sleep 5
done

echo $(status)
