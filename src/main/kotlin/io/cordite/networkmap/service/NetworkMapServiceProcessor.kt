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

import io.cordite.networkmap.storage.Storage
import io.cordite.networkmap.storage.file.NetworkParameterInputsStorage
import io.cordite.networkmap.storage.mongo.MongoTextStorage
import io.cordite.networkmap.utils.all
import io.cordite.networkmap.utils.catch
import io.cordite.networkmap.utils.onSuccess
import io.cordite.networkmap.utils.withFuture
import io.vertx.core.Future
import io.vertx.core.Future.failedFuture
import io.vertx.core.Future.succeededFuture
import io.vertx.core.Vertx
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.sha256
import net.corda.core.internal.SignedDataWithCert
import net.corda.core.internal.signWithCert
import net.corda.core.node.NetworkParameters
import net.corda.core.utilities.loggerFor
import net.corda.nodeapi.internal.SignedNodeInfo
import net.corda.nodeapi.internal.crypto.CertificateAndKeyPair
import net.corda.nodeapi.internal.network.NetworkMap
import net.corda.nodeapi.internal.network.ParametersUpdate
import net.corda.nodeapi.internal.network.SignedNetworkMap
import net.corda.nodeapi.internal.network.SignedNetworkParameters
import rx.Subscription
import java.time.Duration
import java.time.Instant

/**
 * Event processor for the network map
 * This consumes networkparameter inputs changes; and nodeinfo updates
 * and rebuilds the set of files to be served by the server
 */
class NetworkMapServiceProcessor(
  private val vertx: Vertx,
  private val inputs: NetworkParameterInputsStorage,
  private val nodeInfoStorage: Storage<SignedNodeInfo>,
  private val networkMapStorage: Storage<SignedNetworkMap>,
  private val networkParamsStorage: Storage<SignedNetworkParameters>,
  private val parametersUpdateStorage: Storage<ParametersUpdate>,
  private val certificateManager: CertificateManager,
  private val textStorage: MongoTextStorage,
  private val networkMapQueueDelay: Duration,
  private val networkParameterUpdateDelay: Duration
) {
  companion object {
    private val logger = loggerFor<NetworkMapServiceProcessor>()
    const val EXECUTOR = "pool"
    const val CURRENT_PARAMETERS = "current-parameters"
    const val NEXT_PARAMS_UPDATE = "next-params-update"
    const val NETWORK_MAP_KEY = "latest-network-map"
    const val LAST_DIGEST_KEY = "last-digest.txt"

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
  private var subscription: Subscription? = null
  private var networkMapRebuildTimerId: Long? = null
  private lateinit var certs: CertificateAndKeyPair

  fun start(): Future<Unit> {
    certs = certificateManager.networkMapCertAndKeyPair
    subscription = inputs.registerForChanges().subscribe { digest ->
      logger.info("input change detected with hash $digest")
      execute { processNewDigest(digest, "network parameters change") }
    }
    return inputs.digest()
      .compose { currentDigest ->
        processNewDigest(currentDigest, "first setup", true)
      }
  }

  fun stop() {
    subscription = subscription?.let {
      if (!it.isUnsubscribed) {
        it.unsubscribe()
      }
      null
    }
  }

  fun addNode(signedNodeInfo: SignedNodeInfo): Future<Unit> {
    try {
      logger.info("adding signed nodeinfo ${signedNodeInfo.raw.hash}")
      val ni = signedNodeInfo.verified()
      val partyAndCerts = ni.legalIdentitiesAndCerts

      return execute {
        nodeInfoStorage.getAll()
      }
        .onSuccess { nodes ->
          // flatten the current nodes to Party -> PublicKey map
          val registered = nodes.flatMap {
            it.value.verified().legalIdentitiesAndCerts.map {
              it.party.name to it.owningKey
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
          nodeInfoStorage.put(hash.toString(), signedNodeInfo)
            .onSuccess { scheduleNetworkMapRebuild() }
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

  /**
   * we use this function to schedule a rebuild of the network map
   * we do this to avoid masses of network map rebuilds in the case of several nodes joining
   * or DoS attack
   */
  private fun scheduleNetworkMapRebuild() {
    logger.info("queuing network map rebuild in $networkMapQueueDelay")
    // cancel the old timer
    networkMapRebuildTimerId = networkMapRebuildTimerId?.let { vertx.cancelTimer(it); null }
    // setup a timer to rebuild the network map
    vertx.setTimer(networkMapQueueDelay.toMillis()) {
      // we'll queue this on the executor thread to ensure consistency
      execute { createNetworkMap() }
    }
  }

  private fun processNewDigest(digest: String, description: String, forceRebuild: Boolean = false): Future<Unit> {
    logger.info("processing new digest $digest from input directory")
    return getLastDigestProcessed()
      .compose { lastDigest ->
        if (digest != lastDigest) {
          logger.info("digest $digest not processed. last digest is $lastDigest")
          val activationTime = Instant.now().plusMillis(if (forceRebuild) {
            0
          } else {
            networkParameterUpdateDelay.toMillis()
          })
          onInputsChanged(description, activationTime)
            .compose { saveLastDigest(digest) }
        } else {
          logger.info("digest $digest has already been processed")
          succeededFuture(Unit)
        }
      }
      .onSuccess {
        // we always rebuild the network map
        // this can happen for the following cases:
        // 1. we've restarted the node and, whilst the network parameters are up to date, the NetworkMap isn't
        // 2. or we've got a genuine input parameter change
        if (forceRebuild) {
          createNetworkMap()
        } else {
          scheduleNetworkMapRebuild()
        }
      }.catch {
        logger.error("failed to process new digest $digest", it)
      }
  }

  private fun getLastDigestProcessed(): Future<String> {
    return textStorage.getOrDefault(LAST_DIGEST_KEY, "")
  }

  private fun saveLastDigest(digest: String): Future<Unit> {
    return textStorage.put(LAST_DIGEST_KEY, digest)
  }

  private inline fun <reified T : Any> T.sign(): SignedDataWithCert<T> {
    return this.signWithCert(certs.keyPair.private, certs.certificate)
  }

  private fun onInputsChanged(description: String, activation: Instant): Future<Unit> {
    logger.info("processing inputs with activation set for $activation - effective in ${Duration.between(Instant.now(), activation)}")
    return createNetworkParameters(description, activation)
      .compose { createNetworkMap() }
      .onSuccess {
        logger.info("inputs processed")
      }
      .map { }
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

  private fun createNetworkParameters(description: String = "", activation: Instant = Instant.now()): Future<SignedNetworkParameters> {
    logger.info("creating network parameters")

    // get the inputs
    val fWhiteList = inputs.readWhiteList()
    val fNotaries = inputs.readNotaries()

    // get the last network params hash
    logger.info("retrieving current network parameter hash")
    val fCurrentNetworkParamsHash = textStorage.get(CURRENT_PARAMETERS)
      .compose { key ->
        logger.info("current network param hash is $key")
        logger.info("attempting to retrieve network param hash $key")
        networkParamsStorage.get(key)
      }
      .map { snp ->
        logger.info("verifying and extracting network parameters")
        snp.verified()
      }
      // if it's the first time, default to the template
      .recover {
        logger.info("could not find network parameter. using the template instead")
        succeededFuture(templateNetworkParameters)
      }
      .onSuccess {
        logger.info("acquired network parameters")
      }

    // wait for the above to complete
    return all(fWhiteList, fNotaries, fCurrentNetworkParamsHash)
      .map {
        logger.info("creating new network parameter from notaries, whitelist, present time and updating epoch")
        fCurrentNetworkParamsHash.result().copy(
          notaries = fNotaries.result().map { it.second },
          whitelistedContractImplementations = fWhiteList.result(),
          modifiedTime = Instant.now(),
          epoch = fCurrentNetworkParamsHash.result().epoch + 1
        ).sign()
      }
      .compose { snp ->
        logger.info("storing new network parameters to ${snp.raw.hash}")
        networkParamsStorage.put(snp.raw.hash.toString(), snp).map { snp }
      }
      .compose { snp ->
        if (activation <= Instant.now()) {
          textStorage.put(CURRENT_PARAMETERS, snp.raw.hash.toString())
        } else {
          parametersUpdateStorage.put(NEXT_PARAMS_UPDATE, ParametersUpdate(snp.raw.hash, description, activation))
            .compose {
              processParamUpdate()
            }
        }.map { snp }
      }
      .onSuccess {
        logger.info("network parameters saved ${it.raw.hash}")
      }
      .catch {
        logger.info("failed to create network parameters", it)
      }
  }

  private fun processParamUpdate(): Future<Unit> {
    return parametersUpdateStorage.getOrNull(NEXT_PARAMS_UPDATE)
      .compose { paramsUpdate ->
        if (paramsUpdate != null) { // there's a parameter update planned
          val now = Instant.now() // compare to current time
          if (paramsUpdate.updateDeadline <= Instant.now()) {
            logger.info("param update being activated for networkparams ${paramsUpdate.newParametersHash}")
            textStorage.put(CURRENT_PARAMETERS, paramsUpdate.newParametersHash.toString())
              .compose { parametersUpdateStorage.delete(NEXT_PARAMS_UPDATE) }
              .compose { createNetworkMap().map { } }
          } else {
            val duration = Duration.between(now, paramsUpdate.updateDeadline)
            logger.info("preparing parameter update in $duration")
            vertx.setTimer(duration.toMillis()) { execute { processParamUpdate() } }
            succeededFuture(Unit)
          }
        } else {
          succeededFuture(Unit)
        }
      }
      .onSuccess {
        logger.info("processed param update")
      }
      .catch {
        logger.error("failed during processParamUpdate", it)
      }
  }

  private fun createNetworkMap(): Future<SignedNetworkMap> {
    logger.info("creating network map")
    // collect the inputs from disk
    val fNodes = nodeInfoStorage.getKeys().map { keys -> keys.map { key -> SecureHash.parse(key) } }
    val fParamUpdate = parametersUpdateStorage.getOrNull(NEXT_PARAMS_UPDATE)
    val fLatestParamHash = textStorage.get(CURRENT_PARAMETERS).map { SecureHash.parse(it) }

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
        networkMapStorage.put(NETWORK_MAP_KEY, snm).map { snm }
      }
      .onSuccess {
        logger.info("network map rebuilt ${it.raw.hash}")
      }
      .catch {
        logger.error("failed to create network map", it)
      }
  }
}