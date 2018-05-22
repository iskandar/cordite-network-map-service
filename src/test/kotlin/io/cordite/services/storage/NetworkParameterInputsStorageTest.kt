package io.cordite.services.storage

import com.google.common.io.Files
import io.cordite.services.storage.NetworkParameterInputsStorage.Companion.DEFAULT_DIR_NAME
import io.cordite.services.storage.NetworkParameterInputsStorage.Companion.DEFAULT_DIR_NON_VALIDATING_NOTARIES
import io.cordite.services.storage.NetworkParameterInputsStorage.Companion.DEFAULT_DIR_VALIDATING_NOTARIES
import io.cordite.services.storage.NetworkParameterInputsStorage.Companion.WHITELIST_NAME
import io.cordite.services.utils.composeWithFuture
import io.cordite.services.utils.onSuccess
import io.vertx.core.Vertx
import io.vertx.core.file.CopyOptions
import io.vertx.ext.unit.TestContext
import io.vertx.ext.unit.junit.VertxUnitRunner
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(VertxUnitRunner::class)
class NetworkParameterInputsStorageTest {
  companion object {
    private lateinit var vertx: Vertx

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
    nmis.makeDirs().onSuccess {
      val rootDir = File(tempDir, DEFAULT_DIR_NAME)
      context.assertTrue(rootDir.exists())
      context.assertTrue(File(rootDir, DEFAULT_DIR_VALIDATING_NOTARIES).exists())
      context.assertTrue(File(rootDir, DEFAULT_DIR_NON_VALIDATING_NOTARIES).exists())
    }.setHandler(context.asyncAssertSuccess())
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
      .composeWithFuture<Void> {
        val src = File("src/test/resources/sample-input-set/whitelist.txt").absolutePath
        val dst = File(nmis.directory, WHITELIST_NAME).absolutePath
        println("copy $src to $dst")
        vertx.fileSystem().copy(
          src,
          dst,
          CopyOptions().setReplaceExisting(true),
          completer())
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

  }

  private fun createTempDirectory(): File {
    return Files.createTempDir().apply { deleteOnExit() }
  }
}