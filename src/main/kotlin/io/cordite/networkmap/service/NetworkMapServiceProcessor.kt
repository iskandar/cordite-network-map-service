package io.cordite.networkmap.service

import io.cordite.networkmap.storage.*
import io.cordite.networkmap.utils.all
import io.cordite.networkmap.utils.catch
import io.cordite.networkmap.utils.onSuccess
import io.cordite.networkmap.utils.withFuture
import io.vertx.core.Future
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
  private val nodeInfoStorage: SignedNodeInfoStorage,
  private val networkMapStorage: SignedNetworkMapStorage,
  private val networkParamsStorage: SignedNetworkParametersStorage,
  private val parametersUpdateStorage: ParametersUpdateStorage,
  private val textStorage: TextStorage,
  private val certs: CertificateAndKeyPair,
  private val networkParameterUpdateDelay: Duration,
  private val networkMapQueueDelay: Duration
) {
  companion object {
    private val log = loggerFor<NetworkMapServiceProcessor>()
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
  private var lastDigest = ""
  private var subscription: Subscription? = null
  private var networkMapRebuildTimerId: Long? = null

  fun start(): Future<Unit> {
    subscription = inputs.registerForChanges().subscribe { digest ->
      log.info("input change detected with hash $digest")
      execute { processNewDigest(digest, "network parameters change") }
    }

    return execute {
      getLastDigestProcessed()
        .compose {
          lastDigest = it
          inputs.digest()
        }
        .compose {
          processNewDigest(it, "first setup", true)
        }
    }
  }

  fun close() {
    subscription = subscription?.let {
      if (!it.isUnsubscribed) {
        it.unsubscribe()
      }
      null
    }
  }

  fun addNode(signedNodeInfo: SignedNodeInfo): Future<Unit> {
    log.info("adding signed nodeinfo ${signedNodeInfo.raw.hash}")
    return execute {
      val ni = signedNodeInfo.verified()
      log.info("verification passed for ${ni.legalIdentities.firstOrNull()?.name}")
      val hash = signedNodeInfo.raw.sha256()
      nodeInfoStorage.put(hash.toString(), signedNodeInfo)
        .onSuccess { scheduleNetworkMapRebuild() }
        .onSuccess { log.info("node ${signedNodeInfo.raw.hash} for party ${ni.legalIdentities} added") }
    }
  }

  /**
   * we use this function to schedule a rebuild of the network map
   * we do this to avoid masses of network map rebuilds in the case of several nodes joining
   * or DoS attack
   */
  private fun scheduleNetworkMapRebuild() {
    log.info("queuing network map rebuild in $networkMapQueueDelay")
    // cancel the old timer
    networkMapRebuildTimerId = networkMapRebuildTimerId?.let { vertx.cancelTimer(it); null }
    // setup a timer to rebuild the network map
    vertx.setTimer(networkMapQueueDelay.toMillis()) {
      // we'll queue this on the executor thread to ensure consistency
      execute { createNetworkMap() }
    }
  }

  private fun processNewDigest(digest: String, description: String, forceRebuild: Boolean = false): Future<Unit> {
    log.info("processing new digest $digest from input directory")
    return if (digest != lastDigest) {
      val activationTime = Instant.now().plusMillis(if (forceRebuild) {
        0
      } else {
        networkParameterUpdateDelay.toMillis()
      })
      onInputsChanged(description, activationTime)
        .onSuccess {
          lastDigest = digest
        }
    } else {
      succeededFuture(Unit)
    }.onSuccess {
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
      log.error("failed to process new digest $digest", it)
    }
  }

  private fun getLastDigestProcessed(): Future<String> {
    return textStorage.getOrDefault(LAST_DIGEST_KEY, "")
  }

  private inline fun <reified T : Any> T.sign(): SignedDataWithCert<T> {
    return this.signWithCert(certs.keyPair.private, certs.certificate)
  }

  private fun onInputsChanged(description: String, activation: Instant): Future<Unit> {
    log.info("processing inputs with activation set for $activation - effective in ${Duration.between(Instant.now(), activation)}")
    return createNetworkParameters(description, activation)
      .compose { createNetworkMap() }
      .onSuccess {
        log.info("inputs processed")
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
    log.info("creating network parameters")

    // get the inputs
    val fWhiteList = inputs.readWhiteList()
    val fNotaries = inputs.readNotaries()

    // get the last network params hash
    log.info("retrieving current network parameter hash")
    val fCurrentNetworkParamsHash = textStorage.get(CURRENT_PARAMETERS)
      .compose { key ->
        log.info("current network param hash is $key")
        log.info("attempting to retrieve network param hash $key")
        networkParamsStorage.get(key)
      }
      .map { snp ->
        log.info("verifying and extracting network parameters")
        snp.verified()
      }
      // if it's the first time, default to the template
      .recover {
        log.info("could not find network parameter. using the template instead")
        succeededFuture(templateNetworkParameters)
      }
      .onSuccess {
        log.info("acquired network parameters")
      }

    // wait for the above to complete
    return all(fWhiteList, fNotaries, fCurrentNetworkParamsHash)
      .map {
        log.info("creating new network parameter from notaries, whitelist, present time and updating epoch")
        fCurrentNetworkParamsHash.result().copy(
          notaries = fNotaries.result(),
          whitelistedContractImplementations = fWhiteList.result(),
          modifiedTime = Instant.now(),
          epoch = fCurrentNetworkParamsHash.result().epoch + 1
        ).sign()
      }
      .compose { snp ->
        log.info("storing new network parameters to ${snp.raw.hash}")
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
        log.info("network parameters saved ${it.raw.hash}")
      }
      .catch {
        log.info("failed to create network parameters", it)
      }
  }

  private fun processParamUpdate(): Future<Unit> {
    return parametersUpdateStorage.getOrNull(NEXT_PARAMS_UPDATE)
      .compose { paramsUpdate ->
        if (paramsUpdate != null) { // there's a parameter update planned
          val now = Instant.now() // compare to current time
          if (paramsUpdate.updateDeadline <= Instant.now()) {
            log.info("param update being activated for networkparams ${paramsUpdate.newParametersHash}")
            textStorage.put(CURRENT_PARAMETERS, paramsUpdate.newParametersHash.toString())
              .compose { parametersUpdateStorage.delete(NEXT_PARAMS_UPDATE) }
              .compose { createNetworkMap().map { } }
          } else {
            val duration = Duration.between(now, paramsUpdate.updateDeadline)
            log.info("preparing parameter update in $duration")
            vertx.setTimer(duration.toMillis()) { execute { processParamUpdate() } }
            succeededFuture(Unit)
          }
        } else {
          succeededFuture(Unit)
        }
      }
      .onSuccess {
        log.info("processed param update")
      }
      .catch {
        log.error("failed during processParamUpdate", it)
      }
  }

  private fun createNetworkMap(): Future<SignedNetworkMap> {
    log.info("creating network map")
    // collect the inputs from disk
    val fNodes = nodeInfoStorage.getKeys().map { keys -> keys.map { key -> SecureHash.parse(key) } }
    val fParamUpdate = parametersUpdateStorage.getOrNull(NEXT_PARAMS_UPDATE)
    val fLatestParamHash = textStorage.get(CURRENT_PARAMETERS).map { SecureHash.parse(it) }

    // when all collected
    return all(fNodes, fParamUpdate, fLatestParamHash)
      .map {
        log.info("building network map object")
        // build the network map
        NetworkMap(
          networkParameterHash = fLatestParamHash.result(),
          parametersUpdate = fParamUpdate.result(),
          nodeInfoHashes = fNodes.result()
        ).sign()
      }.compose { snm ->
        log.info("saving network map")
        networkMapStorage.put(NETWORK_MAP_KEY, snm).map { snm }
      }
      .onSuccess {
        log.info("network map rebuilt ${it.raw.hash}")
      }
      .catch {
        log.error("failed to create network map", it)
      }
  }
}