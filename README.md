[![pipeline status](https://gitlab.com/cordite/network-map-service/badges/master/pipeline.svg)](https://gitlab.com/cordite/network-map-service/commits/master)



# CI/CD
  + This repo is integrated to Azure AKS. See CI/CD->Kubernetes for details of which cluster.
  + The cluster has a runner deployed and all CI jobs in gitlab-ci.yaml spawn pods in the cluster to run.
  + network-map-service is deployed by the CI using `./deployment/kube_deploy.sh`
  + DNS is provided by CloudFlare and configured using [external-dns](https://github.com/kubernetes-incubator/external-dns)
  + external-dns runs in the kube-system namespace and is deployed using `./deployment/external-dns.yaml'
  + More details on CI/CD and recreating this integration can be found in `./deployment/readme.md`
  + To do : Add persistent storge mapped to NMS_DB_DIR
  + to do : Allow pods to scale horizontally with replica

# Command line parameters

| Property   | Env Variable   | Default             | Description           |
| ---------- | -------------- | ------------------- | --------------------- |
| port       | NMS_PORT       | 8080                | web port              |
| notary.dir | NMS_NOTARY_DIR | notary-certificates | notary cert directory |
| db.dir | NMS_DB_DIR | .db | directory for storing state. at present only the whitelist.txt file |

