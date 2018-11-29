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
@file:Suppress("DEPRECATION")

package io.cordite.networkmap.storage

import com.mongodb.async.client.MongoClient
import io.bluebank.braid.core.logging.loggerFor
import io.cordite.networkmap.keystore.toKeyStore
import io.cordite.networkmap.utils.*
import io.netty.handler.codec.http.HttpHeaderValues
import io.vertx.core.Future
import io.vertx.core.Future.*
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.core.http.HttpHeaders
import io.vertx.ext.web.RoutingContext
import net.corda.nodeapi.internal.SignedNodeInfo
import net.corda.nodeapi.internal.crypto.CertificateAndKeyPair
import net.corda.nodeapi.internal.network.ParametersUpdate
import net.corda.nodeapi.internal.network.SignedNetworkMap
import net.corda.nodeapi.internal.network.SignedNetworkParameters
import org.bson.codecs.pojo.annotations.BsonId
import org.litote.kmongo.async.deleteOne
import org.litote.kmongo.async.distinct
import org.litote.kmongo.async.getCollection
import org.litote.kmongo.eq
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
  val password: String = DEFAULT_PASSWORD,
  private val jksFilename: String = DEFAULT_JKS_FILE
) : AbstractSimpleNameValueStore<CertificateAndKeyPair>(File(parentDirectory, childDirectory), vertx) {
  companion object {
    const val DEFAULT_CHILD_DIR = "certs"
    const val DEFAULT_JKS_FILE = "keys.jks"
    const val DEFAULT_KEY_ALIAS = "key"
    const val DEFAULT_CERT_ALIAS = "cert"
    const val DEFAULT_PASSWORD = "changeme"
  }

  private val parray = password.toCharArray()

  override fun deserialize(location: File): Future<CertificateAndKeyPair> {
    val file = resolveJksFile(location)
    if (!location.exists()) return failedFuture("couldn't find jks file ${file.absolutePath}")
    return vertx.fileSystem().readFile(file.absolutePath)
      .map {
        val ba = it.bytes
        val ks = KeyStore.getInstance("JKS")
        ks.load(ByteArrayInputStream(ba), parray)
        val pk = ks.getKey(DEFAULT_KEY_ALIAS, parray) as PrivateKey
        val cert = ks.getCertificate(DEFAULT_CERT_ALIAS) as X509Certificate
        CertificateAndKeyPair(cert, KeyPair(cert.publicKey, pk))
      }
  }

  override fun serialize(value: CertificateAndKeyPair, location: File): Future<Unit> {
    location.mkdirs()
    val ks = value.toKeyStore(password)
    val ba = with(ByteArrayOutputStream()) {
      ks.store(this, parray)
      this.toByteArray()
    }
    return vertx.fileSystem().writeFile(resolveJksFile(location).absolutePath, ba).map { Unit }
  }

  private fun resolveJksFile(directory: File) = File(directory, jksFilename)
}

class TextStorage(vertx: Vertx, parentDirectory: File, childDirectory: String = DEFAULT_CHILD_DIR) :
  AbstractSimpleNameValueStore<String>(File(parentDirectory, childDirectory), vertx) {

  companion object {
    const val DEFAULT_CHILD_DIR = "etc"
  }

  init {
    makeDirs()
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

@Suppress("DEPRECATION")
class MongoTextStorage(mongoClient: MongoClient,
                       database: String = MongoStorage.DEFAULT_DATABASE,
                       collection: String = "etc") : Storage<String> {
  companion object {
    private val log = loggerFor<MongoTextStorage>()
  }

  private val collection = mongoClient.getDatabase(database).getCollection<IdValue>(collection)

  override fun clear(): Future<Unit> = collection.drop().mapEmpty<Unit>()

  override fun put(key: String, value: String) = collection.insertOne(IdValue(key, value))

  override fun get(key: String): Future<String> = collection.findOneById(key).map { it.value }

  override fun getOrNull(key: String): Future<String?> = collection.findOneById(key).map { it.value }.recover { succeededFuture(null) }

  override fun getOrDefault(key: String, default: String): Future<String> = collection.findOneById(key).map { it.value }.recover { succeededFuture(default) }

  override fun getKeys() = collection.distinct<String>("_id").toList()

  override fun getAll(): Future<Map<String, String>> = collection.find().map { it.id to it.value }.toList().map { it.toMap() }

  override fun delete(key: String): Future<Unit> {
    val result = Future.future<Unit>()
    collection.deleteOne(key) { _, err ->
      when (err) {
        null -> result.complete()
        else -> result.fail(err)
      }
    }
    return result
  }

  override fun exists(key: String): Future<Boolean> {
    val result = Future.future<IdValue>()
    collection.find(IdValue::id eq key).first(singleCallback(result))
    return result.map { true }.recover { succeededFuture(false) }
  }

  override fun serve(key: String, routingContext: RoutingContext, cacheTimeout: Duration) {
    this.get(key).onSuccess { routingContext.end(it) }.catch { routingContext.end(it) }
  }

  fun migrate(textStorage: TextStorage): Future<Unit> {
    return textStorage.getAll()
      .map { it.map { IdValue(it.key, it.value) } }
      .compose {
        if (it.isEmpty()) {
          log.info("text storage is empty; no migration required")
          succeededFuture<Unit>()
        } else {
          log.info("migrating text storage to mongodb")
          it.map {
            log.info("migrating $it")
            collection.replaceOne(it)
          }.all()
            .compose {
              log.info("clearing file-base text storage")
              textStorage.clear()
            }
            .onSuccess { log.info("text storage migration done") }
        }
      }
  }

  data class IdValue(@BsonId val id: String, val value: String)
}