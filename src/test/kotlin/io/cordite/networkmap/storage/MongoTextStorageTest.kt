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
package io.cordite.networkmap.storage

import com.mongodb.reactivestreams.client.MongoClient
import com.mongodb.reactivestreams.client.MongoClients
import io.cordite.networkmap.storage.mongo.MongoStorage
import io.cordite.networkmap.storage.mongo.MongoTextStorage
import io.cordite.networkmap.utils.catch
import io.cordite.networkmap.utils.onSuccess
import io.vertx.ext.unit.TestContext
import io.vertx.ext.unit.junit.VertxUnitRunner
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(VertxUnitRunner::class)
class MongoTextStorageTest {
  companion object {
    private val dbDirectory = createTempDir()

    private lateinit var mongoClient: MongoClient

    @JvmStatic
    @BeforeClass
    fun beforeClass() {
      mongoClient = MongoClients.create(MongoStorage.startEmbeddedDatabase(dbDirectory))
    }

    @JvmStatic
    @AfterClass
    fun afterClass() {
      mongoClient.close()
    }
  }

  @Test
  fun testStorage(context: TestContext) {
    val async = context.async()
    MongoTextStorage(mongoClient).apply {
      this.put("hello", "world")
        .compose {
          this.get("hello")
        }
        .onSuccess {
          context.assertEquals("world", it)
        }
        .onSuccess { async.complete() }
        .catch { context.fail(it) }
    }
  }
}