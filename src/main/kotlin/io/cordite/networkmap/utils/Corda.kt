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

import io.cordite.networkmap.storage.file.NetworkParameterInputsStorage
import net.corda.core.node.services.AttachmentId

fun String.toWhiteList(): Map<String, List<AttachmentId>> {
  return this.lines().parseToWhitelistPairs().groupBy({it -> it.first}, {it -> it.second})
}

fun List<String>.parseToWhitelistPairs(): List<Pair<String, AttachmentId>> {
  return map { it.trim() }
    .filter { it.isNotEmpty() }
    .map { row -> row.split(":") } // simple parsing for the whitelist
    .mapIndexed { index, row ->
      if (row.size != 2) {
        NetworkParameterInputsStorage.log.error("malformed whitelist entry on line $index - expected <class>:<attachment id>")
        null
      } else {
        row
      }
    }
    .mapNotNull {
      // if we have an attachment id, try to parse it
      it?.let {
        try {
          it[0] to AttachmentId.parse(it[1])
        } catch (err: Throwable) {
          NetworkParameterInputsStorage.log.error("failed to parse attachment nodeKey", err)
          null
        }
      }
    }
}

fun List<Pair<String, AttachmentId>>.toWhitelistText(): String {
  return this.joinToString("\n") { it.first + ':' + it.second.toString() }
}


fun String.toWhitelistPairs(): List<Pair<String, AttachmentId>> {
  return this.lines().parseToWhitelistPairs()
}
