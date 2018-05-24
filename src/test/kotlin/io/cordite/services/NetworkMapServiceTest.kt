package io.cordite.services

import io.cordite.services.serialisation.SerializationEnvironment
import io.cordite.services.storage.NetworkParameterInputsStorage
import io.cordite.services.utils.copyFolder
import io.cordite.services.utils.toPath
import io.vertx.core.Vertx
import io.vertx.ext.unit.TestContext
import io.vertx.ext.unit.junit.VertxUnitRunner
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.net.ServerSocket
import java.nio.file.Files

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

  @Before
  fun before(context: TestContext) {

    val path = dbDirectory.absolutePath
    println(path)
    setupDefaultInputFiles(dbDirectory)

    val service = NetworkMapService(dbDirectory, port)
    vertx.deployVerticle(service, context.asyncAssertSuccess())
  }

  @After
  fun after(context: TestContext) {
    vertx.close(context.asyncAssertSuccess())
  }

  @Test
  fun startCluster(context: TestContext) {
    context.async()
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
    Files.copy("src/test/resources/sample-input-set/whitelist.txt".toPath(), File(inputs, NetworkParameterInputsStorage.WHITELIST_NAME).toPath())
    copyFolder("src/test/resources/sample-input-set/validating".toPath(), File(inputs, NetworkParameterInputsStorage.DEFAULT_DIR_VALIDATING_NOTARIES).toPath())
    copyFolder("src/test/resources/sample-input-set/non-validating".toPath(), File(inputs, NetworkParameterInputsStorage.DEFAULT_DIR_NON_VALIDATING_NOTARIES).toPath())
  }

}