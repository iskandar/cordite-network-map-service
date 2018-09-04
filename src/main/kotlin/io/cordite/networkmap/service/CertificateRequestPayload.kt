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

import net.corda.core.identity.CordaX500Name
import net.corda.core.utilities.loggerFor
import org.bouncycastle.asn1.ASN1Encodable
import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.asn1.x500.AttributeTypeAndValue
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x500.style.BCStyle
import org.jgroups.util.Base64
import java.io.ByteArrayInputStream
import java.security.KeyStore
import java.security.cert.*
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager


class CertificateRequestPayload(val certs: List<X509Certificate>, val signature: ByteArray, private val enablePKIValidation: Boolean) {
  companion object {
    private val log = loggerFor<CertificateRequestPayload>()

    private const val BEGIN_CERTIFICATE_TOKEN = "-----BEGIN CERTIFICATE-----"
    private const val END_CERTIFICATE_TOKEN = "-----END CERTIFICATE-----"

    private val certPathValidator = CertPathValidator.getInstance("PKIX")

    private var pkixParams: PKIXParameters
    private var certFactory: CertificateFactory

    init {
      val trustManager = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        .apply { init(null as KeyStore?) }
        .trustManagers
        .filter { it is X509TrustManager }
        .map { it as X509TrustManager }
        .firstOrNull() ?: throw Exception("could not find the default x509 trust manager")

      val trustAnchors = trustManager.acceptedIssuers.map { TrustAnchor(it, null) }.toSet()
      pkixParams = PKIXParameters(trustAnchors).apply { isRevocationEnabled = false }
      certFactory = CertificateFactory.getInstance("X.509")
    }

    fun parse(body: String, enablePKIValidation: Boolean): CertificateRequestPayload {
      val parts = body.split(END_CERTIFICATE_TOKEN)
      if (parts.size < 2) {
        throw RuntimeException("payload must be a set of certs followed by signature")
      }
      val certs = parts.dropLast(1).map { it + END_CERTIFICATE_TOKEN }.map { readCertificate(it) }
      val signatureText = parts.last()
      val sig = Base64.decode(signatureText)
      return CertificateRequestPayload(certs, sig, enablePKIValidation)
    }

    private fun readCertificate(certText: String): X509Certificate {
      try {
        val pem = Base64.decode(certText.replace(BEGIN_CERTIFICATE_TOKEN, "").replace(END_CERTIFICATE_TOKEN, ""))
        val cert = certFactory.generateCertificate(ByteArrayInputStream(pem)) as X509Certificate
        cert.checkValidity()
        return cert
      } catch (ex: Throwable) {
        log.error("failed to read certificate", ex)
        throw ex
      }
    }
  }

  val x500Name: CordaX500Name by lazy {
    val cert = certs.first()
    val x500 = X500Name.getInstance(certs.first().subjectX500Principal.encoded)
    x500.toCordaX500Name()
  }

  fun verify() {
    certs.forEach { it.checkValidity() }
    if (enablePKIValidation) {
      verifyPKIPath()
    }
    verifySignature()
  }

  private fun verifyPKIPath() {
    val certPath = certFactory.generateCertPath(certs)
    certPathValidator.validate(certPath, pkixParams)
  }

  private fun verifySignature() {
    CertificateManager.createSignature().apply {
      initVerify(certs.first())
      verify(signature)
    }
  }
}

fun X500Name.toCordaX500Name(): CordaX500Name {
  val attributesMap: Map<ASN1ObjectIdentifier, ASN1Encodable> = this.rdNs
    .flatMap { it.typesAndValues.asList() }
    .groupBy(AttributeTypeAndValue::getType, AttributeTypeAndValue::getValue)
    .mapValues {
      require(it.value.size == 1) { "Duplicate attribute ${it.key}" }
      it.value[0]
    }

  val cn = attributesMap[BCStyle.CN]?.toString()
  val ou = attributesMap[BCStyle.OU]?.toString()
  val o = attributesMap[BCStyle.O]?.toString()
    ?: "$cn-web"
  val l = attributesMap[BCStyle.L]?.toString()
    ?: "Antarctica"
  val st = attributesMap[BCStyle.ST]?.toString()
  val c = attributesMap[BCStyle.C]?.toString()
    ?: "AQ"

  return CordaX500Name(cn, ou, o, l, st, c)
}