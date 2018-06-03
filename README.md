[![pipeline status](https://gitlab.com/cordite/network-map-service/badges/master/pipeline.svg)](https://gitlab.com/cordite/network-map-service/commits/master)

# Design Criteria

0. Meet the requirements of the Corda Network Map Service protocol, both documented and otherwise.
1. Completely stateless - capable of running in load-balanced clusters.
2. Efficient use of I/O to serve 5000+ concurrent read requests per second from a modest server.
3. Transparent filesystem design to simplify maintenance, backup, and testing.

# CI/CD
  + This repo is integrated to Azure AKS. See CI/CD->Kubernetes for details of which cluster.
  + The cluster has a runner deployed and all CI jobs in gitlab-ci.yaml spawn pods in the cluster to run.
  + network-map-service environment can be built using `./deployment/kube_deploy.sh`
  + DNS is provided by CloudFlare and configured using [external-dns](https://github.com/kubernetes-incubator/external-dns)
  + external-dns runs in the kube-system namespace and is deployed using `./deployment/external-dns.yaml'
  + More details on CI/CD and recreating this integration can be found in `./deployment/readme.md`
  + Persistent storge mapped to NMS_DB_DIR under storage account `corditeedge8` as an Azure file share
  + Release CI job uses deployment rollout strategy to release newer version of image
  + Pods can scale horizontally by changing `replicas: 1` in `./deployment/deployment.yaml`

# Command line parameters

| Property          | Env Variable              | Default   | Description                                                         |
| ----------------- | ------------------------- | --------- | ------------------------------------------------------------------- |
| port              | NMS_PORT                  | 8080      | web port                                                            |
| db                | NMS_DB                    | .db       | database directory for this service                                 |
| cache.timeout     | NMS_CACHE_TIMEOUT         | 2S        | http cache timeout for this service in ISO 8601 duration format     |
| paramUpdate.delay | NMS_PARAMUPDATE_DELAY     | 10S       | schedule duration for a parameter update                            |
| networkMap.delay  | NMS_NETWORKMAP_DELAY      | 1S        | queue time for the network map to update for addition of nodes      |
| username          | NMS_USERNAME              | sa        | system admin username                                               |
| password          | NMS_PASSWORD              | admin     | system admin password                                               |


# Certs
```
helm install \
  --name cert-manager \
  --namespace kube-system \
  --set rbac.create=false \
  stable/cert-manager
```