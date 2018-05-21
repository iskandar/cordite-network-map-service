package io.cordite.services

import io.cordite.services.utils.Options
import io.cordite.services.utils.toFile
import io.vertx.core.AbstractVerticle
import io.vertx.core.Vertx
import net.corda.core.utilities.loggerFor
import java.io.File
import java.time.Duration

open class NetworkMapApp(private val port: Int,
                         private val dbDirectory: File
) : AbstractVerticle() {
  companion object {
    val logger = loggerFor<NetworkMapApp>()

    @JvmStatic
    fun main(args: Array<String>) {
      val options = Options()
      val portOption = options.addOption("port", "8080", "web port")
      val dbDirectory = options.addOption("db", ".db", "database directory for this service")
      val cacheTimeout = options.addOption("timeout", "2S", "http cache timeout for this service in ISO 8601 duration format")
      if (args.contains("--help")) {
        options.printOptions()
        return
      }
      val port = portOption.value.toInt()
      val db = dbDirectory.value.toFile()
      val ct = Duration.parse(cacheTimeout.value)
      val vertx = Vertx.vertx()
      val service = NetworkMapService(dbDirectory = db, port = port, cacheTimeout = ct)
      vertx.deployVerticle(service)
    }
  }
}
