package io.cordite.services

import io.cordite.services.utils.Options
import io.cordite.services.utils.toFile
import io.vertx.core.Vertx
import net.corda.core.utilities.loggerFor
import java.io.File
import java.time.Duration

open class NetworkMapApp(
  private val dbDirectory: File,
  private val port: Int,
  private val cacheTimeout: Duration,
  val networkParamUpdateDelay: Duration,
  val networkMapQueuedUpdateDelay: Duration
)  {
  companion object {
    val logger = loggerFor<NetworkMapApp>()

    @JvmStatic
    fun main(args: Array<String>) {
      val options = Options()
      val portOption = options.addOption("port", "8080", "web port")
      val dbDirectory = options.addOption("db", ".db", "database directory for this service")
      val cacheTimeout = options.addOption("cache.timeout", "2S", "http cache timeout for this service in ISO 8601 duration format")
      val paramUpdateDelay = options.addOption("paramUpdate.delay", "10S", "schedule duration for a parameter update")
      val networkMapUpdateDelay  = options.addOption("networkMap.delay", "1S", "queue time for the network map to react and update for addition of nodes. This is used for reduce the pipeline pressure when there is a high concurrency of nodes registering with the network map")
      if (args.contains("--help")) {
        options.printOptions()
        return
      }
      val port = portOption.value.toInt()
      val db = dbDirectory.value.toFile()
      val ct = Duration.parse("PT${cacheTimeout.value}")
      val puDelay = Duration.parse("PT${paramUpdateDelay.value}")
      val nmDelay = Duration.parse("PT${networkMapUpdateDelay.value}")
      NetworkMapApp(db, port, ct, puDelay, nmDelay).start()
    }
  }

  private fun start() {
    val vertx = Vertx.vertx()
    val service = NetworkMapService(dbDirectory = dbDirectory, port = port, cacheTimeout = cacheTimeout, networkParamUpdateDelay = networkParamUpdateDelay, networkMapQueuedUpdateDelay = networkMapQueuedUpdateDelay)
    vertx.deployVerticle(service)
  }
}
