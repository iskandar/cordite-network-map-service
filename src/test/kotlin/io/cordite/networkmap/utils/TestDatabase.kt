package io.cordite.networkmap.utils

import com.mongodb.reactivestreams.client.MongoClient
import com.mongodb.reactivestreams.client.MongoClients
import io.cordite.networkmap.storage.mongo.MongoStorage
import java.util.concurrent.atomic.AtomicInteger

object TestDatabase {
  private val fountain = AtomicInteger(1)
  private val embeddedMongo = MongoStorage.startEmbeddedDatabase(createTempDir(), isDaemon = true)
  fun createMongoClient(): MongoClient = MongoClients.create(embeddedMongo.connectionString)
  fun createUniqueDBName() = "db-${fountain.getAndIncrement()}"
}