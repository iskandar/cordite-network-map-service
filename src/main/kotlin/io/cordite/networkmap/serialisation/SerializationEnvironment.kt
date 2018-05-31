package io.cordite.networkmap.serialisation

import com.fasterxml.jackson.databind.module.SimpleModule
import io.vertx.core.json.Json
import net.corda.client.jackson.JacksonSupport
import net.corda.client.rpc.internal.KryoClientSerializationScheme
import net.corda.core.identity.CordaX500Name
import net.corda.core.serialization.SerializationContext
import net.corda.core.serialization.SerializationDefaults
import net.corda.core.serialization.internal.SerializationEnvironmentImpl
import net.corda.core.serialization.internal.nodeSerializationEnv
import net.corda.nodeapi.internal.serialization.QuasarWhitelist
import net.corda.nodeapi.internal.serialization.SerializationContextImpl
import net.corda.nodeapi.internal.serialization.SerializationFactoryImpl
import net.corda.nodeapi.internal.serialization.amqp.AMQPClientSerializationScheme
import net.corda.nodeapi.internal.serialization.amqp.AMQPServerSerializationScheme
import net.corda.nodeapi.internal.serialization.amqp.AmqpHeaderV1_0
import java.security.PublicKey

class SerializationEnvironment {
  companion object {

    private val NMS_SERIALIZATION_CONTEXT = SerializationContextImpl(AmqpHeaderV1_0,
      SerializationDefaults.javaClass.classLoader,
      QuasarWhitelist,
      emptyMap(),
      true,
      SerializationContext.UseCase.P2P)

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
        val classloader = ClassLoader.getSystemClassLoader()

        nodeSerializationEnv = SerializationEnvironmentImpl(
          SerializationFactoryImpl().apply {
            registerScheme(KryoClientSerializationScheme())
            registerScheme(AMQPClientSerializationScheme())
            registerScheme(AMQPServerSerializationScheme())
          },
          p2pContext = NMS_SERIALIZATION_CONTEXT.withClassLoader(classloader),
          rpcServerContext = NMS_SERIALIZATION_CONTEXT.withClassLoader(classloader),
          storageContext = NMS_SERIALIZATION_CONTEXT.withClassLoader(classloader),
          checkpointContext = NMS_SERIALIZATION_CONTEXT.withClassLoader(classloader))
      }
    }
  }
}

