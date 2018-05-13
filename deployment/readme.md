# Deployment

## Gitlab/Kubernetes integration
Use your BB gitlab project name for the namespace
```
$ kubectl create namespace network-map-service
$ kubectl -n network-map-service create serviceaccount network-map-service
$ kubectl -n network-map-service get sa/network-map-service -o yaml
$ kubectl -n network-map-service get secret network-map-service-token-<postfix> -o yaml
$ kubectl cluster-info
```
From the secret you need `ca.crt` and `token`. Both are coded in base64. Decode using `base64 -D`. for example
```
KUBE_TOKEN=$(kubectl -n currency-pay-dgl get secret currency-pay-dgl-token-2l6wn -o jsonpath={.data.token} | base64 -D)
```
From the cluster-info you need the URL shown as `Kubernetes master is running at`

### Add Kube config to repo
Go to CI/CD -> Kubernetes -> Add custom cluster. Complete the following fields and save.
   + Kubernetes cluster name : aks-region-a-aks
   + API URL : `Kube Master API URL from previous step`
   + CA Certficate : `decoded ca.crt from previous step`
   + Token : `decoded token from previous step`
   + Project namespace : `name space from previous step`  

Click to install Helm Tiller, Ingress (not required), Prometheus (metrics), Gitlab Runner (for CI) on your cluster

## External DNS
We are using CloudFlare and Kube ExternalDNS - https://github.com/kubernetes-incubator/external-dns
To find out more see - https://github.com/kubernetes-incubator/external-dns/blob/master/docs/tutorials/cloudflare.md  
You need to set `$CF_API_KEY` to the cloudflare api key. `CF_API_EMAIL` may need to change.
Only need one of these per domain in the cluster kube-system namespace 
```
kubectl delete -n kube-system -f ./deployment/external-dns.yaml
cat ./deployment/external-dns.yaml \
 | sed s/REPLACE_WITH_YOUR_CF_API_KEY/${CF_API_KEY}/ \
 | kubectl create -n kube-system -f -
```
Follow the logs with `kubectl -n kube-system logs deployment/external-dns`

## Things they don't tell you
  + $KUBE_CONFIG is a CI variable which deals with all security context on Kube runner
  + Adding label app=<environment> will make environments and metrics work in gitlab

### Creating a new cluster on Azure (not advisable)
```
az login
az group create --name cordite-edge6 --location uksouth
az aks create --resource-group cordite-edge6 --name cordite-edge --node-count 3 --node-vm-size Standard_B2s --generate-ssh-keys --dns-name-prefix cordite-edge
```

### Logs, Kube UI
