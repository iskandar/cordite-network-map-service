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
package io.cordite.networkmap.service

import io.bluebank.braid.core.logging.loggerFor
import io.cordite.networkmap.storage.file.*
import io.cordite.networkmap.utils.all
import io.cordite.networkmap.utils.catch
import io.cordite.networkmap.utils.mapUnit
import io.cordite.networkmap.utils.sign
import io.vertx.core.Future
import io.vertx.core.Vertx
import net.corda.core.crypto.SecureHash
import net.corda.core.node.NetworkParameters
import net.corda.nodeapi.internal.crypto.CertificateAndKeyPair
import net.corda.nodeapi.internal.network.ParametersUpdate
import net.corda.nodeapi.internal.network.SignedNetworkParameters
import java.io.File

class ServiceStorages(
  vertx: Vertx,
  dbDirectory: File
) {
  companion object {
    private val logger = loggerFor<ServiceStorages>()
    const val CURRENT_PARAMETERS = "current-parameters" // key for current network parameters hash
    const val NEXT_PARAMS_UPDATE = "next-params-update" // key for next params update hash
  }

  val certAndKeys = CertificateAndKeyPairStorage(vertx, dbDirectory)
  val input = NetworkParameterInputsStorage(dbDirectory, vertx)
  val nodeInfo = SignedNodeInfoStorage(vertx, dbDirectory)
  val networkParameters = SignedNetworkParametersStorage(vertx, dbDirectory)
  private val parameterUpdate = ParametersUpdateStorage(vertx, dbDirectory)
  val text = TextStorage(vertx, dbDirectory)

  fun setupStorage(): Future<Unit> {
    return all(
      certAndKeys.makeDirs(),
      input.makeDirs(),
      nodeInfo.makeDirs(),
      networkParameters.makeDirs(),
      parameterUpdate.makeDirs(),
      text.makeDirs()
      /*, migrate()*/
    ).mapUnit()
  }

  // TODO: Re-enable when database solution has stabilised
//  private fun migrate(): Future<Unit> {
//    return all(
//      networkParameters.migrate(io.cordite.networkmap.storage.file.SignedNetworkParametersStorage(vertx, dbDirectory)),
//      parameterUpdate.migrate(io.cordite.networkmap.storage.file.ParametersUpdateStorage(vertx, dbDirectory)),
//      // TODO: add something to clear down cached networkmaps on the filesystem from previous versions
//      text.migrate(TextStorage(vertx, dbDirectory)),
//      nodeInfo.migrate(io.cordite.networkmap.storage.file.SignedNodeInfoStorage(vertx, dbDirectory)),
//      certAndKeys.migrate(io.cordite.networkmap.storage.file.CertificateAndKeyPairStorage(vertx, dbDirectory))
//    ).mapUnit()
//  }

  fun getCurrentNetworkParametersHash(): Future<SecureHash> {
    return text.get(CURRENT_PARAMETERS)
      .map { SecureHash.parse(it) as SecureHash }
      .catch { err ->
        logger.error("failed to get current networkparameters hash", err)
      }
  }

  fun getNetworkParameters(hash: SecureHash): Future<NetworkParameters> {
    return networkParameters.get(hash.toString()).map { it.verified() }
  }

  fun getCurrentNetworkParameters(): Future<NetworkParameters> {
    return getCurrentSignedNetworkParameters().map { it.verified() }
      .catch { err ->
        logger.error("failed to get current network parameters", err)
      }
  }

  fun getCurrentSignedNetworkParameters(): Future<SignedNetworkParameters> {
    return text.get(CURRENT_PARAMETERS).compose { key -> networkParameters.get(key) }
      .catch { err ->
        logger.error("failed to get current signed network parameters", err)
      }
  }

  fun storeCurrentParametersHash(hash: SecureHash): Future<SecureHash> {
    logger.info("storing current network parameters $hash")
    return text.put(CURRENT_PARAMETERS, hash.toString()).map { hash }
  }

  fun storeNextParametersUpdate(parametersUpdate: ParametersUpdate): Future<Unit> {
    logger.info("storing next parameter update $parametersUpdate")
    return parameterUpdate.put(NEXT_PARAMS_UPDATE, parametersUpdate)
  }

  fun resetNextParametersUpdate(): Future<Unit> {
    logger.info("resetting next parameter update")
    return parameterUpdate.delete(NEXT_PARAMS_UPDATE)
  }

  fun getParameterUpdateOrNull(): Future<ParametersUpdate?> {
    return parameterUpdate.getOrNull(NEXT_PARAMS_UPDATE)
  }

  fun storeNetworkParameters(newParams: NetworkParameters, certs: CertificateAndKeyPair): Future<SecureHash> {
    val signed = newParams.sign(certs)
    val hash = signed.raw.hash
    logger.info("storing network parameters $hash with values $newParams")
    return networkParameters.put(hash.toString(), signed).map { hash }
  }

}