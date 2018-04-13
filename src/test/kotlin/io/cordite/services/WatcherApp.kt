package io.cordite.services;

import io.cordite.services.utils.DirectoryDigest
import io.cordite.services.utils.Options
import io.cordite.services.utils.scheduleBlocking
import io.cordite.services.utils.toFile
import io.vertx.core.Vertx

// Just a standalone test app for checking the behaviour of stuff
fun main(args: Array<String>) {
  val options = Options()
  val portOption = options.addOption("port", "8080", "web port")
  val notaryDirectory = options.addOption("notary.dir", "notary-certificates", "notary cert directory")
  options.printOptions()
  val vertx = Vertx.vertx()
  val dd = DirectoryDigest("./test-certificates".toFile(), ".*\\.jks".toRegex())
  scheduleDigest(dd, vertx) {
    println(it)
  }
}

fun scheduleDigest(dd: DirectoryDigest, vertx: Vertx, fnChange: (hash: String) -> Unit) = scheduleDigest("", dd, vertx, fnChange)

fun scheduleDigest(lastHash: String, dd: DirectoryDigest, vertx: Vertx, fnChange: (hash: String) -> Unit) {
  vertx.scheduleBlocking(2000) {
    val hash = dd.digest()
    if (lastHash != hash) {
      vertx.runOnContext { fnChange(hash) }
    }
    scheduleDigest(hash, dd, vertx, fnChange)
  }
}


