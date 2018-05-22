package io.cordite.services.storage

import com.google.common.io.Files
import io.cordite.services.utils.onSuccess
import io.vertx.core.Vertx
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
    private lateinit var vertx : Vertx

    @JvmStatic
    @BeforeClass
    fun before(context: TestContext) {
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
    val tempDir = Files.createTempDir()
    tempDir.deleteOnExit()
    val nmis = NetworkParameterInputsStorage(tempDir, vertx)
    nmis.makeDirs()
      .onSuccess {
        val rootDir = File(tempDir, NetworkParameterInputsStorage.DEFAULT_DIR_NAME)
        context.assertTrue(rootDir.exists())
        context.assertTrue(File(rootDir, NetworkParameterInputsStorage.DEFAULT_DIR_VALIDATING_NOTARIES).exists())
        context.assertTrue(File(rootDir, NetworkParameterInputsStorage.DEFAULT_DIR_NON_VALIDATING_NOTARIES).exists())
      }
      .setHandler(context.asyncAssertSuccess())
  }
}