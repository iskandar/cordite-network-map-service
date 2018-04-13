package io.cordite.services

import io.cordite.services.keystore.toX509KeyStore
import net.corda.nodeapi.internal.crypto.X509KeyStore
import net.corda.nodeapi.internal.crypto.X509Utilities
import org.junit.Test
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths

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
    nodeKeyStorePath.toX509KeyStore(password).examine(X509Utilities.CORDA_CLIENT_CA)
  }

  private fun X509KeyStore.examine(alias: String) {
    val aliases = this.aliases().asSequence().toList()
    val certificate = getCertificate(alias)
    val issuerPrincipal = certificate.issuerX500Principal
    val subjectPrincipal = certificate.subjectX500Principal
    val key = certificate.publicKey
  }
}

