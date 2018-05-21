package io.cordite.services.storage

import io.vertx.core.Vertx
import net.corda.core.internal.readObject
import net.corda.core.serialization.serialize
import net.corda.nodeapi.internal.SignedNodeInfo
import net.corda.nodeapi.internal.crypto.CertificateAndKeyPair
import net.corda.nodeapi.internal.network.SignedNetworkMap
import net.corda.nodeapi.internal.network.SignedNetworkParameters
import java.io.File

class SignedNodeInfoStorage(directory: File, vertx: Vertx) :
    AbstractSimpleNameValueStore<SignedNodeInfo>(directory, vertx) {
  override fun deserialize(file: File): SignedNodeInfo {
    return file.toPath().readObject()
  }

  override fun serialize(value: SignedNodeInfo, file: File) {
    file.writeBytes(value.serialize().bytes)
  }
}

class SignedNetworkMapStorage(directory: File, vertx: Vertx) :
    AbstractSimpleNameValueStore<SignedNetworkMap>(directory, vertx) {
  override fun deserialize(file: File): SignedNetworkMap {
    return file.toPath().readObject()
  }
  override fun serialize(value: SignedNetworkMap, file: File) {
    file.writeBytes(value.serialize().bytes)
  }
}

class SignedNetworkParametersStorage(directory: File, vertx: Vertx) :
    AbstractSimpleNameValueStore<SignedNetworkParameters>(directory, vertx) {
  override fun deserialize(file: File): SignedNetworkParameters {
    return file.toPath().readObject()
  }

  override fun serialize(value: SignedNetworkParameters, file: File) {
    file.writeBytes(value.serialize().bytes)
  }
}

class CertificateAndKeyPairStorage(directory: File, vertx: Vertx) :
    AbstractSimpleNameValueStore<CertificateAndKeyPair>(directory, vertx) {
  override fun deserialize(file: File): CertificateAndKeyPair {
    return file.toPath().readObject()
  }

  override fun serialize(value: CertificateAndKeyPair, file: File) {
    file.writeBytes(value.serialize().bytes)
  }
}

class TextStorage(directory: File, vertx: Vertx) :
    AbstractSimpleNameValueStore<String>(directory, vertx) {
  override fun deserialize(file: File): String {
    return file.readText()
  }

  override fun serialize(value: String, file: File) {
    file.writeText(value)
  }
}
