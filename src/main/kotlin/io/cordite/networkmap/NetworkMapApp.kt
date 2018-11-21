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
package io.cordite.networkmap

import com.mongodb.async.SingleResultCallback
import com.mongodb.async.client.MongoCollection
import de.flapdoodle.embed.mongo.MongodStarter
import de.flapdoodle.embed.mongo.config.MongoCmdOptionsBuilder
import de.flapdoodle.embed.mongo.config.MongodConfigBuilder
import de.flapdoodle.embed.mongo.config.Net
import de.flapdoodle.embed.mongo.config.Storage
import de.flapdoodle.embed.mongo.distribution.Version
import de.flapdoodle.embed.process.runtime.Network
import io.cordite.networkmap.service.CertificateManagerConfig
import io.cordite.networkmap.service.NetworkMapService
import io.cordite.networkmap.utils.LogInitialiser
import io.cordite.networkmap.utils.NMSOptions
import io.cordite.networkmap.utils.onSuccess
import io.vertx.core.Future
import net.corda.core.utilities.loggerFor
import org.litote.kmongo.async.KMongo
import org.litote.kmongo.async.ensureIndex
import org.litote.kmongo.async.getCollection
import java.io.File
import kotlin.system.exitProcess


open class NetworkMapApp {
  companion object {
    private val logger = loggerFor<NetworkMapApp>()

    @JvmStatic
    fun main(args: Array<String>) {
      LogInitialiser.init()
      NMSOptions().apply {
        if (args.contains("--help")) {
          printHelp()
          return
        }
        println("starting networkmap with the following options")
        printOptions()
        connectToDatabase()
        bootstrapNMS()
      }
    }

    data class Foo(val bar: String)

    private fun NMSOptions.connectToDatabase() {
      val starter = MongodStarter.getDefaultInstance()

      val bindIp = "localhost"
      val port = Network.getFreeServerPort()
      val location = File(this.dbDirectory, "mongo")
      location.mkdirs()

      val replication = Storage(location.absolutePath, null, 0)
      val mongodConfig = MongodConfigBuilder()
        .version(Version.Main.PRODUCTION)
        .net(Net(bindIp, port, Network.localhostIsIPv6()))
        .replication(replication)
        .cmdOptions(MongoCmdOptionsBuilder()
          .syncDelay(10)
          .useNoPrealloc(false)
          .useSmallFiles(false)
          .useNoJournal(false)
          .enableTextSearch(true)
          .build())
        .build()

      try {
        starter.prepare(mongodConfig).let { mongodExecutable ->
          mongodExecutable.start()
          val mongo = KMongo.createClient("mongodb://$bindIp:$port")
          val db = mongo.getDatabase("test")
          val col = db.getCollection<Foo>()
          col.ensureIndex(Foo::bar)
          col.insertOne(Foo("bar"))
            .compose { col.count() }
            .onSuccess {
              println("inserted one item. count is $it")
            }
          Runtime.getRuntime().addShutdownHook(Thread {
            mongodExecutable.stop()
          })
        }
      } catch (err: Throwable) {
        logger.error("failed to startup mongod", err)
      }
    }

    private fun <T> MongoCollection<T>.insertOne(value: T): Future<Unit> {
      val result = Future.future<Void>()
      this.insertOne(value, singleCallback(result))
      return result.mapEmpty()
    }

    private fun <T> MongoCollection<T>.count(): Future<Long> {
      val result = Future.future<Long>()
      this.count(singleCallback(result))
      return result
    }

    private fun <T> singleCallback(future: Future<T>): SingleResultCallback<T> {
      return SingleResultCallback { value, err ->
        future.completeFrom(value, err)
      }
    }

    private fun <T> Future<T>.completeFrom(value: T?, err: Throwable?) {
      return when {
        err != null -> fail(err)
        else -> complete(value)
      }
    }

    private fun NMSOptions.bootstrapNMS() {
      if (truststore != null && !truststore.exists()) {
        println("failed to find truststore ${truststore.path}")
        exitProcess(-1)
      }
      NetworkMapService(
        dbDirectory = dbDirectory,
        user = user,
        port = port,
        cacheTimeout = cacheTimeout,
        networkParamUpdateDelay = paramUpdateDelay,
        networkMapQueuedUpdateDelay = networkMapUpdateDelay,
        tls = tls,
        certPath = certPath,
        keyPath = keyPath,
        hostname = hostname,
        webRoot = webRoot,
        certificateManagerConfig = CertificateManagerConfig(
          root = root,
          doorManEnabled = enableDoorman,
          certManEnabled = enableCertman,
          certManPKIVerficationEnabled = pkix,
          certManRootCAsTrustStoreFile = truststore,
          certManRootCAsTrustStorePassword = trustStorePassword,
          certManStrictEVCerts = strictEV
        )
      ).startup().setHandler {
        if (it.failed()) {
          logger.error("failed to complete setup", it.cause())
        } else {
          logger.info("started")
        }
      }
    }
  }
}
