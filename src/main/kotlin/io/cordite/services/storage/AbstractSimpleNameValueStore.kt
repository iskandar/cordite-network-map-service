package io.cordite.services.storage

import io.cordite.services.utils.DirectoryDigest
import io.cordite.services.utils.all
import io.vertx.core.Future
import io.vertx.core.Future.future
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import net.corda.core.serialization.deserialize
import net.corda.core.serialization.serialize
import java.io.File

abstract class AbstractSimpleNameValueStore<T : Any>(
  private val dir: File,
  protected val vertx: Vertx
) : Storage<T> {
    private val digest = DirectoryDigest(dir)

  companion object {
    inline fun <reified T : Any> deserialize(file: File, vertx: Vertx): Future<T> {
      val result = Future.future<Buffer>()
      vertx.fileSystem().readFile(file.absolutePath, result.completer())
      return result.map { it.bytes.deserialize<T>() }
    }

    inline fun <reified T : Any> serialize(value: T, file: File, vertx: Vertx) : Future<Unit> {
      val result = Future.future<Void>()
      vertx.fileSystem().writeFile(file.absolutePath, Buffer.buffer(value.serialize().bytes), result.completer())
      return result.map { Unit }
    }
  }

  fun makeDirs() : Future<Unit> {
    val result = future<Void>()
    vertx.fileSystem().mkdirs(dir.absolutePath, result.completer())
    return result.map { Unit }
  }

  override fun clear(): Future<Unit> {
    return getKeys()
      .compose { keys ->
        keys.map { key ->
          delete(key)
        }.all().map { Unit }
      }
  }

  override fun delete(key: String) : Future<Unit> {
    val file = File(dir, key)
    val result = future<Void>()
    vertx.fileSystem().deleteRecursive(file.absolutePath, true, result.completer())
    return result.map { Unit }
  }

  override fun put(key: String, value: T): Future<Unit> {
    return write(key, value)
  }


  override fun get(key: String): Future<T> {
    return read(key)
  }

  override fun getKeys(): Future<List<String>> {
    val result = future<List<String>>()
    vertx.fileSystem().readDir(dir.absolutePath, result.completer())
    return result.map {
      it.map { File(it).name }
    }
  }

  override fun getAll(): Future<Map<String, T>> {
    return getKeys()
      .compose { keys ->
        keys.map { key ->
          read(key).map { key to it }
        }.all()
      }
      .map { it.toMap() }
  }

  protected open fun write(key: String, value: T) : Future<Unit> {
    return serialize(value, File(dir, key))
  }

  protected open fun read(key: String): Future<T> {
    val file = File(dir, key)
    val result = future<T>()
    vertx.fileSystem().exists(file.absolutePath) {
      if (it.failed()) {
        result.fail(it.cause())
      } else {
        if (it.result()) {
          deserialize(file).setHandler(result.completer())
        } else {
          result.fail("could not find key $key")
        }
      }
    }
    return result
  }


  protected abstract fun deserialize(file: File) : Future<T>
  protected abstract fun serialize(value: T, file: File) : Future<Unit>
}