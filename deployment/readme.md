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

Click to install Helm Tiller, Ingress (not working), Prometheus, Gitlab Runner on your cluster

### Things they don't tell you
  + $KUBE_CONFIG is a CI variable which deals with all security context on Kube runner
  + Adding label app=<environment> will allow environments to work in gitlab

## Manual setup of Ingress
Explain of ingress through example:
https://github.com/kubernetes/ingress-nginx/tree/master/docs/examples/static-ip
Use Azure portal and create a static public ip in the resource group of the cluster (prefix: MC_*)  
`brew install helm` if you haven't already got it.
```
helm install stable/nginx-ingress --name network-map-service \
    --set controller.stats.enabled=true \
    --set controller.metrics.enabled=true \
    --set controller.service.loadBalancerIP=23.97.163.246 \
    --set rbac.create=false \
    --namespace=kubes-system 
```