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

import io.cordite.networkmap.utils.*
import io.vertx.core.Vertx
import io.vertx.ext.unit.TestContext
import io.vertx.ext.unit.junit.VertxUnitRunner
import net.corda.core.identity.CordaX500Name
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.loggerFor
import net.corda.core.utilities.millis
import net.corda.node.services.network.NetworkMapClient
import net.corda.testing.driver.internal.InProcessImpl
import net.corda.testing.driver.internal.internalServices
import net.corda.testing.node.NotarySpec
import net.corda.testing.node.User
import net.corda.testing.node.internal.MOCK_VERSION_INFO
import net.corda.testing.node.internal.SharedCompatibilityZoneParams
import net.corda.testing.node.internal.internalDriver
import org.junit.*
import org.junit.runner.RunWith
import java.net.URL
import java.security.cert.X509Certificate
import java.time.Duration

@RunWith(VertxUnitRunner::class)
class CordaNodeTest {
  companion object {
    val log = loggerFor<CordaNodeTest>()
    val CACHE_TIMEOUT = 1.millis
    val NETWORK_PARAM_UPDATE_DELAY : Duration = 100.millis
    const val DEFAULT_NETWORK_MAP_ROOT = "/"

    @JvmField
    @ClassRule
    val mdcClassRule = JunitMDCRule()

    init {
      SerializationTestEnvironment.init()
      LogInitialiser.init()
    }
  }

  @JvmField
  @Rule
  val mdcRule = JunitMDCRule()

  private var vertx = Vertx.vertx()
  private val dbDirectory = createTempDir()
  private val port = getFreePort()
  private val webRoot = DEFAULT_NETWORK_MAP_ROOT
  private lateinit var service: NetworkMapService

  @Before
  fun before(context: TestContext) {
    // we'll need to have a serialization context so that the NMS can set itself up
    // BUT we can't use the one used by the application

    vertx = Vertx.vertx()
    val nmsOptions = NMSOptions(
      dbDirectory = dbDirectory,
      user = InMemoryUser.createUser("", "sa", ""),
      port = port,
      cacheTimeout = CACHE_TIMEOUT,
      tls = false,
      webRoot = DEFAULT_NETWORK_MAP_ROOT,
      paramUpdateDelay = NETWORK_PARAM_UPDATE_DELAY
    )
    this.service = NetworkMapService(nmsOptions = nmsOptions, vertx = vertx)
    service.startup().setHandler(context.asyncAssertSuccess())
  }

  @After
  fun after(context: TestContext) {
    val async = context.async()
    vertx.close {
      context.assertTrue(it.succeeded())
      async.complete()
    }
  }

  @Test
  fun `run node`(context: TestContext) {
    val rootCert = service.certificateManager.rootCertificateAndKeyPair.certificate

    log.info("starting up the driver")
    val zoneParams = SharedCompatibilityZoneParams(URL("http://localhost:$port$DEFAULT_NETWORK_MAP_ROOT"), null, {
      service.addNotaryInfos(it)
    }, rootCert)

    val portAllocation = FreePortAllocation()
    internalDriver(
      portAllocation = portAllocation,
      compatibilityZone = zoneParams,
      notarySpecs = listOf(NotarySpec(CordaX500Name("NotaryService", "Zurich", "CH"))),
      notaryCustomOverrides = mapOf("devMode" to false),
      startNodesInProcess = true
    ) {
      val user = User("user1", "test", permissions = setOf())
      log.info("start up the node")
      val node = startNode(providedName = CordaX500Name("CordaTestNode", "Southwold", "GB"), rpcUsers = listOf(user), customOverrides = mapOf("devMode" to false)).getOrThrow() as InProcessImpl
      log.info("node started. going to sleep to wait for the NMS to update")
      Thread.sleep(2000) // plenty of time for the NMS to synchronise
      val nmc = createNetworkMapClient(context, rootCert)
      val nm = nmc.getNetworkMap().payload
      val nmp = nmc.getNetworkParameters(nm.networkParameterHash).verified()
      context.assertEquals(node.internalServices.networkParameters, nmp)
      val nodeNodes = node.internalServices.networkMapCache.allNodes.toSet()
      val nmNodes = nm.nodeInfoHashes.map { nmc.getNodeInfo(it) }.toSet()
      context.assertEquals(nodeNodes, nmNodes)
      context.assertEquals(2, nodeNodes.size)
    }
  }


  private fun createNetworkMapClient(context: TestContext, rootCert: X509Certificate): NetworkMapClient {
    val async = context.async()
    service.storages.certAndKeys.get(CertificateManager.NETWORK_MAP_CERT_KEY)
      .onSuccess {
        context.put<X509Certificate>("cert", it.certificate)
        async.complete()
      }
      .setHandler(context.asyncAssertSuccess())
    async.awaitSuccess()
    return NetworkMapClient(URL("http://localhost:$port$webRoot"), MOCK_VERSION_INFO).apply {
      start(rootCert)
    }
  }
}