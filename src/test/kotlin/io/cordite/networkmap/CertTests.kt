package io.cordite.networkmap

import io.cordite.networkmap.keystore.toX509KeyStore
import org.junit.Test
import java.io.File
import java.nio.file.Path

operator fun Path.div(other: String): Path = resolve(other)
operator fun File.div(other: String) : File = File(this, other)
class CertTests {
  companion object {
    val certificateDirectory = File("./test-certificates")
    val nodeKeyStorePath = certificateDirectory / "nodekeystore.jks"
    val password = "cordacadevpass"
  }
  @Test
  fun test1() {
    println(System.getProperty("user.dir"))
    nodeKeyStorePath.toX509KeyStore(password)
  }
}

