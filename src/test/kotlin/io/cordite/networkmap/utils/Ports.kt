package io.cordite.networkmap.utils

import java.net.ServerSocket

fun getFreePort(): Int {
  return ServerSocket(0).use { it.localPort }
}

