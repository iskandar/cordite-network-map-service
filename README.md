## Supported tags and respective Dockerfile links
* `v0.1.0`, `latest` - latest stable release
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

| Property          | Env Variable              | Default   | Description                                                         |
| ----------------- | ------------------------- | --------- | ------------------------------------------------------------------- |
| port              | NMS_PORT                  | 8080      | web port                                                            |
| db                | NMS_DB                    | .db       | database directory for this service                                 |
| cache.timeout     | NMS_CACHE_TIMEOUT         | 2S        | http cache timeout for this service in ISO 8601 duration format     |
| paramUpdate.delay | NMS_PARAMUPDATE_DELAY     | 10S       | schedule duration for a parameter update                            |
| networkMap.delay  | NMS_NETWORKMAP_DELAY      | 1S        | queue time for the network map to update for addition of nodes      |
| username          | NMS_USERNAME              | sa        | system admin username                                               |
| password          | NMS_PASSWORD              | admin     | system admin password                                               |
| tls               | NMS_TLS                   | true      | whether TLS is enabled or not                                       |
| tls.cert.path     | NMS_TLS_CERT_PATH         |           | path to cert if TLS is turned on                                    |
| tls.key.path      | NMS_TLS_KEY_PATH          |           | path to key if TLS turned on                                        |


## License
View [license information](https://gitlab.com/cordite/cordite/blob/master/LICENSE) for the software contained in this image.

As with all Docker images, these likely also contain other software which may be under other licenses (such as Bash, etc from the base distribution, along with any direct or indirect dependencies of the primary software being contained).

As for any pre-built image usage, it is the image user's responsibility to ensure that any use of this image complies with any relevant licenses for all software contained within.