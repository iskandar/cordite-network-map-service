#!/bin/sh

IMAGE=registry.gitlab.com/cordite/network-map-service/network-map-service:latest
PORT=80
echo "logging into gitlab docker registery"
echo "registery username:"
read -r USERNAME
echo "registery password:"
read -s PASSWORD

az login
az container delete --resource-group cordite-network-map --name cordite-network-map
az container create --resource-group cordite-network-map --name cordite-network-map --image ${IMAGE} --ports ${PORT} --registry-login-server registry.gitlab.com --registry-username ${USERNAME} --registry-password ${PASSWORD} --dns-name-label cordite-network-map
echo 'running: az container show --resource-group cordite-network-map --name cordite-network-map --query "{FQDN:ipAddress.fqdn,ProvisioningState:provisioningState}" --out table'
az container show --resource-group cordite-network-map --name cordite-network-map --query "{FQDN:ipAddress.fqdn,ProvisioningState:provisioningState}" --out table

