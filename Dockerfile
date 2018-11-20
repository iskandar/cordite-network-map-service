# Copyright 2018, Cordite Foundation.

#  Licensed under the Apache License, Version 2.0 (the "License");
#  you may not use this file except in compliance with the License.
#  You may obtain a copy of the License at

#    http://www.apache.org/licenses/LICENSE-2.0

#  Unless required by applicable law or agreed to in writing, software
#  distributed under the License is distributed on an "AS IS" BASIS,
#  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#  See the License for the specific language governing permissions and
#  limitations under the License.

FROM openjdk:8u181-jdk-alpine3.8

LABEL VENDOR="Cordite.foundation" \
      MAINTAINER="devops@cordite.foundation"

EXPOSE 8080

ENV NMS_TLS=false
ENV NMS_DOORMAN=true
ENV NMS_CERTMAN=true
ENV NMS_PORT=8080
ENV NMS_DB=db

WORKDIR /opt/cordite

RUN mkdir -p /opt/cordite/db /opt/cordite/logs \
 && addgroup -g 1000 -S cordite \
 && adduser -u 1000 -S cordite -G cordite \
 && chgrp -R 0 /opt/cordite \
 && chmod -R g=u /opt/cordite \
 && chown -R cordite:cordite /opt/cordite

USER cordite

VOLUME /opt/cordite/db /opt/cordite/logs

COPY target/network-map-service.jar /opt/cordite/network-map-service.jar

CMD ["/usr/bin/java", "-jar", "/opt/cordite/network-map-service.jar"]
