package io.cordite.networkmap.service

import io.cordite.networkmap.serialisation.SerializationEnvironment
import io.cordite.networkmap.utils.getFreePort
import io.cordite.networkmap.utils.onSuccess
import io.vertx.core.Vertx
import io.vertx.ext.unit.TestContext
import io.vertx.ext.unit.junit.VertxUnitRunner
import net.corda.core.identity.CordaX500Name
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.millis
import net.corda.node.services.network.NetworkMapClient
import net.corda.nodeapi.internal.DEV_ROOT_CA
import net.corda.testing.driver.DriverDSL
import net.corda.testing.driver.DriverParameters
import net.corda.testing.node.User
import net.corda.testing.node.internal.CompatibilityZoneParams
import net.corda.testing.node.internal.DriverDSLImpl
import net.corda.testing.node.internal.genericDriver
import org.junit.After
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import java.net.URL
import java.security.cert.X509Certificate

@RunWith(VertxUnitRunner::class)
class CordaNodeTest {
  companion object {
    init {
      SerializationEnvironment.init()
    }
    val CACHE_TIMEOUT = 1.millis
    val NETWORK_PARAM_UPDATE_DELAY = 1.millis
    val NETWORK_MAP_QUEUE_DELAY = 1.millis
  }

  private var vertx = Vertx.vertx()
  private val dbDirectory = createTempDir()
  private val port = getFreePort()

  private lateinit var service: NetworkMapService

  @Before
  fun before(context: TestContext) {
    vertx = Vertx.vertx()
    val path = dbDirectory.absolutePath
    println("db path: $path")
    println("port   : $port")

//    setupDefaultInputFiles(dbDirectory)


    this.service = NetworkMapService(dbDirectory = dbDirectory,
      user = InMemoryUser.createUser("", "sa", ""),
      port = port,
      cacheTimeout = CACHE_TIMEOUT,
      networkParamUpdateDelay = NETWORK_PARAM_UPDATE_DELAY,
      networkMapQueuedUpdateDelay = NETWORK_MAP_QUEUE_DELAY,
      tls = false,
      vertx = vertx
    )

    service.start().setHandler(context.asyncAssertSuccess())
  }

  @After
  fun after(context: TestContext) {
    vertx.close(context.asyncAssertSuccess())
  }

  /**
   * The following test is broken because the corda node seems to request node infos that are not present in the network map
   * Is node PartyA attempting to get hold of the notary that's not been registered?
   */
  @Ignore
  @Test
  fun `that we can start up a node that connects to networkmap and registers`(context: TestContext) {
    val nmc = createNetworkMapClient(context)
    val nm = nmc.getNetworkMap().payload
    val nmp = nmc.getNetworkParameters(nm.networkParameterHash).verified()
    val notaries = nmp.notaries
    val whitelist = nmp.whitelistedContractImplementations
    val nodes = nm.nodeInfoHashes.map { nmc.getNodeInfo(it) }

    driverWithCompatZone(CompatibilityZoneParams(URL("http://localhost:$port"), {}), DriverParameters(isDebug = true, waitForAllNodesToFinish = true, startNodesInProcess = false)) {
      val user = User("user1", "test", permissions = setOf())
      val node = startNode(providedName = CordaX500Name("PartyA", "New York", "US"), rpcUsers = listOf(user)).getOrThrow()
//      val nodeInfo = node.nodeInfo
//      println(nodes)
      node.stop()
    }
    val nodes2 = nmc.getNetworkMap().payload.nodeInfoHashes.map { nmc.getNodeInfo(it) }
  }

  private fun <A> driverWithCompatZone(compatibilityZone: CompatibilityZoneParams, defaultParameters: DriverParameters = DriverParameters(), dsl: DriverDSL.() -> A): A {
    return genericDriver(
      driverDsl = DriverDSLImpl(
        portAllocation = defaultParameters.portAllocation,
        debugPortAllocation = defaultParameters.debugPortAllocation,
        systemProperties = defaultParameters.systemProperties,
        driverDirectory = defaultParameters.driverDirectory.toAbsolutePath(),
        useTestClock = defaultParameters.useTestClock,
        isDebug = defaultParameters.isDebug,
        startNodesInProcess = defaultParameters.startNodesInProcess,
        waitForAllNodesToFinish = defaultParameters.waitForAllNodesToFinish,
        notarySpecs = defaultParameters.notarySpecs,
        extraCordappPackagesToScan = defaultParameters.extraCordappPackagesToScan,
        jmxPolicy = defaultParameters.jmxPolicy,
        compatibilityZone = compatibilityZone,
        networkParameters = defaultParameters.networkParameters
      ),
      coerce = { it },
      dsl = dsl,
      initialiseSerialization = true
    )
  }

  private fun createNetworkMapClient(context: TestContext): NetworkMapClient {
    val async = context.async()
    service.certificateAndKeyPairStorage.get(NetworkMapService.SIGNING_CERT_NAME)
      .onSuccess {
        context.put<X509Certificate>("cert", it.certificate)
        async.complete()
      }
      .setHandler(context.asyncAssertSuccess())
    async.awaitSuccess()
    return NetworkMapClient(URL("http://localhost:$port"), DEV_ROOT_CA.certificate)
  }
}