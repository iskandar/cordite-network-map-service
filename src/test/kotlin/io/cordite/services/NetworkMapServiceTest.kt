package io.cordite.services

import io.cordite.services.serialisation.SerializationEnvironment
import io.cordite.services.storage.NetworkParameterInputsStorage
import io.cordite.services.utils.SAMPLE_INPUTS
import io.cordite.services.utils.copyFolder
import io.cordite.services.utils.onSuccess
import io.cordite.services.utils.toPath
import io.vertx.core.Vertx
import io.vertx.ext.unit.TestContext
import io.vertx.ext.unit.junit.VertxUnitRunner
import net.corda.node.services.network.NetworkMapClient
import net.corda.nodeapi.internal.DEV_ROOT_CA
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.net.ServerSocket
import java.net.URL
import java.nio.file.Files
import java.security.cert.X509Certificate

@RunWith(VertxUnitRunner::class)
class NetworkMapServiceTest {

  companion object {
    init {
      SerializationEnvironment.init()
    }
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

    setupDefaultInputFiles(dbDirectory)

    this.service = NetworkMapService(dbDirectory, port)
    vertx?.deployVerticle(service, context.asyncAssertSuccess())
  }

  @After
  fun after(context: TestContext) {
    vertx.close(context.asyncAssertSuccess())
  }

  @Test
  fun `that we can retrieve network map and parameters and they are correct`(context: TestContext) {
    val nmc = createNetworkMapClient(context)
    val nmp = nmc.getNetworkParameters(nmc.getNetworkMap().payload.networkParameterHash)
    val notaries = nmp.verified().notaries

    context.assertEquals(2, notaries.size)
    context.assertEquals(1, notaries.filter { it.validating }.count())
    context.assertEquals(1, notaries.filter { !it.validating }.count())

    val nis = getNetworkParties(nmc)
    context.assertEquals(0, nis.size)
  }

  @Test
  fun `that "my-host" is localhost`(context: TestContext) {
    val nmc = createNetworkMapClient(context)
    val hostname = nmc.myPublicHostname()
    context.assertEquals("localhost", hostname)
  }

  private fun getNetworkParties(nmc: NetworkMapClient) =
    nmc.getNetworkMap().payload.nodeInfoHashes.map { nmc.getNodeInfo(it) }


  private fun createNetworkMapClient(context: TestContext) : NetworkMapClient {
    val async = context.async()
    service.certificateAndKeyPairStorage.get(NetworkMapService.CERT_NAME)
      .onSuccess {
        context.put<X509Certificate>("cert", it.certificate)
        async.complete()
      }
      .setHandler(context.asyncAssertSuccess())
    async.awaitSuccess()
    return NetworkMapClient(URL("http://localhost:$port"), DEV_ROOT_CA.certificate)
  }

  private fun createTempDir() : File {
    return Files.createTempDirectory("nms-test-").toFile()
      .apply {
        mkdirs()
        deleteOnExit()
      }
  }

  private fun getFreePort(): Int {
    return ServerSocket(0).use { it.localPort }
  }

  private fun setupDefaultInputFiles(directory: File) {
    val inputs = File(directory, NetworkParameterInputsStorage.DEFAULT_DIR_NAME)
    inputs.mkdirs()
    Files.copy("${SAMPLE_INPUTS}whitelist.txt".toPath(), File(inputs, NetworkParameterInputsStorage.WHITELIST_NAME).toPath())
    copyFolder("${SAMPLE_INPUTS}validating".toPath(), File(inputs, NetworkParameterInputsStorage.DEFAULT_DIR_VALIDATING_NOTARIES).toPath())
    copyFolder("${SAMPLE_INPUTS}non-validating".toPath(), File(inputs, NetworkParameterInputsStorage.DEFAULT_DIR_NON_VALIDATING_NOTARIES).toPath())
  }
}