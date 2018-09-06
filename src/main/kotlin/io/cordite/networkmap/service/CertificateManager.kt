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

import io.bluebank.braid.core.http.write
import io.cordite.networkmap.keystore.toKeyStore
import io.cordite.networkmap.storage.CertificateAndKeyPairStorage
import io.cordite.networkmap.utils.onSuccess
import io.vertx.core.Future
import io.vertx.core.Future.succeededFuture
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.ext.web.RoutingContext
import net.corda.core.crypto.Crypto
import net.corda.core.crypto.SignatureScheme
import net.corda.core.identity.CordaX500Name
import net.corda.core.node.NodeInfo
import net.corda.core.utilities.loggerFor
import net.corda.nodeapi.internal.crypto.CertificateAndKeyPair
import net.corda.nodeapi.internal.crypto.CertificateType
import net.corda.nodeapi.internal.crypto.X509KeyStore
import net.corda.nodeapi.internal.crypto.X509Utilities
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter
import org.bouncycastle.pkcs.PKCS10CertificationRequest
import java.io.ByteArrayOutputStream
import java.security.PublicKey
import java.security.Signature
import java.security.cert.X509Certificate
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.ws.rs.core.HttpHeaders.CONTENT_DISPOSITION
import javax.ws.rs.core.HttpHeaders.CONTENT_TYPE


class CertificateManager(
  private val vertx: Vertx,
  private val rootX500Name: CordaX500Name,
  private val storage: CertificateAndKeyPairStorage,
  certManContext: CertmanContext) {

  companion object {
    private val logger = loggerFor<CertificateManager>()

    internal const val NODE_IDENTITY_PASSWORD = "cordacadevpass" // TODO: move this as a request parameter
    internal const val TRUST_STORE_PASSWORD = "trustpass"
    const val ROOT_CERT_KEY = "root"
    const val NETWORK_MAP_CERT_KEY = "network-map"
    const val DOORMAN_CERT_KEY = "doorman"
    private const val SIG_ALGORITHM = "SHA256withRSA"
    private const val SIG_PROVIDER = "BC"
    private const val ROOT_COMMON_NAME = "Root CA"
    private const val NETWORK_MAP_COMMON_NAME = "Network Map"
    private const val DOORMAN_COMMON_NAME = "Doorman"
    internal fun createSignature(): Signature {
      return Signature.getInstance(SIG_ALGORITHM, SIG_PROVIDER)
    }
  }

  private val csrResponse = mutableMapOf<String, Optional<X509Certificate>>()
  private val certificateRequestPayloadParser = CertificateRequestPayloadParser(certManContext)
  lateinit var networkMapCertAndKeyPair: CertificateAndKeyPair
    private set
  lateinit var doormanCertAndKeyPair: CertificateAndKeyPair
    private set
  lateinit var rootCertificateAndKeyPair: CertificateAndKeyPair
    private set

  fun init(): Future<Unit> {
    return ensureRootCertExists()
      .compose { ensureNetworkMapCertExists() }
      .compose { ensureDoormanCertExists() }
      .onSuccess {
        doormanCertAndKeyPair = it
      }
      .map(Unit)
  }

  fun validateNodeInfoCertificates(nodeInfo: NodeInfo) {
    nodeInfo.legalIdentitiesAndCerts.forEach {
      X509Utilities.validateCertPath(rootCertificateAndKeyPair.certificate, it.certPath)
    }
  }

  fun certmanGenerate(context: RoutingContext) {
    try {
      val payload = certificateRequestPayloadParser.parse(context.bodyAsString)
      payload.verify()
      val x500Name = payload.x500Name
      val stream = generateJKSZipOutputStream(x500Name)
      val bytes = stream.toByteArray()
      context.response()
        .putHeader(CONTENT_TYPE, "application/zip")
        .putHeader(CONTENT_DISPOSITION, "attachment; filename=\"keys.zip\"")
        .end(Buffer.buffer(bytes))
    } catch (err: Throwable) {
      logger.error("certman failed to generate jks files", err)
      context.write(err)
    }
  }

  fun doormanProcessCSR(pkcs10Holder: PKCS10CertificationRequest): Future<String> {
    val id = UUID.randomUUID().toString()
    csrResponse[id] = Optional.empty()
    vertx.runOnContext {
      try {
        val nodePublicKey = JcaPEMKeyConverter().getPublicKey(pkcs10Holder.subjectPublicKeyInfo)
        val name = pkcs10Holder.subject.toCordaX500Name(true)
        val certificate = createCertificate(doormanCertAndKeyPair, name, nodePublicKey, CertificateType.NODE_CA)
        csrResponse[id] = Optional.of(certificate)
      } catch (err: Throwable) {
        logger.error("failed to create certificate for CSR", err)
      }
    }
    return succeededFuture(id)
  }

  fun doormanRetrieveCSRResponse(id: String): Array<X509Certificate> {
    val response = csrResponse[id]
    return if (response != null && response.isPresent) {
      arrayOf(response.get(), doormanCertAndKeyPair.certificate, rootCertificateAndKeyPair.certificate)
    } else {
      arrayOf()
    }
  }

  fun createCertificate(
    name: CordaX500Name,
    certificateType: CertificateType,
    signatureScheme: SignatureScheme = Crypto.ECDSA_SECP256R1_SHA256
  ): CertificateAndKeyPair {
    return createCertificateAndKeyPair(rootCertificateAndKeyPair, name, certificateType, signatureScheme)
  }

  private fun generateJKSZipOutputStream(x500Name: CordaX500Name): ByteArrayOutputStream {
    val nodeCA = createCertificateAndKeyPair(doormanCertAndKeyPair, x500Name, CertificateType.NODE_CA)
    val nodeIdentity = createCertificateAndKeyPair(nodeCA, x500Name, CertificateType.LEGAL_IDENTITY)
    val nodeTLS = createCertificateAndKeyPair(nodeCA, x500Name, CertificateType.TLS)

    val certificatePath = listOf(nodeCA.certificate, doormanCertAndKeyPair.certificate, rootCertificateAndKeyPair.certificate)
    return ByteArrayOutputStream().use {
      ZipOutputStream(it).use { writeKeyStores(it, nodeIdentity, certificatePath, nodeTLS) }
      it
    }
  }

  private fun ensureRootCertExists(): Future<Unit> {
    logger.info("checking for root certificate")
    return storage.get(ROOT_CERT_KEY)
      .onSuccess { logger.info("root certificate found") }
      .recover {
        // we couldn't find the cert - so generate one
        logger.warn("failed to find root certificate. generating a new cert")
        val cert = createSelfSignedCertificateAndKeyPair(rootX500Name.copy(commonName = ROOT_COMMON_NAME))
        storage.put(ROOT_CERT_KEY, cert)
          // but because we're creating a new root key, delete the old networkmap and doorman keys
          .compose {
            logger.info("clearing network-map cert")
            storage.delete(NETWORK_MAP_CERT_KEY) }.recover { succeededFuture() }
          .compose {
            logger.info("clearing doorman cert")
            storage.delete(DOORMAN_CERT_KEY) }.recover { succeededFuture() }
          .map { cert }
      }
      .onSuccess { rootCertificateAndKeyPair = it }
      .mapEmpty()
  }

  private fun ensureNetworkMapCertExists(): Future<Unit> {
    return ensureCertExists(
      "network-map",
      NETWORK_MAP_CERT_KEY,
      rootX500Name.copy(commonName = NETWORK_MAP_COMMON_NAME),
      CertificateType.NETWORK_MAP,
      rootCertificateAndKeyPair)
      .onSuccess { networkMapCertAndKeyPair = it }
      .mapEmpty()
  }

  private fun ensureDoormanCertExists(): Future<CertificateAndKeyPair> {
    return ensureCertExists(
      "doorman",
      DOORMAN_CERT_KEY,
      rootX500Name.copy(commonName = DOORMAN_COMMON_NAME),
      CertificateType.INTERMEDIATE_CA,
      rootCertificateAndKeyPair
    )
  }

  private fun ensureCertExists(
    description: String,
    certName: String,
    name: CordaX500Name,
    certificateType: CertificateType,
    rootCa: CertificateAndKeyPair,
    signatureScheme: SignatureScheme = Crypto.ECDSA_SECP256R1_SHA256
  ): Future<CertificateAndKeyPair> {
    logger.info("checking for $description certificate")
    return storage.get(certName)
      .onSuccess { logger.info("$description certificate found") }
      .recover {
        // we couldn't find the cert - so generate one
        logger.warn("failed to find $description certificate for this NMS. generating a new cert")
        val cert = createCertificateAndKeyPair(rootCa, name, certificateType, signatureScheme)
        storage.put(certName, cert).map { cert }
      }
  }

  internal fun createCertificateAndKeyPair(
    rootCa: CertificateAndKeyPair,
    name: CordaX500Name,
    certificateType: CertificateType,
    signatureScheme: SignatureScheme = Crypto.ECDSA_SECP256R1_SHA256
  ): CertificateAndKeyPair {
    val keyPair = Crypto.generateKeyPair(signatureScheme)
    val certificate = createCertificate(rootCa, name, keyPair.public, certificateType)
    return CertificateAndKeyPair(certificate, keyPair)
  }

  private fun createCertificate(
    rootCa: CertificateAndKeyPair,
    name: CordaX500Name,
    publicKey: PublicKey,
    certificateType: CertificateType
  ): X509Certificate {
    return X509Utilities.createCertificate(
      certificateType = certificateType,
      issuerCertificate = rootCa.certificate,
      issuerKeyPair = rootCa.keyPair,
      subject = name.x500Principal,
      subjectPublicKey = publicKey
    )
  }

  private fun createSelfSignedCertificateAndKeyPair(name: CordaX500Name,
                                                    signatureScheme: SignatureScheme = Crypto.ECDSA_SECP256R1_SHA256): CertificateAndKeyPair {
    val keyPair = Crypto.generateKeyPair(signatureScheme)
    val certificate = X509Utilities.createSelfSignedCACertificate(
      subject = name.x500Principal, keyPair = keyPair
    )
    return CertificateAndKeyPair(certificate, keyPair)
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

  fun generateTrustStoreByteArray(): ByteArray {
    return ByteArrayOutputStream().apply {
      X509KeyStore(TRUST_STORE_PASSWORD).apply {
        setCertificate("cordarootca", rootCertificateAndKeyPair.certificate)
      }.internal.store(this, TRUST_STORE_PASSWORD.toCharArray())
    }.toByteArray()
  }

  private fun writeTrustStore(it: ZipOutputStream) {
    it.putNextEntry(ZipEntry("truststore.jks"))
    generateX509TrustStore().internal.store(it, TRUST_STORE_PASSWORD.toCharArray())
    it.closeEntry()
  }

  private fun generateX509TrustStore(): X509KeyStore {
    return X509KeyStore(TRUST_STORE_PASSWORD).apply {
      setCertificate("cordaintermediateca", doormanCertAndKeyPair.certificate)
      setCertificate("cordarootca", rootCertificateAndKeyPair.certificate)
    }
  }
}