package io.cordite.services.storage

import net.corda.core.crypto.SecureHash
import net.corda.nodeapi.internal.SignedNodeInfo

interface SignedNodeInfoStorage {
  fun store(signedNodeInfo: SignedNodeInfo)
  fun find(hash: SecureHash) : SignedNodeInfo?
  fun allHashes() : List<SecureHash>
}