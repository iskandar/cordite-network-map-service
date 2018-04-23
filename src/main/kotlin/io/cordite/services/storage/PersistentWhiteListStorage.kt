package io.cordite.services.storage

import net.corda.core.node.services.AttachmentId
import net.corda.core.utilities.loggerFor
import java.io.File

/**
 * Simple persisted storage for the whitelist
 * no caching, to ensure correctness for now
 * TODO: optimise with an in-memory cache and file watchers ..
 */
class PersistentWhiteListStorage(dbDir : File) : WhitelistStorage {
  private val whiteListFile = File(dbDir, "whitelist.txt")

  companion object {
    val log = loggerFor<PersistentWhiteListStorage>()
  }

  init {
    log.info("whitelist location: ${whiteListFile.absoluteFile.path}")
    if (!dbDir.exists()) {
      dbDir.mkdirs()
    }
    // atomically ensure file exists
    whiteListFile.createNewFile()
  }

  override fun clear() {
    save(listOf())
  }

  override fun add(list: List<Pair<String, AttachmentId>>) {
    val newList= getAll().toMutableSet().apply { addAll(list) }.toList()
    save(newList)
  }

  override fun getAll(): List<Pair<String, AttachmentId>> {
    return whiteListFile.readLines()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .map { it.split(":").apply {
          assert(size == 2) { "badly formed whitelist entry "}
        } }
        .map { it[0] to AttachmentId.parse(it[1]) }
  }

  override fun getAllAsMap(): Map<String, List<AttachmentId>> {
    return getAll()
        .groupBy { it.first }
        .mapValues { it.value.map { it.second } }
        .toMap()
  }

  private fun save(whiteList: List<Pair<String, AttachmentId>>) {
    val text = whiteList.joinToString("\n") { "${it.first}:${it.second}"}
    whiteListFile.writeText(text)
  }
}