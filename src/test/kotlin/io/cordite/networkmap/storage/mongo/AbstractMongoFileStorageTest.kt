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

import com.mongodb.reactivestreams.client.MongoClient
import com.mongodb.reactivestreams.client.MongoClients
import com.mongodb.reactivestreams.client.MongoDatabase
import io.cordite.networkmap.serialisation.deserializeOnContext
import io.cordite.networkmap.storage.EmbeddedMongo
import io.cordite.networkmap.utils.SerializationTestEnvironment
import io.cordite.networkmap.utils.catch
import io.cordite.networkmap.utils.getFreePort
import io.cordite.networkmap.utils.onSuccess
import io.vertx.core.Future
import io.vertx.core.Vertx
import io.vertx.core.http.HttpClientOptions
import io.vertx.core.http.HttpServerOptions
import io.vertx.ext.unit.TestContext
import io.vertx.ext.unit.junit.VertxUnitRunner
import io.vertx.ext.web.Router
import net.corda.core.serialization.CordaSerializable
import org.junit.*
import org.junit.runner.RunWith
import java.time.Duration

@RunWith(VertxUnitRunner::class)
class AbstractMongoFileStorageTest {
  companion object {
    private val dbDirectory = createTempDir()

    private lateinit var mongoClient: MongoClient
    private lateinit var mongodb: EmbeddedMongo

    init {
      SerializationTestEnvironment.init()
    }

    @JvmStatic
    @BeforeClass
    fun beforeClass() {

      mongodb = MongoStorage.startEmbeddedDatabase(dbDirectory)
      mongoClient = MongoClients.create(mongodb.connectionString)
    }

    @JvmStatic
    @AfterClass
    fun afterClass() {
      mongoClient.close()
      mongodb.close()
    }
  }

  @CordaSerializable
  data class TestData(val name: String)

  class TestDataStorage(name: String, db: MongoDatabase) : AbstractMongoFileStorage<TestData>(name, db) {
    override fun deserialize(location: ByteArray): TestData {
      return location.deserializeOnContext()
    }
  }

  private val vertx = Vertx.vertx()
  private val storage = TestDataStorage("test-data", mongoClient.getDatabase("db"))
  private val port = getFreePort()
  private val fileName = "foo"

  @Before
  fun before(context: TestContext) {
    Router.router(vertx).apply {
      get("/$fileName").handler {
        storage.serve(fileName, it, Duration.ZERO)
      }
      vertx.createHttpServer(HttpServerOptions().setHost("localhost"))
        .requestHandler(this::accept)
        .listen(port)
    }
  }


  @After
  fun after(context: TestContext) {
    val async = context.async()
    vertx.close {
      async.complete()
    }
  }

  @Test
  fun `populate storage and retrieve`(context: TestContext) {
    val testName = "vurt feather"
    val async = context.async()
    storage.put(fileName, TestData(testName))
      .compose { storage.get(fileName) }
      .onSuccess { data ->
        context.assertNotNull(data)
        context.assertEquals(testName, data.name)
      }
      .compose { storage.exists(fileName) }
      .onSuccess { exists -> context.assertTrue(exists, "that file exists") }
      .compose { testRetrievalViaHttp(fileName) }
      .onSuccess { data ->
        context.assertNotNull(data)
        context.assertEquals(testName, data.name)
      }
      .compose { storage.delete(fileName) }
      .compose { storage.exists(fileName) }
      .onSuccess { exists -> context.assertFalse(exists, "that file has been really deleted") }
      .onSuccess { async.complete() }
      .catch { context.fail(it) }
  }

  private fun testRetrievalViaHttp(fileName: String): Future<TestData> {
    val client = vertx.createHttpClient(HttpClientOptions().setDefaultPort(port).setDefaultHost("localhost"))
    val result = Future.future<TestData>()
    try {
      client.get("/$fileName")
        .exceptionHandler { err ->
          result.fail(err)
          client.close()
        }
        .handler {
          it.bodyHandler { buffer ->
            try {
              val value = buffer.bytes.deserializeOnContext<TestData>()
              result.complete(value)
            } catch(err: Throwable) {
              result.fail(err)
            } finally {
              client.close()
            }
          }
        }
        .end()
    } catch(err: Throwable) {
      result.fail(err)
      client.close()
    }
    return result
  }
}