package io.cordite.services

import io.vertx.core.AbstractVerticle
import io.vertx.core.Future
import io.vertx.core.Vertx
import io.vertx.ext.web.Router

class NetworkMapApp(private val port: Int) : AbstractVerticle() {
  companion object {
    val logger = loggerFor(NetworkMapApp::class)
    const val WEB_ROOT = "/network-map"

    @JvmStatic
    fun main(args: Array<String>) {
      val port = getPort()
      val host = (System.getProperty("host") ?: "localhost")
      NetworkMapApp(port).deploy()
    }

    private fun getPort() : Int {
      return getVariable("port", "8080").toInt()
    }

    private fun getVariable(name: String, default: String) : String {
      return (System.getenv(name) ?: System.getProperty(name) ?: default)
    }
  }

  private fun deploy() {
    Vertx.vertx().deployVerticle(this)
  }

  override fun start(startFuture: Future<Void>) {
    logger.info("starting network map with port: $port")

    val router = Router.router(vertx)
    router.get("/").handler {
      it.end("hello, v3")
    }
    router.get(WEB_ROOT).handler {
      it.end("hello, v3")
    }

    vertx
        .createHttpServer()
        .requestHandler(router::accept)
        .listen(port) {
          if (it.failed()) {
            logger.error("failed to startup", it.cause())
            startFuture.fail(it.cause())
          } else {
            logger.info("networkmap service started on http://localhost:$port$WEB_ROOT")
            startFuture.complete()
          }
        }
  }
}