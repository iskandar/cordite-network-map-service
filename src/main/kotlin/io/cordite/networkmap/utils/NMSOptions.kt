package io.cordite.networkmap.utils

import io.cordite.networkmap.service.CertificateManager
import io.cordite.networkmap.service.InMemoryUser
import net.corda.core.identity.CordaX500Name
import net.corda.nodeapi.internal.DEV_ROOT_CA
import java.io.File
import java.time.Duration

class NMSOptions : Options() {
  private val portOpt = addOption("port", "8080", "web port")
  private val dbDirectoryOpt = addOption("db", ".db", "database directory for this service")
  private val cacheTimeoutOpt = addOption("cache-timeout", "2S", "http cache timeout for this service in ISO 8601 duration format")
  private val paramUpdateDelayOpt = addOption("param-update-delay", "10S", "schedule duration for a parameter update")
  private val networkMapUpdateDelayOpt = addOption("network-map-delay", "1S", "queue time for the network map to update for addition of nodes")
  private val usernameOpt = addOption("auth-username", "sa", "system admin username")
  private val passwordOpt = addOption("auth-password", "admin", "system admin password")
  private val tlsOpt = addOption("tls", "false", "whether TLS is enabled or not")
  private val certPathOpt = addOption("tls-cert-path", "", "path to cert if TLS is turned on")
  private val keyPathOpt = addOption("tls-key-path", "", "path to key if TLS turned on")
  private val hostNameOpt = addOption("hostname", "0.0.0.0", "interface to bind the service to")
  private val doormanOpt = addOption("doorman", "true", "enable Corda doorman protocol")
  private val certmanOpt = addOption("certman", "true", "enable Cordite certman protocol so that nodes can authenticate using a signed TLS cert")
  private val certManpkixOpt = addOption("certman-pkix", "false", "enables certman's pkix validation against JDK default truststore")
  private val certmanTruststoreOpt = addOption("certman-truststore", "", "specified a custom truststore instead of the default JRE cacerts")
  private val certmanTruststorePasswordOpt = addOption("certman-truststore-password", "", "truststore password")
  private val certmanStrictEV = addOption("certman-strict-ev", "false", "enables strict constraint for EV certs only in certman")
  private val rootX509Name = addOption("root-ca-name", "CN=\"<replace me>\", OU=Cordite Foundation Network, O=Cordite Foundation, L=London, ST=London, C=GB", "the name for the root ca. If doorman and certman are turned off this will automatically default to Corda dev root ca")
  private val webRootOpt = addOption("web-root","/", "for remapping the root url for all requests")
  private val mongoConnectionOpt = addOption("mongo-connection-string", "embed", "MongoDB connection string. If set to `embed` will start its own mongo instance")
  private val mongodLocationOpt = addOption("mongod-location", "", "optional location of pre-existing mongod server")

  val port get() = portOpt.intValue
  val dbDirectory get() = dbDirectoryOpt.stringValue.toFile()
  val cacheTimeout get() = Duration.parse("PT${cacheTimeoutOpt.stringValue}")
  val paramUpdateDelay get() = Duration.parse("PT${paramUpdateDelayOpt.stringValue}")
  val networkMapUpdateDelay get() = Duration.parse("PT${networkMapUpdateDelayOpt.stringValue}")
  val tls get() = tlsOpt.booleanValue
  val certPath get() = certPathOpt.stringValue
  val keyPath get() = keyPathOpt.stringValue
  val hostname get() = hostNameOpt.stringValue
  val user get() = InMemoryUser.createUser("System Admin", usernameOpt.stringValue, passwordOpt.stringValue)
  val enableDoorman get() = doormanOpt.booleanValue
  val enableCertman get() = certmanOpt.booleanValue
  val pkix get() = certManpkixOpt.booleanValue
  val truststore = if (certmanTruststoreOpt.stringValue.isNotEmpty()) File(certmanTruststoreOpt.stringValue) else null
  val trustStorePassword get() = if (certmanTruststorePasswordOpt.stringValue.isNotEmpty()) certmanTruststorePasswordOpt.stringValue else null
  val strictEV get() = certmanStrictEV.booleanValue
  val root get() = if (!enableDoorman && !enableCertman) {
    DEV_ROOT_CA
  } else {
    CertificateManager.createSelfSignedCertificateAndKeyPair(CordaX500Name.parse(rootX509Name.stringValue))
  }
  val webRoot get() = webRootOpt.stringValue
  val mongoConnectionString get() = mongoConnectionOpt.stringValue
  val mongodLocation get() = mongodLocationOpt.stringValue
}