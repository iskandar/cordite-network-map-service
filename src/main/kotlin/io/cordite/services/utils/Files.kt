package io.cordite.services.utils

import java.io.File
import java.nio.file.Paths

fun String.toPath() = Paths.get(this)
fun String.toFile() = File(this)

fun File.getFiles(pattern: String): Sequence<File> = getFiles(pattern.toRegex())
fun File.getFiles(re: Regex): Sequence<File> {
  return this.walk()
      .filter {
        it.isFile && it.name.matches(re)
      }
}

operator fun File.div(rhs: String) : File {
  return File(this, rhs)
}
