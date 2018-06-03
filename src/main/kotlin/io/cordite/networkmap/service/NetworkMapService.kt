package io.cordite.networkmap.service

import io.cordite.networkmap.serialisation.SerializationEnvironment
import io.cordite.networkmap.storage.*
import io.cordite.networkmap.utils.*
import io.netty.handler.codec.http.HttpHeaderValues
import io.vertx.core.AbstractVerticle
import io.vertx.core.Future
import io.vertx.core.Future.future
import io.vertx.core.Future.succeededFuture
import io.vertx.core.buffer.Buffer
import io.vertx.core.http.HttpHeaders
import io.vertx.core.http.HttpServer
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.handler.*
import io.vertx.ext.web.sstore.LocalSessionStore
import net.corda.core.crypto.Crypto
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.SignedData
import net.corda.core.identity.CordaX500Name
import net.corda.core.serialization.deserialize
import net.corda.core.serialization.serialize
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.hours
import net.corda.core.utilities.loggerFor
import net.corda.core.utilities.seconds
import net.corda.nodeapi.internal.DEV_ROOT_CA
import net.corda.nodeapi.internal.SignedNodeInfo
import net.corda.nodeapi.internal.crypto.CertificateAndKeyPair
import net.corda.nodeapi.internal.crypto.CertificateType
import net.corda.nodeapi.internal.crypto.X509Utilities
import java.io.File
import java.net.InetAddress
import java.security.PublicKey
import java.time.Duration
import javax.security.auth.x500.X500Principal

class NetworkMapService(
  private val dbDirectory: File,
  private val user: InMemoryUser,
  private val port: Int = 9000,
  private val cacheTimeout: Duration = 10.seconds,
  private val networkParamUpdateDelay: Duration = 1.hours,
  private val networkMapQueuedUpdateDelay: Duration = 1.seconds
) : AbstractVerticle() {

  companion object {
    internal const val CERT_NAME = "nms"
    private const val NETWORK_MAP_ROOT = "/network-map"
    private const val ADMIN_ROOT = "/admin"
    private const val ADMIN_API_ROOT = "${ADMIN_ROOT}/api"
    private val logger = loggerFor<NetworkMapService>()

    init {
      SerializationEnvironment.init()
    }
  }

  private lateinit var certs: CertificateAndKeyPair
  private lateinit var inputsStorage: NetworkParameterInputsStorage
  private lateinit var signedNetworkParametersStorage: SignedNetworkParametersStorage
  private lateinit var nodeInfoStorage: SignedNodeInfoStorage
  private lateinit var textStorage: TextStorage
  internal lateinit var certificateAndKeyPairStorage: CertificateAndKeyPairStorage
  private lateinit var signedNetworkMapStorage: SignedNetworkMapStorage
  private lateinit var paramUpdateStorage: ParametersUpdateStorage
  private lateinit var processor: NetworkMapServiceProcessor
  private var httpServer: HttpServer? = null

  override fun start(startFuture: Future<Void>) {
    setupStorage()
      .compose { getDevNetworkMapCa() }
      .compose { setupProcessor() }
      .compose { createHttpServer(createRouter()) }
      .setHandler(startFuture.completer())
  }

  override fun stop(stopFuture: Future<Void>) {
    logger.info("shutting down")
    shutdownProcessor()
      .compose { shutdownHttpServer() }
      .onSuccess {
        logger.info("shutdown complete")
      }
      .catch {
        logger.error("shutdown failed", it)
      }
      .setHandler(stopFuture.completer())
  }

  private fun shutdownProcessor(): Future<Void> {
    processor.close()
    return succeededFuture()
  }

  private fun shutdownHttpServer(): Future<Void> {
    return httpServer?.let {
      val f = future<Void>()
      logger.info("shutting down the http server")
      httpServer?.close(f.completer())
      httpServer = null
      f
    } ?: succeededFuture()
  }

  private fun setupStorage(): Future<Unit> {
    logger.info("setting up database storage")
    inputsStorage = NetworkParameterInputsStorage(dbDirectory, vertx)
    signedNetworkParametersStorage = SignedNetworkParametersStorage(vertx, dbDirectory)
    signedNetworkMapStorage = SignedNetworkMapStorage(vertx, dbDirectory)
    nodeInfoStorage = SignedNodeInfoStorage(vertx, dbDirectory)
    textStorage = TextStorage(vertx, dbDirectory)
    certificateAndKeyPairStorage = CertificateAndKeyPairStorage(vertx, dbDirectory)
    paramUpdateStorage = ParametersUpdateStorage(vertx, dbDirectory)

    return all(
      inputsStorage.makeDirs(),
      signedNetworkParametersStorage.makeDirs(),
      signedNetworkMapStorage.makeDirs(),
      nodeInfoStorage.makeDirs(),
      textStorage.makeDirs(),
      paramUpdateStorage.makeDirs(),
      certificateAndKeyPairStorage.makeDirs()
    ).mapEmpty()
  }

  private fun setupProcessor() : Future<Unit> {
    processor = NetworkMapServiceProcessor(
      vertx,
      inputsStorage,
      nodeInfoStorage,
      signedNetworkMapStorage,
      signedNetworkParametersStorage,
      paramUpdateStorage,
      textStorage,
      certs,
      networkParamUpdateDelay,
      networkMapQueuedUpdateDelay
    )
    return processor.start()
  }

  private fun createHttpServer(router: Router): Future<Void> {
    logger.info("creating http server on port $port")
    val result = Future.future<Void>()
    this.httpServer = vertx
      .createHttpServer()
      .requestHandler(router::accept)
      .listen(port) {
        if (it.failed()) {
          logger.error("failed to startup", it.cause())
          result.fail(it.cause())
        } else {
          logger.info("network map service started")
          logger.info("""mounts:
            |network map:   http://localhost:$port$NETWORK_MAP_ROOT
            |admin website: http://localhost:$port$ADMIN_ROOT
            |admin API:     http://localhost:$port$ADMIN_API_ROOT
          """.trimMargin())
          result.complete()
        }
      }
    return result
  }

  private fun createRouter(): Router {
    val router = Router.router(vertx)
    bindCordaNetworkMapAPI(router)
    bindAdmin(router)
    return router
  }

  private fun bindAdmin(router: Router) {
    val authProvider = InMemoryAuthProvider(user)

    router.route().handler(CookieHandler.create())
    router.route().handler(SessionHandler.create(LocalSessionStore.create(vertx)))

    router.route().handler(UserSessionHandler.create(authProvider))
    val redirectAuthHandler = RedirectAuthHandler.create(authProvider, "login.html")
    router.route("$ADMIN_ROOT*").handler(BodyHandler.create())

    router.route("$ADMIN_ROOT*").handler(redirectAuthHandler)
    router.get("$ADMIN_API_ROOT/whitelist")
      .produces(HttpHeaderValues.TEXT_PLAIN.toString())
      .handler {
        it.handleExceptions {
          inputsStorage.serveWhitelist(this)
        }
      }

    router.get("$ADMIN_API_ROOT/notaries")
      .produces(HttpHeaderValues.APPLICATION_JSON.toString())
      .handler {
        it.handleExceptions {
          inputsStorage.serveNotaries(this)
        }
      }

    router.get("$ADMIN_API_ROOT/nodes")
      .produces(HttpHeaderValues.APPLICATION_JSON.toString())
      .handler {
        it.handleExceptions {
          getAllNodeInfos()
        }
      }

    router.get(ADMIN_ROOT).handler(StaticHandler.create("website")
      .setCachingEnabled(false).setWebRoot("admin"))

    router.get("/user").handler { context ->
      context.end(context.user().principal())
    }
    router.post("/login").handler(FormLoginHandler.create(authProvider))
    router.route("/logout").handler { context ->
      context.clearUser()
      // Redirect back to the index page
      context.response().putHeader("location", ADMIN_ROOT).setStatusCode(302).end()
    }

    router.route().handler(StaticHandler.create("website")
      .setCachingEnabled(false))
  }


  private fun bindCordaNetworkMapAPI(router: Router) {
    router.post("${NETWORK_MAP_ROOT}/publish")
      .consumes(HttpHeaderValues.APPLICATION_OCTET_STREAM.toString())
      .handler {
        it.handleExceptions { postNodeInfo() }
      }

    router.post("${NETWORK_MAP_ROOT}/ack-parameters")
      .consumes(HttpHeaderValues.APPLICATION_OCTET_STREAM.toString())
      .handler {
        it.handleExceptions {
          postAckNetworkParameters()
        }
      }

    router.get(NETWORK_MAP_ROOT)
      .produces(HttpHeaderValues.APPLICATION_OCTET_STREAM.toString())
      .handler { it.handleExceptions { getNetworkMap() } }

    router.get("${NETWORK_MAP_ROOT}/node-info/:hash")
      .produces(HttpHeaderValues.APPLICATION_OCTET_STREAM.toString())
      .handler {
        it.handleExceptions {
          val hash = SecureHash.parse(request().getParam("hash"))
          getNodeInfo(hash)
        }
      }

    router.get("${NETWORK_MAP_ROOT}/network-parameters/:hash")
      .produces(HttpHeaderValues.APPLICATION_OCTET_STREAM.toString())
      .handler {
        it.handleExceptions {
          val hash = SecureHash.parse(request().getParam("hash"))
          getNetworkParameters(hash)
        }
      }

    router.get("${NETWORK_MAP_ROOT}/my-hostname")
      .handler {
        it.handleExceptions {
          val remote = it.request().connection().remoteAddress()
          val ia = InetAddress.getByName(remote.host())
          if (ia.isAnyLocalAddress || ia.isLoopbackAddress) {
            end("localhost")
          } else {
            vertx.createDnsClient().lookup(remote.host()) {
              if (it.failed()) {
                end(it.cause())
              } else {
                end(it.result())
              }
            }
          }
        }
      }

  }

  private fun RoutingContext.getNetworkParameters(hash: SecureHash.SHA256) {
    signedNetworkParametersStorage.get(hash.toString())
      .onSuccess { snp ->
        response().apply {
          putHeader(HttpHeaders.CACHE_CONTROL, "max-age=${cacheTimeout.seconds}")
          putHeader(HttpHeaders.CONTENT_TYPE, HttpHeaderValues.APPLICATION_OCTET_STREAM)
          end(Buffer.buffer(snp.serialize().bytes))
        }
      }
      .catch {
        logger.error("failed to retrieve node info for hash $hash")
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
            logger.info("received acknowledgement from node ${it.verified().legalIdentities}")
          }
          .catch {
            logger.warn("received acknowledgement from unknown node!")
          }

        response().end()
      }
    }
  }

  private fun getDevNetworkMapCa(rootCa: CertificateAndKeyPair = DEV_ROOT_CA): Future<Unit> {
    logger.info("checking for certificate")
    return certificateAndKeyPairStorage.get(CERT_NAME)
      .recover {
        // we couldn't find the cert - so generate one
        logger.warn("failed to find the cert for this NMS. generating new cert")
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
      processor.addNode(signedNodeInfo)
        .onSuccess {
          end("OK")
        }
        .catch {
          end(it)
        }
    }
  }

  private fun RoutingContext.getNetworkMap() {
    signedNetworkMapStorage.serve(NetworkMapServiceProcessor.NETWORK_MAP_KEY, this, cacheTimeout)
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

  private fun RoutingContext.getAllNodeInfos() {
    nodeInfoStorage.getAll()
      .onSuccess {
        end(it.map {
          val node = it.value.verified()
          SimpleNodeInfo(node.addresses, node.legalIdentitiesAndCerts.map { NameAndKey(it.name, it.owningKey) }, node.platformVersion)
        })
      }
      .catch { end(it) }
  }

  data class SimpleNodeInfo(val addresses: List<NetworkHostAndPort>, val parties: List<NameAndKey>, val platformVersion: Int)
  data class NameAndKey(val name: CordaX500Name, val key: PublicKey)
}
