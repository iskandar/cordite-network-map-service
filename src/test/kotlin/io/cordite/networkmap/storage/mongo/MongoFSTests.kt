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
package io.cordite.networkmap.storage.mongo

import com.mongodb.client.model.Filters
import com.mongodb.reactivestreams.client.MongoClient
import com.mongodb.reactivestreams.client.MongoClients
import com.mongodb.reactivestreams.client.gridfs.GridFSBuckets
import io.cordite.networkmap.netty.decodeString
import io.cordite.networkmap.storage.EmbeddedMongo
import io.cordite.networkmap.storage.mongo.serlalisation.asAsyncInputStream
import io.cordite.networkmap.storage.mongo.serlalisation.asAsyncOutputStream
import io.cordite.networkmap.utils.catch
import io.cordite.networkmap.utils.onSuccess
import io.netty.buffer.PooledByteBufAllocator
import io.vertx.core.buffer.Buffer
import io.vertx.ext.unit.TestContext
import io.vertx.ext.unit.junit.VertxUnitRunner
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.charset.StandardCharsets
import kotlin.test.assertNull


@RunWith(VertxUnitRunner::class)
class MongoFSTests {
  companion object {
    private val dbDirectory = createTempDir()

    private lateinit var mongoClient: MongoClient
    private lateinit var mongodb: EmbeddedMongo

    @JvmStatic
    @BeforeClass
    fun beforeClass() {
      mongodb = MongoStorage.startEmbeddedDatabase(dbDirectory)
      mongoClient = MongoClients.create(mongodb.connectionString)
    }

    @JvmStatic
    @AfterClass
    fun afterClass() {
      mongoClient.close()
      mongodb.close()
    }
  }

  @Test
  fun `that ByteBuf can resize`(context: TestContext) {
    val initSize = 5120
    val finalSize = 16777216
    val buffer1 = PooledByteBufAllocator.DEFAULT.buffer(initSize) // bytes
    val bytes = ByteArray(finalSize).apply { fill(0) }
    buffer1.writeBytes(bytes)
    val buffer2 = PooledByteBufAllocator.DEFAULT.buffer(initSize)
    buffer2.writeBytes(buffer1)
    context.assertEquals(16777216,  buffer2.writerIndex())
  }

  @Test
  fun `that we can create and delete buckets`(context: TestContext) {
    val async = context.async()
    val db = mongoClient.getDatabase("db")
    val bucket = GridFSBuckets.create(db, "my-directory")
    val file = "test.txt"
    val contents = "0123456789".repeat(16384)
    val size = contents.length.toLong()
    val inputBuffer = Buffer.buffer(contents)
    bucket.uploadFromStream(file, inputBuffer.byteBuf.asAsyncInputStream())
      .toFuture()
      .compose {
        val buffer = PooledByteBufAllocator.DEFAULT.buffer()
        bucket.downloadToStream(file, buffer.asAsyncOutputStream())
          .toFuture()
          .onSuccess {
            println("read back $it bytes into buffer $buffer")
            context.assertEquals(size, it)
          }.map {
            buffer
          }
      }.onSuccess {
        val msg = it.decodeString(StandardCharsets.UTF_8)
        println(msg)
      }
      .compose {
        bucket.find(Filters.eq("filename", file)).first().toFuture()
      }
      .compose {
        val objectId = it.id
        bucket.delete(objectId).toFuture()
      }
      .compose {
        bucket.find(Filters.eq("filename", file)).first().toFuture()
      }
      .onSuccess {
        assertNull(it) // the file should've been deleted
        async.complete()
      }
      .catch { context.fail(it) }
  }
}


