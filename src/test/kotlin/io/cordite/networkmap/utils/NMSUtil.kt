package io.cordite.networkmap.utils

import io.vertx.core.Future
import io.vertx.core.Future.future
import io.vertx.core.Vertx
import java.time.Duration

class NMSUtil {
	companion object {
		fun waitForNMSUpdate(vertx: Vertx): Future<Long> {
			val extraWait = Duration.ofSeconds(5) // to give a bit more time for CPU starved environments to catchup
			val milliseconds = (NETWORK_PARAM_UPDATE_DELAY + CACHE_TIMEOUT + extraWait).toMillis()
			return future<Long>().apply { vertx.setTimer(milliseconds, this::complete) }
		}
	}
}