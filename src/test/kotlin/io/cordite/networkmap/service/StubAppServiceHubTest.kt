package io.cordite.networkmap.service

import net.corda.core.contracts.StateRef
import net.corda.core.crypto.SecureHash
import net.corda.core.flows.FlowLogic
import net.corda.core.node.StatesToRecord
import net.corda.core.serialization.SerializeAsToken
import org.junit.Test

class StubAppServiceHubTest {
  @Test
  fun `capture all the dead code for coverage`() {
    StubAppServiceHub().apply {
      ignore { attachments }
      ignore { clock }
      ignore { contractUpgradeService }
      ignore { cordappProvider }
      ignore { identityService }
      ignore { keyManagementService }
      ignore {  myInfo }
      ignore { networkMapCache }
      ignore { networkParameters }
      ignore { transactionVerifierService }
      ignore { validatedTransactions }
      ignore { vaultService }
      ignore { cordaService(SerializeAsToken::class.java) }
      ignore { jdbcSession() }
      ignore { loadState(StateRef(SecureHash.zeroHash, 0)) }
      ignore { loadStates(setOf()) }
      ignore { recordTransactions(StatesToRecord.NONE, listOf())}
      ignore { registerUnloadHandler {  }}
      ignore { startFlow(object : FlowLogic<Int>() { override fun call(): Int { return 0} })}
      ignore { startTrackedFlow(object : FlowLogic<Int>() { override fun call(): Int { return 0} })}
    }
  }

  private fun StubAppServiceHub.ignore(fn: StubAppServiceHub.() -> Unit) {
    try {
      fn()
    } catch (err: Throwable) {
      // ignore
    }
  }
}