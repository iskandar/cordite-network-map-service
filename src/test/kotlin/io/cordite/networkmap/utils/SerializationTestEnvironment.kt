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

import io.cordite.networkmap.serialisation.SerializationEnvironment
import net.corda.core.serialization.internal.nodeSerializationEnv
import net.corda.core.utilities.loggerFor
import net.corda.testing.internal.createTestSerializationEnv

class SerializationTestEnvironment : SerializationEnvironment() {
  companion object {
    val log = loggerFor<SerializationTestEnvironment>()
    fun init() {
      SerializationTestEnvironment().setup()
    }
  }

  override fun initialiseSerialisationEnvironment() {
    if (nodeSerializationEnv == null) {
      nodeSerializationEnv = createSerializationEnvironment()
    } else {
      log.warn("SERIALIZATION ENV ALREADY SET")
    }
  }

  override fun createSerializationEnvironment(): net.corda.core.serialization.internal.SerializationEnvironment {
    return createTestSerializationEnv()
  }
}