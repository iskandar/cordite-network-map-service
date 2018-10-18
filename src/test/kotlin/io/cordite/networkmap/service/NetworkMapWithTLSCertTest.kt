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
import io.vertx.core.Vertx
import io.vertx.core.http.HttpClient
import io.vertx.core.http.HttpClientOptions
import io.vertx.core.json.Json
import io.vertx.ext.unit.TestContext
import io.vertx.ext.unit.junit.VertxUnitRunner
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(VertxUnitRunner::class)
class NetworkMapWithTLSCertTest {
  companion object {
    init {
      SerializationTestEnvironment.init()
    }
  }

  private var vertx = Vertx.vertx()
  private val dbDirectory = createTempDir()
  private val port = getFreePort()

  private lateinit var service: NetworkMapService
  private lateinit var client: HttpClient

  @Before
  fun before(context: TestContext) {
    vertx = Vertx.vertx()

    val fRead = vertx.fileSystem().readFiles("/Users/fuzz/tmp")
    val async = context.async()
    fRead.setHandler { async.complete() }
    async.await()


    val path = dbDirectory.absolutePath
    println("db path: $path")
    println("port   : $port")

    setupDefaultInputFiles(dbDirectory)

    val certPath = File("src/test/resources/certificates/domain.crt").absolutePath
    val keyPath =  File("src/test/resources/certificates/domain.key").absolutePath

    this.service = NetworkMapService(dbDirectory = dbDirectory,
      user = InMemoryUser.createUser("", "sa", ""),
      port = port,
      hostname = "127.0.0.1",
      webRoot = NetworkMapServiceTest.WEB_ROOT,
      cacheTimeout = NetworkMapServiceTest.CACHE_TIMEOUT,
      networkParamUpdateDelay = NetworkMapServiceTest.NETWORK_PARAM_UPDATE_DELAY,
      networkMapQueuedUpdateDelay = NetworkMapServiceTest.NETWORK_MAP_QUEUE_DELAY,
      tls = true,
      certPath = certPath,
      keyPath = keyPath,
      vertx = vertx
    )

    service.startup().setHandler(context.asyncAssertSuccess())
    client = vertx.createHttpClient(HttpClientOptions()
      .setDefaultHost("127.0.0.1")
      .setDefaultPort(port)
      .setSsl(true)
      .setTrustAll(true)
      .setVerifyHost(false)
    )
  }

  @After
  fun after(context: TestContext) {
    client.close()
    service.shutdown()
    vertx.close(context.asyncAssertSuccess())
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