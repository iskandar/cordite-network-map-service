package io.cordite.networkmap.service

import io.bluebank.braid.corda.BraidConfig
import io.bluebank.braid.corda.rest.AuthSchema
import io.bluebank.braid.corda.rest.RestConfig
import io.bluebank.braid.core.http.HttpServerConfig
import io.cordite.networkmap.keystore.toJksOptions
import io.cordite.networkmap.keystore.toKeyStore
import io.cordite.networkmap.serialisation.SerializationEnvironment
import io.cordite.networkmap.serialisation.deserializeOnContext
import io.cordite.networkmap.serialisation.serializeOnContext
import io.cordite.networkmap.storage.*
import io.cordite.networkmap.utils.*
import io.netty.handler.codec.http.HttpHeaderValues
import io.swagger.annotations.ApiOperation
import io.swagger.models.Contact
import io.vertx.core.Future
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.core.http.HttpHeaders
import io.vertx.core.net.JksOptions
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.handler.StaticHandler
import net.corda.core.crypto.Crypto
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.SignatureScheme
import net.corda.core.crypto.SignedData
import net.corda.core.node.NodeInfo
import net.corda.core.utilities.loggerFor
import net.corda.nodeapi.internal.DEV_ROOT_CA
import net.corda.nodeapi.internal.SignedNodeInfo
import net.corda.nodeapi.internal.crypto.CertificateAndKeyPair
import net.corda.nodeapi.internal.crypto.CertificateType
import net.corda.nodeapi.internal.crypto.X509Utilities
import java.io.File
import java.net.InetAddress
import java.time.Duration
import javax.security.auth.x500.X500Principal
import javax.ws.rs.core.MediaType

class NetworkMapServiceV2(
  dbDirectory: File,
  user: InMemoryUser,
  private val port: Int,
  private val cacheTimeout: Duration,
  private val networkParamUpdateDelay: Duration,
  private val networkMapQueuedUpdateDelay: Duration,
  private val tls: Boolean,
  private val certPath: String,
  private val keyPath: String
) {
  companion object {
    private const val NETWORK_MAP_ROOT = "/network-map"
    private const val ADMIN_REST_ROOT = "/admin/api"
    private const val ADMIN_BRAID_ROOT = "/braid/api"
    private const val SWAGGER_ROOT = "/swagger"
    private val logger = loggerFor<NetworkMapServiceV2>()

    init {
      SerializationEnvironment.init()
    }
  }

  private val vertx = Vertx.vertx()

  private val certificateAndKeyPairStorage = CertificateAndKeyPairStorage(vertx, dbDirectory)
  private val authService = AuthService(user, File(certificateAndKeyPairStorage.resolveKey("jwt"), "jwt.jceks"))
  private val adminService = AdminServiceImpl()
  private val inputsStorage = NetworkParameterInputsStorage(dbDirectory, vertx)
  private val signedNetworkMapStorage = SignedNetworkMapStorage(vertx, dbDirectory)
  private val nodeInfoStorage = SignedNodeInfoStorage(vertx, dbDirectory)
  private val signedNetworkParametersStorage = SignedNetworkParametersStorage(vertx, dbDirectory)
  private val paramUpdateStorage = ParametersUpdateStorage(vertx, dbDirectory)
  private val textStorage = TextStorage(vertx, dbDirectory)
  private lateinit var signingCertAndKey: CertificateAndKeyPair

  private lateinit var processor: NetworkMapServiceProcessor

  fun start(): Future<Unit> {
    return setupStorage()
      .compose { ensureCertExists("signing", NetworkMapService.SIGNING_CERT_NAME, "Network Map", CertificateType.NETWORK_MAP) }.map { signingCertAndKey = it }
      .compose { startProcessor() }
      .compose { startupBraid() }
  }

  private fun startupBraid(): Future<Unit> {
    try {
      val thisService = this
      val staticHandler = StaticHandler.create("website")
      BraidConfig()
        .withVertx(vertx)
        .withPort(port)
        .withAuthConstructor(authService::createAuthProvider)
        .withService("admin", adminService)
        .withRootPath(ADMIN_BRAID_ROOT)
        .withHttpServerOptions(HttpServerConfig.defaultServerOptions().setSsl(tls).setKeyStoreOptions(createJksOptions()))
        .withRestConfig(RestConfig("Cordite Network Map Service")
          .withAuthSchema(AuthSchema.Token)
          .withSwaggerPath(SWAGGER_ROOT)
          .withApiPath("/") // a little different because we need to mount the network map on '/network-map'
          .withContact(Contact().url("https:cordite.foundation").name("Cordite Foundation"))
          .withDescription("""|<b>Please note:</b> The protected parts of this API require JWT authentication.
            |To activate, execute the <code>login</code> method.
            |Then copy the returned JWT token and insert it into the <i>Authorize</i> swagger dialog box as
            |<code>Bearer &lt;token&gt;</code>
          """.trimMargin().replace("\n", ""))
          .withPaths {
            group("network map") {
              unprotected {
                get(NETWORK_MAP_ROOT, thisService::getNetworkMap)
                post("$NETWORK_MAP_ROOT/publish", thisService::postNodeInfo)
                post("$NETWORK_MAP_ROOT/ack-parameters", thisService::postAckNetworkParameters)
                get("$NETWORK_MAP_ROOT/node-info/:hash", thisService::getNodeInfo)
                get("$NETWORK_MAP_ROOT/network-parameters/:hash", thisService::getNetworkParameter)
                get("$NETWORK_MAP_ROOT/my-hostname", thisService::getMyHostname)
              }
            }
            group("admin") {
              unprotected {
                post("$ADMIN_REST_ROOT/login", authService::login)
                router { route("/*").handler(staticHandler) }
              }
              protected {
                get("$ADMIN_REST_ROOT/whitelist", inputsStorage::serveWhitelist)
                put("$ADMIN_REST_ROOT/whitelist", inputsStorage::appendWhitelist)
                post("$ADMIN_REST_ROOT/whitelist", inputsStorage::replaceWhitelist)
                delete("$ADMIN_REST_ROOT/whitelist", inputsStorage::clearWhitelist)
                get("$ADMIN_REST_ROOT/notaries", inputsStorage::serveNotaries)
                get("$ADMIN_REST_ROOT/nodes", thisService::getAllNodeInfos)
              }
            }
          }
        ).bootstrapBraid(StubAppServiceHub())
      return Future.succeededFuture()
    } catch (err: Throwable) {
      return Future.failedFuture(err)
    }
  }

  private fun startProcessor(): Future<Unit> {
    processor = NetworkMapServiceProcessor(
      vertx,
      inputsStorage,
      nodeInfoStorage,
      signedNetworkMapStorage,
      signedNetworkParametersStorage,
      paramUpdateStorage,
      textStorage,
      signingCertAndKey,
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
      textStorage.makeDirs(),
      paramUpdateStorage.makeDirs(),
      certificateAndKeyPairStorage.makeDirs()
    ).mapEmpty()
  }

  private fun ensureCertExists(
    description: String,
    certName: String,
    commonName: String,
    certificateType: CertificateType,
    signatureScheme: SignatureScheme = Crypto.DEFAULT_SIGNATURE_SCHEME,
    rootCa: CertificateAndKeyPair = DEV_ROOT_CA
  ): Future<CertificateAndKeyPair> {
    logger.info("checking for $description certificate")
    return certificateAndKeyPairStorage.get(certName)
      .recover {
        // we couldn't find the cert - so generate one
        logger.warn("failed to find $description cert for this NMS. generating new cert")
        val cert = createSigningCert(rootCa, commonName, certificateType, signatureScheme)
        certificateAndKeyPairStorage.put(certName, cert).map { cert }
      }
  }

  private fun createSigningCert(rootCa: CertificateAndKeyPair, commonName: String, certificateType: CertificateType, signatureScheme: SignatureScheme): CertificateAndKeyPair {
    val keyPair = Crypto.generateKeyPair(signatureScheme)
    val cert = X509Utilities.createCertificate(
      certificateType,
      rootCa.certificate,
      rootCa.keyPair,
      X500Principal("CN=$commonName,O=Cordite,L=London,C=GB"),
      keyPair.public)
    return CertificateAndKeyPair(cert, keyPair)
  }

  private fun createJksOptions(): JksOptions {
    return when {
      !tls -> JksOptions() // just return a blank option as it won't be used
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

        CertsToJksOptionsConverter(certPath, keyPath).createJksOptions()
      }
      else -> {
        logger.info("generating temporary TLS certificates")
        val inMemoryOnlyPassword = "inmemory"
        createSigningCert(DEV_ROOT_CA, "localhost", CertificateType.TLS, Crypto.RSA_SHA256).toKeyStore(inMemoryOnlyPassword).toJksOptions(inMemoryOnlyPassword)
      }
    }
  }

  @ApiOperation(value = "Retrieve the current signed network map object. The entire object is signed with the network map certificate which is also attached.",
    produces = MediaType.APPLICATION_OCTET_STREAM, response = Buffer::class)
  private fun getNetworkMap(context: RoutingContext) {
    signedNetworkMapStorage.serve(NetworkMapServiceProcessor.NETWORK_MAP_KEY, context, cacheTimeout)
  }

  @ApiOperation(value = "For the node to upload its signed NodeInfo object to the network map")
  private fun postNodeInfo(nodeInfo: Buffer) {
    val signedNodeInfo = nodeInfo.bytes.deserializeOnContext<SignedNodeInfo>()
    processor.addNode(signedNodeInfo)
  }

  @ApiOperation(value = "retrieve all nodeinfos", responseContainer = "List", response = NodeInfo::class)
  private fun getAllNodeInfos(context: RoutingContext) {
    context.setNoCache()
    nodeInfoStorage.getAll()
      .onSuccess {
        context.end(it.map {
          val node = it.value.verified()
          NetworkMapService.SimpleNodeInfo(node.addresses, node.legalIdentitiesAndCerts.map { NetworkMapService.NameAndKey(it.name, it.owningKey) }, node.platformVersion)
        })
      }
      .catch { context.end(it) }
  }

  @ApiOperation(value = "For the node operator to acknowledge network map that new parameters were accepted for future update.")
  private fun postAckNetworkParameters(signedSecureHash: Buffer): Future<Unit> {
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

  @ApiOperation(value = "Retrieve a signed NodeInfo as specified in the network map object.",
    response = Buffer::class,
    produces = MediaType.APPLICATION_OCTET_STREAM
  )
  private fun getNodeInfo(context: RoutingContext) {
    val hash = SecureHash.parse(context.request().getParam("hash"))
    nodeInfoStorage.get(hash.toString())
      .onSuccess { sni ->
        context.response().apply {
          setCacheControl(cacheTimeout)
          putHeader(HttpHeaders.CONTENT_TYPE, HttpHeaderValues.APPLICATION_OCTET_STREAM)
          end(Buffer.buffer(sni.serializeOnContext().bytes))
        }
      }
      .catch {
        logger.error("failed to retrieve node info for hash $hash")
        context.end(it)
      }
  }

  @ApiOperation(value = "Retrieve the signed network parameters. The entire object is signed with the network map certificate which is also attached.",
    response = Buffer::class,
    produces = MediaType.APPLICATION_OCTET_STREAM)
  private fun getNetworkParameter(context: RoutingContext) {
    val hash = SecureHash.parse(context.request().getParam("hash"))
    signedNetworkParametersStorage.get(hash.toString())
      .onSuccess { snp ->
        context.response().apply {
          setCacheControl(cacheTimeout)
          putHeader(HttpHeaders.CONTENT_TYPE, HttpHeaderValues.APPLICATION_OCTET_STREAM)
          end(Buffer.buffer(snp.serializeOnContext().bytes))
        }
      }
      .catch {
        logger.error("failed to retrieve node info for hash $hash")
        context.end(it)
      }
  }

  @ApiOperation(value = "undocumented Corda Networkmap API for retrieving the callers IP",
    response = String::class,
    produces = MediaType.TEXT_PLAIN)
  private fun getMyHostname(context: RoutingContext) {
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
}

