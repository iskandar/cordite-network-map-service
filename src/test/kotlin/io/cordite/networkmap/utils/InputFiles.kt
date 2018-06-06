package io.cordite.networkmap.utils

import io.cordite.networkmap.storage.NetworkParameterInputsStorage
import java.io.File
import java.nio.file.Files

fun setupDefaultInputFiles(directory: File) {
  val inputs = File(directory, NetworkParameterInputsStorage.DEFAULT_DIR_NAME)
  inputs.mkdirs()
  Files.copy("${SAMPLE_INPUTS}whitelist.txt".toPath(), File(inputs, NetworkParameterInputsStorage.WHITELIST_NAME).toPath())
  copyFolder("${SAMPLE_INPUTS}validating".toPath(), File(inputs, NetworkParameterInputsStorage.DEFAULT_DIR_VALIDATING_NOTARIES).toPath())
  copyFolder("${SAMPLE_INPUTS}non-validating".toPath(), File(inputs, NetworkParameterInputsStorage.DEFAULT_DIR_NON_VALIDATING_NOTARIES).toPath())
}
