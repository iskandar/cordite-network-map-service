package io.cordite.services

import io.cordite.services.serialisation.initialiseSerialisation
import io.cordite.services.storage.*
import io.cordite.services.utils.catch
import io.cordite.services.utils.end
import io.cordite.services.utils.handleExceptions
import io.cordite.services.utils.onSuccess
import io.netty.handler.codec.http.HttpHeaderValues
import io.netty.handler.codec.http.HttpResponseStatus
import io.vertx.core.AbstractVerticle
import io.vertx.core.Future
import io.vertx.core.buffer.Buffer
import io.vertx.core.http.HttpHeaders
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.handler.StaticHandler
import net.corda.core.crypto.Crypto
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.SignedData
import net.corda.core.crypto.sha256
import net.corda.core.internal.signWithCert
import net.corda.core.node.NetworkParameters
import net.corda.core.serialization.deserialize
import net.corda.core.serialization.serialize
import net.corda.core.utilities.loggerFor
import net.corda.core.utilities.seconds
import net.corda.nodeapi.internal.DEV_ROOT_CA
import net.corda.nodeapi.internal.SignedNodeInfo
import net.corda.nodeapi.internal.crypto.CertificateAndKeyPair
import net.corda.nodeapi.internal.crypto.CertificateType
import net.corda.nodeapi.internal.crypto.X509Utilities
import net.corda.nodeapi.internal.network.NetworkMap
import net.corda.nodeapi.internal.network.ParametersUpdate
import net.corda.nodeapi.internal.network.SignedNetworkParameters
import java.time.Duration
import java.time.Instant
import javax.security.auth.x500.X500Principal

class NetworkMapService(
    private val port: Int = 9000,
    private val inputsStorage: InputStorage,
    private val signedNetworkParametersStorage: SignedNetworkParametersStorage,
    private val certificateAndKeyPairStorage: CertificateAndKeyPairStorage,
    private val signedNodeInfoStorage: SignedNodeInfoStorage,
    private val signedNetworkMapStorage: SignedNetworkMapStorage,
    private val textStorage: TextStorage,
    private val cacheTimeout : Duration = 10.seconds
) : AbstractVerticle() {
  companion object {
    private const val CERT_NAME = "NMS"
    private const val NETWORK_MAP_KEY = "networkmap"
    private const val LAST_DIGEST_KEY = "last-digest.txt"
    private val log = loggerFor<NetworkMapService>()
  }

  private val ca = getDevNetworkMapCa()

  private val templateNetworkParameters = NetworkParameters(
      minimumPlatformVersion = 1,
      notaries = listOf(),
      maxMessageSize = 10485760,
      maxTransactionSize = Int.MAX_VALUE,
      modifiedTime = Instant.now(),
      epoch = 10,
      whitelistedContractImplementations = mapOf()
      )

  init {
    initialiseSerialisation()
    processInputDirectory()
  }

  private fun processInputDirectory() {
    val digest = inputsStorage.digest()
    val lastDigest = textStorage.getBlocking(LAST_DIGEST_KEY)
    if (lastDigest != digest) {
      val networkMapParameters = createNetworkParameters()
      refreshNetworkMap(networkMapParameters)
      textStorage.putBlocking(LAST_DIGEST_KEY, digest)
    }
  }

  private fun refreshNetworkMap(networkMapParameters: SignedNetworkParameters) {
    val networkMap = createNetworkMap(networkMapParameters)
    signedNetworkMapStorage.putBlocking(NETWORK_MAP_KEY, networkMap)
  }

  private fun createNetworkMap(networkMapParameters: SignedNetworkParameters): NetworkMap {
    val nodeHashes = signedNodeInfoStorage.getKeysBlocking().map { SecureHash.parse(it) }
    return NetworkMap(nodeHashes, networkMapParameters.raw.hash, ParametersUpdate(networkMapParameters.raw.hash, "input files updates", Instant.now()))
  }

  private fun createNetworkParameters() : SignedNetworkParameters {
    val copy = templateNetworkParameters.copy(
        notaries = inputsStorage.readNotaries(),
        whitelistedContractImplementations = inputsStorage.readWhiteList(),
        modifiedTime = Instant.now()
    )
    val signed = copy.signWithCert(ca.keyPair.private, ca.certificate)
    signedNetworkParametersStorage.putBlocking(signed.raw.hash.toString(), signed)
    return signed
  }

  override fun start(startFuture: Future<Void>) {
    createHttpServer(createRouter()).setHandler(startFuture.completer())
  }

  private fun createHttpServer(router: Router): Future<Void> {
    val result = Future.future<Void>()
    vertx
        .createHttpServer()
        .requestHandler(router::accept)
        .listen(port) {
          if (it.failed()) {
            NetworkMapApp.logger.error("failed to startup", it.cause())
            result.fail(it.cause())
          } else {
            NetworkMapApp.logger.info("network map service started")
            NetworkMapApp.logger.info("api mounted on http://localhost:$port${NetworkMapApp.WEB_ROOT}")
            NetworkMapApp.logger.info("website http://localhost:$port")
            result.complete()
          }
        }
    return result
  }

  private fun createRouter(): Router {
    val router = Router.router(vertx)
    bindCordaNetworkMapAPI(router)
    bindStatic(router)
    return router
  }

  private fun bindStatic(router: Router) {
    val staticHandler = StaticHandler.create("website").setCachingEnabled(false).setCacheEntryTimeout(1).setMaxCacheSize(1)
    router.get("/*").handler(staticHandler::handle)
  }


  private fun bindCordaNetworkMapAPI(router: Router) {
    router.post("${NetworkMapApp.WEB_ROOT}/publish")
        .consumes(HttpHeaderValues.APPLICATION_OCTET_STREAM.toString())
        .handler {
          it.handleExceptions { postNetworkMap() }
        }

    router.post("${NetworkMapApp.WEB_ROOT}/ack-parameters")
        .consumes(HttpHeaderValues.APPLICATION_OCTET_STREAM.toString())
        .handler {
          it.handleExceptions {
            postAckNetworkParameters()
          }
        }

    router.get(NetworkMapApp.WEB_ROOT)
        .produces(HttpHeaderValues.APPLICATION_OCTET_STREAM.toString())
        .handler { it.handleExceptions { getNetworkMap() } }

    router.get("${NetworkMapApp.WEB_ROOT}/node-info/:hash")
        .produces(HttpHeaderValues.APPLICATION_OCTET_STREAM.toString())
        .handler {
          it.handleExceptions {
            val hash = SecureHash.parse(request().getParam("hash"))
            getNodeInfo(hash)
          }
        }

    router.get("${NetworkMapApp.WEB_ROOT}/network-parameters/:hash")
        .produces(HttpHeaderValues.APPLICATION_OCTET_STREAM.toString())
        .handler {
          it.handleExceptions {
            val hash = SecureHash.parse(request().getParam("hash"))
            getNetworkParameters(hash)
          }
        }
  }


  private fun RoutingContext.postAckNetworkParameters() {
    request().bodyHandler { buffer ->
      this.handleExceptions {
        val signedParameterHash = buffer.bytes.deserialize<SignedData<SecureHash>>()
        val hash = signedParameterHash.verified()
        signedNodeInfoStorage.get(hash.toString())
            .onSuccess {
              log.info("received acknowledgement from node ${it.verified().legalIdentities }")
            }
            .catch {
              log.warn("received acknowledgement from unknown node!")
            }

        response().end()
      }
    }
  }

  private fun getDevNetworkMapCa(rootCa: CertificateAndKeyPair = DEV_ROOT_CA) : CertificateAndKeyPair {
    return certificateAndKeyPairStorage.getBlocking(CERT_NAME) ?: run {
      val cert = createDevNetworkMapCa(rootCa)
      certificateAndKeyPairStorage.put(CERT_NAME, cert)
      cert
    }
  }

  private fun createDevNetworkMapCa(rootCa: CertificateAndKeyPair): CertificateAndKeyPair {
    val keyPair = Crypto.generateKeyPair()
    val cert = X509Utilities.createCertificate(
        CertificateType.NETWORK_MAP,
        rootCa.certificate,
        rootCa.keyPair,
        X500Principal("CN=Network Map,O=Cordite,L=London,C=GB"),
        keyPair.public)
    return CertificateAndKeyPair(cert, keyPair)
  }

  private fun RoutingContext.postNetworkMap() {
    request().bodyHandler { buffer ->
      val signedNodeInfo = buffer.bytes.deserialize<SignedNodeInfo>()
      signedNodeInfo.verified()
      signedNodeInfoStorage.put(signedNodeInfo.raw.sha256().toString(), signedNodeInfo)
      response().setStatusCode(HttpResponseStatus.OK.code()).end()
    }
  }

  private fun RoutingContext.getNetworkMap() {
    signedNetworkMapStorage.get(NETWORK_MAP_KEY)
        .onSuccess { snm ->
          response().apply {
            putHeader(HttpHeaders.CACHE_CONTROL, "max-age=${cacheTimeout.seconds}")
            end(Buffer.buffer(snm.serialize().bytes))
          }
        }
        .catch {
          this.end(result.cause())
        }
  }

  private fun RoutingContext.getNodeInfo(hash: SecureHash) {
    signedNodeInfoStorage.get(hash.toString())
  }

}