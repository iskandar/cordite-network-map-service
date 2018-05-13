package io.cordite.services.storage

import io.cordite.services.utils.DirectoryDigest
import io.cordite.services.utils.executeBlocking
import io.vertx.core.Future
import io.vertx.core.Vertx
import net.corda.core.internal.readAll
import java.io.File
import kotlin.reflect.KClass

abstract class AbstractSimpleNameValueStore<T : Any>(protected val dir: File, private val vertx: Vertx) : Storage<T> {
    private val digest = DirectoryDigest(dir)

  init {
    dir.mkdirs()
  }

  override fun clear(): Future<Unit> {
    return vertx.executeBlocking {
      clearBlocking()
    }
  }

  override fun clearBlocking() {
    return dir.listFiles().forEach {
      it.deleteRecursively()
    }
  }

  override fun put(key: String, value: T): Future<Unit> {
    return vertx.executeBlocking {
      putBlocking(key, value)
    }
  }

  override fun putBlocking(key: String, value: T) {
    write(key, value)
  }

  override fun get(key: String): Future<T> {
    return vertx.executeBlocking {
      getBlocking(key)
    }
  }

  override fun getBlocking(key: String): T {
    read(key) ?: throw RuntimeException("could not find key $key")
  }

  override fun getKeys(): Future<List<String>> {
    return vertx.executeBlocking {
      getKeysBlocking()
    }
  }

  override fun getKeysBlocking(): List<String> {
    return dir.listFiles().asSequence().map { it.nameWithoutExtension }.toList()
  }


  override fun getAll(): Future<Map<String, T>> {
    return vertx.executeBlocking {
      getAllBlocking()
    }
  }

  override fun getAllBlocking(): Map<String, T> {
    return getKeysBlocking().map { it to read(it) }.filter { it.second != null }.map { it.first to it.second!! }.toMap()
  }

  /**
   * Blocking
   */
  protected open fun write(key: String, value: T) {
    val bytes = serialize(value, File(dir, key))
  }

  /**
   * Blocking
   */
  protected open fun read(key: String): T? {
    val file = File(dir, key)
    return if (file.exists()) {
      deserialize(file)
    } else {
      null
    }
  }

  protected abstract fun deserialize(file: File) : T
  protected abstract fun serialize(value: T, file: File)
}