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

import io.cordite.networkmap.serialisation.SerializationEnvironment
import io.cordite.networkmap.storage.NetworkParameterInputsStorage
import io.cordite.networkmap.utils.CryptoUtils
import io.cordite.networkmap.utils.getFreePort
import io.cordite.networkmap.utils.readFiles
import io.cordite.networkmap.utils.setupDefaultInputFiles
import io.vertx.core.Vertx
import io.vertx.core.http.HttpClientOptions
import io.vertx.core.http.HttpClientResponse
import io.vertx.core.http.HttpHeaders
import io.vertx.ext.unit.TestContext
import io.vertx.ext.unit.junit.VertxUnitRunner
import net.corda.core.internal.signWithCert
import net.corda.core.utilities.millis
import net.corda.core.utilities.seconds
import net.corda.node.services.network.NetworkMapClient
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.internal.TestNodeInfoBuilder
import org.apache.commons.io.FileUtils
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.File
import java.net.URL
import java.nio.file.Files
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.time.Duration
import java.time.Instant
import java.util.*
import kotlin.test.*

@RunWith(VertxUnitRunner::class)
class NetworkMapServiceTest {
  companion object {
    init {
      SerializationEnvironment.init()
    }

    val CACHE_TIMEOUT = 1.millis
    val NETWORK_PARAM_UPDATE_DELAY = 5.seconds
    val NETWORK_MAP_QUEUE_DELAY = 1.seconds
    val TEST_CERT = "-----BEGIN CERTIFICATE-----\n" +
      "MIIDmDCCAoACCQC3XUbDbyNK3zANBgkqhkiG9w0BAQsFADCBjTELMAkGA1UEBhMC\n" +
      "VUsxDzANBgNVBAgMBkxvbmRvbjEPMA0GA1UEBwwGTG9uZG9uMREwDwYDVQQKDAhC\n" +
      "bHVlYmFuazEPMA0GA1UECwwGRW1UZWNoMRQwEgYDVQQDDAtibHVlYmFuay5pbzEi\n" +
      "MCAGCSqGSIb3DQEJARYTc3VwcG9ydEBibHVlYmFuay5pbzAeFw0xODA4MDExNDI4\n" +
      "NTJaFw0xOTA4MDExNDI4NTJaMIGNMQswCQYDVQQGEwJVSzEPMA0GA1UECAwGTG9u\n" +
      "ZG9uMQ8wDQYDVQQHDAZMb25kb24xETAPBgNVBAoMCEJsdWViYW5rMQ8wDQYDVQQL\n" +
      "DAZFbVRlY2gxFDASBgNVBAMMC2JsdWViYW5rLmlvMSIwIAYJKoZIhvcNAQkBFhNz\n" +
      "dXBwb3J0QGJsdWViYW5rLmlvMIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKC\n" +
      "AQEAts30+8GMfpts/zVbcOx669NLQFygl7XohIdxPGSGeqFbda5VgWz4I5jK3zqc\n" +
      "poRpm0JKvfY2RDqfjV7E9DOO6NYsIfBb9ANMyyvV90V1szVEyxAlWr8Sl2DyiVIN\n" +
      "VfouwCZRs6uQ7QH2Xl9Cl8U3/qP3XU1ZyTDqdjMlWvEwXsDqJFfAsft3SKqJblUa\n" +
      "pKYwEy7fN4V6fzOIROpwnlhhdo8it2pojEjAwOhUYjR321gCcfWVOFspzaP6xPrY\n" +
      "iGwJunfJgzGYCDQYPDLvxmC10D20XQWfJyxD5mnvtYqMTd3pLtslqb3ZKrkoWdMF\n" +
      "xEpVrtJt51PmKQ1tjHFF/1Y3BQIDAQABMA0GCSqGSIb3DQEBCwUAA4IBAQAOjFc1\n" +
      "r4g6aq1r9muhYauRtJtZUfYY2M/2nbdguw/AigCDmmHqv49k25+9BqNfpjHejL+m\n" +
      "5PTa+D4mwT6/jnyT1x92VY/fhC8Enu5PXLpyfCJqD7z+XNS3einW9XF5h/8AJK2H\n" +
      "asttWX2o9glxGgUbZUCIKkYbeikMdiX5tGswVL9AMRtCgCj8oi9xxV95fitMXlWQ\n" +
      "IJ4z2zY+6getCfb/lGwhLObUG4miuoVEvBFV2SvT0pCFVGpCEJdwLMB1WxAfu4I6\n" +
      "VJ7p1J4c7rAKzd82LT3D+wmJQ6yzJNr8qA3i9f+odiwfrs2j42Sf2N5TnhHWtNvM\n" +
      "0al+ndBR2JtMeaCq\n" +
      "-----END CERTIFICATE-----\n"
  }

  val TEST_PRIV_KEY = "-----BEGIN PRIVATE KEY-----\n" +
    "MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQC2zfT7wYx+m2z/\n" +
    "NVtw7Hrr00tAXKCXteiEh3E8ZIZ6oVt1rlWBbPgjmMrfOpymhGmbQkq99jZEOp+N\n" +
    "XsT0M47o1iwh8Fv0A0zLK9X3RXWzNUTLECVavxKXYPKJUg1V+i7AJlGzq5DtAfZe\n" +
    "X0KXxTf+o/ddTVnJMOp2MyVa8TBewOokV8Cx+3dIqoluVRqkpjATLt83hXp/M4hE\n" +
    "6nCeWGF2jyK3amiMSMDA6FRiNHfbWAJx9ZU4WynNo/rE+tiIbAm6d8mDMZgINBg8\n" +
    "Mu/GYLXQPbRdBZ8nLEPmae+1ioxN3eku2yWpvdkquShZ0wXESlWu0m3nU+YpDW2M\n" +
    "cUX/VjcFAgMBAAECggEBAKQdsWRYNl7wAOH6MDboR87yaivFPPQW/0IEKvgSM91i\n" +
    "ga7cLa29e+TRZskUYNDqLbmSwXFb2wpUKywLOf0XUKTeqs5pcNRYJhh9KWIOfQW/\n" +
    "vBwmSbL3uaQoCHaaMJjQvCoL/Ou2Cq2NRnchRLLm/0dgQ1MDf6ktfkFR16aWxFs7\n" +
    "iEaIhFG/aBznf/OGdyz906YrcYKDi3AJXjCxXvoGi9lVErNrimBm+Zn4YpRxFvB3\n" +
    "kFvk9rf9KEkGB0OAieEieI8Gr6Xbq6dzDqfjcCiv3XzlZ0txJgaqwRFmhkU1UO+J\n" +
    "VyRtmb30SrcSFgrYDD8ifqRaFKTjk9ZmUSXybi/sXV0CgYEA2nvbOfItDULuJ+iq\n" +
    "rymhHHk5IQ4B6/BExqe0WJJWdc1kezjHIZ35/9N9xFnqcD8I/q1iu/ZpHIJI00wC\n" +
    "YEMSZE1Xk+X4002dBKby8QCB6JUY7wXPMFWQxMCR0cskPfNGdGRfEwhrNbKy8E84\n" +
    "hq3SIF+UBfSvJPOLvvhGEY7bcnMCgYEA1jG0dqh3/aM2otW+fgkcTKf4QlQUW8VY\n" +
    "3hqtwtJzPRciXHJIxLSiMD5kkaN+ydCxSqXoBo0/tjimVXyH0zQ1xgR5cy7OEQuB\n" +
    "y5dvC/gPHU238nPadZb5Z9ErzRew50VB6H5tBrkxOE1TDGaySquhvVKVCxJJlt6X\n" +
    "GpmZxIIduqcCgYA2kRh/sGxwE3dHoGSAuvTyF5SdHNJ+CtQiiWARfvr5EQM3g0a4\n" +
    "rqvxqPCQSaSzxAqLEOLH7xLxe9iUbTdqs1W0l1x4I8exfoDo2Il0h5vqatJ/YAQP\n" +
    "Hk+51B6XNxUmI8xE5YyZRFECaE8olaCYgnEohLaDhkj4AZu1ZmyZlgRY4QKBgA8h\n" +
    "AaMj8R28Kn7D5CmY0SPk9VcSA0IcJVPCxKUvIi6ddLLc66DhNVd9ALN8vdbZY7xn\n" +
    "DYVw8qAXTkBZhGp5lJbA+CcXljyD+I39yz0oL0EdnTGF11dY65LWpmZdFwSu0qHu\n" +
    "VBsWd5CHfacxlcRKbSknLRnUF9iNLlUVplPH8PufAoGAEK3IvUfA7lOveOjto6kP\n" +
    "D0nW7GicgcVVqmJS3H26zLBGCUIVE18grdJoUVWK3UIONbWapKBWYKIGJjP3pHgL\n" +
    "e7v0dZkXHmz+aQ7Df1H+0Lz2DxPVJ4mmHKt5kvnSsjciGK0vtLsE3kgDrxI7ve9i\n" +
    "sd33h8YmCt8965Z4NFAfUsc=\n" +
    "-----END PRIVATE KEY-----"

  private var vertx = Vertx.vertx()
  private val dbDirectory = createTempDir()
  private val port = getFreePort()

  private lateinit var service: NetworkMapService

  @Before
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

  @Test
  fun `that we can retrieve network map and parameters and they are correct`(context: TestContext) {
    val nmc = createNetworkMapClient()
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
    val nmc = createNetworkMapClient()
    val hostname = nmc.myPublicHostname()
    context.assertEquals("localhost", hostname)
  }

  @Test
  fun `that we can add a new node`(context: TestContext) {
    val nmc = createNetworkMapClient()
    val tnib = TestNodeInfoBuilder()
    tnib.addIdentity(ALICE_NAME)
    val sni = tnib.buildWithSigned()
    nmc.publish(sni.signed)
    Thread.sleep(NETWORK_MAP_QUEUE_DELAY.toMillis() * 2)
    val nm = nmc.getNetworkMap().payload
    val nhs = nm.nodeInfoHashes
    context.assertEquals(1, nhs.size)
    assertEquals(sni.signed.raw.hash, nhs[0])
  }

  @Test(expected = NullPointerException::class)
  fun `that we cannot register the same node name with a different key`(context: TestContext) {
    val nmc = createNetworkMapClient()

    val sni1 = TestNodeInfoBuilder().let {
      it.addIdentity(ALICE_NAME)
      it.buildWithSigned()
    }
    nmc.publish(sni1.signed)
    Thread.sleep(NETWORK_MAP_QUEUE_DELAY.toMillis() * 2)
    val nm = nmc.getNetworkMap().payload
    val nhs = nm.nodeInfoHashes
    context.assertEquals(1, nhs.size)
    assertEquals(sni1.signed.raw.hash, nhs[0])

    val sni2 = TestNodeInfoBuilder().let {
      it.addIdentity(ALICE_NAME)
      it.buildWithSigned()
    }

    val pk1 = sni1.nodeInfo.legalIdentities.first().owningKey
    val pk2 = sni2.nodeInfo.legalIdentities.first().owningKey
    assertNotEquals(pk1, pk2)
    nmc.publish(sni2.signed) // <-- will throw a meaningless NPE see https://github.com/corda/corda/issues/3442
  }


  @Test
  fun `that we can modify the network parameters`(context: TestContext) {
    val nmc = createNetworkMapClient()
    deleteValidatingNotaries(dbDirectory)
    Thread.sleep(NetworkParameterInputsStorage.DEFAULT_WATCH_DELAY)
    Thread.sleep(NETWORK_MAP_QUEUE_DELAY.toMillis() * 2)
    val nm = nmc.getNetworkMap().payload
    assertNotNull(nm.parametersUpdate, "expecting parameter update plan")
    val deadLine = nm.parametersUpdate!!.updateDeadline
    val delay = Duration.between(Instant.now(), deadLine)
    assert(delay > Duration.ZERO && delay <= NETWORK_PARAM_UPDATE_DELAY)
    Thread.sleep(delay.toMillis() * 2)
    val nm2 = nmc.getNetworkMap().payload
    assertNull(nm2.parametersUpdate)
    val nmp = nmc.getNetworkParameters(nm2.networkParameterHash).verified()
    assertEquals(1, nmp.notaries.size)
    assertTrue(nmp.notaries.all { !it.validating })
  }

  @Test
  fun `that we can submit a certificate and signature to certman`(context: TestContext) {
    // prepare the payload
    val privKey = CryptoUtils.decodePEMPrivateKey(TEST_PRIV_KEY)
    val cert = CertificateFactory.getInstance("X.509").generateCertificate(ByteArrayInputStream(TEST_CERT.toByteArray())) as X509Certificate
    val sign = Base64.getEncoder().encodeToString(TEST_CERT.signWithCert(privKey, cert).raw.bytes)
    val payload = TEST_CERT + sign

    val client = vertx.createHttpClient(HttpClientOptions().setDefaultHost("localhost").setDefaultPort(port))
    val async = context.async()

    client.post("${NetworkMapService.CERTMAN_REST_ROOT}/generate")
      .putHeader(HttpHeaders.CONTENT_LENGTH, payload.length.toString())
      .exceptionHandler {
        context.fail(it)
      }
      .handler { it ->
        if (it.isOkay()) {

        } else {
          context.fail("failed with ${it.statusMessage()}")
        }
        it.bodyHandler { body ->
          async.complete()
        }
      }
      .end(payload)
  }

  private fun getNetworkParties(nmc: NetworkMapClient) =
    nmc.getNetworkMap().payload.nodeInfoHashes.map { nmc.getNodeInfo(it) }


  private fun createNetworkMapClient(): NetworkMapClient {
    return NetworkMapClient(URL("http://localhost:$port"), service.certificateManager.networkMapCertAndKeyPair.certificate)
  }

  private fun createTempDir(): File {
    return Files.createTempDirectory("nms-test-").toFile()
      .apply {
        mkdirs()
        deleteOnExit()
      }
  }

  private fun deleteValidatingNotaries(directory: File) {
    val inputs = File(directory, NetworkParameterInputsStorage.DEFAULT_DIR_NAME)
    FileUtils.cleanDirectory(File(inputs, NetworkParameterInputsStorage.DEFAULT_DIR_VALIDATING_NOTARIES))
  }
}

private fun HttpClientResponse.isOkay(): Boolean {
  return ((this.statusCode() / 100) == 2)
}
