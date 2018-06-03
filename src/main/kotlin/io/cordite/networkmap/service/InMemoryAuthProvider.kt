package io.cordite.networkmap.service

import io.vertx.core.AsyncResult
import io.vertx.core.Future.failedFuture
import io.vertx.core.Future.succeededFuture
import io.vertx.core.Handler
import io.vertx.core.json.JsonObject
import io.vertx.ext.auth.AbstractUser
import io.vertx.ext.auth.AuthProvider
import io.vertx.ext.auth.User
import net.corda.core.crypto.SecureHash

class InMemoryUser(val name: String, val username: String, password: String) : AbstractUser() {
  companion object {
    fun createUser(name: String, username: String, password: String) = InMemoryUser(name, username, password)
  }
  internal val passwordHash = SecureHash.sha256(password)

  override fun doIsPermitted(permission: String?, resultHandler: Handler<AsyncResult<Boolean>>?) {
    resultHandler?.handle(succeededFuture(true))
  }

  override fun setAuthProvider(authProvider: AuthProvider?) {
  }

  override fun principal(): JsonObject {
    return JsonObject()
      .put("name", name)
      .put("username", username)
  }
}

class InMemoryAuthProvider(users: List<InMemoryUser>) : AuthProvider {
  constructor(vararg users: InMemoryUser) : this(users.toList())

  private val userMap = users.map { it.username to it }.toMap()
  override fun authenticate(authInfo: JsonObject, resultHandler: Handler<AsyncResult<User>>) {
    val username = authInfo.getString("username") ?: let {
      resultHandler.handle(failedFuture("expected 'username' field"))
      return
    }

    val passwordHash = authInfo.getString("password")?.let { SecureHash.sha256(it) } ?: let {
      resultHandler.handle(failedFuture("expected 'password' field"))
      return
    }

    val user = userMap[username]
    if (user != null && user.passwordHash == passwordHash) {
      resultHandler.handle(succeededFuture(user))
    } else {
      resultHandler.handle(failedFuture("authentication failed"))
    }
  }

}