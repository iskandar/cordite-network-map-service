# Network Map Service FAQ

## Contents

1. [Show me how to setup a simple network](#1-show-me-how-to-set-up-a-simple-network)
2. [How do I set up TLS](#2-how-do-i-setup-tls)
3. [How do I add a node to a network run using Java?](#3-how-do-i-add-a-node-to-a-network-run-using-java)

## Questions

### 1. Show me how to set up a simple network

Steps:

* 1.1 Start the NMS
* 1.2 Prepare the Cordapp project
* 1.3 Register the Nodes
* 1.4 Designate the Notary

Video of the following section being demonstrated on a laptop:

<figure class="video_container">
  <iframe width="560" height="315" src="https://www.youtube.com/embed/NczNdVxEZyM" frameborder="0" allow="accelerometer; autoplay; encrypted-media; gyroscope; picture-in-picture" allowfullscreen></iframe>
</figure>

#### 1.1 Start the NMS ...

##### ... the Docker way
- `docker run -p 8080:8080 cordite/network-map:v0.3.6` 
- check it's started using a browser http://localhost:8080

##### ... the Java way

Alternatively you can run it using traditional Java development tools.

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

#### 1.2. Prepare the Cordapp project
- [ ] checkout the cordapp kotlin template (or any other cordapp project)
    
    ```bash
    git clone git@github.com:corda/cordapp-template-kotlin.git
    ```

- [ ] ensure that your cordapp X509 names have the following fields: 
  * `L` - Location
  * `C` - Country
  * `O` - Organisation
  * `OU` - Organisation Unit
- [ ] build the nodes:
  
  ```bash
  ./gradlew clean deployNodes
  ```
  
- [ ] add the `compatibilityZoneURL` to the node.config within each node directory 

  ```bash
  pushd build/nodes
  for N in */; do
        echo 'compatibilityZoneURL="http://localhost:8080"' >> $N/node.conf
  done
  popd
  ```
- [ ] ensure that all state is removed from the node directories
  
  ```bash
  pushd build/nodes
  for N in */; do
        pushd $N
        rm -rf network-parameters nodeInfo-* persistence.mv.db certificates/*
        popd
  done
  popd
  ```
  
#### 1.3. Register the nodes
  - [ ] download the network truststore

      ```bash 
      curl http://localhost:8080/network-map/truststore -o ~/tmp/network-truststore.jks
      ```
  - [ ] for each node run initial registration

    ```bash
    pushd build/nodes
    for N in */; do
          pushd $N
          java -jar corda.jar --initial-registration --network-root-truststore ~/tmp/network-truststore.jks --network-root-truststore-password trustpass
          popd
    done
    popd
    ```
  - [ ] in each node directory, start the node:

    ```bash
    java -jar corda.jar
    ```
  
  - [ ] check that nodes have been registered with the NMS [http://localhost:8080](http://localhost:8080)

#### 1.4 Designate the notary
- [ ] login to the NMS API and cache the token

  ```bash
  TOKEN=`curl -X POST "http://localhost:8080//admin/api/login" -H  "accept: text/plain" -H  "Content-Type: application/json" -d "{  \"user\": \"sa\",  \"password\": \"admin\"}"`
  ```

- [ ] Upload the notary

    ```bash
    pushd build/nodes/Notary
    NODEINFO=`ls nodeInfo*`
    curl -X POST -H "Authorization: Bearer $TOKEN" -H "accept: text/plain" -H "Content-Type: application/octet-stream" --data-binary @$NODEINFO http://localhost:8080//admin/api/notaries/validating
    popd
    ```

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
