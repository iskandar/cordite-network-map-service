package io.cordite.services

import io.cordite.services.storage.InMemorySignedNodeInfoStorage
import io.cordite.services.storage.InMemoryWhiteListStorage
import io.cordite.services.storage.PersistentWhiteListStorage
import io.cordite.services.utils.toFile
import java.io.File

class TestingNetworkMapApp(port: Int = 8080,
                           notaryDir: File = File("test-notary-info-and-cert"),
                           dbDir : File = File(".db"))
  : NetworkMapApp(
    port = port,
    notaryDir = notaryDir,
    nodeInfoStorage = InMemorySignedNodeInfoStorage(),
    whiteListStorage = PersistentWhiteListStorage(dbDir)) {

  init {
  }
  companion object {
    @JvmStatic
    fun main(args: Array<String>) {
      TestingNetworkMapApp().deploy()
    }
  }
}