[![pipeline status](https://gitlab.com/cordite/network-map-service/badges/master/pipeline.svg)](https://gitlab.com/cordite/network-map-service/commits/master)



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

| Property   | Env Variable   | Default             | Description           |
| ---------- | -------------- | ------------------- | --------------------- |
| port       | NMS_PORT       | 8080                | web port              |
| notary.dir | NMS_NOTARY_DIR | notary-certificates | notary cert directory |
| db.dir | NMS_DB_DIR | .db | directory for storing state. at present only the whitelist.txt file |

