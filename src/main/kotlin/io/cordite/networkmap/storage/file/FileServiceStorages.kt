package io.cordite.networkmap.storage.file

import io.cordite.networkmap.service.ServiceStorages
import io.cordite.networkmap.storage.Storage
import io.cordite.networkmap.utils.all
import io.cordite.networkmap.utils.mapUnit
import io.vertx.core.Future
import io.vertx.core.Vertx
import net.corda.nodeapi.internal.SignedNodeInfo
import net.corda.nodeapi.internal.crypto.CertificateAndKeyPair
import net.corda.nodeapi.internal.network.ParametersUpdate
import net.corda.nodeapi.internal.network.SignedNetworkParameters
import java.io.File

class FileServiceStorages(vertx: Vertx, dbDirectory: File) : ServiceStorages() {
  override val certAndKeys : Storage<CertificateAndKeyPair> = CertificateAndKeyPairStorage(vertx, dbDirectory)
  val input = NetworkParameterInputsStorage(dbDirectory, vertx)
  override val nodeInfo : Storage<SignedNodeInfo> = SignedNodeInfoStorage(vertx, dbDirectory)
  override val networkParameters : Storage<SignedNetworkParameters> = SignedNetworkParametersStorage(vertx, dbDirectory)
  override val parameterUpdate : Storage<ParametersUpdate> = ParametersUpdateStorage(vertx, dbDirectory)
  override val text = TextStorage(vertx, dbDirectory)

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