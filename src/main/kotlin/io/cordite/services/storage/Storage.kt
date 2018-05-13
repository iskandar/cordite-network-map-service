package io.cordite.services.storage

import io.vertx.core.Future


interface Storage<T> {
  fun clear() : Future<Unit>
  fun clearBlocking()

  fun put(key: String, value: T) : Future<Unit>
  fun putBlocking(key: String, value: T)

  fun get(key: String) : Future<T>
  fun getBlocking(key: String) : T

  fun getKeys() : Future<List<String>>
  fun getKeysBlocking() : List<String>

  fun getAll() : Future<Map<String, T>>
  fun getAllBlocking() : Map<String, T>
}