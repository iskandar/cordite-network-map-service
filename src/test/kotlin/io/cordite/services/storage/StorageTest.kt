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
class StorageTest {
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
  fun `that storage creates parent directory`(context: TestContext) {
    val tempDir = Files.createTempDir()
    tempDir.deleteOnExit()
    val dbDir = File(tempDir, "db")
    val textStorage = TextStorage(vertx, dbDir)
    textStorage.makeDirs()
      .onSuccess {
        context.assertTrue(dbDir.exists())
        context.assertTrue(File(dbDir, TextStorage.DEFAULT_CHILD_DIR).exists())
      }
      .setHandler(context.asyncAssertSuccess())
  }
}
