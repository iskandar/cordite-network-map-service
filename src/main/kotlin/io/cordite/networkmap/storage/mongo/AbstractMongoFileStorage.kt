package io.cordite.networkmap.storage.mongo

import com.mongodb.client.model.Filters
import com.mongodb.reactivestreams.client.MongoDatabase
import com.mongodb.reactivestreams.client.gridfs.GridFSBuckets
import io.cordite.networkmap.serialisation.serializeOnContext
import io.cordite.networkmap.storage.Storage
import io.cordite.networkmap.storage.mongo.rx.toObservable
import io.cordite.networkmap.storage.mongo.serlalisation.asAsyncOutputStream
import io.cordite.networkmap.storage.mongo.serlalisation.toAsyncOutputStream
import io.cordite.networkmap.utils.*
import io.netty.buffer.PooledByteBufAllocator
import io.netty.handler.codec.http.HttpHeaderValues
import io.vertx.core.Future
import io.vertx.core.buffer.Buffer
import io.vertx.core.http.HttpHeaders
import io.vertx.ext.web.RoutingContext
import net.corda.core.toFuture
import net.corda.core.utilities.loggerFor
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.time.Duration

abstract class AbstractMongoFileStorage<T : Any>(val name: String, val db: MongoDatabase) : Storage<T> {
  companion object {
    private val log = loggerFor<AbstractMongoFileStorage<*>>()
  }

  private var bucket = GridFSBuckets.create(db, name)

  override fun clear(): Future<Unit> {
    return bucket.drop().toFuture()
      .onSuccess { bucket = GridFSBuckets.create(db, name) }
      .mapEmpty()
  }

  override fun put(key: String, value: T): Future<Unit> {
    val bytes = value.serializeOnContext().let { ByteBuffer.wrap(it.bytes) }
    val stream = bucket.openUploadStream(key)
    return stream.write(bytes).toFuture()
      .compose { stream.close().toFuture() }
      .mapEmpty()
  }

  override fun get(key: String): Future<T> {
    return ByteArrayOutputStream().use { arrayStream ->
      bucket.downloadToStream(key, arrayStream.toAsyncOutputStream()).toFuture()
        .map {
          deserialize(arrayStream.toByteArray())
        }
    }
  }

  override fun getOrNull(key: String): Future<T?> {
    return get(key)
      .recover {
        Future.succeededFuture(null)
      }
  }

  override fun getOrDefault(key: String, default: T): Future<T> {
    return get(key)
      .recover {
        Future.succeededFuture(default)
      }
  }

  override fun getKeys(): Future<List<String>> {
    return db.getCollection("$name.files", FileName::class.java).find()
      .toObservable()
      .map { it.filename }
      .toList()
      .map { it as List<String> }
      .toFuture().toVertxFuture()
  }

  override fun getAll(): Future<Map<String, T>> {
    // nominal implementation - very slow - considering speeding up
    return getKeys()
      .compose { keys ->
        keys.map { key ->
          get(key).map { key to it }
        }.all()
      }
      .map { pairs ->
        pairs.toMap()
      }
  }

  override fun delete(key: String): Future<Unit> {
    return bucket.find(Filters.eq("filename", key)).first().toFuture()
      .compose { fileDescriptor ->
        when (fileDescriptor) {
          null -> Future.succeededFuture<Unit>()
          else -> {
            bucket.delete(fileDescriptor.objectId).toFuture().mapEmpty<Unit>()
          }
        }
      }
  }

  override fun exists(key: String): Future<Boolean> {
    return bucket.find(Filters.eq("filename", key)).first().toFuture()
      .map { it != null }
  }

  override fun serve(key: String, routingContext: RoutingContext, cacheTimeout: Duration) {
    val byteBuf = PooledByteBufAllocator.DEFAULT.buffer()
    bucket.downloadToStream(key, byteBuf.asAsyncOutputStream())
      .toFuture()
      .onSuccess {
        val buffer = Buffer.buffer(byteBuf)
        routingContext.response().apply {
          putHeader(HttpHeaders.CACHE_CONTROL, "max-age=${cacheTimeout.seconds}")
          putHeader(HttpHeaders.CONTENT_TYPE, HttpHeaderValues.APPLICATION_OCTET_STREAM)
          putHeader(HttpHeaders.CONTENT_LENGTH, buffer.length().toString())
          end(buffer)
        }
      }
      .catch { error ->
        try {
          routingContext.end(error)
          byteBuf.release()
        } catch (err: Throwable) {
          log.error("failed to send error to client for request to serve $key")
        }
      }
      .finally {
//        byteBuf.release() // Netty appears to release the buffer anyway
      }
  }

  protected abstract fun deserialize(location: ByteArray): T
  data class FileName(val filename: String)
}

