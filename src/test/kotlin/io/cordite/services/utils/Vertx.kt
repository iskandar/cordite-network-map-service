package io.cordite.services.utils

import io.vertx.core.Vertx

fun Vertx.scheduleBlocking(delay: Long, fn: () -> Unit) {
  this.setTimer(delay) {
    this.executeBlocking<Unit>({ fn() }, {})
  }
}