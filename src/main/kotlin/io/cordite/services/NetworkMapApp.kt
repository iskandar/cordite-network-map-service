package io.cordite.services

import io.cordite.services.utils.Options
import io.cordite.services.utils.toFile
import io.vertx.core.AbstractVerticle
import io.vertx.core.Vertx
import net.corda.core.utilities.loggerFor
import java.io.File

open class NetworkMapApp(private val port: Int,
                         private val dbDirectory: File
) : AbstractVerticle() {
  companion object {
    val logger = loggerFor<NetworkMapApp>()
    const val WEB_ROOT = "/network-map"
    const val WEB_API = "/api"

    @JvmStatic
    fun main(args: Array<String>) {
      val options = Options()
      val portOption = options.addOption("port", "8080", "web port")
      val dbDirectory = options.addOption("db.dir", ".db", "database directory for this service ")
      if (args.contains("--help")) {
        options.printOptions()
        return
      }
      val port = portOption.value.toInt()
      val db = dbDirectory.value.toFile()
      val vertx = Vertx.vertx()
      val service = NetworkMapService(dbDirectory = db, port = port)
      vertx.deployVerticle(service)
    }
  }
}
