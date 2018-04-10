package io.cordite.services

import net.corda.core.crypto.SecureHash
import net.corda.nodeapi.internal.SignedNodeInfo

interface SignedNodeInfoStorage {
  fun store(signedNodeInfo: SignedNodeInfo)
  fun find(hash: SecureHash) : SignedNodeInfo?
}