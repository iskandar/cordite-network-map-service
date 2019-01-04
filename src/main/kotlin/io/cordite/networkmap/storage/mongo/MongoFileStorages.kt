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

import com.mongodb.reactivestreams.client.MongoClient
import io.cordite.networkmap.serialisation.deserializeOnContext
import net.corda.nodeapi.internal.SignedNodeInfo
import net.corda.nodeapi.internal.network.ParametersUpdate
import net.corda.nodeapi.internal.network.SignedNetworkMap
import net.corda.nodeapi.internal.network.SignedNetworkParameters

class SignedNodeInfoStorage(client: MongoClient, databaseName: String, bucketName: String = DEFAULT_BUCKET_NAME)
  : AbstractMongoFileStorage<SignedNodeInfo>(client, databaseName, bucketName) {
  companion object {
    const val DEFAULT_BUCKET_NAME = "nodes"
  }

  override fun deserialize(data: ByteArray): SignedNodeInfo {
    return data.deserializeOnContext()
  }
}

class ParametersUpdateStorage(client: MongoClient, databaseName: String, bucketName: String = DEFAULT_BUCKET_NAME)
  : AbstractMongoFileStorage<ParametersUpdate>(client, databaseName, bucketName){
  companion object {
    const val DEFAULT_BUCKET_NAME = "parameters-update"
  }

  override fun deserialize(data: ByteArray): ParametersUpdate {
    return data.deserializeOnContext()
  }
}


class SignedNetworkMapStorage(client: MongoClient, databaseName: String, bucketName: String = DEFAULT_BUCKET_NAME)
  : AbstractMongoFileStorage<SignedNetworkMap>(client, databaseName, bucketName){
  companion object {
    const val DEFAULT_BUCKET_NAME = "network-map"
  }

  override fun deserialize(data: ByteArray): SignedNetworkMap {
    return data.deserializeOnContext()
  }
}

class SignedNetworkParametersStorage(client: MongoClient, databaseName: String, bucketName: String = DEFAULT_BUCKET_NAME)
  : AbstractMongoFileStorage<SignedNetworkParameters>(client, databaseName, bucketName){
  companion object {
    const val DEFAULT_BUCKET_NAME = "signed-network-parameters"
  }

  override fun deserialize(data: ByteArray): SignedNetworkParameters {
    return data.deserializeOnContext()
  }
}

