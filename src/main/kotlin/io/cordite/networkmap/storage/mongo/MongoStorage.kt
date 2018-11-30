
package io.cordite.networkmap.storage.mongo

import com.fasterxml.jackson.annotation.JsonProperty
import com.mongodb.client.model.Filters
import com.mongodb.reactivestreams.client.MongoClient
import com.mongodb.reactivestreams.client.MongoClients
import com.mongodb.reactivestreams.client.MongoCollection
import io.bluebank.braid.core.logging.loggerFor
import io.cordite.networkmap.storage.EmbeddedMongo
import io.cordite.networkmap.utils.NMSOptions
import io.vertx.core.Future
import org.bson.codecs.configuration.CodecRegistries
import org.bson.conversions.Bson
import org.reactivestreams.Publisher
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription
import java.io.File
import kotlin.reflect.KProperty
import kotlin.reflect.jvm.javaField


object MongoStorage {
  const val DEFAULT_DATABASE = "nms"
  val codecRegistry = CodecRegistries.fromRegistries(MongoClients.getDefaultCodecRegistry(),
    CodecRegistries.fromProviders(JacksonCodecProvider(ObjectMapperFactory.mapper)))!!

  fun connect(nmsOptions: NMSOptions): MongoClient {
    val connectionString = if (nmsOptions.mongoHost == "embed") {
      startEmbeddedDatabase(nmsOptions)
    } else {
      "mongodb://${nmsOptions.mongoUser}:${nmsOptions.mongoPassword}@${nmsOptions.mongoHost}:${nmsOptions.mongoPort}"
    }

    return MongoClients.create(connectionString)
  }

  private fun startEmbeddedDatabase(nmsOptions: NMSOptions): String {
    return with(nmsOptions) {
      startEmbeddedDatabase(dbDirectory, mongoUser, mongoPassword, mongodLocation)
    }
  }

  fun startEmbeddedDatabase(dbDirectory: File, mongoUser: String, mongoPassword: String, mongodLocation: String = ""): String {
    return EmbeddedMongo.create(File(dbDirectory, "mongo").absolutePath, mongoUser, mongoPassword, mongodLocation).connectionString
  }

}

inline fun <reified T : Any> MongoClient.getTypeCollection(db: String, collection: String) : MongoCollection<T> {
  return this.getDatabase(db).withCodecRegistry(MongoStorage.codecRegistry).getCollection(collection, T::class.java)
}

fun <T> Publisher<T>.toFuture() : Future<T> {
  val subscriber = SubscriberOnFuture<T>()
  this.subscribe(subscriber)
  return subscriber
}

class SubscriberOnFuture<T>(private val future: Future<T> = Future.future()) : Subscriber<T>, Future<T> by future {
  companion object {
    private val log = loggerFor<SubscriberOnFuture<*>>()
  }

  override fun onComplete() {
    try {
      when {
        future.failed() -> log.error("failed to complete future because future was failed")
        !future.succeeded() -> {
          val msg = "did not receive any value to complete the future"
          log.error(msg)
          future.fail(msg)
        }
      }
    } catch (err: Throwable) {
      log.error("failed to complete future")
    }
  }

  override fun onSubscribe(s: Subscription?) {
    s?.request(1)
  }

  override fun onNext(t: T) {
    try {
      when {
        future.isComplete -> log.error("future has already been completed")
        else -> future.complete(t)
      }
    } catch (err: Throwable) {
      log.error("failed to complete future", err)
    }
  }

  override fun onError(t: Throwable?) {
    try {
      future.fail(t)
    } catch (err : Throwable) {
      log.error("failed to fail future", err)
    }
  }
}

infix fun <R> KProperty<R>.eq(key: R): Bson {
  val jsonProperty = this.javaField!!.getDeclaredAnnotation(JsonProperty::class.java)
  return when (jsonProperty) {
    null -> this.name
    else -> jsonProperty.value
  }.let { Filters.eq(it, key) }
}