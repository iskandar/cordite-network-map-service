## Contents

- [Supported tags and respective Dockerfile links](#supported-tags-and-respective-dockerfile-links)
- [Design criteria and Features](#design-criteria-and-features)
- [Backlog of Features, Improvements, and Optimisations](#backlog-of-features--improvements--and-optimisations)
- [FAQ](#faq)
- [How do I get in touch?](#how-do-i-get-in-touch-)
- [What if something does not work?](#what-if-something-does-not-work-)
- [How do I contribute?](#how-do-i-contribute-)
- [Who is behind the Network Map Service?](#who-is-behind-the-network-map-service-)
- [What open source license has this been released under?](#what-open-source-license-has-this-been-released-under-)
- [How do I start the network map service?](#how-do-i-start-the-network-map-service-)
  * [Using Docker](#using-docker)
  * [Using Java](#using-java)
- [Command line parameters](#command-line-parameters)
- [Doorman protocol](#doorman-protocol)
  * [Retrieving the NetworkMap `network-map-truststore.jks`](#retrieving-the-networkmap--network-map-truststorejks-)
- [Certman protocol](#certman-protocol)
- [How do I add a node to a network run using Java?](#how-do-i-add-a-node-to-a-network-run-using-java-)
- [Releasing NMS](#releasing-nms)
- [License](#license)

## Supported tags and respective Dockerfile links
* `v0.3.3`, `latest` - latest stable release
* `edge` - latest master build, unstable

## Design criteria and Features
1. Meet the requirements of the [Corda Network Map Service protocol](https://docs.corda.net/network-map.html), both documented and otherwise
2. A nominal implementation of the Doorman protocol
3. A new protocol, [_Certman_](#certman-protocol), for registration with client-provided certificates
4. Completely stateless - capable of running in load-balanced clusters
5. Efficient use of I/O to serve 5000+ concurrent read requests per second from a modest server
6. Transparent filesystem design to simplify maintenance, backup, and testing

## Backlog of Features, Improvements, and Optimisations

See our issues board for what this networkmap can't do as yet:

https://gitlab.com/cordite/network-map-service/boards

## FAQ

See [here](FAQ.md).

## How do I get in touch?
  + News is announced on [@We_are_Cordite](https://twitter.com/we_are_cordite)
  + More information can be found on [Cordite website](https://cordite.foundation)
  + We use #cordite channel on [Corda slack](https://slack.corda.net/) 
  + We informally meet at the [Corda London meetup](https://www.meetup.com/pro/corda/)

## What if something does not work?
We encourage you to raise any issues/bugs you find in Cordite. Please follow the below steps before raising issues:
   1. Check on the [Issues backlog](https://gitlab.com/cordite/network-map-service/issues) to make sure an issue on the topic has not already been raised
   2. Post your question on the #cordite channel on [Corda slack](https://slack.corda.net/)
   3. If none of the above help solve the issue, [raise an issue](https://gitlab.com/cordite/network-map-service/issues/new?issue) following the contributions guide

## How do I contribute?
We welcome contributions both technical and non-technical with open arms! There's a lot of work to do here. The [Contributing Guide](https://gitlab.com/cordite/network-map-service/blob/master/contributing.md) provides more information on how to contribute.

## Who is behind the Network Map Service?
Network Map Service is being developed by a group of financial services companies, software vendors and open source contributors. The project is hosted on here on GitLab. 

## What open source license has this been released under?
All software in this repository is licensed under the Apache License, Version 2.0 (the "License"); you may not use this software except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0. Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.

## How do I start a simple network on my local workstation or laptop?

See the detailed instructions in the [FAQ](FAQ.md#1-show-me-how-to-set-up-a-simple-network)

You can configure the service using `-D` system properties. See the section for 
[command line parameters](#command-line-parameters).

## Command line parameters

| Property                    | Env Variable                        | Default                                                                                               | Description                                                                                                              |
| --------------------------- | ----------------------------------- | ----------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------ |
| auth-password               | NMS_AUTH_PASSWORD                   | admin                                                                                                 | system admin password                                                                                                    |
| auth-username               | NMS_AUTH_USERNAME                   | sa                                                                                                    | system admin username                                                                                                    |
| cache-timeout               | NMS_CACHE_TIMEOUT                   | 2S                                                                                                    | http cache timeout for this service in ISO 8601 duration format                                                          |
| certman                     | NMS_CERTMAN                         | true                                                                                                  | enable Cordite certman protocol so that nodes can authenticate using a signed TLS cert                                   |
| certman-pkix                | NMS_CERTMAN_PKIX                    | false                                                                                                 | enables certman's pkix validation against JDK default truststore                                                         |
| certman-strict-ev           | NMS_CERTMAN_STRICT_EV               | false                                                                                                 | enables strict constraint for EV certs only in certman                                                                   |
| certman-truststore          | NMS_CERTMAN_TRUSTSTORE              |                                                                                                       | specified a custom truststore instead of the default JRE cacerts                                                         |
| certman-truststore-password | NMS_CERTMAN_TRUSTSTORE_PASSWORD     |                                                                                                       | truststore password                                                                                                      |
| db                          | NMS_DB                              | .db                                                                                                   | database directory for this service                                                                                      |
| doorman                     | NMS_DOORMAN                         | true                                                                                                  | enable Corda doorman protocol                                                                                            |
| hostname                    | NMS_HOSTNAME                        | 0.0.0.0                                                                                               | interface to bind the service to                                                                                         |
| network-map-delay           | NMS_NETWORK_MAP_DELAY               | 1S                                                                                                    | queue time for the network map to update for addition of nodes                                                           |
| param-update-delay          | NMS_PARAM_UPDATE_DELAY              | 10S                                                                                                   | schedule duration for a parameter update                                                                                 |
| port                        | NMS_PORT                            | 8080                                                                                                  | web port                                                                                                                 |
| root-ca-name                | NMS_ROOT_CA_NAME                    | CN="<replace me>", OU=Cordite Foundation Network, O=Cordite Foundation, L=London, ST=London, C=GB     | the name for the root ca. If doorman and certman are turned off this will automatically default to Corda dev root ca     |
| tls                         | NMS_TLS                             | false                                                                                                 | whether TLS is enabled or not                                                                                            |
| tls-cert-path               | NMS_TLS_CERT_PATH                   |                                                                                                       | path to cert if TLS is turned on                                                                                         |
| tls-key-path                | NMS_TLS_KEY_PATH                    |                                                                                                       | path to key if TLS turned on                                           

## Doorman protocol

This network map supports the Corda doorman protocol. This facility can be disabled with `doorman` system property or `NMS_DOORMAN` environment variable.

### Retrieving the NetworkMap `network-map-truststore.jks`

If you wish to use the doorman protocol to register a node as per [Corda](https://docs.corda.net/permissioning.html#connecting-to-a-compatibility-zone) you will need the network's `network-map-truststore.jks`.

You can do this using the url `<network-map-url>/network-map/truststore`.

## Certman protocol

This network map provides an alternative means of gaining the required [keystore files](https://docs.corda.net/permissioning.html#installing-the-certificates-on-the-nodes) using any TLS certificate and private key, issued by a formal PKI root CA.

Assuming you have certificate `domain.crt` and its corresponding private key `domain.key`, and assuming the network map is bound to `http://localhost:8080`, the following command line will retrieve the keystore files:

```bash
openssl dgst -sha256 -sign domain.key domain.crt | base64 | cat domain.crt - | curl -k -X POST -d @- http://localhost:8080/certman/api/generate -o keys.zip
```

This essentially signs the certificate with your private key and sends _only_ the certificate and signature to the network-map. 
If the certificate passes validation, the request returns a zip file of the keystores required by the node. 
These should be stored in the `<node-directory>/certificates`.

## How do I add a node to a network run using Java?
  + Start the network map service with TLS disabled (`$ java -Dtls=false -jar target/network-map-service.jar`)
    + If you don't disable TLS and you don't have a valid TLS certificate for the network map service, nodes will not 
      be able to join the network
  + Create a Corda node
  + Point the node to your network map service by adding the following line to the node's `node.conf` file: 
    `compatibilityZoneURL="http://localhost:8080"`
  + Download the network root truststore from `http://localhost:8080/network-map/truststore` and place it in the node's 
    folder under `certificates/`
  + Register the node with the network map service using `java -jar corda.jar --initial-registration --network-root-truststore-password trustpass`
  + Start the node using `java -jar corda.jar`
  + Visit the network map UI at `https://localhost:8080` to see the node

## Releasing NMS

To release NMS you just need to tag it.  It is then released to docker hub.

## License
View [license information](https://gitlab.com/cordite/cordite/blob/master/LICENSE) for the software contained in this image.

As with all Docker images, these likely also contain other software which may be under other licenses (such as Bash, etc from the base distribution, along with any direct or indirect dependencies of the primary software being contained).

As for any pre-built image usage, it is the image user's responsibility to ensure that any use of this image complies with any relevant licenses for all software contained within.