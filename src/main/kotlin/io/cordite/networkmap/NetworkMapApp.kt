package io.cordite.networkmap

import io.cordite.networkmap.service.InMemoryUser
import io.cordite.networkmap.service.NetworkMapService
import io.cordite.networkmap.utils.Options
import io.cordite.networkmap.utils.toFile
import io.vertx.core.Vertx
import java.io.File
import java.time.Duration

open class NetworkMapApp(
  private val dbDirectory: File,
  private val user: InMemoryUser,
  private val port: Int,
  private val cacheTimeout: Duration,
  private val networkParamUpdateDelay: Duration,
  private val networkMapQueuedUpdateDelay: Duration,
  private val tls: Boolean
)  {
  companion object {

    @JvmStatic
    fun main(args: Array<String>) {
      val options = Options()
      val portOption = options.addOption("port", "8080", "web port")
      val dbDirectory = options.addOption("db", ".db", "database directory for this service")
      val cacheTimeout = options.addOption("cache.timeout", "2S", "http cache timeout for this service in ISO 8601 duration format")
      val paramUpdateDelay = options.addOption("paramUpdate.delay", "10S", "schedule duration for a parameter update")
      val networkMapUpdateDelay  = options.addOption("networkMap.delay", "1S", "queue time for the network map to update for addition of nodes")
      val username = options.addOption("username", "sa", "system admin username")
      val password = options.addOption("password", "admin", "system admin password")
      val tls = options.addOption("tls", "true", "whether TLS is enabled or not")

      if (args.contains("--help")) {
        options.printOptions()
        return
      }

      val port = portOption.value.toInt()
      val db = dbDirectory.value.toFile()
      val ct = Duration.parse("PT${cacheTimeout.value}")
      val puDelay = Duration.parse("PT${paramUpdateDelay.value}")
      val nmDelay = Duration.parse("PT${networkMapUpdateDelay.value}")
      val isTls = tls.value.toBoolean()
      val user = InMemoryUser.createUser("System Admin", username.value, password.value)
      NetworkMapApp(db, user, port, ct, puDelay, nmDelay, isTls).start()
    }
  }

  private fun start() {
    val vertx = Vertx.vertx()
    val service = NetworkMapService(dbDirectory = dbDirectory, user = user, port = port, cacheTimeout = cacheTimeout, networkParamUpdateDelay = networkParamUpdateDelay, networkMapQueuedUpdateDelay = networkMapQueuedUpdateDelay, tls = tls)
    vertx.deployVerticle(service)
  }
}
