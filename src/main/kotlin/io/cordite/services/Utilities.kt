package io.cordite.services

import io.netty.handler.codec.http.HttpHeaderValues
import io.netty.handler.codec.http.HttpResponseStatus
import io.vertx.ext.web.RoutingContext
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.reflect.KClass
import io.vertx.core.http.HttpHeaders.CONTENT_TYPE
import io.vertx.core.http.HttpHeaders.CONTENT_LENGTH

fun <T : Any> loggerFor(clazz : KClass<T>): Logger = LoggerFactory.getLogger(clazz.java)
fun RoutingContext.end(text: String) {
  val length = text.length
  response().apply {
    putHeader(CONTENT_LENGTH, length.toString())
    putHeader(CONTENT_TYPE, HttpHeaderValues.TEXT_PLAIN)
    end(text)
  }
}

fun RoutingContext.handleExceptions(fn: RoutingContext.() -> Unit) {
  try {
    this.fn()
  } catch (err: Throwable) {
    response()
        .setStatusCode(HttpResponseStatus.INTERNAL_SERVER_ERROR.code())
        .setStatusMessage(err.message)
        .end()
  }
}