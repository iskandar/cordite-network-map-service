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
@file:Suppress("DEPRECATION")

package io.cordite.networkmap.service

import io.cordite.networkmap.changeset.Change
import io.cordite.networkmap.changeset.changeSet
import io.cordite.networkmap.serialisation.deserializeOnContext
import io.cordite.networkmap.utils.*
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpHeaderValues
import io.swagger.annotations.ApiOperation
import io.vertx.core.Future
import io.vertx.core.Future.failedFuture
import io.vertx.core.Future.succeededFuture
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.ext.web.RoutingContext
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.sha256
import net.corda.core.internal.SignedDataWithCert
import net.corda.core.internal.signWithCert
import net.corda.core.node.NetworkParameters
import net.corda.core.node.NotaryInfo
import net.corda.core.serialization.serialize
import net.corda.core.utilities.loggerFor
import net.corda.nodeapi.internal.SignedNodeInfo
import net.corda.nodeapi.internal.crypto.CertificateAndKeyPair
import net.corda.nodeapi.internal.network.NetworkMap
import net.corda.nodeapi.internal.network.ParametersUpdate
import net.corda.nodeapi.internal.network.SignedNetworkMap
import net.corda.nodeapi.internal.network.SignedNetworkParameters
import java.time.Duration
import java.time.Instant
import javax.ws.rs.core.MediaType

/**
 * Event processor for the network map
 * This consumes networkparameter inputs changes; and nodeinfo updates
 * and rebuilds the set of files to be served by the server
 */
class NetworkMapServiceProcessor(
  private val vertx: Vertx,
  private val storages: ServiceStorages,
  private val certificateManager: CertificateManager,
  private val networkMapQueueDelay: Duration
) {
  companion object {
    private val logger = loggerFor<NetworkMapServiceProcessor>()
    const val EXECUTOR = "pool"
    const val CURRENT_PARAMETERS = "current-parameters"
    const val NEXT_PARAMS_UPDATE = "next-params-update"
    const val NETWORK_MAP_KEY = "latest-network-map"

    private val templateNetworkParameters = NetworkParameters(
      minimumPlatformVersion = 1,
      notaries = listOf(),
      maxMessageSize = 10485760,
      maxTransactionSize = Int.MAX_VALUE,
      modifiedTime = Instant.now(),
      epoch = 1, // this will be incremented when used for the first time
      whitelistedContractImplementations = mapOf()
    )
  }

  // we use a single thread to queue changes to the map, to ensure consistency
  private val executor = vertx.createSharedWorkerExecutor(EXECUTOR, 1)
  private var networkMapRebuildTimerId: Long? = null
  private lateinit var certs: CertificateAndKeyPair

  fun start(): Future<Unit> {
    certs = certificateManager.networkMapCertAndKeyPair
    return createNetworkParameters()
      .compose { createNetworkMap() }
      .mapUnit()
  }

  fun stop() {}

  fun addNode(signedNodeInfo: SignedNodeInfo): Future<Unit> {
    try {
      logger.info("adding signed nodeinfo ${signedNodeInfo.raw.hash}")
      val ni = signedNodeInfo.verified()
      val partyAndCerts = ni.legalIdentitiesAndCerts

      // TODO: optimise this to use the database, and avoid loading all nodes into memory

      return execute {
        storages.nodeInfo.getAll()
      }
        .onSuccess { nodes ->
          // flatten the current nodes to Party -> PublicKey map
          val registered = nodes.flatMap { namedSignedNodeInfo ->
            namedSignedNodeInfo.value.verified().legalIdentitiesAndCerts.map { partyAndCertificate ->
              partyAndCertificate.party.name to partyAndCertificate.owningKey
            }
          }.toMap()

          // now filter the party and certs of the nodeinfo we're trying to register
          val registeredWithDifferentKey = partyAndCerts.filter {
            // looking for where the public keys differ
            registered[it.party.name].let { pk ->
              pk != null && pk != it.owningKey
            }
          }
          if (registeredWithDifferentKey.any()) {
            val names = registeredWithDifferentKey.joinToString("\n") { it.name.toString() }
            val msg = "node failed to registered because the following names have already been registered with different public keys $names"
            logger.warn(msg)
            throw RuntimeException(msg)
          }
        }
        .compose {
          val hash = signedNodeInfo.raw.sha256()
          storages.nodeInfo.put(hash.toString(), signedNodeInfo)
            .compose { scheduleNetworkMapRebuild() }
            .onSuccess { logger.info("node ${signedNodeInfo.raw.hash} for party ${ni.legalIdentities} added") }
        }
        .catch { ex ->
          logger.error("failed to add node", ex)
        }
    } catch (err: Throwable) {
      logger.error("failed to add node", err)
      return failedFuture(err)
    }
  }

  fun updateNetworkParameters(update: (NetworkParameters) -> NetworkParameters, description: String = "", activation: Instant = Instant.now()) : Future<Unit> {
    return getCurrentNetworkParameters()
      .map { update(it).sign() } // apply changeset and sign
      .compose { snp ->
        val hash = snp.raw.hash
        storages.networkParameters.put(hash.toString(), snp).map { hash }
      }
      .compose { hash -> storages.parameterUpdate.put(NEXT_PARAMS_UPDATE, ParametersUpdate(hash, description, activation)) }
      .compose {
        if (activation <= Instant.now()) {
          createNetworkMap().mapUnit()
        } else {
          scheduleNetworkMapRebuild()
          succeededFuture()
        }
      }
  }

  @Suppress("MemberVisibilityCanBePrivate")
  @ApiOperation(value = """For the non validating notary to upload its signed NodeInfo object to the network map",
    Please ignore the way swagger presents this. To upload a notary info file use:
      <code>
      curl -X POST -H "Authorization: Bearer &lt;token&gt;" -H "accept: text/plain" -H  "Content-Type: application/octet-stream" --data-binary @nodeInfo-007A0CAE8EECC5C9BE40337C8303F39D34592AA481F3153B0E16524BAD467533 http://localhost:8080//admin/api/notaries/nonValidating
      </code>
      """,
    consumes = MediaType.APPLICATION_OCTET_STREAM
  )
  fun postNonValidatingNotaryNodeInfo(nodeInfoBuffer: Buffer): Future<String> {
    return try {
      val nodeInfo = nodeInfoBuffer.bytes.deserializeOnContext<SignedNodeInfo>().verified()
      val updater = changeSet(Change.AddNotary(NotaryInfo(nodeInfo.legalIdentities.first(), false)))
      updateNetworkParameters(updater).map { "OK" }
    } catch (err: Throwable) {
      Future.failedFuture(err)
    }
  }

  @Suppress("MemberVisibilityCanBePrivate")
  @ApiOperation(value = """For the validating notary to upload its signed NodeInfo object to the network map.
    Please ignore the way swagger presents this. To upload a notary info file use:
      <code>
      curl -X POST -H "Authorization: Bearer &lt;token&gt;" -H "accept: text/plain" -H  "Content-Type: application/octet-stream" --data-binary @nodeInfo-007A0CAE8EECC5C9BE40337C8303F39D34592AA481F3153B0E16524BAD467533 http://localhost:8080//admin/api/notaries/validating
      </code>
      """,
    consumes = MediaType.APPLICATION_OCTET_STREAM
  )
  fun postValidatingNotaryNodeInfo(nodeInfoBuffer: Buffer): Future<String> {
    return try {
      val nodeInfo = nodeInfoBuffer.bytes.deserializeOnContext<SignedNodeInfo>().verified()
      val updater = changeSet(Change.AddNotary(NotaryInfo(nodeInfo.legalIdentities.first(), true)))
      updateNetworkParameters(updater).map { "OK" }
    } catch (err: Throwable) {
      Future.failedFuture(err)
    }
  }

  @ApiOperation(value = "append to the whitelist")
  fun appendWhitelist(append: String): Future<Unit> {
    return try {
      val parsed = append.toWhiteList()
      val updater = changeSet(Change.AppendWhiteList(parsed))
      updateNetworkParameters(updater).mapUnit()
    } catch (err: Throwable) {
      Future.failedFuture(err)
    }
  }

  @ApiOperation(value = "replace the whitelist")
  fun replaceWhitelist(replacement: String): Future<Unit> {
    return try {
      val parsed = replacement.toWhiteList()
      val updater = changeSet(Change.ReplaceWhiteList(parsed))
      updateNetworkParameters(updater).mapUnit()
    } catch (err: Throwable) {
      Future.failedFuture(err)
    }
  }


  @ApiOperation(value = "clears the whitelist")
  fun clearWhitelist(): Future<Unit> {
    return try {
      val updater = changeSet(Change.ClearWhiteList)
      updateNetworkParameters(updater).mapUnit()
    } catch (err: Throwable) {
      Future.failedFuture(err)
    }
  }

  @ApiOperation(value = "serve whitelist", response = String::class)
  fun serveWhitelist(routingContext: RoutingContext) {
    getCurrentNetworkParameters()
      .map {
        it.whitelistedContractImplementations
          .flatMap { entry ->
            entry.value.map { attachmentId ->
              "${entry.key}:$attachmentId"
            }
          }.joinToString("\n")
      }
      .onSuccess { whitelist ->
        routingContext.response()
          .setNoCache().putHeader(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.TEXT_PLAIN).end(whitelist)
      }
      .catch { routingContext.setNoCache().end(it) }
  }

  @Suppress("MemberVisibilityCanBePrivate")
  @ApiOperation(value = "delete a validating notary with the node key")
  fun deleteValidatingNotary(nodeKey: String): Future<Unit> {
    return try {
      val nameHash = SecureHash.parse(nodeKey)
      updateNetworkParameters(changeSet(Change.RemoveNotary(nameHash, true))).mapUnit()
    } catch (err: Throwable) {
      Future.failedFuture(err)
    }
  }

  @Suppress("MemberVisibilityCanBePrivate")
  @ApiOperation(value = "delete a non-validating notary with the node key")
  fun deleteNonValidatingNotary(nodeKey: String): Future<Unit> {
    return try {
      val nameHash = SecureHash.parse(nodeKey)
      updateNetworkParameters(changeSet(Change.RemoveNotary(nameHash, false))).mapUnit()
    } catch (err: Throwable) {
      Future.failedFuture(err)
    }
  }

  @Suppress("MemberVisibilityCanBePrivate")
  @ApiOperation(value = "delete a node by its key")
  fun deleteNode(nodeKey: String): Future<Unit> {
    return storages.nodeInfo.delete(nodeKey)
      .compose { createNetworkMap() }
      .mapUnit()
  }

  @ApiOperation(value = "serve set of notaries", response = SimpleNotaryInfo::class, responseContainer = "List")
  fun serveNotaries(routingContext: RoutingContext) {
    getCurrentNetworkParameters()
      .map { networkParameters ->
        networkParameters.notaries.map {
          SimpleNotaryInfo(it.identity.name.serialize().hash.toString(), it)
        }
      }
      .onSuccess {
        simpleNodeInfos -> routingContext.setNoCache().end(simpleNodeInfos)
      }
      .catch {
        routingContext.setNoCache().end(it)
      }
  }

  @Suppress("MemberVisibilityCanBePrivate")
  @ApiOperation(value = "retrieve all nodeinfos", responseContainer = "List", response = SimpleNodeInfo::class)
  fun serveNodes(context: RoutingContext) {
    context.setNoCache()
    storages.nodeInfo.getAll()
      .onSuccess { mapOfNodes ->
        context.end(mapOfNodes.map { namedNodeInfo ->
          val node = namedNodeInfo.value.verified()
          SimpleNodeInfo(
            nodeKey = namedNodeInfo.key,
            addresses = node.addresses,
            parties = node.legalIdentitiesAndCerts.map { NameAndKey(it.name, it.owningKey) },
            platformVersion = node.platformVersion
          )
        })
      }
      .catch { context.end(it) }
  }

  @Suppress("MemberVisibilityCanBePrivate")
  @ApiOperation(value = "Retrieve the current network parameters",
    produces = MediaType.APPLICATION_JSON, response = NetworkParameters::class)
  fun getCurrentNetworkParameters(context: RoutingContext) {
    getCurrentNetworkParameters()
      .onSuccess { context.end(it) }
      .catch { context.end(it) }
  }

  private fun getCurrentNetworkParameters() : Future<NetworkParameters> {
    return getCurrentSignedNetworkParameters().map { it.verified() }
  }

  private fun getCurrentSignedNetworkParameters() : Future<SignedNetworkParameters> {
    return storages.text.get(CURRENT_PARAMETERS).compose { key -> storages.networkParameters.get(key) }
  }

  /**
   * we use this function to schedule a rebuild of the network map
   * we do this to avoid masses of network map rebuilds in the case of several nodes joining
   * or DoS attack
   */
  private fun scheduleNetworkMapRebuild() : Future<Unit> {
    logger.info("queuing network map rebuild in $networkMapQueueDelay")
    // cancel the old timer
    networkMapRebuildTimerId = networkMapRebuildTimerId?.let { vertx.cancelTimer(it); null }
    // setup a timer to rebuild the network map
    return if (networkMapQueueDelay == Duration.ZERO) {
      createNetworkMap().mapUnit()
    } else {
      vertx.setTimer(networkMapQueueDelay.toMillis()) {
        // we'll queue this on the executor thread to ensure consistency
        execute { createNetworkMap() }
      }
      succeededFuture<Unit>()
    }
  }

  private inline fun <reified T : Any> T.sign(): SignedDataWithCert<T> {
    return this.signWithCert(certs.keyPair.private, certs.certificate)
  }

  private fun <T> execute(fn: () -> Future<T>): Future<T> {
    return withFuture { result ->
      executor.executeBlocking<T>({
        fn().setHandler(it.completer())
      }, {
        result.handle(it)
      })
    }
  }

  private fun createNetworkParameters(): Future<SignedNetworkParameters> {
    logger.info("creating network parameters")

    // get the inputs

    logger.info("retrieving current network parameter")
    return getCurrentSignedNetworkParameters()
      .recover {
        logger.info("could not find network parameters - creating one from the template")
        val snp = templateNetworkParameters.sign()
        val hash = snp.raw.hash.toString()
        storages.networkParameters.put(hash, snp)
          .compose {
            storages.text.put(CURRENT_PARAMETERS, hash)
          }
          .map { snp } // return the signed network parameters
          .onSuccess { result ->
            logger.info("network parameters saved ${result.raw.hash}")
          }
          .catch { err ->
            logger.info("failed to create network parameters", err)
          }
      }
  }

  private fun createNetworkMap(): Future<SignedNetworkMap> {
    logger.info("creating network map")
    // collect the inputs from disk
    val fNodes = storages.nodeInfo.getKeys().map { keys -> keys.map { key -> SecureHash.parse(key) } }
    val fParamUpdate = storages.parameterUpdate.getOrNull(NEXT_PARAMS_UPDATE)
    val fLatestParamHash = storages.text.get(CURRENT_PARAMETERS).map { SecureHash.parse(it) }

    // when all collected
    return all(fNodes, fParamUpdate, fLatestParamHash)
      .map {
        logger.info("building network map object")
        // build the network map
        NetworkMap(
          networkParameterHash = fLatestParamHash.result(),
          parametersUpdate = fParamUpdate.result(),
          nodeInfoHashes = fNodes.result()
        ).sign()
      }.compose { snm ->
        logger.info("saving network map")
        storages.networkMap.put(NETWORK_MAP_KEY, snm).map { snm }
      }
      .onSuccess {
        logger.info("network map rebuilt ${it.raw.hash}")
      }
      .catch {
        logger.error("failed to create network map", it)
      }
  }
}