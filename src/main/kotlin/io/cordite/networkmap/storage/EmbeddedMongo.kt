package io.cordite.networkmap.storage

import de.flapdoodle.embed.mongo.Command
import de.flapdoodle.embed.mongo.MongoShellStarter
import de.flapdoodle.embed.mongo.MongodStarter
import de.flapdoodle.embed.mongo.config.*
import de.flapdoodle.embed.mongo.config.Storage
import de.flapdoodle.embed.mongo.distribution.Version
import de.flapdoodle.embed.process.config.io.ProcessOutput
import de.flapdoodle.embed.process.io.IStreamProcessor
import de.flapdoodle.embed.process.io.LogWatchStreamProcessor
import de.flapdoodle.embed.process.io.NamedOutputStreamProcessor
import de.flapdoodle.embed.process.io.Processors.console
import de.flapdoodle.embed.process.io.Processors.namedConsole
import de.flapdoodle.embed.process.runtime.Network
import io.bluebank.braid.core.logging.loggerFor
import java.io.*
import java.util.*


class EmbeddedMongo private constructor(
  dbDirectory: String,
  private val user: String,
  private val password: String,
  private val enableAuth: Boolean
) : Closeable {
  companion object {
    const val INIT_TIMEOUT_MS = 30000L
    const val USER_ADDED_TOKEN = "Successfully added user"
    private val log = loggerFor<EmbeddedMongo>()
    fun create(dbDirectory: String, username: String, password: String): EmbeddedMongo {
      // start it up and add the admin user
      EmbeddedMongo(dbDirectory, username, password, false).use {
        it.addAdmin()
        // implicit shutdown
      }
      return EmbeddedMongo(dbDirectory, username, password, true)
        .apply { setupShutdownHook() }
        .also {
          log.info("mongo database started on ${it.connectionString}")
        }
    }
  }

  private val bindIP = "localhost"
  private val port = Network.getFreeServerPort()
  private val location = File(dbDirectory).also { it.mkdirs() }
  private val replication = Storage(location.absolutePath, null, 0)
  private val mongodConfig = MongodConfigBuilder()
    .version(Version.Main.PRODUCTION)
    .net(Net(bindIP, port, Network.localhostIsIPv6()))
    .replication(replication)
    .cmdOptions(MongoCmdOptionsBuilder()
      .enableAuth(enableAuth)
      .syncDelay(10)
      .useNoPrealloc(false)
      .useSmallFiles(false)
      .useNoJournal(false)
      .enableTextSearch(true)
      .build())
    .build()

  private val executable = MongodStarter.getDefaultInstance().prepare(mongodConfig)
  val connectionString
    get() = when (enableAuth) {
      true -> "mongodb://$user:$password@$bindIP:$port"
      false -> "mongodb://$bindIP:$port"
    }


  init {
    executable.start()
  }

  override fun close() {
    executable.stop()
  }

  fun setupShutdownHook() {
    Runtime.getRuntime().addShutdownHook(Thread {
      close()
    })
  }

  @Throws(IOException::class)
  private fun runScriptAndWait(scriptText: String, token: String, failures: Array<String>?, dbName: String, username: String?, password: String?) {
    val mongoOutput: IStreamProcessor = if (token.isNotEmpty()) {
      LogWatchStreamProcessor(
        String.format(token),
        if (failures != null) HashSet(failures.toList()) else emptySet(),
        namedConsole("[mongo shell output]"))
    } else {
      NamedOutputStreamProcessor("[mongo shell output]", console())
    }
    val runtimeConfig = RuntimeConfigBuilder()
      .defaults(Command.Mongo)
      .processOutput(ProcessOutput(
        mongoOutput,
        namedConsole("[mongo shell error]"),
        console()))
      .build()
    val starter = MongoShellStarter.getInstance(runtimeConfig)
    val scriptFile = writeTmpScriptFile(scriptText)
    val builder = MongoShellConfigBuilder()
    if (dbName.isNotEmpty()) {
      builder.dbName(dbName)
    }
    if (username != null && username.isNotEmpty()) {
      builder.username(username)
    }
    if (password != null && password.isNotEmpty()) {
      builder.password(password)
    }
    starter.prepare(builder
      .scriptName(scriptFile.absolutePath)
      .version(mongodConfig.version())
      .net(mongodConfig.net())
      .build()).start()
    if (mongoOutput is LogWatchStreamProcessor) {
      mongoOutput.waitForResult(INIT_TIMEOUT_MS)
    }
  }

  @Throws(IOException::class)
  private fun writeTmpScriptFile(scriptText: String): File {
    val scriptFile = File.createTempFile("tempfile", ".js")
    scriptFile.deleteOnExit()
    val bw = BufferedWriter(FileWriter(scriptFile))
    bw.write(scriptText)
    bw.close()
    return scriptFile
  }

  @Throws(IOException::class)
  private fun addAdmin() {
    val script = """
      db.createUser({
        "user": "$user",
        "pwd": "$password",
        "roles": [ { "role": "userAdminAnyDatabase", "db": "admin" }, "readWriteAnyDatabase" ]
      })
    """.trimIndent()
    runScriptAndWait(script, USER_ADDED_TOKEN, arrayOf("couldn't add user", "failed to load", "login failed"), "admin", null, null)
  }
}

