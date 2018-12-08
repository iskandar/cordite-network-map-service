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