package io.cordite.networkmap.storage.mongo

import io.cordite.networkmap.service.ServiceStorages
import io.cordite.networkmap.service.StorageType
import io.cordite.networkmap.storage.Storage
import io.cordite.networkmap.utils.NMSOptions
import io.vertx.core.Future
import net.corda.nodeapi.internal.SignedNodeInfo
import net.corda.nodeapi.internal.crypto.CertificateAndKeyPair
import net.corda.nodeapi.internal.network.ParametersUpdate
import net.corda.nodeapi.internal.network.SignedNetworkParameters

class MongoServiceStorages(private val nmsOptions: NMSOptions) : ServiceStorages() {
  init {
    assert(nmsOptions.storageType == StorageType.MONGO) { "mongo service cannot be initiated with storage type set to ${nmsOptions.storageType}"}
  }

  private val mongoClient = MongoStorage.connect(nmsOptions)
  override val certAndKeys: Storage<CertificateAndKeyPair> = CertificateAndKeyPairStorage(mongoClient, nmsOptions.mongodDatabase)
  override val nodeInfo: Storage<SignedNodeInfo> = SignedNodeInfoStorage(mongoClient, nmsOptions.mongodDatabase)
  override val networkParameters: Storage<SignedNetworkParameters> = SignedNetworkParametersStorage(mongoClient, nmsOptions.mongodDatabase)
  override val parameterUpdate: Storage<ParametersUpdate> = ParametersUpdateStorage(mongoClient, nmsOptions.mongodDatabase)
  override val text: Storage<String> = MongoTextStorage(mongoClient, nmsOptions.mongodDatabase)

  override fun setupStorage(): Future<Unit> {
    return Future.succeededFuture()
  }
}