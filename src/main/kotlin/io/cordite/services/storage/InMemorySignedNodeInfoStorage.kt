package io.cordite.services.storage

import net.corda.core.crypto.SecureHash
import net.corda.nodeapi.internal.SignedNodeInfo

class InMemorySignedNodeInfoStorage : SignedNodeInfoStorage {
  private val nodeInfoMap = mutableMapOf<SecureHash, SignedNodeInfo>()

  override fun store(signedNodeInfo: SignedNodeInfo) {
    nodeInfoMap[signedNodeInfo.raw.hash] = signedNodeInfo
  }

  override fun find(hash: SecureHash): SignedNodeInfo? {
    return nodeInfoMap[hash]
  }

  override fun allHashes(): List<SecureHash> {
    return nodeInfoMap.keys.toList()
  }
}