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
import io.vertx.core.Vertx
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
  internal val rootCertificateAndKeyPair: CertificateAndKeyPair,
  private val storage: CertificateAndKeyPairStorage) {

  companion object {
    private val logger = loggerFor<CertificateManager>()
    private var lastSerialNumber = 0L // TODO: fix

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

  private val csrResponse = mutableMapOf<String, Optional<X509Certificate>>()

  private lateinit var networkMapCertAndKeyPair: CertificateAndKeyPair
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

  fun generateJKSZipForTLSCertAndSig(context: RoutingContext) {
    try {
      val payload= CertificateRequestPayload.parse(context.bodyAsString)
      payload.verify()
      val x500Name = payload.x500Name
      val stream = generateJKSZipOutputStream(x500Name)
      val bytes = stream.toByteArray()
      context.response()
        .putHeader(CONTENT_TYPE, "application/zip")
        .putHeader(CONTENT_DISPOSITION, "attachment; filename=keys.zip")
        .end(Buffer.buffer(bytes))
    } catch (err: Throwable) {
      context.write(err)
    }
  }

  fun doormanProcessCSR(pkcs10Holder: PKCS10CertificationRequest): Future<String> {
    val id = UUID.randomUUID().toString()
    csrResponse[id] = Optional.empty()
    vertx.runOnContext {
      try {
        val nodePublicKey = JcaPEMKeyConverter().getPublicKey(pkcs10Holder.subjectPublicKeyInfo)
        val name = pkcs10Holder.subject.toCordaX500Name()
        val certificate = createCertificate(doormanCertAndKeyPair, name, nodePublicKey, CertificateType.NODE_CA)
        csrResponse[id] = Optional.of(certificate)
      } catch (err: Throwable) {
        logger.error("failed to create certificate for CSR", err)
      }
    }
    return Future.succeededFuture(id)
  }

  fun doormanRetrieveCSRResponse(id: String) : Array<X509Certificate> {
    val response = csrResponse[id] ?: throw RuntimeException("request $id not found")
    return if (response.isPresent) {
      arrayOf(response.get(), doormanCertAndKeyPair.certificate, rootCertificateAndKeyPair.certificate)
    } else {
      arrayOf()
    }
  }

  fun ensureNetworkMapCertExists(): Future<CertificateAndKeyPair> {
    return ensureCertExists("signing", NETWORK_MAP_CERT_KEY, rootX500Name.copy(commonName = NETWORK_MAP_COMMON_NAME), CertificateType.NETWORK_MAP, rootCertificateAndKeyPair)
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
    val stream = ByteArrayOutputStream().use {
      ZipOutputStream(it).use { writeKeyStores(it, nodeIdentity, certificatePath, nodeTLS) }
      it
    }
    return stream
  }

  private fun ensureDoormanCertExists(): Future<CertificateAndKeyPair> {
    return ensureCertExists("signing", DOORMAN_CERT_KEY, rootX500Name.copy(commonName = DOORMAN_COMMON_NAME), CertificateType.INTERMEDIATE_CA, rootCertificateAndKeyPair)
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
      .recover {
        // we couldn't find the cert - so generate one
        logger.warn("failed to find $description cert for this NMS. generating new cert")
        val cert = createCertificateAndKeyPair(rootCa, name, certificateType, signatureScheme)
        storage.put(certName, cert).map { cert }
      }
  }

  private fun createCertificateAndKeyPair(
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
      certificateType,
      rootCa.certificate,
      rootCa.keyPair,
      name.x500Principal,
      publicKey)
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
      setCertificate("cordarootca", rootCertificateAndKeyPair.certificate)
    }.internal.store(it, TRUST_STORE_PASSWORD.toCharArray())
    it.closeEntry()
  }
}