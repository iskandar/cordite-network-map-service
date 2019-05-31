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

import net.corda.testing.driver.PortAllocation
import java.io.IOException
import java.net.ServerSocket
import java.util.*

fun getFreePort(): Int {
  return ServerSocket(0).use { it.localPort }
}

class FreePortAllocation(val range: IntRange = 10_000 .. 30_000) : PortAllocation() {
  companion object {
    private const val RETRIES = 10
  }
  private val random = Random()
  private val span = range.last - range.first
  override fun nextPort(): Int {
    return generateSequence { random.nextDouble() }.map { it -> (range.first + (span * it)).toInt() }.take(RETRIES).firstOrNull() { port ->
      try {
        ServerSocket(port).use { true }
      } catch (err: IOException) {
        false
      }
    } ?: error("failed to locate an unused port with $RETRIES retries")
  }
}