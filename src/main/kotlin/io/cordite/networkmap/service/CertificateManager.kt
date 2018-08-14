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
import net.corda.nodeapi.internal.crypto.X509KeyStore
import net.corda.nodeapi.internal.crypto.X509Utilities
import java.io.ByteArrayOutputStream
import java.security.Signature
import java.security.cert.X509Certificate
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.ws.rs.core.HttpHeaders.CONTENT_DISPOSITION
import javax.ws.rs.core.HttpHeaders.CONTENT_TYPE

class CertificateManager(
  private val rootX500Name: CordaX500Name,
  internal val rootCertificate: CertificateAndKeyPair,
  private val storage: CertificateAndKeyPairStorage) {

  companion object {
    private val logger = loggerFor<CertificateManager>()

    internal const val NODE_IDENTITY_PASSWORD = "cordacadevpass" // TODO: move this as a request parameter
    internal const val TRUST_STORE_PASSWORD = "trustpass"
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
  private lateinit var doormanCertAndKeyPair: CertificateAndKeyPair


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
      val payload= CertificateRequestPayload.parse(context.bodyAsString)
      payload.verify()
      val nodeCA = createCertificate(doormanCertAndKeyPair, payload.x500Name, CertificateType.NODE_CA)
      val nodeIdentity = createCertificate(nodeCA, payload.x500Name, CertificateType.LEGAL_IDENTITY)
      val nodeTLS = createCertificate(nodeCA, payload.x500Name, CertificateType.TLS)

      val certificatePath = listOf(nodeCA.certificate, doormanCertAndKeyPair.certificate, rootCertificate.certificate)
      val bytes = ByteArrayOutputStream().use {
        ZipOutputStream(it).use { writeKeyStores(it, nodeIdentity, certificatePath, nodeTLS) }
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

  fun createCertificate(
    name: CordaX500Name,
    certificateType: CertificateType,
    signatureScheme: SignatureScheme = Crypto.ECDSA_SECP256R1_SHA256
  ): CertificateAndKeyPair {
    return createCertificate(rootCertificate, name, certificateType, signatureScheme)
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

  private fun writeKeyStores(it: ZipOutputStream, nodeIdentity: CertificateAndKeyPair, certificatePath: List<X509Certificate>, nodeTLS: CertificateAndKeyPair) {
    writeTrustStore(it)
    writeNodeKeyStore(it, nodeIdentity, certificatePath)
    writeSslKeyStore(it, nodeTLS, certificatePath)
  }

  private fun writeSslKeyStore(it: ZipOutputStream, nodeTLS: CertificateAndKeyPair, certificatePath: List<X509Certificate>) {
    it.putNextEntry(ZipEntry("sslkeystore.jks"))
    nodeTLS.toKeyStore(X509Utilities.CORDA_CLIENT_TLS, "identity-private-key", NODE_IDENTITY_PASSWORD, certificatePath).store(it, NODE_IDENTITY_PASSWORD.toCharArray())
    it.closeEntry()
  }

  private fun writeNodeKeyStore(it: ZipOutputStream, nodeIdentity: CertificateAndKeyPair, certificatePath: List<X509Certificate>) {
    it.putNextEntry(ZipEntry("nodekeystore.jks"))
    nodeIdentity.toKeyStore(X509Utilities.CORDA_CLIENT_CA, "identity-private-key", NODE_IDENTITY_PASSWORD, certificatePath).store(it, NODE_IDENTITY_PASSWORD.toCharArray())
    it.closeEntry()
  }

  private fun writeTrustStore(it: ZipOutputStream) {
    it.putNextEntry(ZipEntry("truststore.jks"))
    X509KeyStore(TRUST_STORE_PASSWORD).apply {
      setCertificate("cordaintermediateca", doormanCertAndKeyPair.certificate)
      setCertificate("cordarootca", rootCertificate.certificate)
    }.internal.store(it, TRUST_STORE_PASSWORD.toCharArray())
    it.closeEntry()
  }
}