package io.cordite.services.utils

import io.cordite.services.NetworkMapApp
import io.netty.handler.codec.http.HttpHeaderValues
import io.netty.handler.codec.http.HttpResponseStatus
import io.vertx.core.Future
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

fun <T : Any> RoutingContext.end(obj: T) {
  val result = Json.encode(obj)
  response().apply {
    putHeader(HttpHeaders.CONTENT_TYPE, HttpHeaderValues.APPLICATION_JSON)
    putHeader(HttpHeaders.CONTENT_LENGTH, result.length.toString())
    end(result)
  }
}

fun RoutingContext.end(err: Throwable) {
  response().apply {
    statusCode = 500
    statusMessage = err.message
    end()
  }
}

fun Vertx.scheduleBlocking(delay: Long, fn: () -> Unit) {
  this.setTimer(delay) {
    this.executeBlocking<Unit>({ fn() }, {})
  }
}

fun <T> Vertx.executeBlocking(fn: () -> T): Future<T> {
  val result = Future.future<T>()
  this.executeBlocking({ f: Future<T> ->
    try {
      f.complete(fn())
    } catch (err: Throwable) {
      f.fail(err)
    }
  }) {
    result.completer().handle(it)
  }
  return result
}

fun <T> Future<T>.onSuccess(fn: (T) -> Unit): Future<T> {
  val result = Future.future<T>()
  setHandler {
    try {
      if (it.succeeded()) {
        fn(it.result())
      }
      result.completer().handle(it)
    } catch (err: Throwable) {
      result.fail(err)
    }
  }
  return result
}

fun <T> Future<T>.catch(fn: (Throwable) -> Unit): Future<T> {
  val result = Future.future<T>()
  setHandler {
    try {
      if (it.failed()) {
        fn(it.cause())
      }
      result.completer().handle(it)
    } catch (err: Throwable) {
      result.fail(err)
    }
  }
  return result
}