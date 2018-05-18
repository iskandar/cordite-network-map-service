package io.cordite.services

import com.google.common.io.Files
import io.netty.handler.codec.http.HttpHeaderValues
import io.vertx.core.Future
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.core.http.HttpClientOptions
import io.vertx.core.http.HttpHeaders
import io.vertx.core.http.HttpMethod
import io.vertx.ext.unit.TestContext
import io.vertx.ext.unit.junit.VertxUnitRunner
import net.corda.core.node.services.AttachmentId
import net.corda.core.utilities.loggerFor
import net.corda.node.services.network.NetworkMapClient
import net.corda.nodeapi.internal.DEV_ROOT_CA
import org.apache.mina.util.AvailablePortFinder
import org.junit.After
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

@Ignore // TODO: fix these tests
@RunWith(VertxUnitRunner::class)
class NetworkMapAppTest {
  companion object {
    val log = loggerFor<NetworkMapAppTest>()
  }

  private val certDir = Files.createTempDir()
  private val dbDir = Files.createTempDir()
  val port = AvailablePortFinder.getNextAvailable()
  private val vertx = Vertx.vertx()
  private val clientVertx = Vertx.vertx()
  private val httpClient = clientVertx.createHttpClient(HttpClientOptions().setDefaultHost("localhost").setDefaultPort(port))

  @Before
  fun before(context: TestContext) {
    log.info("temp dir is $certDir")
    certDir.deleteOnExit()
    vertx.deployVerticle(TestingNetworkMapApp(port = port, notaryDir = certDir, dbDir = dbDir), context.asyncAssertSuccess())
  }

  @After
  fun after(context: TestContext) {
    vertx.close(context.asyncAssertSuccess())
    clientVertx.close(context.asyncAssertSuccess())
  }

  @Test
  fun `that we can update the whitelist`(context: TestContext) {
    val nm = NetworkMapClient(URL("http://localhost:$port"), DEV_ROOT_CA.certificate)
    val wl0 = nm.getNetworkParameters(nm.getNetworkMap().payload.networkParameterHash).verified().whitelistedContractImplementations
    context.assertEquals(0, wl0.size)
    val e1 = generateWhitelistEntry()
    val e2 = generateWhitelistEntry()
    api(HttpMethod.PUT, "/whitelist", e1)
        .map {
          Thread.sleep(100)
          val wl1 = nm.getNetworkParameters(nm.getNetworkMap().payload.networkParameterHash).verified().whitelistedContractImplementations
          context.assertEquals(1, wl1.size)
          context.assertEquals(1, wl1.entries.first().value.size)
          val wle1 = wl1.entries.first().let { "${it.key}:${it.value[0]}" }
          context.assertEquals(e1, wle1)
        }
        .compose {
          api(HttpMethod.PUT, "/whitelist", e2)
        }
        .map {
          Thread.sleep(100)
          val wl2 = nm.getNetworkParameters(nm.getNetworkMap().payload.networkParameterHash).verified().whitelistedContractImplementations
          context.assertEquals(1, wl2.size)
          val asList = wl2.flatMap { it.value.map { sh -> it.key to sh } }.map { "${it.first}:${it.second}" }

          context.assertTrue(asList.contains(e1) && asList.contains(e2))
        }
        .compose {
          api(HttpMethod.POST, "/whitelist", e1)
        }
        .map {
          Thread.sleep(100)
          val wl1 = nm.getNetworkParameters(nm.getNetworkMap().payload.networkParameterHash).verified().whitelistedContractImplementations
          context.assertEquals(1, wl1.size)
          context.assertEquals(1, wl1.entries.first().value.size)
          val wle1 = wl1.entries.first().let { "${it.key}:${it.value[0]}" }
          context.assertEquals(e1, wle1)
        }
        .setHandler(context.asyncAssertSuccess())
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
    NetworkMapAppTest::class.java.getResourceAsStream("/$resourceName").use { input ->
      FileOutputStream(dstFile).use { output ->
        input.copyTo(output)
      }
    }
  }

  private fun api(method: HttpMethod, path: String, payload: String): Future<Buffer> {
    val result = Future.future<Buffer>()
    httpClient
        .request(method, port, "localhost", "${NetworkMapApp.WEB_API}$path")
        .handler { response ->
          if (response.statusCode() / 100 != 2) {
            result.fail(response.statusMessage())
          } else {
            response.bodyHandler { buffer ->
              result.complete(buffer)
            }
          }
        }
        .exceptionHandler {
          result.fail(it)
        }
        .putHeader(HttpHeaders.CONTENT_LENGTH, payload.length.toString())
        .putHeader(HttpHeaders.CONTENT_TYPE, HttpHeaderValues.TEXT_PLAIN)
        .end(payload)
    return result
  }

  private fun generateWhitelistEntry(): String {
    return "${NetworkMapAppTest::class.java.name}:${AttachmentId.randomSHA256()}"
  }
}