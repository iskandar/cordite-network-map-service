#!/bin/sh

IMAGE=registry.gitlab.com/cordite/network-map-service/network-map-service:latest
echo "logging into gitlab docker registery"
echo "registery username:"
read -r username
echo "registery password:"
read -s password

az login
az container delete --name cordite-network-map
az container create --resource-group cordite-network-map --name cordite-network-map --image ${IMAGE} --ports 8080 --registry-login-server registry.gitlab.com --registry-username ${username} --registry-password ${password} --dns-name-label cordite-network-map
echo 'running: az container show --resource-group cordite-network-map --name cordite-network-map --query "{FQDN:ipAddress.fqdn,ProvisioningState:provisioningState}" --out table'
az container show --resource-group cordite-network-map --name cordite-network-map --query "{FQDN:ipAddress.fqdn,ProvisioningState:provisioningState}" --out table

