package io.cordite.services.storage

import io.cordite.services.utils.*
import io.vertx.core.Future
import io.vertx.core.Vertx
import net.corda.core.identity.Party
import net.corda.core.node.NodeInfo
import net.corda.core.node.NotaryInfo
import net.corda.core.node.services.AttachmentId
import net.corda.core.serialization.deserialize
import net.corda.core.utilities.loggerFor
import net.corda.nodeapi.internal.SignedNodeInfo
import rx.Observable
import rx.subjects.PublishSubject
import java.io.File

class NetworkParameterInputsStorage(parentDir: File,
                                    private val vertx: Vertx,
                                    childDir: String = DEFAULT_DIR_NAME,
                                    validatingNotariesDirectoryName: String = DEFAULT_DIR_VALIDATING_NOTARIES,
                                    nonValidatingNotariesDirectoryName: String = DEFAULT_DIR_NON_VALIDATING_NOTARIES) {
  companion object {
    private val log = loggerFor<NetworkParameterInputsStorage>()
    const val WHITELIST_NAME = "whitelist.txt"
    private const val TIME_OUT = 2_000L
    const val DEFAULT_DIR_NAME = "inputs"
    const val DEFAULT_DIR_VALIDATING_NOTARIES = "validating-notaries"
    const val DEFAULT_DIR_NON_VALIDATING_NOTARIES = "non-validating-notaries"
  }

  internal val directory = File(parentDir, childDir)
  internal val whitelistPath = File(directory, WHITELIST_NAME)
  internal val validatingNotariesPath = File(directory, validatingNotariesDirectoryName)
  internal val nonValidatingNotariesPath = File(directory, nonValidatingNotariesDirectoryName)

  private val digest = DirectoryDigest(directory)
  private var lastDigest: String = ""
  private val publishSubject = PublishSubject.create<String>()

  init {
    vertx.periodicStream(TIME_OUT).handler {
      digest()
        .onSuccess {
          if (lastDigest != it) {
            lastDigest = it
            publishSubject.onNext(it)
          }
        }
    }
  }

  fun makeDirs(): Future<Unit> {
    return vertx.fileSystem().mkdirs(validatingNotariesPath.absolutePath)
      .compose { vertx.fileSystem().mkdirs(nonValidatingNotariesPath.absolutePath) }
      .map { Unit }
  }

  fun digest(): Future<String> {
    return digest.digest(vertx)
  }

  fun registerForChanges(): Observable<String> {
    return publishSubject
  }

  fun readWhiteList(): Future<Map<String, List<AttachmentId>>> {
    return vertx.fileSystem().readFile(whitelistPath.absolutePath)
      .map {
        it.toString().lines()
          .parseToWhitelistPairs()
          .groupBy { it.first } // group by the FQN of classes to List<Pair<String, SecureHash>>>
          .mapValues { it.value.map { it.second } } // remap to FQN -> List<SecureHash>
          .toMap() // and generate the final map
      }
  }


  fun readNotaries(): Future<List<NotaryInfo>> {
    val validating = readNodeInfos(validatingNotariesPath)
      .compose { nodeInfos ->
        vertx.executeBlocking {
          nodeInfos.mapNotNull { nodeInfo ->
            try {
              NotaryInfo(nodeInfo.verified().notaryIdentity(), true)
            } catch (err: Throwable) {
              log.error("failed to process notary", err)
              null
            }
          }
        }
      }

    val nonValidating = readNodeInfos(nonValidatingNotariesPath)
      .compose { nodeInfos ->
        vertx.executeBlocking {
          nodeInfos.mapNotNull { nodeInfo ->
            try {
              NotaryInfo(nodeInfo.verified().notaryIdentity(), false)
            } catch (err: Throwable) {
              log.error("failed to process notary", err)
              null
            }
          }
        }
      }

    return listOf(validating, nonValidating).all()
      .map { (validating, nonValidating) ->
        val ms = validating.toMutableSet()
        ms.addAll(nonValidating)
        ms.toList()
      }
  }

  private fun readNodeInfos(dir: File): Future<List<SignedNodeInfo>> {
    return vertx.fileSystem().readDir(dir.absolutePath)
      .compose { files ->
        files.map { file ->
          vertx.fileSystem().readFile(file)
            .compose { buffer ->
              vertx.executeBlocking {
                try {
                  buffer.bytes.deserialize<SignedNodeInfo>()
                } catch (err: Throwable) {
                  log.error("failed to deserialize SignedNodeInfo $file")
                  null
                }
              }
            }
        }.all().map { nodeInfos -> nodeInfos.filterNotNull() }
      }
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

  private fun List<String>.parseToWhitelistPairs(): List<Pair<String, AttachmentId>> {
    return map { it.trim() }
      .filter { it.isNotEmpty() }
      .map { row -> row.split(":") } // simple parsing for the whitelist
      .mapIndexed { index, row ->
        if (row.size != 2) {
          log.error("malformed whitelist entry on line $index - expected <class>:<attachment id>")
          null
        } else {
          row
        }
      }
      .mapNotNull {
        // if we have an attachment id, try to parse it
        it?.let {
          try {
            it[0] to AttachmentId.parse(it[1])
          } catch (err: Throwable) {
            log.error("failed to parse attachment id", err)
            null
          }
        }
      }
  }
}

