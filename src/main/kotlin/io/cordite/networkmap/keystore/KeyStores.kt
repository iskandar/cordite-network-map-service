package io.cordite.networkmap.keystore

import net.corda.nodeapi.internal.crypto.X509KeyStore
import java.io.File
import java.io.FileInputStream
import java.security.KeyStore

fun File.toX509KeyStore(password: String) : X509KeyStore {
  val input = FileInputStream(this)
  val keystore = KeyStore.getInstance(KeyStore.getDefaultType())
  keystore.load(input, password.toCharArray())
  return X509KeyStore(keystore, password)
}