package io.cordite.services

import io.vertx.core.AbstractVerticle
import io.vertx.core.Future
import io.vertx.core.Vertx
import io.vertx.ext.web.Router

class NetworkMapApp(val host: String, val port: Int) : AbstractVerticle() {
  companion object {
    val logger = loggerFor(NetworkMapApp::class)
    val webroot = "/network-map"
    @JvmStatic
    fun main(args: Array<String>) {
      val port = (System.getProperty("port") ?: "8080").toInt()
      val host = (System.getProperty("host") ?: "localhost")
      NetworkMapApp(host, port).deploy()
    }
  }

  private fun deploy() {
    Vertx.vertx().deployVerticle(this)
  }

  override fun start(startFuture: Future<Void>) {
    logger.info("starting network map with host: $host port: $port")
    val router = Router.router(vertx)
    router.get("$webroot").handler { it.end("hello") }

    vertx
        .createHttpServer()
        .requestHandler(router::accept)
        .listen(port, host) {
          if (it.failed()) {
            logger.error("failed to startup", it.cause())
            startFuture.fail(it.cause())
          } else {
            logger.info("networkmap service started on http://$host:$port$webroot")
            startFuture.complete()
          }
        }
  }
}