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

import io.cordite.networkmap.service.CertificateManagerConfig
import io.cordite.networkmap.service.NetworkMapService
import io.cordite.networkmap.utils.LogInitialiser
import io.cordite.networkmap.utils.NMSOptions
import net.corda.core.utilities.loggerFor
import kotlin.system.exitProcess

open class NetworkMapApp {
  companion object {
    private val logger = loggerFor<NetworkMapApp>()

    @JvmStatic
    fun main(args: Array<String>) {
      LogInitialiser.init()
      NMSOptions().apply {
        if (args.contains("--help")) {
          printHelp()
          return
        }
        println("starting networkmap with the following options")
        printOptions()
        bootstrapNMS()
      }
    }

    private fun NMSOptions.bootstrapNMS() {
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
        hostname = hostname,
        webRoot = webRoot,
        certificateManagerConfig = CertificateManagerConfig(
          root = root,
          doorManEnabled = enableDoorman,
          certManEnabled = enableCertman,
          certManPKIVerficationEnabled = pkix,
          certManRootCAsTrustStoreFile = truststore,
          certManRootCAsTrustStorePassword = trustStorePassword,
          certManStrictEVCerts = strictEV
        )
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
