#!/bin/sh

mkdir -p ${NMS_DB}/mongo
./mongodb-linux-x86_64-amazon2-4.0.4/bin/mongod -dbpath ${NMS_DB}/mongo &
java -jar /opt/cordite/network-map-service.jar
