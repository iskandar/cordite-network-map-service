/**
 *   Copyright 2018, Cordite Foundation.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package io.cordite.networkmap.storage

import de.flapdoodle.embed.mongo.Command
import de.flapdoodle.embed.mongo.MongoShellStarter
import de.flapdoodle.embed.mongo.MongodExecutable
import de.flapdoodle.embed.mongo.MongodStarter
import de.flapdoodle.embed.mongo.config.*
import de.flapdoodle.embed.mongo.config.Storage
import de.flapdoodle.embed.mongo.distribution.Feature
import de.flapdoodle.embed.mongo.distribution.IFeatureAwareVersion
import de.flapdoodle.embed.process.config.io.ProcessOutput
import de.flapdoodle.embed.process.distribution.BitSize
import de.flapdoodle.embed.process.distribution.Distribution
import de.flapdoodle.embed.process.distribution.Platform
import de.flapdoodle.embed.process.extract.ImmutableExtractedFileSet
import de.flapdoodle.embed.process.io.IStreamProcessor
import de.flapdoodle.embed.process.io.LogWatchStreamProcessor
import de.flapdoodle.embed.process.io.NamedOutputStreamProcessor
import de.flapdoodle.embed.process.io.Processors.console
import de.flapdoodle.embed.process.io.Processors.namedConsole
import de.flapdoodle.embed.process.runtime.Network
import de.flapdoodle.embed.process.store.StaticArtifactStore
import io.bluebank.braid.core.logging.loggerFor
import java.io.*
import java.util.*


class EmbeddedMongo private constructor(
  dbDirectory: String,
  private val mongodLocation: String, // empty string if none available
  private val enableAuth: Boolean
) : Closeable {
  companion object {
    const val INIT_TIMEOUT_MS = 30000L
    const val USER_ADDED_TOKEN = "Successfully added user"
    const val MONGO_USER = "mongo"
    const val MONGO_PASSWORD = "mongo"
    private val log = loggerFor<EmbeddedMongo>()
    fun create(dbDirectory: String, mongodLocation: String): EmbeddedMongo {
      // start it up and add the admin user
      EmbeddedMongo(dbDirectory, mongodLocation, false).use {
        it.addAdmin()
        // implicit shutdown
      }
      return EmbeddedMongo(dbDirectory, mongodLocation, true)
        .apply { setupShutdownHook() }
        .also {
          log.info("mongo database started on ${it.connectionString} mounted on ${it.location.absolutePath}")
        }
    }
  }

  private object RequiredVersion : IFeatureAwareVersion {
    private val features = EnumSet.of(Feature.SYNC_DELAY, Feature.STORAGE_ENGINE, Feature.ONLY_64BIT, Feature.NO_CHUNKSIZE_ARG, Feature.MONGOS_CONFIGDB_SET_STYLE, Feature.NO_HTTP_INTERFACE_ARG, Feature.ONLY_WITH_SSL, Feature.ONLY_WINDOWS_2008_SERVER, Feature.NO_SOLARIS_SUPPORT, Feature.NO_BIND_IP_TO_LOCALHOST)
    override fun getFeatures(): EnumSet<Feature> = features
    override fun asInDownloadPath() = "4.0.4"
    override fun enabled(feature: Feature?) = features.contains(feature)
  }

  private val bindIP = "localhost"
  private val port = Network.getFreeServerPort()
  private val location = File(dbDirectory).also { it.mkdirs() }
  private val replication = Storage(location.absolutePath, null, 0)
  private val executable : MongodExecutable
  val connectionString
    get() = when (enableAuth) {
      true -> "mongodb://$MONGO_USER:$MONGO_PASSWORD@$bindIP:$port"
      false -> "mongodb://$bindIP:$port"
    }

  private val mongodConfig = MongodConfigBuilder()
    .version(RequiredVersion)
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

  init {
    val runtimeConfig = RuntimeConfigBuilder().defaults(Command.MongoD)
      .apply {
        if (mongodLocation.isNotBlank()) {
          val execFile = File(mongodLocation).absoluteFile
          if (execFile.exists()) {
            throw FileNotFoundException("could not locate mongod executable $mongodLocation")
          }
          val fileSet = ImmutableExtractedFileSet.builder(execFile.parentFile).baseDirIsGenerated(false).executable(execFile).build()
          val distribution = Distribution(RequiredVersion, Platform.detect(), BitSize.detect())
          val store = StaticArtifactStore(mutableMapOf(distribution to fileSet))
          artifactStore(store)
        }
      }
      .build()
    val starter = MongodStarter.getInstance(runtimeConfig)
    executable = starter.prepare(mongodConfig)
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
        "user": "$MONGO_USER",
        "pwd": "$MONGO_PASSWORD",
        "roles": [ { "role": "userAdminAnyDatabase", "db": "admin" }, "readWriteAnyDatabase" ]
      })
    """.trimIndent()
    runScriptAndWait(script, USER_ADDED_TOKEN, arrayOf("couldn't add user", "failed to load", "login failed"), "admin", null, null)
  }
}

