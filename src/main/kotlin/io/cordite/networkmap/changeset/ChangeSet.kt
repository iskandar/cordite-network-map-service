package io.cordite.networkmap.changeset

import io.vertx.core.json.Json
import net.corda.core.crypto.SecureHash
import net.corda.core.node.NetworkParameters
import net.corda.core.node.NotaryInfo
import net.corda.core.node.services.AttachmentId
import net.corda.core.serialization.serialize
import java.time.Instant
import java.util.function.Function

fun changeSet(vararg change: Change): (NetworkParameters) -> NetworkParameters {
  return changeSet(change.toList())
}

fun changeSet(changes: List<Change>): (NetworkParameters) -> NetworkParameters {
  return { np: NetworkParameters ->
    changes.fold(np) { acc, change -> change.apply(acc) }.let { it.copy(epoch = it.epoch + 1) }
  }
}

sealed class Change : Function<NetworkParameters, NetworkParameters> {
  val description get() = "${this.javaClass.simpleName!!}: ${Json.encode(this)}"

  data class AddNotary(val notary: NotaryInfo) : Change() {
    override fun apply(networkParameters: NetworkParameters) =
      networkParameters.copy(
        notaries = networkParameters.notaries.toMutableSet().apply { add(notary) }.toList(),
        modifiedTime = Instant.now()
      )
  }

  data class RemoveNotary(val nameHash: SecureHash, val validating : Boolean) : Change() {
    override fun apply(networkParameters: NetworkParameters) =
      networkParameters.copy(
        notaries = networkParameters.notaries.filter { it.identity.name.serialize().hash != nameHash || it.validating != validating },
        modifiedTime = Instant.now()
      )
  }

  data class ReplaceNotaries(val notaries: List<NotaryInfo>) : Change() {
    override fun apply(networkParameters: NetworkParameters) =
      networkParameters.copy(
        notaries = notaries.distinct(),
        modifiedTime = Instant.now()
      )
  }

  object ClearNotaries : Change() {
    override fun apply(networkParameters: NetworkParameters) =
      networkParameters.copy(
        notaries = emptyList(),
        modifiedTime = Instant.now()
      )
  }

  data class AppendWhiteList(val whitelist: Map<String, List<AttachmentId>>) : Change() {
    override fun apply(networkParameters: NetworkParameters): NetworkParameters {
      val newWhiteList = networkParameters.whitelistedContractImplementations
        .map { it.key to it.value }
        .zip(whitelist.entries.map { it.key to it.value }) { a, b ->
          when {
            a.first == b.first -> {
              listOf(a.first to (a.second + b.second))
            }
            else -> {
              listOf(a, b)
            }
          }
        }.flatten().toMap()

      return networkParameters.copy(
        whitelistedContractImplementations = newWhiteList,
        modifiedTime = Instant.now()
      )
    }
  }

  data class ReplaceWhiteList(val whitelist: Map<String, List<AttachmentId>>) : Change() {
    override fun apply(networkParameters: NetworkParameters) = networkParameters.copy(
      whitelistedContractImplementations = whitelist,
      modifiedTime = Instant.now()
    )
  }

  object ClearWhiteList : Change() {
    override fun apply(t: NetworkParameters) = t.copy(
      whitelistedContractImplementations = emptyMap(),
      modifiedTime = Instant.now()
    )
  }
}