package io.cordite.services.storage

import io.vertx.core.Future


interface Storage<T> {
  fun clear() : Future<Unit>
  fun put(key: String, value: T) : Future<Unit>
  fun get(key: String) : Future<T>
  fun getKeys() : Future<List<String>>
  fun getAll() : Future<Map<String, T>>
  fun delete(key: String) : Future<Unit>
}