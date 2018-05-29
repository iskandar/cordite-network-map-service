package io.cordite.services.storage

import io.cordite.services.utils.readFile
import io.cordite.services.utils.writeFile
import io.netty.handler.codec.http.HttpHeaderValues
import io.vertx.core.Future
import io.vertx.core.Future.failedFuture
import io.vertx.core.Future.future
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.core.http.HttpHeaders
import io.vertx.ext.web.RoutingContext
import net.corda.core.crypto.SecureHash
import net.corda.nodeapi.internal.SignedNodeInfo
import net.corda.nodeapi.internal.crypto.CertificateAndKeyPair
import net.corda.nodeapi.internal.network.ParametersUpdate
import net.corda.nodeapi.internal.network.SignedNetworkMap
import net.corda.nodeapi.internal.network.SignedNetworkParameters
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.KeyPair
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.time.Duration


class SignedNodeInfoStorage(
  vertx: Vertx,
  parentDirectory: File,
  childDirectory: String = DEFAULT_CHILD_DIR
) :
  AbstractSimpleNameValueStore<SignedNodeInfo>(File(parentDirectory, childDirectory), vertx) {

  companion object {
    const val DEFAULT_CHILD_DIR = "nodes"
  }

  override fun deserialize(location: File): Future<SignedNodeInfo> {
    return AbstractSimpleNameValueStore.deserialize(location, vertx)
  }

  override fun serialize(value: SignedNodeInfo, location: File): Future<Unit> {
    return serialize(value, location, vertx)
  }
}

class ParametersUpdateStorage(vertx: Vertx, parentDirectory: File, childDirectory: String = DEFAULT_CHILD_DIR)
  : AbstractSimpleNameValueStore<ParametersUpdate>(File(parentDirectory, childDirectory), vertx) {
  companion object {
    const val DEFAULT_CHILD_DIR = "parameters-update"
  }

  override fun serialize(value: ParametersUpdate, location: File): Future<Unit> {
    return serialize(value, location, vertx)
  }

  override fun deserialize(location: File): Future<ParametersUpdate> {
    return deserialize(location, vertx)
  }
}

class SecureHashStorage(vertx: Vertx, parentDirectory: File, childDirectory: String = DEFAULT_CHILD_DIR)
  : AbstractSimpleNameValueStore<SecureHash>(File(parentDirectory, childDirectory), vertx) {
  companion object {
    const val DEFAULT_CHILD_DIR = "secure-hashes"
  }

  override fun serialize(value: SecureHash, location: File): Future<Unit> {
    return serialize(value, location, vertx)
  }

  override fun deserialize(location: File): Future<SecureHash> {
    return deserialize(location, vertx)
  }
}

class SignedNetworkMapStorage(
  vertx: Vertx,
  parentDirectory: File,
  childDirectory: String = DEFAULT_CHILD_DIR
) :
  AbstractSimpleNameValueStore<SignedNetworkMap>(File(parentDirectory, childDirectory), vertx) {
  companion object {
    const val DEFAULT_CHILD_DIR = "network-map"
  }

  override fun deserialize(location: File): Future<SignedNetworkMap> {
    return AbstractSimpleNameValueStore.deserialize(location, vertx)
  }

  override fun serialize(value: SignedNetworkMap, location: File): Future<Unit> {
    return serialize(value, location, vertx)
  }
}

class SignedNetworkParametersStorage(
  vertx: Vertx,
  parentDirectory: File,
  childDirectory: String = DEFAULT_CHILD_DIR
) :
  AbstractSimpleNameValueStore<SignedNetworkParameters>(File(parentDirectory, childDirectory), vertx) {
  companion object {
    const val DEFAULT_CHILD_DIR = "signed-network-parameters"
  }

  override fun deserialize(location: File): Future<SignedNetworkParameters> {
    return AbstractSimpleNameValueStore.deserialize(location, vertx)
  }

  override fun serialize(value: SignedNetworkParameters, location: File): Future<Unit> {
    return serialize(value, location, vertx)
  }
}

class CertificateAndKeyPairStorage(
  vertx: Vertx,
  parentDirectory: File,
  childDirectory: String = DEFAULT_CHILD_DIR
) : AbstractSimpleNameValueStore<CertificateAndKeyPair>(File(parentDirectory, childDirectory), vertx) {
  companion object {
    const val DEFAULT_CHILD_DIR = "certs"
    const val DEFAULT_JKS_FILE = "keys.jks"
    const val DEFAULT_KEY_ALIAS = "key"
    const val DEFAULT_CERT_ALIAS = "cert"
    private val P = "___".toCharArray()
  }

  override fun deserialize(location: File): Future<CertificateAndKeyPair> {
    val file = File(location, DEFAULT_JKS_FILE)
    if (!location.exists()) return failedFuture("couldn't find jks file ${file.absolutePath}")
    return vertx.fileSystem().readFile(file.absolutePath)
      .map {
        val ba = it.bytes
        val ks = KeyStore.getInstance("JKS")
        ks.load(ByteArrayInputStream(ba), P)
        val pk = ks.getKey(DEFAULT_KEY_ALIAS, P) as PrivateKey
        val cert = ks.getCertificate(DEFAULT_CERT_ALIAS) as X509Certificate
        CertificateAndKeyPair(cert, KeyPair(cert.publicKey, pk))
      }
  }

  override fun serialize(value: CertificateAndKeyPair, location: File) : Future<Unit> {
    location.mkdirs()

    val ks = KeyStore.getInstance("JKS")
    ks.load(null, null)
    ks.setKeyEntry(DEFAULT_KEY_ALIAS, value.keyPair.private, P, arrayOf(value.certificate))
    ks.setCertificateEntry(DEFAULT_CERT_ALIAS, value.certificate)
    val ba = with (ByteArrayOutputStream()) {
      ks.store(this, P)
      this.toByteArray()
    }
    return vertx.fileSystem().writeFile(File(location, DEFAULT_JKS_FILE).absolutePath, ba).map { Unit }
  }
}

class TextStorage(vertx: Vertx, parentDirectory: File, childDirectory: String = DEFAULT_CHILD_DIR) :
  AbstractSimpleNameValueStore<String>(File(parentDirectory, childDirectory), vertx) {

  companion object {
    const val DEFAULT_CHILD_DIR = "etc"
  }

  override fun deserialize(location: File): Future<String> {
    val result = future<Buffer>()
    vertx.fileSystem().readFile(location.absolutePath, result.completer())
    return result.map { it.toString() }
  }

  override fun serialize(value: String, location: File): Future<Unit> {
    val result = future<Void>()
    vertx.fileSystem().writeFile(location.absolutePath, Buffer.buffer(value), result.completer())
    return result.map { Unit }
  }

  override fun serve(key: String, routingContext: RoutingContext, cacheTimeout: Duration) {
    routingContext.response().apply {
      putHeader(HttpHeaders.CACHE_CONTROL, "max-age=${cacheTimeout.seconds}")
      putHeader(HttpHeaders.CONTENT_TYPE, HttpHeaderValues.TEXT_PLAIN)
        .sendFile(resolveKey(key).absolutePath)
    }
  }
}
