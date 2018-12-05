package io.cordite.networkmap.utils

import net.corda.core.utilities.loggerFor
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.slf4j.MDC

class JunitMDCRule : TestWatcher() {
  companion object {
    private val log = loggerFor<JunitMDCRule>()
    const val MDC_CLASS = "test-class"
    const val MDC_NAME = "test-name"
  }
  override fun starting(description: Description?) {
    description?.apply {
      log.info("starting test: $className $methodName")
      MDC.put(MDC_CLASS, this.className)
      MDC.put(MDC_NAME, this.methodName)
    }
  }

  override fun finished(description: Description?) {
    description?.apply {
      MDC.remove(MDC_CLASS)
      MDC.remove(MDC_NAME)
      log.info("stopping test: $className $methodName")
    }
  }
}