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
package io.cordite.networkmap.service

import com.fasterxml.jackson.core.type.TypeReference
import io.cordite.networkmap.utils.*
import io.vertx.core.Future
import io.vertx.core.Vertx
import io.vertx.core.http.HttpClient
import io.vertx.core.http.HttpClientOptions
import io.vertx.core.json.Json
import io.vertx.ext.unit.TestContext
import io.vertx.ext.unit.junit.VertxUnitRunner
import org.junit.*
import org.junit.runner.RunWith
import java.io.File
import java.time.Duration

@RunWith(VertxUnitRunner::class)
class NetworkMapWithTLSCertTest {
  companion object {
    @JvmField
    @ClassRule
    val mdcClassRule = JunitMDCRule()

    init {
      SerializationTestEnvironment.init()
    }
  }

  private var vertx = Vertx.vertx()
  private val dbDirectory = createTempDir()
  private val port = getFreePort()

  private lateinit var service: NetworkMapService
  private lateinit var client: HttpClient


  @JvmField
  @Rule
  val mdcRule = JunitMDCRule()

  @Before
  fun before(context: TestContext) {
    vertx = Vertx.vertx()
    val certPath = File("src/test/resources/certificates/domain.crt").absolutePath
    val keyPath = File("src/test/resources/certificates/domain.key").absolutePath

    client = vertx.createHttpClient(HttpClientOptions()
      .setDefaultHost("127.0.0.1")
      .setDefaultPort(port)
      .setSsl(true)
      .setTrustAll(true)
      .setVerifyHost(false)
    )

    this.service = NetworkMapService(dbDirectory = dbDirectory,
      user = InMemoryUser.createUser("", "sa", ""),
      port = port,
      cacheTimeout = NetworkMapServiceTest.CACHE_TIMEOUT,
      networkMapQueuedUpdateDelay = Duration.ZERO,
      paramUpdateDelay = Duration.ZERO,
      tls = true,
      certPath = certPath,
      keyPath = keyPath,
      vertx = vertx,
      hostname = "127.0.0.1",
      webRoot = NetworkMapServiceTest.WEB_ROOT,
      mongoClient = TestDatabase.createMongoClient(),
      mongoDatabase = TestDatabase.createUniqueDBName()
    )

    val completed = Future.future<Unit>()
    service.startup().setHandler(completed.completer())
    completed
      .compose {  service.processor.initialiseWithTestData(vertx) }
      .setHandler(context.asyncAssertSuccess())
  }

  @After
  fun after(context: TestContext) {
    client.close()
    service.shutdown()
    val async = context.async()
    vertx.close {
      context.assertTrue(it.succeeded())
      async.complete()
    }
  }

  @Test
  fun `that we can retrieve notaries`(context: TestContext) {
    val async = context.async()
    client.futureGet("${NetworkMapServiceTest.WEB_ROOT}/admin/api/notaries")
      .onSuccess {
        val decoded = Json.decodeValue(it, object : TypeReference<List<SimpleNotaryInfo>>() {})
        context.assertNotEquals(0, decoded.size)
        async.complete()
      }
      .catch(context::fail)
  }
}