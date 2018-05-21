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
import io.vertx.core.Future.succeededFuture
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
import java.io.File
import java.time.Duration
import java.time.Instant
import javax.security.auth.x500.X500Principal

class NetworkMapService(
  private val dbDirectory: File,
  private val port: Int = 9000,
  private val cacheTimeout: Duration = 10.seconds
) : AbstractVerticle() {
  companion object {
    private const val CERT_NAME = "NMS"
    private const val NETWORK_MAP_KEY = "networkmap"
    private const val LAST_DIGEST_KEY = "last-digest.txt"
    private const val WEB_ROOT = "/network-map"
    private val log = loggerFor<NetworkMapService>()
  }

  private lateinit var certs : CertificateAndKeyPair
  private lateinit var inputsStorage: NetworkParameterInputsStorage
  private lateinit var signedNetworkParametersStorage: SignedNetworkParametersStorage
  private lateinit var nodeInfoStorage: SignedNodeInfoStorage
  private lateinit var textStorage: TextStorage
  private lateinit var certificateAndKeyPairStorage: CertificateAndKeyPairStorage
  private lateinit var signedNetworkMapStorage: SignedNetworkMapStorage

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
  }

  override fun start(startFuture: Future<Void>) {
    setupStorage()
      .compose { getDevNetworkMapCa() }
      .compose { processInputDirectory() }
      .compose { createHttpServer(createRouter()) }
      .setHandler(startFuture.completer())
  }

  private fun setupStorage(): Future<Unit> {
    inputsStorage = NetworkParameterInputsStorage(dbDirectory, vertx)
    inputsStorage.registerForChanges().subscribe { this.processInputDirectory() }
    signedNetworkParametersStorage = SignedNetworkParametersStorage(dbDirectory, vertx)
    signedNetworkMapStorage = SignedNetworkMapStorage(dbDirectory, vertx)
    nodeInfoStorage = SignedNodeInfoStorage(dbDirectory, vertx)
    textStorage = TextStorage(dbDirectory, vertx)
    certificateAndKeyPairStorage = CertificateAndKeyPairStorage(dbDirectory, vertx)
    return succeededFuture()
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
          NetworkMapApp.logger.info("api mounted on http://localhost:$port$WEB_ROOT")
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
    router.post("$WEB_ROOT/publish")
      .consumes(HttpHeaderValues.APPLICATION_OCTET_STREAM.toString())
      .handler {
        it.handleExceptions { postNodeInfo() }
      }

    router.post("$WEB_ROOT/ack-parameters")
      .consumes(HttpHeaderValues.APPLICATION_OCTET_STREAM.toString())
      .handler {
        it.handleExceptions {
          postAckNetworkParameters()
        }
      }

    router.get(WEB_ROOT)
      .produces(HttpHeaderValues.APPLICATION_OCTET_STREAM.toString())
      .handler { it.handleExceptions { getNetworkMap() } }

    router.get("$WEB_ROOT/node-info/:hash")
      .produces(HttpHeaderValues.APPLICATION_OCTET_STREAM.toString())
      .handler {
        it.handleExceptions {
          val hash = SecureHash.parse(request().getParam("hash"))
          getNodeInfo(hash)
        }
      }

    router.get("$WEB_ROOT/network-parameters/:hash")
      .produces(HttpHeaderValues.APPLICATION_OCTET_STREAM.toString())
      .handler {
        it.handleExceptions {
          val hash = SecureHash.parse(request().getParam("hash"))
          getNetworkParameters(hash)
        }
      }
  }

  private fun RoutingContext.getNetworkParameters(hash: SecureHash.SHA256) {
    signedNetworkParametersStorage.get(hash.toString())
      .onSuccess {

      }
      .catch {
        log.error("failed to retrieve node info for hash $hash")
        this.end(it)
      }
  }


  private fun RoutingContext.postAckNetworkParameters() {
    request().bodyHandler { buffer ->
      this.handleExceptions {
        val signedParameterHash = buffer.bytes.deserialize<SignedData<SecureHash>>()
        val hash = signedParameterHash.verified()
        nodeInfoStorage.get(hash.toString())
          .onSuccess {
            log.info("received acknowledgement from node ${it.verified().legalIdentities}")
          }
          .catch {
            log.warn("received acknowledgement from unknown node!")
          }

        response().end()
      }
    }
  }

  private fun getDevNetworkMapCa(rootCa: CertificateAndKeyPair = DEV_ROOT_CA): Future<Unit> {
    return certificateAndKeyPairStorage.get(CERT_NAME)
      .recover { // we couldn't find the cert - so generate one
        log.warn("Failed to find the cert for this NMS, therefore generating one ")
        val cert = createDevNetworkMapCa(rootCa)
        certificateAndKeyPairStorage.put(CERT_NAME, cert).map { cert }
      }
      .onSuccess { it ->
        certs = it
      }
      .mapEmpty()
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

  private fun RoutingContext.postNodeInfo() {
    request().bodyHandler { buffer ->
      val signedNodeInfo = buffer.bytes.deserialize<SignedNodeInfo>()
      signedNodeInfo.verified()
      nodeInfoStorage.put(signedNodeInfo.raw.sha256().toString(), signedNodeInfo)
      response().setStatusCode(HttpResponseStatus.OK.code()).end()
    }
  }

  private fun RoutingContext.getNetworkMap() {
    signedNetworkMapStorage.get(NETWORK_MAP_KEY)
      .onSuccess { snm ->
        response().apply {
          putHeader(HttpHeaders.CACHE_CONTROL, "max-age=${cacheTimeout.seconds}")
          putHeader(HttpHeaders.CONTENT_TYPE, HttpHeaderValues.APPLICATION_OCTET_STREAM)
          end(Buffer.buffer(snm.serialize().bytes))
        }
      }
      .catch {
        this.end(it)
      }
  }

  private fun RoutingContext.getNodeInfo(hash: SecureHash) {
    nodeInfoStorage.get(hash.toString())
      .onSuccess { sni ->
        response().apply {
          putHeader(HttpHeaders.CACHE_CONTROL, "max-age=${cacheTimeout.seconds}")
          end(Buffer.buffer(sni.serialize().bytes))
        }
      }
      .catch {
        this.end(it)
      }
  }

  private fun processInputDirectory(): Future<Unit> {
    return inputsStorage.digest()
      .compose { digest ->
        textStorage
          .get(LAST_DIGEST_KEY)
          .compose { lastDigest ->
            if (lastDigest != digest) {
              createNetworkParameters()
                .compose { snp ->
                  refreshNetworkMap(snp)
                }
                .compose {
                  textStorage.put(LAST_DIGEST_KEY, digest)
                }
            } else {
              succeededFuture()
            }
          }
      }
  }

  private fun refreshNetworkMap(networkMapParameters: SignedNetworkParameters): Future<Unit> {
    return createNetworkMap(networkMapParameters)
      .compose { networkMap ->
        signedNetworkMapStorage.put(NETWORK_MAP_KEY, networkMap.signWithCert(certs.keyPair.private, certs.certificate))
      }
  }

  private fun createNetworkMap(networkMapParameters: SignedNetworkParameters): Future<NetworkMap> {
    return nodeInfoStorage.getKeys().map {
      it.map { SecureHash.parse(it) }
    }.map { nodeHashes ->
      NetworkMap(nodeHashes, networkMapParameters.raw.hash, ParametersUpdate(networkMapParameters.raw.hash, "input files updates", Instant.now()))
    }
  }

  private fun createNetworkParameters(): Future<SignedNetworkParameters> {
    val copy = templateNetworkParameters.copy(
      notaries = inputsStorage.readNotaries(),
      whitelistedContractImplementations = inputsStorage.readWhiteList(),
      modifiedTime = Instant.now()
    )
    val signed = copy.signWithCert(certs.keyPair.private, certs.certificate)
    return signedNetworkParametersStorage.put(signed.raw.hash.toString(), signed).map { signed }
  }
}