package io.cordite.services.storage

import io.cordite.services.utils.DirectoryDigest
import io.vertx.core.Future
import io.vertx.core.Vertx
import net.corda.core.identity.Party
import net.corda.core.internal.readObject
import net.corda.core.node.NodeInfo
import net.corda.core.node.NotaryInfo
import net.corda.core.node.services.AttachmentId
import net.corda.core.utilities.loggerFor
import net.corda.nodeapi.internal.SignedNodeInfo
import rx.Observable
import rx.subjects.PublishSubject
import java.io.File

class NetworkParameterInputsStorage(parentDir: File,
                                    private val vertx: Vertx,
                                    childDir: String = DEFAULT_DIR_NAME,
                                    validatingNotariesDirectoryName: String = "validating-notaries",
                                    nonValidatingNotariesDirectoryName: String = "non-validating-notaries") {
  companion object {
    private val log = loggerFor<NetworkParameterInputsStorage>()
    private const val WHITELIST_NAME = "whitelist.txt"
    private const val TIME_OUT = 2_000L
    const val DEFAULT_DIR_NAME = "inputs"
  }

  private val directory = File(parentDir, childDir)
  private val whitelistPath = File(directory, WHITELIST_NAME)
  private val validatingNotariesPath = File(directory, validatingNotariesDirectoryName)
  private val nonValidatingNotariesPath = File(directory, nonValidatingNotariesDirectoryName)

  private val digest = DirectoryDigest(directory)
  private var lastDigest = digest()
  private val publishSubject = PublishSubject.create<Unit>()

  init {
    vertx.periodicStream(TIME_OUT).handler {
      if (digest() != lastDigest) {
        publishSubject.onNext(Unit)
      }
    }
  }

  fun digest(): Future<String> {
    return digest.digest(vertx)
  }

  fun registerForChanges(): Observable<Unit> {
    return publishSubject
  }

  fun readWhiteList(): Map<String, List<AttachmentId>> {
    return whitelistPath.readLines()
      .map { it.trim() }
      .filter { it.isNotEmpty() }
      .map { row -> row.split(":") }
      .mapIndexed { index, row ->
        if (row.size != 2) {
          log.error("malformed whitelist entry on line $index - expected <class>:<attachment id>")
          null
        } else {
          row
        }
      }
      .map {
        it?.let {
          try {
            it[0] to AttachmentId.parse(it[1])
          } catch (err: Throwable) {
            log.error("failed to parse attachment id", err)
            null
          }
        }
      }
      .filter { it != null }
      .map { it!! }
      .groupBy { it.first }
      .mapValues { it.value.map { it.second } }
      .toMap()
  }


  fun readNotaries(): List<NotaryInfo> {
    val validating = readNodeInfos(validatingNotariesPath)
      .map {
        try {
          NotaryInfo(it.verified().notaryIdentity(), true)
        } catch (err: Throwable) {
          log.error("failed to process notary", err)
          null
        }
      }
      .filter { it != null }
      .map { it!! }

    val nonValidating = readNodeInfos(nonValidatingNotariesPath)
      .map {
        try {
          NotaryInfo(it.verified().notaryIdentity(), true)
        } catch (err: Throwable) {
          log.error("failed to process notary", err)
          null
        }
      }
      .filter { it != null }
      .map { it!! }

    val ms = validating.toMutableSet()
    ms.addAll(nonValidating)
    return ms.toList()
  }

  private fun readNodeInfos(dir: File): List<SignedNodeInfo> {
    return dir.listFiles()
      .map {
        try {
          it.toPath().readObject<SignedNodeInfo>()
        } catch (err: Throwable) {
          log.error("failed to deserialize SignedNodeInfo ${it.path}")
          null
        }
      }
      .filter { it != null }
      .map { it!! }
  }

  private fun NodeInfo.notaryIdentity(): Party {
    return when (legalIdentities.size) {
    // Single node notaries have just one identity like all other nodes. This identity is the notary identity
      1 -> legalIdentities[0]
    // Nodes which are part of a distributed notary have a second identity which is the composite identity of the
    // cluster and is shared by all the other members. This is the notary identity.
      2 -> legalIdentities[1]
      else -> throw IllegalArgumentException("Not sure how to get the notary identity in this scenerio: $this")
    }
  }
}

