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
package io.cordite.networkmap.storage.mongo

import com.mongodb.client.model.Filters
import com.mongodb.client.model.ReplaceOptions
import com.mongodb.reactivestreams.client.MongoClient
import io.bluebank.braid.core.logging.loggerFor
import io.cordite.networkmap.storage.Storage
import io.cordite.networkmap.storage.file.TextStorage
import io.cordite.networkmap.storage.mongo.serlalisation.BsonId
import io.cordite.networkmap.utils.*
import io.vertx.core.Future
import io.vertx.core.impl.NoStackTraceThrowable
import io.vertx.ext.web.RoutingContext
import org.reactivestreams.Subscription
import rx.Subscriber
import java.time.Duration

class MongoTextStorage(mongoClient: MongoClient,
                       database: String = MongoStorage.DEFAULT_DATABASE,
                       collection: String = "etc") : Storage<String> {
  companion object {
    private val log = loggerFor<MongoTextStorage>()
  }

  private val collection = mongoClient.getDatabase(database).getTypedCollection<KeyValue>(collection)

  override fun clear(): Future<Unit> = collection.drop().toFuture().mapUnit()

  override fun put(key: String, value: String): Future<Unit> = collection
    .replaceOne(KeyValue::key eq key, KeyValue(key, value), ReplaceOptions().upsert(true))
    .toFuture().mapUnit()

  fun put(keyValue: KeyValue): Future<Unit> = collection
    .replaceOne(KeyValue::key eq keyValue.key, keyValue, ReplaceOptions().upsert(true))
    .toFuture().mapUnit()

  override fun get(key: String): Future<String> = collection.find(KeyValue::key eq key)
    .first()
    .toFuture()
    .map {
      if (it == null) throw NoStackTraceThrowable("did not find value for key $key")
      it.value
    }

  fun migrate(textStorage: TextStorage): Future<Unit> {
    return textStorage.getAll()
      .map { it.map { KeyValue(it.key, it.value) } }
      .compose {
        if (it.isEmpty()) {
          log.info("text storage is empty; no migration required")
          Future.succeededFuture(Unit)
        } else {
          log.info("migrating text storage to mongodb")
          it.map {
            log.info("migrating $it")
            put(it)
          }.all()
            .compose {
              log.info("clearing file-base text storage")
              textStorage.clear()
            }
            .onSuccess { log.info("text storage migration done") }
        }
      }
  }

  override fun getOrNull(key: String): Future<String?> {
    return collection.find(KeyValue::key eq key)
      .first()
      .toFuture()
      .map {
        it?.value
      }
  }

  override fun getKeys(): Future<List<String>> {
    val list = mutableListOf<String>()
    val result = Future.future<List<String>>()
    collection.find()
      .subscribe(object : Subscriber<KeyValue>(), org.reactivestreams.Subscriber<KeyValue> {
        override fun onCompleted() {
          result.complete(list)
        }

        override fun onComplete() {
          result.complete(list)
        }

        override fun onSubscribe(subscription: Subscription?) {}

        override fun onNext(kv: KeyValue) {
          list.add(kv.key)
        }

        override fun onError(exception: Throwable?) {
          result.fail(exception)
        }
      })
    return result
  }

  override fun getAll(): Future<Map<String, String>> {
    val map = mutableMapOf<String, String>()
    val result = Future.future<Map<String, String>>()
    collection.find()
      .subscribe(object : Subscriber<KeyValue>(), org.reactivestreams.Subscriber<KeyValue> {
        override fun onCompleted() {
          result.complete(map)
        }

        override fun onComplete() {
          result.complete(map)
        }

        override fun onSubscribe(subscription: Subscription?) {}

        override fun onNext(kv: KeyValue) {
          map[kv.key] = kv.value
        }

        override fun onError(exception: Throwable?) {
          result.fail(exception)
        }
      })
    return result
  }

  override fun getAll(keys: List<String>): Future<Map<String, String>> {
    val map = mutableMapOf<String, String>()
    val result = Future.future<Map<String, String>>()
    collection.find()
      .filter(Filters.`in`(KeyValue::key.name, keys))
      .subscribe(object : Subscriber<KeyValue>(), org.reactivestreams.Subscriber<KeyValue> {
        override fun onCompleted() {
          result.complete(map)
        }

        override fun onComplete() {
          result.complete(map)
        }

        override fun onSubscribe(subscription: Subscription?) {}

        override fun onNext(kv: KeyValue) {
          map[kv.key] = kv.value
        }

        override fun onError(exception: Throwable?) {
          result.fail(exception)
        }
      })
    return result
  }

  override fun delete(key: String): Future<Unit> {
    return collection.deleteOne(Filters.eq(KeyValue::key.name, key))
      .toFuture()
      .mapUnit()
  }

  override fun exists(key: String): Future<Boolean> {
    return collection.countDocuments(Filters.eq(KeyValue::key.name, key))
      .toFuture()
      .map {
        it > 0
      }
  }

  override fun serve(key: String, routingContext: RoutingContext, cacheTimeout: Duration) {
    get(key)
      .onSuccess {
        routingContext.response().setCacheControl(cacheTimeout).end(it)
      }
      .catch {
        routingContext.end(it)
      }
  }


  data class KeyValue(@BsonId val key: String, val value: String)
}