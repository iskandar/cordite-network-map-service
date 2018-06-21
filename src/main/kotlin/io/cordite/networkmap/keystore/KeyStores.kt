package io.cordite.networkmap.keystore

import io.cordite.networkmap.storage.CertificateAndKeyPairStorage
import io.vertx.core.buffer.Buffer
import io.vertx.core.net.JksOptions
import net.corda.nodeapi.internal.crypto.CertificateAndKeyPair
import net.corda.nodeapi.internal.crypto.X509KeyStore
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.security.KeyStore

fun File.toX509KeyStore(password: String) : X509KeyStore {
  val input = FileInputStream(this)
  val keystore = KeyStore.getInstance(KeyStore.getDefaultType())
  keystore.load(input, password.toCharArray())
  return X509KeyStore(keystore, password)
}

fun KeyStore.toJksOptions(keyStorePassword: String) : JksOptions {
  val buffer = ByteArrayOutputStream().use { os ->
    store(os, keyStorePassword.toCharArray())
    Buffer.buffer(os.toByteArray())
  }
  val jksOptions = JksOptions()
  jksOptions.password = keyStorePassword
  jksOptions.value = buffer
  return jksOptions
}

fun CertificateAndKeyPair.toKeyStore(password: String) : KeyStore {
  val passwordCharArray = password.toCharArray()
  val ks = KeyStore.getInstance("JKS")
  ks.load(null, null)
  ks.setKeyEntry(CertificateAndKeyPairStorage.DEFAULT_KEY_ALIAS, keyPair.private, passwordCharArray, arrayOf(certificate))
  ks.setCertificateEntry(CertificateAndKeyPairStorage.DEFAULT_CERT_ALIAS, certificate)
  return ks
}