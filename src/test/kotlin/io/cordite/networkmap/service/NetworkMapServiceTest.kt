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

import com.mongodb.reactivestreams.client.MongoClients
import io.cordite.networkmap.storage.EmbeddedMongo
import io.cordite.networkmap.storage.NetworkParameterInputsStorage
import io.cordite.networkmap.storage.mongo.MongoStorage
import io.cordite.networkmap.utils.*
import io.vertx.core.Vertx
import io.vertx.core.http.HttpClientOptions
import io.vertx.core.http.HttpClientResponse
import io.vertx.core.http.HttpHeaders
import io.vertx.ext.unit.TestContext
import io.vertx.ext.unit.junit.VertxUnitRunner
import net.corda.core.crypto.sign
import net.corda.core.identity.PartyAndCertificate
import net.corda.core.internal.signWithCert
import net.corda.core.node.NodeInfo
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.millis
import net.corda.core.utilities.seconds
import net.corda.node.services.network.NetworkMapClient
import net.corda.nodeapi.internal.NodeInfoAndSigned
import net.corda.nodeapi.internal.crypto.CertificateType
import net.corda.nodeapi.internal.crypto.X509Utilities
import net.corda.testing.core.ALICE_NAME
import org.apache.commons.io.FileUtils
import org.junit.*
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.net.URL
import java.nio.file.Files
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.time.Duration
import java.time.Instant
import java.util.*
import java.util.zip.ZipInputStream
import kotlin.test.*

@RunWith(VertxUnitRunner::class)
class NetworkMapServiceTest {
  companion object {
    init {
      LogInitialiser.init()
      SerializationTestEnvironment.init()
    }

    @JvmField
    @ClassRule
    val mdcClassRule = JunitMDCRule()

    val CACHE_TIMEOUT = 1.millis
    val NETWORK_PARAM_UPDATE_DELAY = 5.seconds
    val NETWORK_MAP_QUEUE_DELAY = 1.seconds
    val WEB_ROOT = "/root"
    val TEST_CERT = "-----BEGIN CERTIFICATE-----\n" +
      "MIIDoDCCAogCCQDHFxXNfHiwizANBgkqhkiG9w0BAQsFADCBkTELMAkGA1UEBhMC\n" +
      "R0IxDzANBgNVBAgMBkxvbmRvbjEPMA0GA1UEBwwGTG9uZG9uMREwDwYDVQQKDAhC\n" +
      "bHVlYmFuazEPMA0GA1UECwwGRW1UZWNoMRgwFgYDVQQDDA9FbVRlY2ggQmx1ZWJh\n" +
      "bmsxIjAgBgkqhkiG9w0BCQEWE3N1cHBvcnRAYmx1ZWJhbmsuaW8wHhcNMTgwODEw\n" +
      "MTQ0ODQwWhcNMTkwODEwMTQ0ODQwWjCBkTELMAkGA1UEBhMCR0IxDzANBgNVBAgM\n" +
      "BkxvbmRvbjEPMA0GA1UEBwwGTG9uZG9uMREwDwYDVQQKDAhCbHVlYmFuazEPMA0G\n" +
      "A1UECwwGRW1UZWNoMRgwFgYDVQQDDA9FbVRlY2ggQmx1ZWJhbmsxIjAgBgkqhkiG\n" +
      "9w0BCQEWE3N1cHBvcnRAYmx1ZWJhbmsuaW8wggEiMA0GCSqGSIb3DQEBAQUAA4IB\n" +
      "DwAwggEKAoIBAQDh5A78CfW5tAvrQpcXFWzXvlxZQ8BitkvyLEc8vW1SM8FqVhg9\n" +
      "2ufRbHiSLAf5CQLNYExcr6sPbAQ9QbqYyIe10rF0mwh4mL38yZ3acTI0duTG45CL\n" +
      "AC8c7Rh57OavsT4I3p5ZKGRuo4g4l46SbqmgZ/VbYbOTJCYmSFfHVZycQIDwe61H\n" +
      "fCkBY0XYTS4t75RADqj/2d7Z2zfrIbVhWpRXOHuq/bD/Ean+/0znTN4Gz6zaFXss\n" +
      "Tw2zmzM65tRCh1I0o/60Gk17/mvk2cQo730k/AeVsBGDXN1P6Aidfc+iWobqYP4d\n" +
      "wztZYjOxrULvyfoRy0vy3Fz3jjN8dWRFFzNBAgMBAAEwDQYJKoZIhvcNAQELBQAD\n" +
      "ggEBAHZbp0NhtiJmCqKKRWyVz2KLVv71ref2e23v3cThV3/2d+CneQK2i71AqEw2\n" +
      "Xw6vWwBUtRtoG4hSNfDlyp5zJEhb6f93o97rNV/UUM6LI7Yw0Vc5DOabmmErTyeD\n" +
      "KLRT+4E2vb2sDemxxLEZqPSOzRUQ5YDBOcm06I2pvz6ynGbWDR+FDnfpx+GqTLX/\n" +
      "bruKnRHykXJG85jiQpL9tHlImVuI8H5DY9g9qJy4Bzisz4QphYsLQ5TA2zGhe4TX\n" +
      "1aSu713IZhOLMdIPy54KbIl8eYAA5BV8s+ybFxqxHQe557tthsnU2GH/BR/77L6/\n" +
      "Tl3Q1mLJ/rx9N6Ae6M5FcBklyu0=\n" +
      "-----END CERTIFICATE-----\n"
  }

  val TEST_PRIV_KEY = "-----BEGIN PRIVATE KEY-----\n" +
    "MIIEvgIBADANBgkqhkiG9w0BAQEFAASCBKgwggSkAgEAAoIBAQDh5A78CfW5tAvr\n" +
    "QpcXFWzXvlxZQ8BitkvyLEc8vW1SM8FqVhg92ufRbHiSLAf5CQLNYExcr6sPbAQ9\n" +
    "QbqYyIe10rF0mwh4mL38yZ3acTI0duTG45CLAC8c7Rh57OavsT4I3p5ZKGRuo4g4\n" +
    "l46SbqmgZ/VbYbOTJCYmSFfHVZycQIDwe61HfCkBY0XYTS4t75RADqj/2d7Z2zfr\n" +
    "IbVhWpRXOHuq/bD/Ean+/0znTN4Gz6zaFXssTw2zmzM65tRCh1I0o/60Gk17/mvk\n" +
    "2cQo730k/AeVsBGDXN1P6Aidfc+iWobqYP4dwztZYjOxrULvyfoRy0vy3Fz3jjN8\n" +
    "dWRFFzNBAgMBAAECggEBAJk4HOXeR5uXwYHpIBzbPoG0MGWn7AXIywjP+d38Svu3\n" +
    "+ViMX1MNebJ2le3oCdxkvU7bI9C2oxwJ03JNdCkP0+WDrTR/uCY8zJl7lCPIJhqq\n" +
    "DpHNZ4yxKkO/mpuREgRX/9D6V4P4Pu9A4zQnsOAoScxw118NjUWf/nR3G3ss2dat\n" +
    "L7NJRnDbc7sMZ6ae42+PFjS0/klfs9UVn0mcB8Ny0FHJNW2IAncZG9C4FaUFXbRZ\n" +
    "6y9Ymwyv1uJglUT4yjok3EpyMlPrfDgN0Acoq2GYc4QLSWuqzVVQim1ErTAXF5GJ\n" +
    "XRG1HkOydHWOSkGiYtQ7XUHPugs8NIUSQ99wnuSPPsECgYEA9qjd6I6I7ydIkTeC\n" +
    "LUwFiWBeVoVic7hKIK9V5wyDZPfoNJzN3IEF0F3eD/uc14MCKKrh9GyweN9FAeHE\n" +
    "nty1vrHt4qHntZDw9lcvj4W1Gmidxc9VT1oJJ+tprIKVaGkVAkpjRJM5ra1aQr44\n" +
    "MIrvhW5nustUf24gr+mJR+4T0TkCgYEA6nHbntnqJA07IT8UKgEPodI/EV87lRju\n" +
    "ewW2aNhwfmvRoHU74ihYTMYP6zxl/Z/v6T7iSgZrfyhQ36NI5sQmF1jdTNqeoSKS\n" +
    "6NXElvmT7hel02T4a2ubVtI2HhyrHnrDFlMTA/tNxPr2vZCaLHjwci2dKxQKjCxy\n" +
    "Kt8Ur5l22kkCgYEAqFV6jFmqDjyrA5/0UWGObcC84SNKm1rsC/5dC7+4dFHTwQQ6\n" +
    "YgATra5B/HplAZdBA+wLJLqAfR0yhSRFAX3y8t+PT5na/kiaiiPaK4K+o/U9p1/m\n" +
    "Aq+ZjArXJYpA2O7ODbAiqwwm0uZ5sQ8MXeSTrmY4mHxngEfyOtuQeux5zdECgYBC\n" +
    "PLDkDIVOcj6Ggh/cTjhwa8pNyi43TbfzIgYLUTtXPHcZcoXcu7FW346X05StN4a8\n" +
    "y3t7lpzAbE+NH8D1Ee4BIqZDlHDE7dO73MmSLilRV3UOaLSXBOv6d6G6mDbwgZak\n" +
    "tAvnUBUE1jLoE/a7IeAtIh4Jkbv5JoWK/0QE6MLfoQKBgGVdROa8ojkec2ILJv6w\n" +
    "WDJp8nJnwH4y3aEvoprVfIG8wK53J+4+OGUJcN5i+/wWHlQF4MGDGh/l4+R1XhrP\n" +
    "ZvnS0xzmBX/Ng14Q8F0a7FAaV2eY8oGdJ4/mfKN02fEYIOZXjYbUjyg0S9ovtd4/\n" +
    "L0wpAim0Z/ZxMyUIQzwODgdW\n" +
    "-----END PRIVATE KEY-----\n"

  private var vertx = Vertx.vertx()
  private val dbDirectory = createTempDir()
  private val port = getFreePort()

  private lateinit var service: NetworkMapService

  private lateinit var mongodb: EmbeddedMongo

  @JvmField
  @Rule
  val mdcRule = JunitMDCRule()

  @Before
  fun before(context: TestContext) {
    mongodb = MongoStorage.startEmbeddedDatabase(dbDirectory, isDaemon = false)
    vertx = Vertx.vertx()

    val fRead = vertx.fileSystem().readFiles("/Users/fuzz/tmp")
    val async = context.async()
    fRead.setHandler { async.complete() }
    async.await()


    val path = dbDirectory.absolutePath
    println("db path: $path")
    println("port   : $port")

    setupDefaultInputFiles(dbDirectory)

    val mongoClient = MongoClients.create(mongodb.connectionString)
    this.service = NetworkMapService(dbDirectory = dbDirectory,
      user = InMemoryUser.createUser("", "sa", ""),
      port = port,
      cacheTimeout = CACHE_TIMEOUT,
      networkParamUpdateDelay = NETWORK_PARAM_UPDATE_DELAY,
      networkMapQueuedUpdateDelay = NETWORK_MAP_QUEUE_DELAY,
      tls = false,
      vertx = vertx,
      webRoot = WEB_ROOT,
      certificateManagerConfig = CertificateManagerConfig(
          root = CertificateManager.createSelfSignedCertificateAndKeyPair(CertificateManagerConfig.DEFAULT_ROOT_NAME),
          doorManEnabled = false,
          certManEnabled = true,
          certManPKIVerficationEnabled = false,
          certManRootCAsTrustStoreFile = null,
          certManRootCAsTrustStorePassword = null,
          certManStrictEVCerts = false),
      mongoClient = mongoClient,
      mongoDatabase = MongoStorage.DEFAULT_DATABASE
    )

    service.startup().setHandler(context.asyncAssertSuccess())
  }

  @After
  fun after(context: TestContext) {
    service.shutdown()
    val async = context.async()
    vertx.close {
      mongodb.close()
      context.assertTrue(it.succeeded())
      async.complete()
    }
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
    val sni = createAliceSignedNodeInfo()
    nmc.publish(sni.signed)
    Thread.sleep(NETWORK_MAP_QUEUE_DELAY.toMillis() * 2)
    val nm = nmc.getNetworkMap().payload
    val nhs = nm.nodeInfoHashes
    context.assertEquals(1, nhs.size)
    assertEquals(sni.signed.raw.hash, nhs[0])
  }

  @Test
  fun `that we cannot register the same node name with a different key`(context: TestContext) {
    val nmc = createNetworkMapClient()

    val sni1 = createAliceSignedNodeInfo()
    nmc.publish(sni1.signed)
    Thread.sleep(NETWORK_MAP_QUEUE_DELAY.toMillis() * 2)
    val nm = nmc.getNetworkMap().payload
    val nhs = nm.nodeInfoHashes
    context.assertEquals(1, nhs.size)
    assertEquals(sni1.signed.raw.hash, nhs[0])

    val sni2 = createAliceSignedNodeInfo()

    val pk1 = sni1.nodeInfo.legalIdentities.first().owningKey
    val pk2 = sni2.nodeInfo.legalIdentities.first().owningKey
    assertNotEquals(pk1, pk2)
    try {
      nmc.publish(sni2.signed)
      throw RuntimeException("should have throw IOException complaining that the node has been registered before")
    } catch(err: Throwable) {
      if (err !is IOException) {
        throw err
      }
      assertEquals("Response Code 500: node failed to registered because the following names have already been registered with different public keys O=Alice Corp, L=Madrid, C=ES", err.message)
    }
  }


  @Test
  fun `that we can modify the network parameters`() {
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

    client.post("${NetworkMapServiceTest.WEB_ROOT}${NetworkMapService.CERTMAN_REST_ROOT}/generate")
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
          ZipInputStream(ByteArrayInputStream(body.bytes)).use {
            var entry = it.nextEntry
            while (entry != null) {

              entry = it.nextEntry
            }
            async.complete()
          }
        }
      }
      .end(payload)
  }

  private fun getNetworkParties(nmc: NetworkMapClient) =
    nmc.getNetworkMap().payload.nodeInfoHashes.map { nmc.getNodeInfo(it) }


  private fun createNetworkMapClient(): NetworkMapClient {
    return NetworkMapClient(URL("http://localhost:$port$WEB_ROOT"), service.certificateManager.rootCertificateAndKeyPair.certificate)
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

  private fun createAliceSignedNodeInfo(): NodeInfoAndSigned {
    val cm = service.certificateManager
    // create the certificate chain from the doorman to node CA to legal identity
    val nodeCA = cm.createCertificateAndKeyPair(cm.doormanCertAndKeyPair, ALICE_NAME, CertificateType.NODE_CA)
    val legalIdentity = cm.createCertificateAndKeyPair(nodeCA, ALICE_NAME, CertificateType.LEGAL_IDENTITY)
    val certPath = X509Utilities.buildCertPath(
      legalIdentity.certificate,
      nodeCA.certificate,
      cm.doormanCertAndKeyPair.certificate,
      cm.rootCertificateAndKeyPair.certificate
    )
    val alicePartyAndCertificate = PartyAndCertificate(certPath)
    val ni = NodeInfo(listOf(NetworkHostAndPort("localhost", 10001)), listOf(alicePartyAndCertificate), 1, 1)
    return NodeInfoAndSigned(ni) { _, serialised ->
      legalIdentity.keyPair.private.sign(serialised.bytes)
    }
  }
}

private fun HttpClientResponse.isOkay(): Boolean {
  return ((this.statusCode() / 100) == 2)
}
