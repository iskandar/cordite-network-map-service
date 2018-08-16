## Supported tags and respective Dockerfile links
* `v0.2.1`, `latest` - latest stable release
* `edge` - latest master build, unstable

## Design Criteria
1. Meet the requirements of the [Corda Network Map Service protocol](https://docs.corda.net/network-map.html), both documented and otherwise.
2. Completely stateless - capable of running in load-balanced clusters.
3. Efficient use of I/O to serve 5000+ concurrent read requests per second from a modest server.
4. Transparent filesystem design to simplify maintenance, backup, and testing.

## How to get in touch?
We use #cordite channel on [Corda slack](https://slack.corda.net/) 
We informally meet at the [Corda London meetup](https://www.meetup.com/pro/corda/)


## How to use this image
```  
$ docker run -p 8080:8080 cordite/network-map
```
Once the node is running, you will be able to see the UI for accessing network map at `https://localhost:8080`

## Command line parameters / Environment Variables

| Property          | Env Variable              | Default     | Description                                                                        |
| ----------------- | ------------------------- | ----------- | ---------------------------------------------------------------------------------- |
| port              | NMS_PORT                  | 8080        | web port                                                                           |
| db                | NMS_DB                    | .db         | database directory for this service                                                |
| cache.timeout     | NMS_CACHE_TIMEOUT         | 2S          | http cache timeout for this service in ISO 8601 duration format                    |
| paramUpdate.delay | NMS_PARAMUPDATE_DELAY     | 10S         | schedule duration for a parameter update                                           |
| networkMap.delay  | NMS_NETWORKMAP_DELAY      | 1S          | queue time for the network map to update for addition of nodes                     |
| username          | NMS_USERNAME              | sa          | system admin username                                                              |
| password          | NMS_PASSWORD              | admin       | system admin password                                                              |
| tls               | NMS_TLS                   | true        | whether TLS is enabled or not                                                      |
| tls.cert.path     | NMS_TLS_CERT_PATH         |             | path to cert if TLS is turned on                                                   |
| tls.key.path      | NMS_TLS_KEY_PATH          |             | path to key if TLS turned on                                                       |
| hostname          | NMS_HOSTNAME              | 0.0.0.0     | interface to bind the service to                                                   |
| doorman           | NMS_DOORMAN               | true        | enable doorman protocol                                                            |
| certman           | NMS_CERTMAN               | true        | enable certman protocol so that nodes can authenticate using a signed TLS cert     |


## Doorman protocol

This network map supports the Corda doorman protocol. This facility can be disabled with `doorman` system property or `NMS_DOORMAN` environment variable.

### Retrieving the NetworkMap `network-map-truststore.jks`

If you wish to use the doorman protocol to register a node as per defined by [Corda](https://docs.corda.net/permissioning.html#connecting-to-a-compatibility-zone) you will need the network's `network-map-truststore.jks`.

You can do this using the url `<network-map-url>/network-map/truststore`

## Certman protocol

This network map provides an alternative means of gaining the required [keystore files](https://docs.corda.net/permissioning.html#installing-the-certificates-on-the-nodes) using any TLS certificate and private key, issued by a formal PKI root CA.

Assuming you have certificate `domain.crt` and its corresponding private key `domain.key`, and assuming the network map is bound to `http://localhost:8080`, the following command line will retrieve the keystore files:

```bash
openssl dgst -sha256 -sign domain.key domain.crt | base64 | cat domain.crt - | curl -k -X POST -d @- http://localhost:8080/certman/api/generate -o keys.zip
```

This essentially signs the certificate with your private key and sends _only_ the certificate and signature to the network-map. 
If the certificate passes validation it, the request returns a zip file of the keystores required by the node. 
These should be stored in the `<node-directory>/certificates`.  

## License
View [license information](https://gitlab.com/cordite/cordite/blob/master/LICENSE) for the software contained in this image.

As with all Docker images, these likely also contain other software which may be under other licenses (such as Bash, etc from the base distribution, along with any direct or indirect dependencies of the primary software being contained).

As for any pre-built image usage, it is the image user's responsibility to ensure that any use of this image complies with any relevant licenses for all software contained within.