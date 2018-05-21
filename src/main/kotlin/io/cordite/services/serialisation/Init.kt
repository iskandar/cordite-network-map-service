package io.cordite.services.serialisation

import com.fasterxml.jackson.databind.module.SimpleModule
import io.vertx.core.json.Json
import net.corda.client.jackson.JacksonSupport
import net.corda.client.rpc.internal.KryoClientSerializationScheme
import net.corda.core.identity.CordaX500Name
import net.corda.core.serialization.internal.SerializationEnvironmentImpl
import net.corda.core.serialization.internal.nodeSerializationEnv
import net.corda.nodeapi.internal.serialization.AMQP_P2P_CONTEXT
import net.corda.nodeapi.internal.serialization.SerializationFactoryImpl
import net.corda.nodeapi.internal.serialization.amqp.AMQPClientSerializationScheme
import java.security.PublicKey

class SerializationEnvironment {
  companion object {
    init {
      initialiseJackson()
      initialiseSerialisationEnvironment()
    }

    fun init() {
      // implicit static causes one-time init of this class
    }

    private fun initialiseJackson() {
      val module = SimpleModule()
        .addDeserializer(CordaX500Name::class.java, JacksonSupport.CordaX500NameDeserializer)
        .addSerializer(CordaX500Name::class.java, JacksonSupport.CordaX500NameSerializer)
        .addSerializer(PublicKey::class.java, PublicKeySerializer())
        .addDeserializer(PublicKey::class.java, PublicKeyDeserializer())
      Json.mapper.registerModule(module)
      Json.prettyMapper.registerModule(module)
    }

    private fun initialiseSerialisationEnvironment() {
      if (nodeSerializationEnv == null) {
        nodeSerializationEnv = SerializationEnvironmentImpl(
          SerializationFactoryImpl().apply {
            registerScheme(KryoClientSerializationScheme())
            registerScheme(AMQPClientSerializationScheme())
          },
          AMQP_P2P_CONTEXT)
      }
    }
  }
}

