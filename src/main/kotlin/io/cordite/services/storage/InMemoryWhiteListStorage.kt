package io.cordite.services.storage

import net.corda.core.node.services.AttachmentId

class InMemoryWhiteListStorage : WhitelistStorage {
  private var whiteList = listOf<Pair<String, AttachmentId>>()
  override fun add(list: List<Pair<String, AttachmentId>>) {
    val s = whiteList.toMutableSet()
    s.addAll(list)
    whiteList = s.toList()
  }

  override fun getAll(): List<Pair<String, AttachmentId>> {
    return whiteList
  }

  override fun clear() {
    whiteList = listOf()
  }
  override fun getAllAsMap(): Map<String, List<AttachmentId>> {
    return whiteList
        .groupBy { it.first }
        .mapValues { it.value.map { it.second } }
        .toMap()
  }
}