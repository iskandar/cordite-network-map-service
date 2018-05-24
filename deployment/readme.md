# Deployment

## To do
- [ ] kube_deploy.sh is not bullet proof and in need of TLC

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

## Things they don't tell you
  + $KUBE_CONFIG is a CI variable which deals with all security context on Kube runner
  + Adding label app=<environment> will make environments and metrics work in gitlab

### External DNS (cluster wide resource)
We are using CloudFlare and Kube ExternalDNS - https://github.com/kubernetes-incubator/external-dns
To find out more see - https://github.com/kubernetes-incubator/external-dns/blob/master/docs/tutorials/cloudflare.md  
Only need one of these per domain in the cluster kube-system namespace 
Follow the logs with `kubectl -n kube-system logs deployment/external-dns`


### TLS certificates (cluster wide resource)
We are using CloudFlare and Kube cert-manager - https://cert-manager.readthedocs.io/
Only need one of these per domain in the cluster kube-system namespace 
Follow the logs on the cert-manager pod in namespace=kube-system
```
kubectl get certificates -n currency-pay-dgl
kubectl describe certificate nwm-dgl-edge-cordite-biz -n currency-pay-dgl
```