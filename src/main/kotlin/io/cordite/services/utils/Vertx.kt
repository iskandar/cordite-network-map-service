package io.cordite.services.utils

import io.cordite.services.NetworkMapApp
import io.netty.handler.codec.http.HttpHeaderValues
import io.netty.handler.codec.http.HttpResponseStatus
import io.vertx.core.Vertx
import io.vertx.core.http.HttpHeaders
import io.vertx.core.json.Json
import io.vertx.ext.web.RoutingContext
import net.corda.core.utilities.loggerFor

private val logger = loggerFor<NetworkMapApp>()

fun RoutingContext.end(text: String) {
  val length = text.length
  response().apply {
    putHeader(HttpHeaders.CONTENT_LENGTH, length.toString())
    putHeader(HttpHeaders.CONTENT_TYPE, HttpHeaderValues.TEXT_PLAIN)
    end(text)
  }
}

fun RoutingContext.handleExceptions(fn: RoutingContext.() -> Unit) {
  try {
    this.fn()
  } catch (err: Throwable) {
    logger.error("web request failed", err)
    response()
        .setStatusCode(HttpResponseStatus.INTERNAL_SERVER_ERROR.code())
        .setStatusMessage(err.message)
        .end()
  }
}

fun <T: Any> RoutingContext.end(obj: T) {
  val result = Json.encode(obj)
  response().apply {
    putHeader(HttpHeaders.CONTENT_TYPE, HttpHeaderValues.APPLICATION_JSON)
    putHeader(HttpHeaders.CONTENT_LENGTH, result.length.toString())
    end(result)
  }
}

fun Vertx.scheduleBlocking(delay: Long, fn: () -> Unit) {
  this.setTimer(delay) {
    this.executeBlocking<Unit>({ fn() }, {})
  }
}