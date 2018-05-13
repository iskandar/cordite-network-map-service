package io.cordite.services

import io.cordite.services.storage.InMemorySignedNodeInfoStorage
import io.cordite.services.storage.PersistentWhiteListStorage
import java.io.File

class TestingNetworkMapApp(port: Int = 9000,
                           notaryDir: File = File("test-notary-info-and-cert"),
                           dbDir : File = File("test-notary-info-and-cert"))
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