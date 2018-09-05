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