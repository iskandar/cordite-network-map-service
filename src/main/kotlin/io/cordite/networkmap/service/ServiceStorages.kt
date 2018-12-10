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

import com.mongodb.reactivestreams.client.MongoClient
import io.cordite.networkmap.storage.file.CertificateAndKeyPairStorage
import io.cordite.networkmap.storage.file.NetworkParameterInputsStorage
import io.cordite.networkmap.storage.file.TextStorage
import io.cordite.networkmap.storage.mongo.*
import io.cordite.networkmap.utils.all
import io.cordite.networkmap.utils.mapUnit
import io.vertx.core.Future
import io.vertx.core.Vertx
import java.io.File

class ServiceStorages(
  private val vertx: Vertx,
  private val dbDirectory: File,
  mongoClient: MongoClient,
  mongoDatabase: String
) {
  val certAndKeys = CertificateAndKeyPairStorage(vertx, dbDirectory)
  val input = NetworkParameterInputsStorage(dbDirectory, vertx)
  val networkMap = SignedNetworkMapStorage(mongoClient, mongoDatabase)
  val nodeInfo = SignedNodeInfoStorage(mongoClient, mongoDatabase)
  val networkParameters = SignedNetworkParametersStorage(mongoClient, mongoDatabase)
  val parameterUpdate = ParametersUpdateStorage(mongoClient, mongoDatabase)
  val text = MongoTextStorage(mongoClient, mongoDatabase)

  fun setupStorage(): Future<Unit> {
    return all(
      input.makeDirs(),
      networkParameters.migrate(io.cordite.networkmap.storage.file.SignedNetworkParametersStorage(vertx, dbDirectory)),
      parameterUpdate.migrate(io.cordite.networkmap.storage.file.ParametersUpdateStorage(vertx, dbDirectory)),
      networkMap.migrate(io.cordite.networkmap.storage.file.SignedNetworkMapStorage(vertx, dbDirectory)),
      text.migrate(TextStorage(vertx, dbDirectory)),
      nodeInfo.migrate(io.cordite.networkmap.storage.file.SignedNodeInfoStorage(vertx, dbDirectory)),
      certAndKeys.makeDirs()
    ).mapUnit()
  }

}