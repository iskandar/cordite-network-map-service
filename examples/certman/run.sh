#!/bin/bash
openssl dgst -sha256 -sign domain.key domain.crt | base64 | cat domain.crt - | curl -k -X POST -d @- http://localhost:8080/certman/api/generate -o download/keys.zip
pushd download
rm -rf *.jks
unzip keys.zip
popd
rm -rf node/certificates/*
cp download/*.jks node/certificates/

