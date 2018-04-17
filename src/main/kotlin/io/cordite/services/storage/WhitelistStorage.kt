package io.cordite.services.storage

import net.corda.core.node.services.AttachmentId
import org.apache.qpid.proton.amqp.transport.Attach

interface WhitelistStorage {
  fun clear()
  fun add(list: List<Pair<String, AttachmentId>>)
  fun getAll() : List<Pair<String, AttachmentId>>
  fun getAllAsMap() : Map<String, List<AttachmentId>>
}