@file:Suppress("DEPRECATION")

package io.cordite.networkmap.storage

import com.mongodb.async.SingleResultCallback
import com.mongodb.async.client.MongoClient
import com.mongodb.async.client.MongoCollection
import com.mongodb.async.client.MongoIterable
import com.mongodb.client.result.UpdateResult
import io.cordite.networkmap.utils.NMSOptions
import io.cordite.networkmap.utils.completeFrom
import io.vertx.core.Future
import org.litote.kmongo.async.KMongo
import org.litote.kmongo.async.findOneById
import org.litote.kmongo.async.replaceOne
import java.io.File

object MongoStorage {
  const val DEFAULT_DATABASE = "nms"

  fun connect(nmsOptions: NMSOptions): MongoClient {
    val connectionString = if (nmsOptions.mongoHost == "embed") {
      startEmbeddedDatabase(nmsOptions)
    } else {
      "mongodb://${nmsOptions.mongoUser}:${nmsOptions.mongoPassword}@${nmsOptions.mongoHost}:${nmsOptions.mongoPort}"
    }
    return KMongo.createClient(connectionString)
  }

  private fun startEmbeddedDatabase(nmsOptions: NMSOptions): String {
    return with(nmsOptions) {
      startEmbeddedDatabase(dbDirectory, mongoUser, mongoPassword)
    }
  }

  fun startEmbeddedDatabase(dbDirectory: File, mongoUser: String, mongoPassword: String): String {
    return EmbeddedMongo.create(File(dbDirectory, "mongo").absolutePath, mongoUser, mongoPassword).connectionString
  }
}

fun <T : Any> MongoCollection<T>.insertOne(value: T): Future<Unit> {
  val result = Future.future<Void>()
  this.insertOne(value, singleCallback(result))
  return result.mapEmpty()
}

fun <T : Any> MongoCollection<T>.insertMany(value: List<T>): Future<Unit> {
  if (value.isEmpty()) return Future.succeededFuture()
  val result = Future.future<Void>()
  this.insertMany(value, singleCallback(result))
  return result.mapEmpty()
}

inline fun <reified T : Any> MongoCollection<T>.replaceOne(value: T): Future<Unit> {
  val result = Future.future<UpdateResult>()
  this.replaceOne(value, singleCallback(result)::onResult)
  return result.mapEmpty()
}

fun <T> MongoCollection<T>.count(): Future<Long> {
  val result = Future.future<Long>()
  this.count(singleCallback(result))
  return result
}

fun <T> MongoCollection<T>.drop(): Future<Void> {
  val result = Future.future<Void>()
  this.drop(singleCallback(result))
  return result
}

fun <T> MongoCollection<T>.findOneById(id: Any): Future<T> {
  val result = Future.future<T>()
  this.findOneById(id, singleCallback(result)::onResult)
  return result
}

fun <T> MongoIterable<T>.toList(): Future<List<T>> {
  val result = Future.future<ArrayList<T>>()
  this.into(arrayListOf(), singleCallback(result))
  return result.map { it as List<T> }
}

fun <T> singleCallback(future: Future<T>): SingleResultCallback<T> {
  return SingleResultCallback { value, err ->
    future.completeFrom(value, err)
  }
}

