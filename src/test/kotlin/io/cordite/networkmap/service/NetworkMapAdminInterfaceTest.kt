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
import io.cordite.networkmap.storage.parseToWhitelistPairs
import io.cordite.networkmap.storage.toWhitelistPairs
import io.cordite.networkmap.storage.toWhitelistText
import io.cordite.networkmap.utils.*
import io.vertx.core.Vertx
import io.vertx.core.http.HttpClient
import io.vertx.core.http.HttpClientOptions
import io.vertx.core.json.Json
import io.vertx.ext.unit.TestContext
import io.vertx.ext.unit.junit.VertxUnitRunner
import io.vertx.kotlin.core.json.JsonObject
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.security.KeyStore

@RunWith(VertxUnitRunner::class)
class NetworkMapAdminInterfaceTest {
  companion object {
    init {
      SerializationTestEnvironment.init()
    }
    private var vertx = Vertx.vertx()
    private val dbDirectory = createTempDir()
    private val port = getFreePort()

    private lateinit var service: NetworkMapService
    private lateinit var client: HttpClient

    @JvmStatic
    @BeforeClass
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
      setupDefaultNodes(dbDirectory)

      this.service = NetworkMapService(dbDirectory = dbDirectory,
        user = InMemoryUser.createUser("", "sa", ""),
        port = port,
        hostname = "127.0.0.1",
        cacheTimeout = NetworkMapServiceTest.CACHE_TIMEOUT,
        networkParamUpdateDelay = NetworkMapServiceTest.NETWORK_PARAM_UPDATE_DELAY,
        networkMapQueuedUpdateDelay = NetworkMapServiceTest.NETWORK_MAP_QUEUE_DELAY,
        tls = true,
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

    @JvmStatic
    @AfterClass
    fun after(context: TestContext) {
      client.close()
      service.shutdown()
      vertx.close(context.asyncAssertSuccess())
    }
  }

  @Test
  fun `that we can login, retrieve notaries, nodes, whitelist, and we can delete the whitelist`(context: TestContext) {
    val async = context.async()
    var key = ""
    var whitelist = ""

    client.futurePost("${NetworkMapService.ADMIN_REST_ROOT}/login", JsonObject("user" to "sa", "password" to ""))
      .onSuccess {
        key = "Bearer $it"
        println(key)
      }
      .compose {
        client.futureGet("${NetworkMapService.ADMIN_REST_ROOT}/notaries")
      }
      .onSuccess {
        val notaries = Json.decodeValue(it, object : TypeReference<List<SimpleNotaryInfo>>() {})
        context.assertEquals(2, notaries.size)
      }
      .compose {
        client.futureGet("${NetworkMapService.ADMIN_REST_ROOT}/nodes")
      }
      .onSuccess {
        val nodes = Json.decodeValue(it, object : TypeReference<List<SimpleNodeInfo>>() {})
        context.assertEquals(2, nodes.size)
      }
      .compose {
        client.futureGet("${NetworkMapService.ADMIN_REST_ROOT}/whitelist")
      }
      .onSuccess {
        whitelist = it.toString()
        val lines = whitelist.toWhitelistPairs()
        context.assertNotEquals(0, lines.size)
      }
      .compose { // delete the whitelist
        client.futureDelete("${NetworkMapService.ADMIN_REST_ROOT}/whitelist", "Authorization" to key)
      }
      .compose { // get the whitelist
        client.futureGet("${NetworkMapService.ADMIN_REST_ROOT}/whitelist")
      }
      .onSuccess { // check its empty
        context.assertTrue(it.toString().isEmpty())
      }
      .compose { // append a set of white list items
        val updated = whitelist.toWhitelistPairs().drop(1)
        val newWhiteList = updated.toWhitelistText()
        client.futurePut("${NetworkMapService.ADMIN_REST_ROOT}/whitelist", newWhiteList, "Authorization" to key)
      }
      .compose {
        client.futureGet("${NetworkMapService.ADMIN_REST_ROOT}/whitelist")
      }
      .onSuccess {
        context.assertEquals(whitelist.toWhitelistPairs().size - 1, it.toString().toWhitelistPairs().size)
      }
      .compose { // set the complete whitelist
        client.futurePost("${NetworkMapService.ADMIN_REST_ROOT}/whitelist", whitelist, "Authorization" to key)
      }
      .compose {
        client.futureGet("${NetworkMapService.ADMIN_REST_ROOT}/whitelist")
      }
      .onSuccess {
        context.assertEquals(whitelist.lines().sorted().parseToWhitelistPairs(), it.toString().lines().sorted().parseToWhitelistPairs())
      }
      .onSuccess {
        async.complete()
      }
      .catch(context::fail)
  }

  @Test
  fun `that we can download the truststore`(context: TestContext) {
    val async = context.async()
    client.futureGet("${NetworkMapService.NETWORK_MAP_ROOT}/truststore")
      .map { buffer ->
        ByteArrayInputStream(buffer.bytes).use { stream ->
          KeyStore.getInstance(KeyStore.getDefaultType()).apply { load(stream, CertificateManager.TRUST_STORE_PASSWORD.toCharArray()) }
        }
      }
      .onSuccess {
        async.complete()
      }
      .catch(context::fail)
  }

  @Test
  fun `that downloading a certificate from the doorman with unknown csr id returns no content`(context: TestContext) {
    val async = context.async()
    client.get("/certificate/999")
      .exceptionHandler {
        context.fail(it)
      }
      .handler {
        context.assertEquals(HttpURLConnection.HTTP_NO_CONTENT, it.statusCode())
        async.complete()
      }
      .end()
  }
}