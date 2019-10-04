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


import com.fasterxml.jackson.core.type.TypeReference
import io.cordite.networkmap.service.SimpleNotaryInfo
import io.vertx.core.Future
import io.vertx.core.Future.future
import io.vertx.core.Vertx
import io.vertx.core.http.HttpClient
import io.vertx.core.json.Json
import net.corda.core.node.NetworkParameters
import net.corda.core.utilities.loggerFor
import java.time.Duration

class NMSUtil {
	companion object {
		val log = loggerFor<NMSUtil>()
		fun waitForNMSUpdate(vertx: Vertx): Future<Long> {
			val extraWait = Duration.ofSeconds(15) // to give a bit more time for CPU starved environments to catchup
			val milliseconds = (NETWORK_PARAM_UPDATE_DELAY + CACHE_TIMEOUT + extraWait).toMillis()
			return future<Long>().apply { vertx.setTimer(milliseconds, this::complete) }
		}
	}
}