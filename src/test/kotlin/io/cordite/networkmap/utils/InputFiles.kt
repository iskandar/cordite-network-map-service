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
package io.cordite.networkmap.utils

import io.cordite.networkmap.storage.NetworkParameterInputsStorage
import io.cordite.networkmap.storage.SignedNodeInfoStorage
import java.io.File
import java.nio.file.Files

fun setupDefaultInputFiles(directory: File) {
  val inputs = File(directory, NetworkParameterInputsStorage.DEFAULT_DIR_NAME)
  inputs.mkdirs()
  Files.copy("${SAMPLE_INPUTS}whitelist.txt".toPath(), File(inputs, NetworkParameterInputsStorage.WHITELIST_NAME).toPath())
  copyFolder("${SAMPLE_INPUTS}validating".toPath(), File(inputs, NetworkParameterInputsStorage.DEFAULT_DIR_VALIDATING_NOTARIES).toPath())
  copyFolder("${SAMPLE_INPUTS}non-validating".toPath(), File(inputs, NetworkParameterInputsStorage.DEFAULT_DIR_NON_VALIDATING_NOTARIES).toPath())
}

fun setupDefaultNodes(directory: File) {
  val nodes = File(directory, SignedNodeInfoStorage.DEFAULT_CHILD_DIR)
  nodes.mkdirs()
  copyFolder(SAMPLE_NODES.toPath(), nodes.toPath())
}
