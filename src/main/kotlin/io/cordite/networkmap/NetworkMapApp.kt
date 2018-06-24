package io.cordite.networkmap

import io.cordite.networkmap.service.InMemoryUser
import io.cordite.networkmap.service.NetworkMapServiceV2
import io.cordite.networkmap.utils.Options
import io.cordite.networkmap.utils.toFile
import net.corda.core.utilities.loggerFor
import java.time.Duration

open class NetworkMapApp  {
  companion object {
    private val logger = loggerFor<NetworkMapApp>()

    @JvmStatic
    fun main(args: Array<String>) {
      val options = Options()
      val portOpt = options.addOption("port", "8080", "web port")
      val dbDirectoryOpt = options.addOption("db", ".db", "database directory for this service")
      val cacheTimeoutOpt = options.addOption("cache.timeout", "2S", "http cache timeout for this service in ISO 8601 duration format")
      val paramUpdateDelayOpt = options.addOption("paramUpdate.delay", "10S", "schedule duration for a parameter update")
      val networkMapUpdateDelayOpt  = options.addOption("networkMap.delay", "1S", "queue time for the network map to update for addition of nodes")
      val usernameOpt = options.addOption("username", "sa", "system admin username")
      val passwordOpt = options.addOption("password", "admin", "system admin password")
      val tlsOpt = options.addOption("tls", "true", "whether TLS is enabled or not")
      val certPathOpt = options.addOption("tls.cert.path", "", "path to cert if TLS is turned on")
      val keyPathOpt = options.addOption("tls.key.path", "", "path to key if TLS turned on")
      if (args.contains("--help")) {
        options.printOptions()
        return
      }

      val port = portOpt.value.toInt()
      val dbDirectory = dbDirectoryOpt.value.toFile()
      val cacheTimeout = Duration.parse("PT${cacheTimeoutOpt.value}")
      val paramUpdateDelay = Duration.parse("PT${paramUpdateDelayOpt.value}")
      val networkMapUpdateDelay = Duration.parse("PT${networkMapUpdateDelayOpt.value}")
      val tls = tlsOpt.value.toBoolean()
      val certPath = certPathOpt.value
      val keyPath = keyPathOpt.value
      val user = InMemoryUser.createUser("System Admin", usernameOpt.value, passwordOpt.value)

      NetworkMapServiceV2(
        dbDirectory = dbDirectory,
        user = user,
        port = port,
        cacheTimeout = cacheTimeout,
        networkParamUpdateDelay = paramUpdateDelay,
        networkMapQueuedUpdateDelay = networkMapUpdateDelay,
        tls = tls,
        certPath = certPath,
        keyPath = keyPath
      ).start().setHandler {
        if (it.failed()) {
          logger.error("failed to complete setup", it.cause())
        } else {
          logger.info("started")
        }
      }
    }
  }
}
