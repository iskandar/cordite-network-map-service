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
import io.cordite.networkmap.serialisation.parseWhitelist
import io.cordite.networkmap.storage.file.NetworkParameterInputsStorage.Companion.DEFAULT_DIR_NON_VALIDATING_NOTARIES
import io.cordite.networkmap.storage.file.NetworkParameterInputsStorage.Companion.DEFAULT_DIR_VALIDATING_NOTARIES
import io.cordite.networkmap.utils.*
import io.vertx.core.Vertx
import io.vertx.core.http.HttpClient
import io.vertx.core.http.HttpClientOptions
import io.vertx.core.json.Json
import io.vertx.ext.unit.TestContext
import io.vertx.ext.unit.junit.VertxUnitRunner
import io.vertx.kotlin.core.json.jsonObjectOf
import net.corda.core.node.NetworkParameters
import net.corda.core.utilities.loggerFor
import org.junit.*
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.File
import java.net.HttpURLConnection
import java.security.KeyStore
import java.time.Duration

@RunWith(VertxUnitRunner::class)
class NetworkMapAdminInterfaceTest {
  companion object {
    private val log = loggerFor<NetworkMapAdminInterfaceTest>()

    init {
      SerializationTestEnvironment.init()
    }

    private var vertx = Vertx.vertx()
    private val dbDirectory = createTempDir()
    private val port = getFreePort()

    private lateinit var service: NetworkMapService
    private lateinit var client: HttpClient

    @JvmField
    @ClassRule
    val mdcClassRule = JunitMDCRule()


    @JvmStatic
    @BeforeClass
    fun before(context: TestContext) {
      vertx = Vertx.vertx()

      val async = context.async()
      val nmsOptions = NMSOptions(
        dbDirectory = dbDirectory,
        user = InMemoryUser("", "sa", ""),
        port = port,
        cacheTimeout = NetworkMapServiceTest.CACHE_TIMEOUT,
        tls = true,
        hostname = "127.0.0.1",
        webRoot = NetworkMapServiceTest.WEB_ROOT,
        paramUpdateDelay = Duration.ZERO
      )
      this.service = NetworkMapService(nmsOptions)

      service.startup().setHandler {
        when {
          it.succeeded() -> async.complete()
          else -> context.fail(it.cause())
        }
      }

      client = vertx.createHttpClient(HttpClientOptions()
        .setDefaultHost("127.0.0.1")
        .setDefaultPort(port)
        .setSsl(true)
        .setTrustAll(true)
        .setVerifyHost(false)
      )

      async.await()

      service.processor.initialiseWithTestData(vertx).setHandler(context.asyncAssertSuccess())
    }

    @JvmStatic
    @AfterClass
    fun after(context: TestContext) {
      client.close()
      service.shutdown()
      val async = context.async()
      vertx.close {
        context.assertTrue(it.succeeded())
        async.complete()
      }
    }
  }

  @JvmField
  @Rule
  val mdcRule = JunitMDCRule()

  @Test
  fun `that we can login, retrieve notaries, nodes, whitelist, and we can modify notaries and whitelist`(context: TestContext) {
    val async = context.async()
    var key = ""
    var whitelist = ""

    log.info("logging in")
    client.futurePost("${NetworkMapServiceTest.WEB_ROOT}${NetworkMapService.ADMIN_REST_ROOT}/login", jsonObjectOf("user" to "sa", "password" to ""))
      .onSuccess {
        key = "Bearer $it"
        log.info("key: $key")
      }
      .compose {
        log.info("getting notaries")
        client.futureGet("${NetworkMapServiceTest.WEB_ROOT}${NetworkMapService.ADMIN_REST_ROOT}/notaries")
      }
      .onSuccess {
        log.info("succeeded in getting notaries")
        val notaries = Json.decodeValue(it, object : TypeReference<List<SimpleNotaryInfo>>() {})
        context.assertEquals(2, notaries.size, "notaries should be correct count")
        log.info("count of notaries is right")
      }
      .compose {
        log.info("get nodes")
        client.futureGet("${NetworkMapServiceTest.WEB_ROOT}${NetworkMapService.ADMIN_REST_ROOT}/nodes")
      }
      .onSuccess {
        log.info("succeeded getting nodes")
        val nodes = Json.decodeValue(it, object : TypeReference<List<SimpleNodeInfo>>() {})
        context.assertEquals(2, nodes.size, "nodes should be correct count")
        log.info("node count is correct")
      }
      .compose {
        log.info("posting non-validating notary nodeInfo")
        val nodeInfo1 = File("$SAMPLE_INPUTS$DEFAULT_DIR_NON_VALIDATING_NOTARIES/", "nodeInfo-B5CD5B0AD037FD930549D9F3D562AB9B0E94DAB8284DB205E2E82F639EAB4341")
        val payload = vertx.fileSystem().readFileBlocking(nodeInfo1.absolutePath)
        client.futurePost("${NetworkMapServiceTest.WEB_ROOT}${NetworkMapService.ADMIN_REST_ROOT}/notaries/nonValidating", payload, "Authorization" to key)
      }
      .compose {
        log.info("posting validating notary nodeInfo")
        val nodeInfoPath = File("$SAMPLE_INPUTS$DEFAULT_DIR_VALIDATING_NOTARIES/", "nodeInfo-007A0CAE8EECC5C9BE40337C8303F39D34592AA481F3153B0E16524BAD467533")
        val payload = vertx.fileSystem().readFileBlocking(nodeInfoPath.absolutePath)
        client.futurePost("${NetworkMapServiceTest.WEB_ROOT}${NetworkMapService.ADMIN_REST_ROOT}/notaries/validating", payload, "Authorization" to key)
      }
      .compose {
        log.info("getting notaries")
        client.futureGet("${NetworkMapServiceTest.WEB_ROOT}${NetworkMapService.ADMIN_REST_ROOT}/notaries")
      }
      .onSuccess {
        log.info("succeeded in getting notaries")
        val notaries = Json.decodeValue(it, object : TypeReference<List<SimpleNotaryInfo>>() {})
        context.assertEquals(2, notaries.size, "notaries should be correct count after update")
        log.info("notary count is correct")
      }
      .compose {
        log.info("getting whitelist")
        client.futureGet("${NetworkMapServiceTest.WEB_ROOT}${NetworkMapService.ADMIN_REST_ROOT}/whitelist")
      }
      .onSuccess {
        whitelist = it.toString()
        val lines = whitelist.parseWhitelist()
        context.assertNotEquals(0, lines.size)
      }
      .compose {
        // delete the whitelist
        log.info("deleting whitelist")
        client.futureDelete("${NetworkMapServiceTest.WEB_ROOT}${NetworkMapService.ADMIN_REST_ROOT}/whitelist", "Authorization" to key)
      }
      .compose {
        // get the whitelist
        log.info("getting whitelist")
        client.futureGet("${NetworkMapServiceTest.WEB_ROOT}${NetworkMapService.ADMIN_REST_ROOT}/whitelist")
      }
      .onSuccess {
        // check its empty
        context.assertTrue(it.toString().isEmpty())
      }
      .compose {
        // append a set of white list items
        log.info("appending to whitelist")
        val wls = whitelist.parseWhitelist()
        val keyToRemove = wls.keys.first()
        val updated = wls - keyToRemove
        val newWhiteList = updated.toString()
        client.futurePut("${NetworkMapServiceTest.WEB_ROOT}${NetworkMapService.ADMIN_REST_ROOT}/whitelist", newWhiteList, "Authorization" to key)
      }
      .compose {
        log.info("getting whitelist")
        client.futureGet("${NetworkMapServiceTest.WEB_ROOT}${NetworkMapService.ADMIN_REST_ROOT}/whitelist")
      }
      .onSuccess {
        context.assertEquals(whitelist.parseWhitelist().size - 1, it.toString().parseWhitelist().size)
      }
      .compose {
        // set the complete whitelist
        log.info("posting whitelist")
        client.futurePost("${NetworkMapServiceTest.WEB_ROOT}${NetworkMapService.ADMIN_REST_ROOT}/whitelist", whitelist, "Authorization" to key)
      }
      .compose {
        log.info("getting whitelist")
        client.futureGet("${NetworkMapServiceTest.WEB_ROOT}${NetworkMapService.ADMIN_REST_ROOT}/whitelist")
      }
      .onSuccess {
        context.assertEquals(whitelist.parseWhitelist(), it.toString().parseWhitelist())
      }
      .onSuccess {
        async.complete()
      }
      .catch(context::fail)
  }

  @Test
  fun `that we can download the truststore`(context: TestContext) {
    val async = context.async()
    client.futureGet("${NetworkMapServiceTest.WEB_ROOT}${NetworkMapService.NETWORK_MAP_ROOT}/truststore")
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
    @Suppress("DEPRECATION")
    client.get("${NetworkMapServiceTest.WEB_ROOT}/certificate/999") {
      context.assertEquals(HttpURLConnection.HTTP_NO_CONTENT, it.statusCode())
      async.complete()
    }.exceptionHandler {
      context.fail(it)
    }.end()
  }

  @Test
  fun `that we can retrieve the current network parameters`(context: TestContext) {
    val async = context.async()
    var np: NetworkParameters? = null

    service.processor.createSignedNetworkMap()
      .map { it.verified().networkParameterHash.toString() }
      .compose { service.storages.networkParameters.get(it) }
      .map { np = it.verified() }
      .compose { client.futureGet("${NetworkMapServiceTest.WEB_ROOT}${NetworkMapService.ADMIN_REST_ROOT}/network-parameters/current") }
      .map { Json.decodeValue(it, NetworkParameters::class.java) }
      .onSuccess { context.assertEquals(np, it) }
      .onSuccess { async.complete() }
      .catch { context.fail(it) }
  }

  @Test
  fun `that we can download build properties`(context: TestContext) {
    val async = context.async()
    @Suppress("DEPRECATION")
    client.get("${NetworkMapServiceTest.WEB_ROOT}${NetworkMapService.ADMIN_REST_ROOT}/build-properties") { response ->
      context.assertEquals(HttpURLConnection.HTTP_OK, response.statusCode())
      response.bodyHandler { buffer ->
        val properties = Json.decodeValue(buffer, Map::class.java)
        context.assertTrue(properties.isNotEmpty())
        async.complete()
      }
    }.exceptionHandler {
      context.fail(it)
    }.end()
  }
}
