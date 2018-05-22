package io.cordite.services.utils

import com.google.common.collect.Lists
import io.cordite.services.NetworkMapApp
import io.netty.handler.codec.http.HttpHeaderValues
import io.netty.handler.codec.http.HttpResponseStatus
import io.vertx.core.Future
import io.vertx.core.Future.future
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.core.file.FileSystem
import io.vertx.core.http.HttpHeaders
import io.vertx.core.json.Json
import io.vertx.ext.web.RoutingContext
import net.corda.core.utilities.loggerFor
import java.util.concurrent.atomic.AtomicInteger

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

fun <T> List<Future<T>>.all() : Future<List<T>> {
  val results = Lists.newLinkedList<T>()
  val fResult = future<List<T>>()
  val countdown = AtomicInteger(this.size)
  this.forEach { future ->
    future.setHandler { ar ->
      when {
        ar.succeeded() && fResult.succeeded() -> {
          logger.error("received a successful result in List<Future<T>>.all after all futures where apparently completed!")
        }
        ar.succeeded() -> {
          results.addLast(ar.result())
          if (countdown.decrementAndGet() == 0) {
            fResult.complete(results)
          }
        }
        else -> {
          // we've got a failed future - report it
          fResult.fail(ar.cause())
        }
      }
    }
  }
  return fResult
}

fun FileSystem.mkdirs(path: String) : Future<Void> {
  return withFuture { mkdirs(path, it.completer()) }
}

fun FileSystem.readFile(path: String) : Future<Buffer> {
  return withFuture { readFile(path, it.completer()) }
}

fun FileSystem.readDir(path: String) : Future<List<String>> {
  return withFuture { readDir(path, it.completer())}
}

private fun <T> withFuture(fn: (Future<T>) -> Unit) : Future<T> {
  val result = future<T>()
  fn(result)
  return result
}

fun <T> Future<*>.composeWithFuture(fn: Future<T>.() -> Unit) : Future<T> {
  return this.compose {
    val result = future<T>()
    result.fn()
    result
  }
}

