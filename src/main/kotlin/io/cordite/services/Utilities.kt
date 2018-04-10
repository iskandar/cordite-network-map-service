package io.cordite.services

import io.vertx.ext.web.RoutingContext
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.reflect.KClass

fun <T : Any> loggerFor(clazz : KClass<T>): Logger = LoggerFactory.getLogger(clazz.java)
fun RoutingContext.end(text: String) {
  val length = text.length;
  response().apply {
    putHeader(io.vertx.core.http.HttpHeaders.CONTENT_LENGTH, length.toString())
    end(text)
  }
}