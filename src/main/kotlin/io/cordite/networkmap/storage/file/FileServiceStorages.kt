package io.cordite.networkmap.storage.file

import io.cordite.networkmap.service.ServiceStorages
import io.cordite.networkmap.storage.Storage
import io.cordite.networkmap.utils.NMSOptions
import io.cordite.networkmap.utils.all
import io.cordite.networkmap.utils.mapUnit
import io.vertx.core.Future
import io.vertx.core.Vertx
import net.corda.nodeapi.internal.SignedNodeInfo
import net.corda.nodeapi.internal.crypto.CertificateAndKeyPair
import net.corda.nodeapi.internal.network.ParametersUpdate
import net.corda.nodeapi.internal.network.SignedNetworkParameters

class FileServiceStorages(vertx: Vertx, nmsOptions: NMSOptions) : ServiceStorages() {
  override val certAndKeys : Storage<CertificateAndKeyPair> = CertificateAndKeyPairStorage(vertx, nmsOptions.dbDirectory)
  val input = NetworkParameterInputsStorage(vertx, nmsOptions.dbDirectory)
  override val nodeInfo : Storage<SignedNodeInfo> = SignedNodeInfoStorage(vertx, nmsOptions.dbDirectory)
  override val networkParameters : Storage<SignedNetworkParameters> = SignedNetworkParametersStorage(vertx, nmsOptions.dbDirectory)
  override val parameterUpdate : Storage<ParametersUpdate> = ParametersUpdateStorage(vertx, nmsOptions.dbDirectory)
  override val text = TextStorage(vertx, nmsOptions.dbDirectory)

  override fun setupStorage(): Future<Unit> {
    return all(
      (certAndKeys as CertificateAndKeyPairStorage).makeDirs(),
      input.makeDirs(),
      (nodeInfo as SignedNodeInfoStorage).makeDirs(),
      (networkParameters as SignedNetworkParametersStorage).makeDirs(),
      (parameterUpdate as ParametersUpdateStorage).makeDirs(),
      text.makeDirs()
    ).mapUnit()
  }
}