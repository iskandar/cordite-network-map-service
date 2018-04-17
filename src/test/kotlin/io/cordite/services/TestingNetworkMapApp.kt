package io.cordite.services

import io.cordite.services.storage.InMemorySignedNodeInfoStorage
import io.cordite.services.storage.InMemoryWhiteListStorage
import java.io.File

class TestingNetworkMapApp(port: Int = 8080,
                           notaryDir: File = File("test-certificates"))
  : NetworkMapApp(
    port = port,
    notaryDir = notaryDir,
    nodeInfoStorage = InMemorySignedNodeInfoStorage(),
    whiteListStorage = InMemoryWhiteListStorage()) {
  companion object {
    @JvmStatic
    fun main(args: Array<String>) {
      TestingNetworkMapApp().deploy()
    }
  }
}