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

import com.mongodb.reactivestreams.client.MongoClient
import io.cordite.networkmap.storage.mongo.MongoTextStorage
import io.cordite.networkmap.utils.JunitMDCRule
import io.cordite.networkmap.utils.TestDatabase
import io.cordite.networkmap.utils.catch
import io.cordite.networkmap.utils.onSuccess
import io.vertx.ext.unit.TestContext
import io.vertx.ext.unit.junit.VertxUnitRunner
import org.junit.*
import org.junit.runner.RunWith

@RunWith(VertxUnitRunner::class)
class MongoTextStorageTest {
  companion object {

    private lateinit var mongoClient: MongoClient

    @JvmField
    @ClassRule
    val mdcClassRule = JunitMDCRule()

    @JvmStatic
    @BeforeClass
    fun beforeClass() {
      mongoClient = TestDatabase.createMongoClient()
    }

    @JvmStatic
    @AfterClass
    fun afterClass() {
      mongoClient.close()
    }
  }

  @JvmField
  @Rule
  val mdcRule = JunitMDCRule()


  @Test
  fun testStorage(context: TestContext) {
    val async = context.async()
    val key = "hello"
    val value = "world"
    MongoTextStorage(mongoClient, TestDatabase.createUniqueDBName()).apply {
      this.get("hello")
        .recover { this.put(key, value).map { value } }
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