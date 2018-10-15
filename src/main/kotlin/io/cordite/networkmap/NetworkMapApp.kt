/**
 *   Copyright 2018, Cordite Foundation.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package io.cordite.networkmap

import io.cordite.networkmap.service.CertificateManager
import io.cordite.networkmap.service.CertificateManagerConfig
import io.cordite.networkmap.service.InMemoryUser
import io.cordite.networkmap.service.NetworkMapService
import io.cordite.networkmap.utils.Options
import io.cordite.networkmap.utils.toFile
import net.corda.core.identity.CordaX500Name
import net.corda.core.utilities.loggerFor
import net.corda.nodeapi.internal.DEV_ROOT_CA
import java.io.File
import java.time.Duration
import kotlin.system.exitProcess

open class NetworkMapApp {
  companion object {
    private val logger = loggerFor<NetworkMapApp>()

    @JvmStatic
    fun main(args: Array<String>) {
      val options = Options()
      val portOpt = options.addOption("port", "8080", "web port")
      val dbDirectoryOpt = options.addOption("db", ".db", "database directory for this service")
      val cacheTimeoutOpt = options.addOption("cache-timeout", "2S", "http cache timeout for this service in ISO 8601 duration format")
      val paramUpdateDelayOpt = options.addOption("param-update-delay", "10S", "schedule duration for a parameter update")
      val networkMapUpdateDelayOpt = options.addOption("network-map-delay", "1S", "queue time for the network map to update for addition of nodes")
      val usernameOpt = options.addOption("auth-username", "sa", "system admin username")
      val passwordOpt = options.addOption("auth-password", "admin", "system admin password")
      val tlsOpt = options.addOption("tls", "true", "whether TLS is enabled or not")
      val certPathOpt = options.addOption("tls-cert-path", "", "path to cert if TLS is turned on")
      val keyPathOpt = options.addOption("tls-key-path", "", "path to key if TLS turned on")
      val hostNameOpt = options.addOption("hostname", "0.0.0.0", "interface to bind the service to")
      val doormanOpt = options.addOption("doorman", "true", "enable Corda doorman protocol")
      val certmanOpt = options.addOption("certman", "true", "enable Cordite certman protocol so that nodes can authenticate using a signed TLS cert")
      val certManpkixOpt = options.addOption("certman-pkix", "false", "enables certman's pkix validation against JDK default truststore")
      val certmanTruststoreOpt = options.addOption("certman-truststore", "", "specified a custom truststore instead of the default JRE cacerts")
      val certmanTruststorePasswordOpt = options.addOption("certman-truststore-password", "", "truststore password")
      val certmanStrictEV = options.addOption("certman-strict-ev", "false", "enables strict constraint for EV certs only in certman")
      val rootX509Name = options.addOption("root-ca-name", "CN=\"<replace me>\", OU=Cordite Foundation Network, O=Cordite Foundation, L=London, ST=London, C=GB", "the name for the root ca. If doorman and certman are turned off this will automatically default to Corda dev root ca")
      val webRootOpt = options.addOption("web-root","", "for remapping the root url for all requests")

      if (args.contains("--help")) {
        options.printHelp()
        return
      }
      println("starting networkmap with the following options")
      options.printOptions()

      val port = portOpt.intValue
      val dbDirectory = dbDirectoryOpt.stringValue.toFile()
      val cacheTimeout = Duration.parse("PT${cacheTimeoutOpt.stringValue}")
      val paramUpdateDelay = Duration.parse("PT${paramUpdateDelayOpt.stringValue}")
      val networkMapUpdateDelay = Duration.parse("PT${networkMapUpdateDelayOpt.stringValue}")
      val tls = tlsOpt.booleanValue
      val certPath = certPathOpt.stringValue
      val keyPath = keyPathOpt.stringValue
      val user = InMemoryUser.createUser("System Admin", usernameOpt.stringValue, passwordOpt.stringValue)
      val enableDoorman = doormanOpt.booleanValue
      val enableCertman = certmanOpt.booleanValue
      val pkix = certManpkixOpt.booleanValue
      val truststore = if (certmanTruststoreOpt.stringValue.isNotEmpty()) File(certmanTruststoreOpt.stringValue) else null
      val trustStorePassword = if (certmanTruststorePasswordOpt.stringValue.isNotEmpty()) certmanTruststorePasswordOpt.stringValue else null
      val strictEV = certmanStrictEV.booleanValue
      val root = if (!enableDoorman && !enableCertman) {
        DEV_ROOT_CA
      } else {
        CertificateManager.createSelfSignedCertificateAndKeyPair(CordaX500Name.parse(rootX509Name.stringValue))
      }

      if (truststore != null && !truststore.exists()) {
        println("failed to find truststore ${truststore.path}")
        exitProcess(-1)
      }

      NetworkMapService(
        dbDirectory = dbDirectory,
        user = user,
        port = port,
        cacheTimeout = cacheTimeout,
        networkParamUpdateDelay = paramUpdateDelay,
        networkMapQueuedUpdateDelay = networkMapUpdateDelay,
        tls = tls,
        certPath = certPath,
        keyPath = keyPath,
        hostname = hostNameOpt.stringValue,
        certificateManagerConfig = CertificateManagerConfig(
          root = root,
          doorManEnabled = enableDoorman,
          certManEnabled = enableCertman,
          certManPKIVerficationEnabled = pkix,
          certManRootCAsTrustStoreFile = truststore,
          certManRootCAsTrustStorePassword = trustStorePassword,
          certManStrictEVCerts = strictEV
        ),
      webRoot
      ).startup().setHandler {
        if (it.failed()) {
          logger.error("failed to complete setup", it.cause())
        } else {
          logger.info("started")
        }
      }
    }
  }
}
