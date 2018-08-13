package io.cordite.networkmap.utils

import net.corda.core.crypto.Crypto
import java.security.PrivateKey
import java.util.*

object CryptoUtils {
  fun decodePEMPrivateKey(pem: String) : PrivateKey {
    val encodedPrivKey = Base64.getDecoder().decode(pem.lines().filter { !it.startsWith("---") }.joinToString(separator = ""))
    return Crypto.decodePrivateKey(encodedPrivKey)
  }
}