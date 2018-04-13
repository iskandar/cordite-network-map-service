package io.cordite.services.utils

import toHex
import java.io.File
import java.io.FileInputStream
import java.io.SequenceInputStream
import java.security.DigestInputStream
import java.security.MessageDigest

class DirectoryDigest(private val path: File,
                      private val regex: Regex = ".*".toRegex(),
                      private val digestAlgorithm : String = "MD5") {

  fun digest(): String {
    val fileStreams = path.getFiles(regex).map { FileInputStream(it) }

    return DigestInputStream(
        SequenceInputStream(fileStreams.toEnumeration()),
        MessageDigest.getInstance(digestAlgorithm)).use {
      while(it.read() > -1) {}
      it.messageDigest.digest().toHex()
    }
  }
}


