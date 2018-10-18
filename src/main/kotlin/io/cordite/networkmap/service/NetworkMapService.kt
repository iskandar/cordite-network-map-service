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

import io.bluebank.braid.corda.BraidConfig
import io.bluebank.braid.corda.rest.AuthSchema
import io.bluebank.braid.corda.rest.RestConfig
import io.bluebank.braid.core.http.HttpServerConfig
import io.cordite.networkmap.serialisation.SerializationEnvironment
import io.cordite.networkmap.serialisation.deserializeOnContext
import io.cordite.networkmap.serialisation.serializeOnContext
import io.cordite.networkmap.storage.*
import io.cordite.networkmap.utils.*
import io.netty.handler.codec.http.HttpHeaderValues
import io.netty.handler.codec.http.HttpResponseStatus
import io.swagger.annotations.ApiOperation
import io.swagger.models.Contact
import io.vertx.core.Future
import io.vertx.core.Handler
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.core.http.HttpServerOptions
import io.vertx.core.net.SelfSignedCertificate
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.handler.StaticHandler
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.SignedData
import net.corda.core.identity.CordaX500Name
import net.corda.core.node.NotaryInfo
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.loggerFor
import net.corda.nodeapi.internal.SignedNodeInfo
import org.bouncycastle.pkcs.PKCS10CertificationRequest
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.InetAddress
import java.security.PublicKey
import java.time.Duration
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.ws.rs.core.HttpHeaders
import javax.ws.rs.core.HttpHeaders.*
import javax.ws.rs.core.MediaType

class NetworkMapService(
  private val dbDirectory: File,
  user: InMemoryUser,
  private val port: Int,
  private val cacheTimeout: Duration,
  private val networkParamUpdateDelay: Duration,
  private val networkMapQueuedUpdateDelay: Duration,
  private val tls: Boolean = true,
  private val certPath: String = "",
  private val keyPath: String = "",
  private val vertx: Vertx = Vertx.vertx(),
  private val hostname: String = "localhost",
  webRoot: String = "/",
  private val certificateManagerConfig: CertificateManagerConfig = CertificateManagerConfig(
    root = CertificateManager.createSelfSignedCertificateAndKeyPair(CertificateManagerConfig.DEFAULT_ROOT_NAME),
    doorManEnabled = true,
    certManEnabled = true,
    certManPKIVerficationEnabled = false,
    certManRootCAsTrustStoreFile = null,
    certManRootCAsTrustStorePassword = null,
    certManStrictEVCerts = false)
) {
  companion object {
    internal const val NETWORK_MAP_ROOT = "/network-map"
    internal const val ADMIN_REST_ROOT = "/admin/api"
    internal const val CERTMAN_REST_ROOT = "/certman/api"
    private const val ADMIN_BRAID_ROOT = "/braid/api"
    private const val SWAGGER_ROOT = "/swagger"
    private val logger = loggerFor<NetworkMapService>()

    init {
      SerializationEnvironment.init()
    }
  }

  private val root = webRoot.dropLastWhile { it == '/' }

  private val networkMapRoot = root + NETWORK_MAP_ROOT
  private val adminRestRoot = root + ADMIN_REST_ROOT
  private val certmanRestRoot = root + CERTMAN_REST_ROOT
  private val adminBraidRoot: String = root + ADMIN_BRAID_ROOT
  private val swaggerRoot: String = root + SWAGGER_ROOT

  internal val certificateAndKeyPairStorage = CertificateAndKeyPairStorage(vertx, dbDirectory)
  private val authService = AuthService(user, File(certificateAndKeyPairStorage.resolveKey("jwt"), "jwt.jceks"))
  private val adminService = AdminServiceImpl()
  private val inputsStorage = NetworkParameterInputsStorage(dbDirectory, vertx)
  private val signedNetworkMapStorage = SignedNetworkMapStorage(vertx, dbDirectory)
  private val nodeInfoStorage = SignedNodeInfoStorage(vertx, dbDirectory)
  private val signedNetworkParametersStorage = SignedNetworkParametersStorage(vertx, dbDirectory)
  private lateinit var processor: NetworkMapServiceProcessor
  internal val certificateManager = CertificateManager(vertx, certificateAndKeyPairStorage, certificateManagerConfig)

  fun startup(): Future<Unit> {
    // N.B. Ordering is important here
    return setupStorage()
      .compose { startCertManager() }
      .compose { startProcessor() }
      .compose { startupBraid() }
  }

  fun shutdown(): Future<Unit> {
    processor.stop()
    return Future.succeededFuture()
  }

  private fun startupBraid(): Future<Unit> {
    try {
      val thisService = this
      val staticHandler = StaticHandler.create("website/public").setCachingEnabled(false)
      val result = Future.future<Unit>()
      val templateEngine = ResourceMvelTemplateEngine(
        cachingEnabled = true,
        properties =  mapOf("location" to root),
        rootPath = "website/public/"
      )
      BraidConfig()
        .withVertx(vertx)
        .withPort(port)
        .withAuthConstructor(authService::createAuthProvider)
        .withService("admin", adminService)
        .withRootPath(adminBraidRoot)
        .withHttpServerOptions(createHttpServerOptions())
        .withRestConfig(RestConfig("Cordite Network Map Service")
          .withAuthSchema(AuthSchema.Token)
          .withSwaggerPath(swaggerRoot)
          .withApiPath("/") // a little different because we need to mount the network map on '/network-map'
          .withContact(Contact().url("https://cordite.foundation").name("Cordite Foundation"))
          .withDescription("""|<h4><a href="/">Cordite Networkmap Service</a></h4>
            |<b>Please note:</b> The protected parts of this API require JWT authentication.
            |To activate, execute the <code>login</code> method.
            |Then copy the returned JWT token and insert it into the <i>Authorize</i> swagger dialog box as
            |<code>Bearer &lt;token&gt;</code>
          """.trimMargin().replace("\n", ""))
          .withPaths {
            group("network map") {
              unprotected {
                get("$networkMapRoot", thisService::getNetworkMap)
                post("$networkMapRoot/publish", thisService::postNodeInfo)
                post("$networkMapRoot/ack-parameters", thisService::postAckNetworkParameters)
                get("$networkMapRoot/node-info/:hash", thisService::getNodeInfo)
                get("$networkMapRoot/network-parameters/:hash", thisService::getNetworkParameter)
                get("$networkMapRoot/my-hostname", thisService::getMyHostname)
                get("$networkMapRoot/truststore", thisService::getNetworkTrustStore)
              }
            }
            if (certificateManagerConfig.doorManEnabled) {
              group("doorman") {
                unprotected {
                  post("${root}/certificate", thisService::postCSR)
                  get("${root}/certificate/:id", thisService::retrieveCSRResult)
                }
              }
            }
            if (certificateManagerConfig.certManEnabled) {
              group("certman") {
                unprotected {
                  post("$certmanRestRoot/generate", certificateManager::certmanGenerate)
                }
              }
            }
            group("admin") {
              unprotected {
                post("$adminRestRoot/login", authService::login)
                get("$adminRestRoot/whitelist", inputsStorage::serveWhitelist)
                get("$adminRestRoot/notaries", thisService::serveNotaries)
                get("$adminRestRoot/nodes", thisService::serveNodes)
                post("$adminRestRoot/notaries/validating/nodeInfo", inputsStorage::postValidatingNotaryNodeInfo)
                post("$adminRestRoot/notaries/nonValidating/nodeInfo", inputsStorage::postNonValidatingNotaryNodeInfo)
                router {
                  route("$root/").handler { context ->
                    if (context.request().path() == root) {
                      context.response().putHeader(HttpHeaders.LOCATION, "$root/").setStatusCode(HttpResponseStatus.MOVED_PERMANENTLY.code()).end()
                    } else {
                      context.next()
                    }
                  }
                  route("$root/*").handler { context ->
                    templateEngine.handler(context, root)
                  }
                  route("$root/*").handler {
                    staticHandler.handle(it)
                  }
                }
              }
              protected {
                put("$adminRestRoot/whitelist", inputsStorage::appendWhitelist)
                post("$adminRestRoot/whitelist", inputsStorage::replaceWhitelist)
                delete("$adminRestRoot/whitelist", inputsStorage::clearWhitelist)
                delete("$adminRestRoot/notaries", thisService::deleteNotary)
                delete("$adminRestRoot/nodes/:nodeKey", thisService::deleteNode)
              }
            }
          }
        ).bootstrapBraid(serviceHub = StubAppServiceHub(), fn = Handler {
          if (it.succeeded()) {
            result.complete()
          } else {
            result.fail(it.cause())
          }
        })
      return result
    } catch (err: Throwable) {
      return Future.failedFuture(err)
    }
  }

  @Suppress("MemberVisibilityCanBePrivate")
  @ApiOperation(value = "Retrieve the current signed network map object. The entire object is signed with the network map certificate which is also attached.",
    produces = MediaType.APPLICATION_OCTET_STREAM, response = Buffer::class)
  fun getNetworkMap(context: RoutingContext) {
    signedNetworkMapStorage.serve(NetworkMapServiceProcessor.NETWORK_MAP_KEY, context, cacheTimeout)
  }

  @Suppress("MemberVisibilityCanBePrivate")
  @ApiOperation(value = "For the node to upload its signed NodeInfo object to the network map",
    consumes = MediaType.APPLICATION_OCTET_STREAM
  )
  fun postNodeInfo(nodeInfo: Buffer): Future<Unit> {
    val signedNodeInfo = nodeInfo.bytes.deserializeOnContext<SignedNodeInfo>()
    if (!certificateManagerConfig.devMode) {
      // formally check that this node has been registered via our certs
      certificateManager.validateNodeInfoCertificates(signedNodeInfo.verified())
    }
    return processor.addNode(signedNodeInfo)
  }

  @Suppress("MemberVisibilityCanBePrivate")
  @ApiOperation(value = "Receives a certificate signing request",
    consumes = MediaType.APPLICATION_OCTET_STREAM
  )
  fun postCSR(pkcS10CertificationRequest: Buffer): Future<String> {
    val csr = PKCS10CertificationRequest(pkcS10CertificationRequest.bytes)
    return certificateManager.doormanProcessCSR(csr)
  }

  @Suppress("MemberVisibilityCanBePrivate")
  @ApiOperation(value = "Retrieve the certificate chain as a zipped binary block")
  fun retrieveCSRResult(routingContext: RoutingContext) {
    try {
      val id = routingContext.request().getParam("id")
      val certificates = certificateManager.doormanRetrieveCSRResponse(id)
      if (certificates.isEmpty()) {
        routingContext.response().setStatusCode(HttpURLConnection.HTTP_NO_CONTENT).end()
      } else {
        val bytes = ByteArrayOutputStream().use {
          ZipOutputStream(it).use { zipStream ->
            certificates.forEach { certificate ->
              zipStream.putNextEntry(ZipEntry(certificate.subjectX500Principal.name))
              zipStream.write(certificate.encoded)
              zipStream.closeEntry()
            }
          }
          it.toByteArray()
        }
        routingContext.response().apply {
          putHeader(CONTENT_TYPE, MediaType.APPLICATION_OCTET_STREAM)
          putHeader(CONTENT_LENGTH, bytes.size.toString())
          end(Buffer.buffer(bytes))
        }
      }
    } catch (err: Throwable) {
      routingContext.response().setStatusMessage(err.message).setStatusCode(HttpURLConnection.HTTP_UNAUTHORIZED).end()
    }
  }

  @Suppress("MemberVisibilityCanBePrivate")
  @ApiOperation(value = "retrieve all nodeinfos", responseContainer = "List", response = SimpleNodeInfo::class)
  fun serveNodes(context: RoutingContext) {
    context.setNoCache()
    nodeInfoStorage.getAll()
      .onSuccess {
        context.end(it.map {
          val node = it.value.verified()
          SimpleNodeInfo(it.key, node.addresses, node.legalIdentitiesAndCerts.map { NameAndKey(it.name, it.owningKey) }, node.platformVersion)
        })
      }
      .catch { context.end(it) }
  }

  @ApiOperation(value = "server set of notaries", response = SimpleNotaryInfo::class, responseContainer = "List")
  fun serveNotaries(routingContext: RoutingContext) {
    inputsStorage.readNotaries()
      .onSuccess {
        val simpleNotaryInfos = it.map { SimpleNotaryInfo(File(it.first).name, it.second) }
        routingContext.setNoCache().end(simpleNotaryInfos)
      }
      .catch {
        routingContext.setNoCache().end(it)
      }
  }

  @Suppress("MemberVisibilityCanBePrivate")
  @ApiOperation(value = "delete a node by its key")
  fun deleteNode(nodeKey: String): Future<Unit> {
    return nodeInfoStorage.delete(nodeKey)
  }

  @Suppress("MemberVisibilityCanBePrivate")
  @ApiOperation(value = "delete a notary")
  fun deleteNotary(simpleNotary: SimpleNotaryInfo): Future<Unit> {
    return inputsStorage.deleteNotary(simpleNotary.nodeKey, simpleNotary.notaryInfo.validating)
  }

  @Suppress("MemberVisibilityCanBePrivate")
  @ApiOperation(value = "For the node operator to acknowledge network map that new parameters were accepted for future update.")
  fun postAckNetworkParameters(signedSecureHash: Buffer): Future<Unit> {
    val signedParameterHash = signedSecureHash.bytes.deserializeOnContext<SignedData<SecureHash>>()
    val hash = signedParameterHash.verified()
    return nodeInfoStorage.get(hash.toString())
      .onSuccess {
        logger.info("received acknowledgement from node ${it.verified().legalIdentities}")
      }
      .catch {
        logger.warn("received acknowledgement from unknown node!")
      }
      .mapEmpty<Unit>()
  }

  @Suppress("MemberVisibilityCanBePrivate")
  @ApiOperation(value = "Retrieve a signed NodeInfo as specified in the network map object.",
    response = Buffer::class,
    produces = MediaType.APPLICATION_OCTET_STREAM
  )
  fun getNodeInfo(context: RoutingContext) {
    val hash = SecureHash.parse(context.request().getParam("hash"))
    nodeInfoStorage.get(hash.toString())
      .onSuccess { sni ->
        context.response().apply {
          setCacheControl(cacheTimeout)
          putHeader(CONTENT_TYPE, HttpHeaderValues.APPLICATION_OCTET_STREAM)
          end(Buffer.buffer(sni.serializeOnContext().bytes))
        }
      }
      .catch {
        logger.error("failed to retrieve node info for hash $hash")
        context.end(it)
      }
  }

  @Suppress("MemberVisibilityCanBePrivate")
  @ApiOperation(value = "Retrieve the signed network parameters. The entire object is signed with the network map certificate which is also attached.",
    response = Buffer::class,
    produces = MediaType.APPLICATION_OCTET_STREAM)
  fun getNetworkParameter(context: RoutingContext) {
    val hash = SecureHash.parse(context.request().getParam("hash"))
    signedNetworkParametersStorage.get(hash.toString())
      .onSuccess { snp ->
        context.response().apply {
          setCacheControl(cacheTimeout)
          putHeader(CONTENT_TYPE, HttpHeaderValues.APPLICATION_OCTET_STREAM)
          end(Buffer.buffer(snp.serializeOnContext().bytes))
        }
      }
      .catch {
        logger.error("failed to retrieve the signed network parameters for hash $hash", it)
        context.end(it)
      }
  }

  @Suppress("MemberVisibilityCanBePrivate")
  @ApiOperation(value = "Retrieve this network-map's truststore",
    response = Buffer::class,
    produces = MediaType.APPLICATION_OCTET_STREAM)
  fun getNetworkTrustStore(context: RoutingContext) {
    try {
      context.response().apply {
        putHeader(CONTENT_TYPE, HttpHeaderValues.APPLICATION_OCTET_STREAM)
        putHeader(CONTENT_DISPOSITION, "attachment; filename=\"network-truststore.jks\"")
        end(Buffer.buffer(certificateManager.generateTrustStoreByteArray()))
      }
    } catch (err: Throwable) {
      context.end(err)
    }
  }

  @Suppress("MemberVisibilityCanBePrivate")
  @ApiOperation(value = "undocumented Corda Networkmap API for retrieving the caller's IP",
    response = String::class,
    produces = MediaType.TEXT_PLAIN)
  fun getMyHostname(context: RoutingContext) {
    val remote = context.request().connection().remoteAddress()
    val ia = InetAddress.getByName(remote.host())
    if (ia.isAnyLocalAddress || ia.isLoopbackAddress) {
      context.end("localhost")
    } else {
      // try to do a reverse DNS
      vertx.createDnsClient().lookup(remote.host()) {
        if (it.failed()) {
          context.end(remote.host())
        } else {
          context.end(it.result())
        }
      }
    }
  }

  private fun startCertManager(): Future<Unit> {
    return certificateManager.init()
  }

  private fun startProcessor(): Future<Unit> {
    processor = NetworkMapServiceProcessor(
      vertx,
      dbDirectory,
      inputsStorage,
      nodeInfoStorage,
      signedNetworkMapStorage,
      signedNetworkParametersStorage,
      certificateManager,
      networkParamUpdateDelay,
      networkMapQueuedUpdateDelay
    )
    return processor.start()
  }

  private fun setupStorage(): Future<Unit> {
    return all(
      inputsStorage.makeDirs(),
      signedNetworkParametersStorage.makeDirs(),
      signedNetworkMapStorage.makeDirs(),
      nodeInfoStorage.makeDirs(),
      certificateAndKeyPairStorage.makeDirs()
    ).mapEmpty()
  }

  private fun createHttpServerOptions(): HttpServerOptions {
    val serverOptions = HttpServerConfig.defaultServerOptions().setHost(hostname).setSsl(tls)

    return when {
      !tls -> serverOptions
      certPath.isNotBlank() && keyPath.isNotBlank() -> {
        logger.info("using cert file $certPath")
        logger.info("using key file $keyPath")
        if (!File(certPath).exists()) {
          val msg = "cert path does not exist: $certPath"
          logger.error(msg)
          throw RuntimeException(msg)
        }

        if (!File(keyPath).exists()) {
          val msg = "key path does not exist: $keyPath"
          logger.error(msg)
          throw RuntimeException(msg)
        }

        val jksOptions = CertsToJksOptionsConverter(certPath, keyPath).createJksOptions()
        serverOptions.setKeyStoreOptions(jksOptions)
      }
      else -> {
        logger.info("generating temporary TLS certificates")
        val certificate = SelfSignedCertificate.create()
        serverOptions
          .setKeyCertOptions(certificate.keyCertOptions())
          .setTrustOptions(certificate.trustOptions())
      }
    }
  }
}

data class SimpleNodeInfo(val nodeKey: String, val addresses: List<NetworkHostAndPort>, val parties: List<NameAndKey>, val platformVersion: Int)
data class SimpleNotaryInfo(val nodeKey: String, val notaryInfo: NotaryInfo)
data class NameAndKey(val name: CordaX500Name, val key: PublicKey)

