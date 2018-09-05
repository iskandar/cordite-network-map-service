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
package io.cordite.networkmap.service

import java.io.File
import java.io.FileInputStream
import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.security.cert.PKIXParameters
import java.security.cert.TrustAnchor
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

class CertmanContext(val enabled: Boolean,
                     val enablePKIVerfication : Boolean,
                     val trustStoreFile: File?,
                     val trustStorePassword: String?,
                     val strictEVCerts : Boolean) {

  val pkixParams: PKIXParameters
  val certFactory: CertificateFactory

  init {
    val keystore = if (trustStoreFile != null) {
      KeyStore.getInstance(KeyStore.getDefaultType()).apply {
        load(FileInputStream(trustStoreFile), trustStorePassword?.toCharArray())
      }
    } else {
      null
    }
    val trustManager = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
      .apply { init(keystore) }
      .trustManagers
      .filter { it is X509TrustManager }
      .map { it as X509TrustManager }
      .firstOrNull() ?: throw Exception("could not find the default x509 trust manager")

    val trustAnchors = trustManager.acceptedIssuers.map { TrustAnchor(it, null) }.toSet()
    pkixParams = PKIXParameters(trustAnchors).apply { isRevocationEnabled = false }
    certFactory = CertificateFactory.getInstance("X.509")
  }
}