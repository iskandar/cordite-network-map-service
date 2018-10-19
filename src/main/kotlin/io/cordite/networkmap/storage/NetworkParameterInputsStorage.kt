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
package io.cordite.networkmap.storage

import io.cordite.networkmap.serialisation.deserializeOnContext
import io.cordite.networkmap.utils.*
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpHeaderValues
import io.swagger.annotations.ApiOperation
import io.vertx.core.Future
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.ext.web.RoutingContext
import net.corda.core.identity.Party
import net.corda.core.node.NodeInfo
import net.corda.core.node.NotaryInfo
import net.corda.core.node.services.AttachmentId
import net.corda.core.serialization.serialize
import net.corda.core.utilities.loggerFor
import net.corda.nodeapi.internal.SignedNodeInfo
import rx.Observable
import rx.subjects.PublishSubject
import java.io.File
import javax.ws.rs.core.MediaType


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
    const val VALIDATING_NOTARY = "validating-notary"
    const val NON_VALIDATING_NOTARY = "non-validating-notary"
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

  fun deleteNotary(identity: String, validating: Boolean): Future<Unit> {
    val file = if (validating) {
      File(validatingNotariesPath, identity)
    } else {
      File(nonValidatingNotariesPath, identity)
    }
    return vertx.fileSystem().deleteFile(file.absolutePath)
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
      val parsed = append.toWhitelistPairs()
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
      val cleaned = replacement.toWhitelistPairs().distinct().joinToString("\n") { "${it.first}:${it.second}" }
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

  @Suppress("MemberVisibilityCanBePrivate")
  @ApiOperation(value = """For the validating notary to upload its signed NodeInfo object to the network map.
    Please ignore the way swagger presents this. To upload a notary info file use:
      <code>
      curl -X POST -H "Authorization: Bearer &lt;token&gt;" -H "accept: text/plain" -H  "Content-Type: application/octet-stream" --data-binary @nodeInfo-007A0CAE8EECC5C9BE40337C8303F39D34592AA481F3153B0E16524BAD467533 http://localhost:8080//admin/api/notaries/validating
      </code>
      """,
    consumes = MediaType.APPLICATION_OCTET_STREAM
  )
  fun postValidatingNotaryNodeInfo(nodeInfo: Buffer) : Future<String> {
    return postNotaryNodeInfo(nodeInfo, VALIDATING_NOTARY)
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
  fun postNonValidatingNotaryNodeInfo(nodeInfo: Buffer) : Future<String> {
    return postNotaryNodeInfo(nodeInfo, NON_VALIDATING_NOTARY)
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

  private fun postNotaryNodeInfo(nodeInfo: Buffer, notaryType: String): Future<String> {
    return try {
      val signedNodeInfo = nodeInfo.bytes.deserializeOnContext<SignedNodeInfo>()
      val nodeHash = signedNodeInfo.verified().legalIdentities[0].name.serialize().hash
      val filePath = when (notaryType) {
        VALIDATING_NOTARY -> "${validatingNotariesPath.absolutePath}/nodeInfo-$nodeHash"
        NON_VALIDATING_NOTARY -> "${nonValidatingNotariesPath.absolutePath}/nodeInfo-$nodeHash"
        else -> throw Exception()
      }
      vertx.fileSystem().writeFile(filePath, nodeInfo.bytes).map { "OK" }
    } catch (err: UnsupportedOperationException) {
      val message = "Failed to upload $notaryType nodeInfo. Expected valid nodeInfo file"
      log.error(message, err)
      throw err
    } catch (err: Throwable) {
      val message = "Failed to upload $notaryType nodeInfo"
      log.error(message, err)
      throw err
    }
  }
}

fun List<Pair<String, AttachmentId>>.toWhitelistText(): String {
  return this.joinToString("\n") { it.first + ':' + it.second.toString() }
}


fun String.toWhitelistPairs(): List<Pair<String, AttachmentId>> {
  return this.lines().parseToWhitelistPairs()
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