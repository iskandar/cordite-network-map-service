FROM openjdk:8-jre
MAINTAINER Fuzz <fuzz@bluebank.io>
EXPOSE 8080
ENTRYPOINT ["/usr/bin/java", "-jar", "/usr/share/myservice/network-map-service.jar"]
ADD target/network-map-service.jar /usr/share/myservice/network-map-service.jar