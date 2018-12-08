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