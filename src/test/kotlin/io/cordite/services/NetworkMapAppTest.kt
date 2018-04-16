package io.cordite.services

import com.google.common.io.Files
import io.vertx.core.Vertx
import io.vertx.ext.unit.TestContext
import io.vertx.ext.unit.junit.VertxUnitRunner
import net.corda.core.utilities.loggerFor
import net.corda.node.services.network.NetworkMapClient
import net.corda.nodeapi.internal.DEV_ROOT_CA
import org.apache.mina.util.AvailablePortFinder
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

@RunWith(VertxUnitRunner::class)
class NetworkMapAppTest {
  private val vertx = Vertx.vertx()

  companion object {
    val log = loggerFor<NetworkMapAppTest>()
  }

  private val certDir = Files.createTempDir()
  val port = AvailablePortFinder.getNextAvailable()

  @Before
  fun before(context: TestContext) {
    log.info("temp dir is $certDir")
    certDir.deleteOnExit()
    vertx.deployVerticle(TestingNetworkMapApp(port = port, notaryDir = certDir), context.asyncAssertSuccess())
  }

  @After
  fun after(context: TestContext) {
    vertx.close(context.asyncAssertSuccess())
  }

  @Test(expected = NullPointerException::class)
  fun `that we cannot locate a stale network parameter hash`(context: TestContext) {
    val nm = NetworkMapClient(URL("http://localhost:$port"), DEV_ROOT_CA.certificate)
    val networkMap = nm.getNetworkMap()
    copyResource("certificates/nodekeystore.jks", certDir)
    Thread.sleep(2_000)
    nm.getNetworkParameters(networkMap.payload.networkParameterHash)
  }

  @Test
  fun `that we can locate an update network parameter hash`(context: TestContext) {
    val nm = NetworkMapClient(URL("http://localhost:$port"), DEV_ROOT_CA.certificate)
    val networkMap = nm.getNetworkMap()
    val np = nm.getNetworkParameters(networkMap.payload.networkParameterHash)
    assertEquals(np.raw.hash, networkMap.payload.networkParameterHash)
    copyResource("certificates/nodekeystore.jks", certDir)
    Thread.sleep(2_000)
    val networkMap2 = nm.getNetworkMap()
    assertNotEquals(networkMap.payload.networkParameterHash, networkMap2.payload.networkParameterHash)
    val np2 = nm.getNetworkParameters(networkMap2.payload.networkParameterHash)
    assertEquals(np2.raw.hash, networkMap2.payload.networkParameterHash)
  }

  private fun copyResource(resourceName: String, parentDir: File) {
    val dstFile = File(parentDir, resourceName)
    dstFile.parentFile.mkdirs()
    NetworkMapAppTest::class.java.getResourceAsStream("/$resourceName").use { input->
      FileOutputStream(dstFile).use { output ->
        input.copyTo(output)
      }
    }
  }


}