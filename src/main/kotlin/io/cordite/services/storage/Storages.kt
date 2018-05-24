package io.cordite.services.storage

import io.cordite.services.utils.all
import io.cordite.services.utils.readFile
import io.cordite.services.utils.writeFile
import io.vertx.core.Future
import io.vertx.core.Future.future
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import net.corda.nodeapi.internal.SignedNodeInfo
import net.corda.nodeapi.internal.crypto.CertificateAndKeyPair
import net.corda.nodeapi.internal.network.SignedNetworkMap
import net.corda.nodeapi.internal.network.SignedNetworkParameters
import org.bouncycastle.cert.jcajce.JcaX509CertificateHolder
import org.bouncycastle.util.io.pem.PemObject
import org.bouncycastle.util.io.pem.PemReader
import org.bouncycastle.util.io.pem.PemWriter
import java.io.*
import java.security.KeyFactory
import java.security.KeyPair
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec


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
  childDirectory: String = DEFAULT_CHILD_DIR,
  private val certFilename: String = DEFAULT_CERT_FILENAME,
  private val keyFilename: String = DEFAULT_KEY_FILENAME
) : AbstractSimpleNameValueStore<CertificateAndKeyPair>(File(parentDirectory, childDirectory), vertx) {
  companion object {
    const val DEFAULT_CHILD_DIR = "certs"
    const val DEFAULT_CERT_FILENAME = "cert"
    const val DEFAULT_KEY_FILENAME = "secret"
    private val certFactory = CertificateFactory.getInstance("X509")
    private val keyFactory = KeyFactory.getInstance("RSA")
  }

  override fun deserialize(location: File): Future<CertificateAndKeyPair> {
    val cert = vertx.fileSystem().readFile(File(location, certFilename).absolutePath)
      .map {
        with(PemReader(InputStreamReader(ByteArrayInputStream(it.bytes)))) {
          certFactory.generateCertificate(ByteArrayInputStream(this.readPemObject().content)) as X509Certificate
        }
      }

    val privateKey = vertx.fileSystem().readFile(File(location, keyFilename).absolutePath)
      .map {
        with(PemReader(InputStreamReader(ByteArrayInputStream(it.bytes)))) {
          val spec = PKCS8EncodedKeySpec(this.readPemObject().content)
          keyFactory.generatePrivate(spec)
        }
      }

    return all(cert, privateKey)
      .map {
        CertificateAndKeyPair(cert.result(), KeyPair(cert.result().publicKey, privateKey.result()))
      }
  }

  override fun serialize(value: CertificateAndKeyPair, location: File): Future<Unit> {
    location.mkdirs()
    val holder = JcaX509CertificateHolder(value.certificate)

    val certArray = with(ByteArrayOutputStream()) {
      with(PemWriter(OutputStreamWriter(this))) {
        writeObject(PemObject("CERTIFICATE", holder.toASN1Structure().encoded))
      }
      this.toByteArray()
    }

    val keyArray = with(ByteArrayOutputStream()) {
      with(PemWriter(OutputStreamWriter(this))) {
        writeObject(PemObject("PRIVATE KEY", value.keyPair.private.encoded))
      }
      this.toByteArray()
    }

    return all(
      vertx.fileSystem().writeFile(File(location, certFilename).absolutePath, certArray),
      vertx.fileSystem().writeFile(File(location, keyFilename).absolutePath, keyArray)
    ).map { Unit }
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
}
