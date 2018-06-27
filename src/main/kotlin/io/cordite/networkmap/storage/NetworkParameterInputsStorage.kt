package io.cordite.networkmap.storage

import io.cordite.networkmap.serialisation.deserializeOnContext
import io.cordite.networkmap.utils.*
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpHeaderValues
import io.swagger.annotations.ApiOperation
import io.vertx.core.Future
import io.vertx.core.Vertx
import io.vertx.ext.web.RoutingContext
import net.corda.core.identity.Party
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
                                    validatingNotariesDirectoryName: String = DEFAULT_DIR_VALIDATING_NOTARIES,
                                    nonValidatingNotariesDirectoryName: String = DEFAULT_DIR_NON_VALIDATING_NOTARIES,
                                    pollRate: Long = DEFAULT_WATCH_DELAY) {
  companion object {
    internal val log = loggerFor<NetworkParameterInputsStorage>()
    const val WHITELIST_NAME = "whitelist.txt"
    const val DEFAULT_WATCH_DELAY = 2_000L
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
    digest.digest(vertx).setHandler {
      if (it.failed()) {
        log.error("failed to get digest for input director", it.cause())
      } else {
        lastDigest = it.result()
        // setup the watch
        vertx.periodicStream(pollRate).handler {
          digest()
            .onSuccess {
              if (lastDigest != it) {
                lastDigest = it
                publishSubject.onNext(it)
              }
            }
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

  @ApiOperation(value = "serve whitelist", response = String::class)
  fun serveWhitelist(routingContext: RoutingContext) {
    vertx.fileSystem().exists(whitelistPath.absolutePath) {
      if (it.result()) {
        routingContext.response()
          .setNoCache()
          .putHeader(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.TEXT_PLAIN)
          .sendFile(whitelistPath.absolutePath)
      } else {
        routingContext.response()
          .setNoCache()
          .putHeader(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.TEXT_PLAIN)
          .end("")
      }
    }
  }

  fun deleteNotary(identity: String, validating: Boolean) : Future<Unit> {
    val file = if (validating) {
      File(validatingNotariesPath, identity)
    } else {
      File(nonValidatingNotariesPath, identity)
    }
    return vertx.fileSystem().deleteFile(file.absolutePath)
      .mapEmpty()
  }

  fun readWhiteList(): Future<Map<String, List<AttachmentId>>> {
    try {
      return vertx.fileSystem().readFile(whitelistPath.absolutePath)
        .map {
          it.toString().lines()
            .parseToWhitelistPairs()
            .groupBy { it.first } // group by the FQN of classes to List<Pair<String, SecureHash>>>
            .mapValues { it.value.map { it.second } } // remap to FQN -> List<SecureHash>
            .toMap() // and generate the final map
        }
        .onSuccess {
          log.info("retrieved whitelist")
        }
        .recover {
          log.warn("whitelist file not found at ${whitelistPath.absolutePath}")
          Future.succeededFuture<Map<String, List<AttachmentId>>>(emptyMap())
        }
    } catch (err: Throwable) {
      return Future.failedFuture(err)
    }
  }

  @ApiOperation(value = "append to the whitelist")
  fun appendWhitelist(append: String): Future<Unit> {
    return try {
      val parsed = append.lines().parseToWhitelistPairs()
      readWhiteList()
        .map { wl ->
          val flattened = wl.flatMap { item ->
            item.value.map { item.key to it }
          }
          (flattened + parsed).distinct().joinToString("\n") { "${it.first}:${it.second}" }
        }
        .compose { newValue ->
          vertx.fileSystem().writeFile(whitelistPath.absolutePath, newValue.toByteArray())
        }
        .mapEmpty()
    } catch (err: Throwable) {
      Future.failedFuture(err)
    }
  }

  @ApiOperation(value = "replace the whitelist")
  fun replaceWhitelist(replacement: String): Future<Unit> {
    return try {
      val cleaned = replacement.lines().parseToWhitelistPairs().distinct().joinToString("\n") { "${it.first}:${it.second}" }
      vertx.fileSystem().writeFile(whitelistPath.absolutePath, cleaned.toByteArray())
        .mapEmpty()
    } catch (err: Throwable) {
      Future.failedFuture(err)
    }
  }

  @ApiOperation(value = "clears the whitelist")
  fun clearWhitelist(): Future<Unit> {
    return try {
      vertx.fileSystem().writeFile(whitelistPath.absolutePath, "".toByteArray()).mapEmpty()
    } catch (err: Throwable) {
      Future.failedFuture(err)
    }
  }

  fun readNotaries(): Future<List<Pair<String, NotaryInfo>>> {
    val validating = readNodeInfos(validatingNotariesPath)
      .compose { nodeInfos ->
        vertx.executeBlocking {
          nodeInfos.mapNotNull { nodeInfo ->
            try {
              nodeInfo.first to NotaryInfo(nodeInfo.second.verified().notaryIdentity(), true)
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
              nodeInfo.first to NotaryInfo(nodeInfo.second.verified().notaryIdentity(), false)
            } catch (err: Throwable) {
              log.error("failed to process notary", err)
              null
            }
          }
        }
      }

    return all(validating, nonValidating)
      .map { (validating, nonValidating) ->
        val ms = validating.toMutableSet()
        ms.addAll(nonValidating)
        ms.toList()
      }
      .onSuccess {
        log.info("retrieved notaries")
      }
      .catch {
        log.error("failed to retrieve notaries", it)
      }
  }

  private fun readNodeInfos(dir: File): Future<List<Pair<String, SignedNodeInfo>>> {
    return vertx.fileSystem().readFiles(dir.absolutePath)
      .compose { buffers ->
        vertx.executeBlocking {
          buffers.mapNotNull { (file, buffer) ->
            try {
              file to buffer.bytes.deserializeOnContext<SignedNodeInfo>()
            } catch (err: Throwable) {
              log.error("failed to deserialize SignedNodeInfo $file")
              null
            }
          }
        }
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



}

fun List<String>.parseToWhitelistPairs(): List<Pair<String, AttachmentId>> {
  return map { it.trim() }
    .filter { it.isNotEmpty() }
    .map { row -> row.split(":") } // simple parsing for the whitelist
    .mapIndexed { index, row ->
      if (row.size != 2) {
        NetworkParameterInputsStorage.log.error("malformed whitelist entry on line $index - expected <class>:<attachment id>")
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
          NetworkParameterInputsStorage.log.error("failed to parse attachment nodeKey", err)
          null
        }
      }
    }
}