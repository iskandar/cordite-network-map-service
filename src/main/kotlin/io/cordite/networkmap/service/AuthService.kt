/**
 *   Copyright 2018, Cordite Foundation.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package io.cordite.networkmap.service

import io.bluebank.braid.core.security.JWTUtils
import io.swagger.annotations.ApiModelProperty
import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import io.vertx.ext.auth.AuthProvider
import io.vertx.ext.auth.jwt.JWTAuth
import io.vertx.ext.auth.jwt.JWTOptions
import net.corda.core.crypto.SecureHash
import net.corda.core.utilities.loggerFor
import java.io.File
import java.io.FileOutputStream

/**
 * Authentication service for the REST service
 */
class AuthService(private val adminUser: InMemoryUser, private val jksFile: File) {
  companion object {
    val log = loggerFor<AuthService>()
  }

  private val jwtSecret = "secret"
  private var jwtAuth: JWTAuth? = null

  fun login(request: LoginRequest): String {
    if (jwtAuth == null) {
      log.error("auth provider not initialised")
      throw RuntimeException("internal error")
    }

    if (request.matches(adminUser)) {
      return jwtAuth!!.generateToken(JsonObject().put("user", request.user), JWTOptions().setExpiresInMinutes(24 * 60))
    } else {
      throw RuntimeException("failed to authenticate")
    }
  }

  fun createAuthProvider(vertx: Vertx): AuthProvider {
    return jwtAuth ?: run {
      ensureJWTKeyStoreExists()
      jwtAuth = JWTAuth.create(vertx, JsonObject().put("keyStore", JsonObject()
        .put("path", this.jksFile.absolutePath)
        .put("type", "jceks")
        .put("password", jwtSecret)))
      jwtAuth!!
    }
  }

  private fun ensureJWTKeyStoreExists() {
    val ks = JWTUtils.createSimpleJWTKeyStore(jwtSecret)
    jksFile.parentFile.mkdirs()
    FileOutputStream(this.jksFile.absoluteFile).use {
      ks.store(it, jwtSecret.toCharArray())
      it.flush()
    }
  }

  data class LoginRequest(@ApiModelProperty(value = "user name", example = "sa") val user: String, @ApiModelProperty(value = "password", example = "admin") val password: String) {
    fun matches(value: InMemoryUser): Boolean {
      return value.username == user && value.passwordHash == SecureHash.sha256(password)
    }
  }
}