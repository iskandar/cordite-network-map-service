# Network Map Service FAQ

## Contents

1. [Show me how to setup a simple network](#1-show-me-how-to-set-up-a-simple-network)
2. [How do I set up TLS](#2-how-do-i-setup-tls)
3. [How do I add a node to a network run using Java?](#3-how-do-i-add-a-node-to-a-network-run-using-java)

## Questions

### 1. Show me how to set up a simple network

There's two ways you can start a full network:

* [Using Docker](#using-docker)
* [Using Java](#using-java)

Once the service is running, you will be able to use the Admin UI for accessing network map at `https://localhost:8080` 
with username 'sa' and password 'admin'.

#### Using Docker

Start the docker instance. 

```bash
docker run -p 8080:8080 cordite/network-map
```

This will start the server, without TLS, and with doorman and certman enabled.

You should see the following output:

```bash
starting networkmap with the following options
auth-password               - ****
auth-username               - sa
cache-timeout               - 2S
certman                     - true
certman-pkix                - false
certman-strict-ev           - false
certman-truststore          - 
certman-truststore-password - 
db                          - .db
doorman                     - true
hostname                    - 0.0.0.0
network-map-delay           - 1S
param-update-delay          - 10S
port                        - 8080
root-ca-name                - CN="<replace me>", OU=Cordite Foundation Network, O=Cordite Foundation, L=London, ST=London, C=GB
tls                         - false
tls-cert-path               - 
tls-key-path                - 
web-root                    - /
...
2018-11-20 15:43:22,680 INFO  i.b.braid.corda.CordaSockJSHandler - root API mount for braid: /braid/api/
2018-11-20 15:43:22,681 INFO  i.b.braid.corda.CordaSockJSHandler - mounting braid service network to http://localhost:8080/braid/api/network/braid/*
2018-11-20 15:43:22,681 INFO  i.b.braid.corda.CordaSockJSHandler - mounting braid service flows to http://localhost:8080/braid/api/flows/braid/*
2018-11-20 15:43:22,681 INFO  i.b.braid.corda.CordaSockJSHandler - mounting braid service admin to http://localhost:8080/braid/api/admin/braid/*
2018-11-20 15:43:22,695 INFO  i.b.braid.corda.rest.RestMounter - swagger json bound to http://localhost:8080/swagger/swagger.json
2018-11-20 15:43:22,696 INFO  i.b.braid.corda.rest.RestMounter - Swagger UI bound to http://localhost:8080/swagger
2018-11-20 15:43:23,228 INFO  i.b.braid.corda.rest.RestMounter - REST end point bound to http://localhost:8080/
2018-11-20 15:43:23,352 INFO  BasicInfo - Braid server started on                 : http://localhost:8080/braid/api/
2018-11-20 15:43:23,487 INFO  i.c.n.s.NetworkMapServiceProcessor - saving network map
2018-11-20 15:43:23,506 INFO  io.cordite.networkmap.NetworkMapApp - started
...

```

Assuming you have a cordapp project based on the [Kotlin template](https://github.com/corda/cordapp-template-kotlin)

```bash

# we'll setup PartyA to use the network
cd build/nodes/PartyA

# delete all certificates, all additionalNodeInfos, the database, the network parameters
rm -rf persistence.mv.db nodeInfo-* network-parameters certificates additional-node-infos

# append a line to point to the network map 
echo "compatibilityZoneURL=\"http://localhost:8080\"\n" >> node.conf

# download the network map trust store
curl -o /var/tmp/network-truststore.jks http://localhost:8080//network-map/truststore

# initialise the node through the doorman
java -jar corda.jar --initial-registration --network-root-truststore /var/tmp/network-truststore.jks --network-root-truststore-password trustpass

```

If everything was successful you should see:

```bash
Successfully registered Corda node with compatibility zone, node identity keys and certificates are stored in '/Users/fuzz/dev/cordapp-template-kotlin/build/nodes/PartyA/certificates', it is advised to backup the private keys and certificates.
Corda node will now terminate.
```

#### Using Java

Right now, this project doesn't deploy to a public repo (it will soon!). In the meantime ...

Install dependencies: 
* JDK 8u181
* NodeJS 11

Then build the project:
```bash 
mvn clean install -DskipTests
```

Execute the networkmap:

```bash
cd target
java -jar network-map-service.jar
```

Then following the remaining instruction in the [docker case](#using-docker)

### 2. How do I set up TLS?

Corda places certain requirements for connecting to any network map that's been secure with TLS.
Notably it requires formal certificates from any of the existing well-know root certificate authorities, recognised by the JRE.

Therefore to enable TLS, you will need:

* A DV certificate from any of the major CAs. You can get a free one from [Let's Encrypt](https://letsencrypt.org/)
* You need to be running your NMS on a server with the hostname referenced by the certificate. It's not recommended to try this on a dev laptop/workstation.

Then you will need to configure the NMS to use your certificate and private key. 
The following are instructions for doing this using both Docker as well as the java command line.

#### Using TLS certificates with the Docker NMS image

Assuming you have a directory on your host called with path `/opt/my-certs`, containing your certificate `tls.crt` and private key `tls.key`.

```
docker run -p 8080:8080 \
    -e NMS_TLS=true \
    -e NMS_TLS_CERT_PATH=/opt/certs/tls.crt \
    -e NMS_TLS_KEY_PATH=/opt/certs/tls.key \
    -v /opt/my-certs:/opt/certs \
    cordite/network-map
```

#### Using TLS certificates when running the NMS jar using Java

Again, assuming the same certificate and key paths, you can pass these in using 
Java system properties e.g.

```
java \
-Dtls=true \
-Dtls-cert-path=/opt/my-certs/tls.crt \
-Dtls-key-path=/opt/my-certs/tls.key \
-jar target/network-map-service.jar
```

### 3. How do I add a node to a network run using Java?

  + Start the network map service with TLS disabled (`$ java -Dtls=false -jar target/network-map-service.jar`)
    + If you don't disable TLS and you don't have a valid TLS certificate for the network map service, nodes will not 
      be able to join the network
  + Create a Corda node
  + Clean out the node if required by deleting the contents of the `certificates` and `additional-node-infos` folders, and the `persistence.mv.db` and `network-parameters` files 
  + Point the node to your network map service by adding the following line to the node's `node.conf` file: 
    `compatibilityZoneURL="http://localhost:8080"`
  + Download the network root truststore from `http://localhost:8080/network-map/truststore` and place it in the node's 
    folder under `certificates/`
  + Register the node with the network map service using `java -jar corda.jar --initial-registration --network-root-truststore-password trustpass`
  + Start the node using `java -jar corda.jar`
  + Visit the network map UI at `https://localhost:8080` to see the node
