package io.cordite.services.storage

import io.cordite.services.serialisation.SerializationEnvironment
import io.cordite.services.storage.NetworkParameterInputsStorage.Companion.DEFAULT_DIR_NAME
import io.cordite.services.storage.NetworkParameterInputsStorage.Companion.DEFAULT_DIR_NON_VALIDATING_NOTARIES
import io.cordite.services.storage.NetworkParameterInputsStorage.Companion.DEFAULT_DIR_VALIDATING_NOTARIES
import io.cordite.services.storage.NetworkParameterInputsStorage.Companion.WHITELIST_NAME
import io.cordite.services.utils.*
import io.vertx.core.Vertx
import io.vertx.ext.unit.TestContext
import io.vertx.ext.unit.junit.VertxUnitRunner
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.file.Files


@RunWith(VertxUnitRunner::class)
class NetworkParameterInputsStorageTest {
  companion object {
    private lateinit var vertx: Vertx

    init {
      SerializationEnvironment.init()
    }

    @JvmStatic
    @BeforeClass
    fun before() {
      vertx = Vertx.vertx()
    }

    @JvmStatic
    @AfterClass
    fun after(context: TestContext) {
      vertx.close(context.asyncAssertSuccess())
    }
  }

  @Test
  fun `that we create the input folder`(context: TestContext) {
    val tempDir = createTempDirectory()
    val nmis = NetworkParameterInputsStorage(tempDir, vertx)
    nmis.makeDirs()
      .onSuccess {
        val rootDir = File(tempDir, DEFAULT_DIR_NAME)
        context.assertTrue(rootDir.exists())
        context.assertTrue(File(rootDir, DEFAULT_DIR_VALIDATING_NOTARIES).exists())
        context.assertTrue(File(rootDir, DEFAULT_DIR_NON_VALIDATING_NOTARIES).exists())
      }
      .setHandler(context.asyncAssertSuccess())
  }

  @Test
  fun `that digest stream correctly signals a change in the input set`(context: TestContext) {
    val tempDir = createTempDirectory()
    val nmis = NetworkParameterInputsStorage(tempDir, vertx)

    var initialDigest = ""
    var newDigest = ""

    nmis.makeDirs()
      .onSuccess { println("directories created in ${nmis.directory}") }
      .compose { nmis.digest() }
      .onSuccess {
        initialDigest = it
        println("initial digest: $it")
      }
      .compose {
        val src = File("src/test/resources/sample-input-set/whitelist.txt").absolutePath
        val dst = File(nmis.directory, WHITELIST_NAME).absolutePath
        println("copy $src to $dst")
        vertx.fileSystem().copy(src, dst)
      }
      .compose { nmis.digest() }
      .onSuccess {
        newDigest = it
        context.assertNotEquals(initialDigest, newDigest)
        println("new digest: $it")
      }
      .onSuccess {
        // setup the listener
        val async = context.async(1)
        nmis.registerForChanges().subscribe {
          println("change received: $it")
          context.assertEquals(newDigest, it, "new digest from publication should match the actual digest for the change")
          async.countDown()
        }
      }
      .setHandler(context.asyncAssertSuccess())
  }

  @Test
  fun `that we can load whitelist and notaries`(context: TestContext) {
    val tempDir = createTempDirectory()
    val nmis = NetworkParameterInputsStorage(tempDir, vertx)
    val async = context.async()

    nmis.makeDirs()
      .onSuccess { println("directories created in ${nmis.directory}") }
      .onSuccess {
        // copy the whitelist
        Files.copy("src/test/resources/sample-input-set/whitelist.txt".toPath(), nmis.whitelistPath.toPath())
        copyFolder("src/test/resources/sample-input-set/validating".toPath(), nmis.validatingNotariesPath.toPath())
        copyFolder("src/test/resources/sample-input-set/non-validating".toPath(), nmis.nonValidatingNotariesPath.toPath())
      }
      .onSuccess {
        // setup the listener
        nmis.registerForChanges().subscribe {
          println("change received: $it")
          nmis.readWhiteList()
            .onSuccess {
              context.assertEquals(14, it.size)
            }
            .compose {
              nmis.readNotaries()
            }
            .onSuccess {
              context.assertEquals(1, it.count { it.validating })
              context.assertEquals(1, it.count { !it.validating })
            }
            .onSuccess { async.complete() }
            .catch {
              context.fail(it)
            }
        }
      }
      .setHandler(context.asyncAssertSuccess())
  }

  private fun createTempDirectory(): File {
    return Files.createTempDirectory("nms-test").toFile().apply { deleteOnExit() }
  }


}