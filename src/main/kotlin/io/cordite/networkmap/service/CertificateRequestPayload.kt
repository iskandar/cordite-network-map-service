package io.cordite.networkmap.service

import net.corda.core.identity.CordaX500Name
import org.bouncycastle.asn1.ASN1Encodable
import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.asn1.x500.AttributeTypeAndValue
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x500.style.BCStyle
import org.jgroups.util.Base64
import java.io.ByteArrayInputStream
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

class CertificateRequestPayload(val cert: X509Certificate, val signature: ByteArray) {
  companion object {
    private const val BEGIN_CERTIFICATE_TOKEN = "-----BEGIN CERTIFICATE-----"
    private const val END_CERTIFICATE_TOKEN = "-----END CERTIFICATE-----"

    fun parse(body: String) : CertificateRequestPayload {
      val parts = body.split(END_CERTIFICATE_TOKEN)
      if (parts.size != 2) {
        throw RuntimeException("payload must be the cert followed by signature")
      }
      val certText = parts[0] + END_CERTIFICATE_TOKEN
      val signatureText = parts[1]
      val cert = readCertificate(certText)
      val sig = Base64.decode(signatureText)
      return CertificateRequestPayload(cert, sig)
    }

    private fun readCertificate(certText: String): X509Certificate {
      val pem = Base64.decode(certText.replace(BEGIN_CERTIFICATE_TOKEN, "").replace(END_CERTIFICATE_TOKEN, ""))
      val factory = CertificateFactory.getInstance("X.509")
      val cert = factory.generateCertificate(ByteArrayInputStream(pem)) as X509Certificate
      cert.checkValidity()
      return cert
    }
  }

  val x500Name: CordaX500Name by lazy {
    val name = X500Name.getInstance(cert.subjectX500Principal.encoded)
    val attributesMap: Map<ASN1ObjectIdentifier, ASN1Encodable> = name.rdNs
      .flatMap { it.typesAndValues.asList() }
      .groupBy(AttributeTypeAndValue::getType, AttributeTypeAndValue::getValue)
      .mapValues {
        require(it.value.size == 1) { "Duplicate attribute ${it.key}" }
        it.value[0]
      }

    val cn = attributesMap[BCStyle.CN]?.toString()
    val ou = attributesMap[BCStyle.OU]?.toString()
    val o = attributesMap[BCStyle.O]?.toString()
      ?: throw IllegalArgumentException("Corda X.500 names must include an O attribute")
    val l = attributesMap[BCStyle.L]?.toString()
      ?: throw IllegalArgumentException("Corda X.500 names must include an L attribute")
    val st = attributesMap[BCStyle.ST]?.toString()
    val c = attributesMap[BCStyle.C]?.toString()
      ?: throw IllegalArgumentException("Corda X.500 names must include an C attribute")

    CordaX500Name(cn, ou, o, l, st, c)
  }

  fun verify() {
    CertificateManager.createSignature().apply {
      initVerify(cert)
      verify(signature)
    }
  }
}