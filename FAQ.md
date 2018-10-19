# Network Map Service Wiki

## Contents

1. [How do I setup TLS](1-how-do-i-setup-tls)

## Questions

### 1. How do I setup TLS?

Corda places certain requirements for connecting to any network map that's been secure with TLS.
Notably it requires formal certificates from any of the existing well-know root certificate authorities, recognised by the JRE.

Therefore to enable TLS, you will need:

* A DV certificate from any of the major CAs. You can get a free one from [Let's Encrypt](https://letsencrypt.org/)
* You need to be running your NMS on a server with the hostname referenced by the certificate

Then you will need to configure the NMS to use your certificate and private key. 
The following are instructions for doing this using both Docker as well as the java command line.

#### Using TLS certificates with the Docker NMS image

Assuming you have a directory on your host called with path `/opt/my-certs`, containing your certificate `tls.crt` and private key `tls.key`.

```
docker run -p 8080:8080 -e NMS_TLS=true -e NMS_TLS_CERT_PATH=/opt/certs/tls.crt -e NMS_TLS_KEY_PATH=/opt/certs/tls.key -v /opt/my-certs:/opt/certs cordite/network-map
```

#### Using TLS certificates when running the NMS jar using Java

Again, assuming the same certificate and key paths, you can pass these in using 
Java system properties e.g.

```
java -Dtls=true -Dtls-cert-path=/opt/my-certs/tls.crt -Dtls-key-path=/opt/my-certs/tls.key -jar target/network-map-service.jar
```

