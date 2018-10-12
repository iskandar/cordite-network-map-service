FROM openjdk:8-jre
MAINTAINER Cordite Foundation <devops@cordite.foundation>
EXPOSE 80
ENTRYPOINT ["/usr/bin/java", "-jar", "/usr/share/cordite/network-map-service.jar", "-Dport=80"]
ADD target/network-map-service.jar /usr/share/cordite/network-map-service.jar