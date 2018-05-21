package io.cordite.services.storage

import io.vertx.core.Future
import io.vertx.core.Future.future
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import net.corda.nodeapi.internal.SignedNodeInfo
import net.corda.nodeapi.internal.crypto.CertificateAndKeyPair
import net.corda.nodeapi.internal.network.SignedNetworkMap
import net.corda.nodeapi.internal.network.SignedNetworkParameters
import java.io.File

class SignedNodeInfoStorage(
  parentDirectory: File,
  vertx: Vertx,
  childDirectory: String = DEFAULT_CHILD_DIR
) :
  AbstractSimpleNameValueStore<SignedNodeInfo>(File(parentDirectory, childDirectory), vertx) {

  companion object {
    const val DEFAULT_CHILD_DIR = "nodes"
  }

  override fun deserialize(file: File): Future<SignedNodeInfo> {
    return AbstractSimpleNameValueStore.deserialize(file, vertx)
  }

  override fun serialize(value: SignedNodeInfo, file: File) : Future<Unit> {
    return serialize(value, file, vertx)
  }
}

class SignedNetworkMapStorage(
  parentDirectory: File,
  vertx: Vertx,
  childDirectory: String = DEFAULT_CHILD_DIR
) :
  AbstractSimpleNameValueStore<SignedNetworkMap>(File(parentDirectory, childDirectory), vertx) {
  companion object {
    const val DEFAULT_CHILD_DIR = "network-map"
  }

  override fun deserialize(file: File): Future<SignedNetworkMap> {
    return AbstractSimpleNameValueStore.deserialize(file, vertx)
  }

  override fun serialize(value: SignedNetworkMap, file: File) : Future<Unit> {
    return serialize(value, file, vertx)
  }
}

class SignedNetworkParametersStorage(
  parentDirectory: File,
  vertx: Vertx,
  childDirectory: String = DEFAULT_CHILD_DIR
) :
  AbstractSimpleNameValueStore<SignedNetworkParameters>(File(parentDirectory, childDirectory), vertx) {
  companion object {
    const val DEFAULT_CHILD_DIR = "signed-network-parameters"
  }

  override fun deserialize(file: File): Future<SignedNetworkParameters> {
    return AbstractSimpleNameValueStore.deserialize(file, vertx)
  }

  override fun serialize(value: SignedNetworkParameters, file: File) : Future<Unit> {
    return serialize(value, file, vertx)
  }
}

class CertificateAndKeyPairStorage(
  parentDirectory: File,
  vertx: Vertx,
  childDirectory: String = DEFAULT_CHILD_DIR
) :
  AbstractSimpleNameValueStore<CertificateAndKeyPair>(File(parentDirectory, childDirectory), vertx) {
  companion object {
    const val DEFAULT_CHILD_DIR = "certs"
  }

  override fun deserialize(file: File): Future<CertificateAndKeyPair> {
    return AbstractSimpleNameValueStore.deserialize(file, vertx)
  }

  override fun serialize(value: CertificateAndKeyPair, file: File) : Future<Unit> {
    return serialize(value, file, vertx)
  }
}

class TextStorage(parentDirectory: File, vertx: Vertx, childDirectory: String = DEFAULT_CHILD_DIR) :
  AbstractSimpleNameValueStore<String>(File(parentDirectory, childDirectory), vertx) {

  companion object {
    const val DEFAULT_CHILD_DIR = "etc"
  }

  override fun deserialize(file: File): Future<String> {
    val result = future<Buffer>()
    vertx.fileSystem().readFile(file.absolutePath, result.completer())
    return result.map { it.toString() }
  }

  override fun serialize(value: String, file: File) : Future<Unit> {
    val result = future<Void>()
    vertx.fileSystem().writeFile(file.absolutePath, Buffer.buffer(value), result.completer())
    return result.map { Unit }
  }
}
