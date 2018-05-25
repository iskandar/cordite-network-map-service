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
  private val vertx = Vertx.vertx()
  private val dbDirectory = createTempDir()
  private val port = getFreePort()

  private lateinit var service: NetworkMapService

  @Before
  fun before(context: TestContext) {

    val path = dbDirectory.absolutePath
    println(path)
    setupDefaultInputFiles(dbDirectory)

    this.service = NetworkMapService(dbDirectory, port)
    vertx.deployVerticle(service, context.asyncAssertSuccess())
  }

  @After
  fun after(context: TestContext) {
    vertx.close(context.asyncAssertSuccess())
  }

  @Test
  fun startCluster(context: TestContext) {
    val async = context.async()
    service.certificateAndKeyPairStorage.get(NetworkMapService.CERT_NAME)
      .onSuccess {
        context.put<X509Certificate>("cert", it.certificate)
        async.complete()
      }
      .setHandler(context.asyncAssertSuccess())
    async.awaitSuccess()
    val nmc = NetworkMapClient(URL("http://localhost:$port"), DEV_ROOT_CA.certificate)
    val nis = nmc.getNetworkMap().payload.nodeInfoHashes.map { nmc.getNodeInfo(it) }
//    val np = nmc.getNetworkParameters(nmc.getNetworkMap().payload.networkParameterHash)
  }

  private fun createTempDir() : File {
    return Files.createTempDirectory("nms-test-").toFile()
      .apply {
        mkdirs()
        deleteOnExit()
      }
  }

  private fun getFreePort(): Int {
    return with(ServerSocket(0)) { localPort }
  }

  private fun setupDefaultInputFiles(directory: File) {
    val inputs = File(directory, NetworkParameterInputsStorage.DEFAULT_DIR_NAME)
    inputs.mkdirs()
    Files.copy("${SAMPLE_INPUTS}whitelist.txt".toPath(), File(inputs, NetworkParameterInputsStorage.WHITELIST_NAME).toPath())
    copyFolder("${SAMPLE_INPUTS}validating".toPath(), File(inputs, NetworkParameterInputsStorage.DEFAULT_DIR_VALIDATING_NOTARIES).toPath())
    copyFolder("${SAMPLE_INPUTS}non-validating".toPath(), File(inputs, NetworkParameterInputsStorage.DEFAULT_DIR_NON_VALIDATING_NOTARIES).toPath())
  }

}