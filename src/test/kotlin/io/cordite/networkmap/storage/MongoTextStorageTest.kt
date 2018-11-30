package io.cordite.networkmap.storage

import com.mongodb.reactivestreams.client.MongoClient
import com.mongodb.reactivestreams.client.MongoClients
import io.cordite.networkmap.storage.mongo.MongoStorage
import io.cordite.networkmap.storage.mongo.MongoTextStorage
import io.cordite.networkmap.utils.catch
import io.cordite.networkmap.utils.onSuccess
import io.vertx.ext.unit.TestContext
import io.vertx.ext.unit.junit.VertxUnitRunner
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(VertxUnitRunner::class)
class MongoTextStorageTest {
  companion object {
    private val dbDirectory = createTempDir()

    private lateinit var mongoClient: MongoClient

    @JvmStatic
    @BeforeClass
    fun beforeClass() {
      mongoClient = MongoClients.create(MongoStorage.startEmbeddedDatabase(dbDirectory, "sa", "admin"))
    }

    @JvmStatic
    @AfterClass
    fun afterClass() {
      mongoClient.close()
    }
  }

  @Test
  fun testStorage(context: TestContext) {
    val async = context.async()
    MongoTextStorage(mongoClient).apply {
      this.put("hello", "world")
        .compose {
          this.get("hello")
        }
        .onSuccess {
          context.assertEquals("world", it)
        }
        .onSuccess { async.complete() }
        .catch { context.fail(it) }
    }
  }
}