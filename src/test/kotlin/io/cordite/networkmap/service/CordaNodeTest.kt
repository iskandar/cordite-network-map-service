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
import net.corda.core.serialization.internal._globalSerializationEnv
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.millis
import net.corda.node.services.network.NetworkMapClient
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.internal.InProcessImpl
import net.corda.testing.driver.internal.internalServices
import net.corda.testing.node.User
import net.corda.testing.node.internal.SharedCompatibilityZoneParams
import org.junit.*
import org.junit.runner.RunWith
import java.net.URL
import java.security.cert.X509Certificate

@RunWith(VertxUnitRunner::class)
class CordaNodeTest {
  companion object {
    val CACHE_TIMEOUT = 1.millis
    val NETWORK_PARAM_UPDATE_DELAY = 1.millis
    val NETWORK_MAP_QUEUE_DELAY = 1.millis
    const val DEFAULT_NETWORK_MAP_ROOT = "/"

    @JvmField
    @ClassRule
    val mdcClassRule = JunitMDCRule()

    init {
      SerializationTestEnvironment.init()
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
    this.service = NetworkMapService(dbDirectory = dbDirectory,
      user = InMemoryUser.createUser("", "sa", ""),
      port = port,
      cacheTimeout = CACHE_TIMEOUT,
      networkMapQueuedUpdateDelay = NETWORK_MAP_QUEUE_DELAY,
      paramUpdateDelay = NETWORK_PARAM_UPDATE_DELAY,
      tls = false,
      vertx = vertx,
      webRoot = DEFAULT_NETWORK_MAP_ROOT,
      mongoClient = TestDatabase.createMongoClient(),
      mongoDatabase = TestDatabase.createUniqueDBName())
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
    // in the vain hope to make the serialization context harmonious between two servers that really don't want to play in the same process
    _globalSerializationEnv.set(null)

    val rootCert = service.certificateManager.rootCertificateAndKeyPair.certificate

    driverWithCompatZone(SharedCompatibilityZoneParams(URL("http://localhost:$port$DEFAULT_NETWORK_MAP_ROOT"), {
      // TODO: register notaries
    }, rootCert), DriverParameters(waitForAllNodesToFinish = false, isDebug = true, startNodesInProcess = true)) {
      val user = User("user1", "test", permissions = setOf())
      val node = startNode(providedName = CordaX500Name("PartyA", "New York", "US"), rpcUsers = listOf(user)).getOrThrow() as InProcessImpl

      // we'll directly access the network map and compare
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
    return NetworkMapClient(URL("http://localhost:$port$webRoot"), rootCert)
  }
}