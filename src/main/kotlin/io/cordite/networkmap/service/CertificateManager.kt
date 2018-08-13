package io.cordite.networkmap.service

import io.bluebank.braid.core.http.write
import io.cordite.networkmap.keystore.toKeyStore
import io.cordite.networkmap.storage.CertificateAndKeyPairStorage
import io.cordite.networkmap.utils.onSuccess
import io.vertx.core.Future
import io.vertx.core.buffer.Buffer
import io.vertx.ext.web.RoutingContext
import net.corda.core.crypto.Crypto
import net.corda.core.crypto.SignatureScheme
import net.corda.core.identity.CordaX500Name
import net.corda.core.utilities.loggerFor
import net.corda.nodeapi.internal.crypto.CertificateAndKeyPair
import net.corda.nodeapi.internal.crypto.CertificateType
import net.corda.nodeapi.internal.crypto.X509Utilities
import org.bouncycastle.asn1.ASN1Encodable
import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.asn1.x500.AttributeTypeAndValue
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x500.style.BCStyle
import org.jgroups.util.Base64
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.ws.rs.core.HttpHeaders.CONTENT_DISPOSITION
import javax.ws.rs.core.HttpHeaders.CONTENT_TYPE

class CertificateManager(
  private val rootX500Name: CordaX500Name,
  private val rootCertificate: CertificateAndKeyPair,
  private val storage: CertificateAndKeyPairStorage) {

  companion object {
    private val logger = loggerFor<CertificateManager>()

    internal const val BEGIN_CERTIFICATE_TOKEN = "-----BEGIN CERTIFICATE-----"
    internal const val END_CERTIFICATE_TOKEN = "-----END CERTIFICATE-----"
    internal const val NODE_IDENTITY_PASSWORD = "cordacadevpass" // TODO: move this as a request parameter

    const val NETWORK_MAP_CERT_KEY = "nms"
    const val DOORMAN_CERT_KEY = "dm"
    private const val SIG_ALGORITHM = "SHA256withRSA"
    private const val SIG_PROVIDER = "BC"
    private const val NETWORK_MAP_COMMON_NAME = "Network Map"
    private const val DOORMAN_COMMON_NAME = "Doorman"
    internal fun createSignature(): Signature {
      return Signature.getInstance(SIG_ALGORITHM, SIG_PROVIDER)
    }
  }

  internal lateinit var networkMapCertAndKeyPair: CertificateAndKeyPair
  internal lateinit var doormanCertAndKeyPair: CertificateAndKeyPair

  fun init(): Future<Unit> {
    return ensureNetworkMapCertExists()
      .onSuccess {
        networkMapCertAndKeyPair = it
      }.compose {
        ensureDoormanCertExists()
      }.onSuccess {
        doormanCertAndKeyPair = it
      }.mapEmpty()
  }

  fun generateJKSZipForNewSubscription(context: RoutingContext) {
    try {
      val body = context.bodyAsString
      val parts = body.split(END_CERTIFICATE_TOKEN)
      if (parts.size != 2) {
        context.write(RuntimeException("payload must be the cert followed by signature"))
        return
      }
      val certText = parts[0] + END_CERTIFICATE_TOKEN
      val signatureText = parts[1]

      val cert = readCertificate(certText)
      verifySignature(cert, signatureText)

      // TODO: insert additional checks and operations here

      val x500Name = getCordaX500NameFromCert(cert)

      val nodeCA = createCertificate(doormanCertAndKeyPair, x500Name, CertificateType.NODE_CA)
      val nodeIdentity = createCertificate(nodeCA, x500Name, CertificateType.LEGAL_IDENTITY)
      val nodeTLS = createCertificate(nodeCA, x500Name, CertificateType.TLS)

      val certificatePath = listOf(nodeCA.certificate, doormanCertAndKeyPair.certificate, rootCertificate.certificate)
      val bytes = ByteArrayOutputStream().use {
        ZipOutputStream(it).use {
          it.putNextEntry(ZipEntry("nodekeystore.jks"))
          nodeIdentity.toKeyStore(X509Utilities.CORDA_CLIENT_CA, "identity-private-key", NODE_IDENTITY_PASSWORD, certificatePath).store(it, NODE_IDENTITY_PASSWORD.toCharArray())
          it.closeEntry()
          it.putNextEntry(ZipEntry("sslkeystore.jks"))
          nodeTLS.toKeyStore(X509Utilities.CORDA_CLIENT_TLS, "identity-private-key", NODE_IDENTITY_PASSWORD, certificatePath).store(it, NODE_IDENTITY_PASSWORD.toCharArray())
          it.closeEntry()
        }
        it.toByteArray()
      }

      context.response()
        .putHeader(CONTENT_TYPE, "application/zip")
        .putHeader(CONTENT_DISPOSITION, "attachment; filename=keys.zip")
        .end(Buffer.buffer(bytes))
    } catch (err: Throwable) {
      context.write(err)
    }
  }


  fun ensureNetworkMapCertExists(): Future<CertificateAndKeyPair> {
    return ensureCertExists("signing", NETWORK_MAP_CERT_KEY, rootX500Name.copy(commonName = NETWORK_MAP_COMMON_NAME), CertificateType.NETWORK_MAP, rootCertificate)
  }

  private fun ensureDoormanCertExists(): Future<CertificateAndKeyPair> {
    return ensureCertExists("signing", DOORMAN_CERT_KEY, rootX500Name.copy(commonName = DOORMAN_COMMON_NAME), CertificateType.INTERMEDIATE_CA, rootCertificate)
  }

  private fun ensureCertExists(
    description: String,
    certName: String,
    name: CordaX500Name,
    certificateType: CertificateType,
    rootCa: CertificateAndKeyPair,
    signatureScheme: SignatureScheme = Crypto.DEFAULT_SIGNATURE_SCHEME
  ): Future<CertificateAndKeyPair> {
    logger.info("checking for $description certificate")
    return storage.get(certName)
      .recover {
        // we couldn't find the cert - so generate one
        logger.warn("failed to find $description cert for this NMS. generating new cert")
        val cert = createCertificate(rootCa, name, certificateType, signatureScheme)
        storage.put(certName, cert).map { cert }
      }
  }

  private fun verifySignature(cert: X509Certificate, signatureText: String) {
    createSignature().apply {
      initVerify(cert)
      verify(Base64.decode(signatureText))
    }
  }

  fun createCertificate(
    name: CordaX500Name,
    certificateType: CertificateType,
    signatureScheme: SignatureScheme = Crypto.ECDSA_SECP256R1_SHA256
  ): CertificateAndKeyPair {
    return createCertificate(rootCertificate, name, certificateType, signatureScheme)
  }

  private fun createCertificate(
    rootCa: CertificateAndKeyPair,
    name: CordaX500Name,
    certificateType: CertificateType,
    signatureScheme: SignatureScheme = Crypto.ECDSA_SECP256R1_SHA256
  ): CertificateAndKeyPair {
    val keyPair = Crypto.generateKeyPair(signatureScheme)
    val cert = X509Utilities.createCertificate(
      certificateType,
      rootCa.certificate,
      rootCa.keyPair,
      name.x500Principal,
      keyPair.public)
    return CertificateAndKeyPair(cert, keyPair)
  }

  private fun readCertificate(certText: String): X509Certificate {
    val pem = Base64.decode(certText.replace(BEGIN_CERTIFICATE_TOKEN, "").replace(END_CERTIFICATE_TOKEN, ""))
    val factory = CertificateFactory.getInstance("X.509")
    val cert = factory.generateCertificate(ByteArrayInputStream(pem)) as X509Certificate
    cert.checkValidity()
    return cert
  }

  private fun getCordaX500NameFromCert(cert: X509Certificate): CordaX500Name {
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

    return CordaX500Name(cn, ou, o, l, st, c)
  }

}