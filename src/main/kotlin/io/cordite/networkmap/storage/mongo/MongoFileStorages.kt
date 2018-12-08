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

import com.mongodb.reactivestreams.client.MongoDatabase
import io.cordite.networkmap.serialisation.deserializeOnContext
import net.corda.nodeapi.internal.SignedNodeInfo

class SignedNodeInfoStorage(database: MongoDatabase, bucketName: String = DEFAULT_BUCKET_NAME)
  : AbstractMongoFileStorage<SignedNodeInfo>(bucketName, database) {
  companion object {
    const val DEFAULT_BUCKET_NAME = "nodes"
  }

  override fun deserialize(data: ByteArray): SignedNodeInfo {
    return data.deserializeOnContext()
  }
}