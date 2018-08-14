package io.cordite.networkmap.utils

import net.corda.testing.driver.DriverDSL
import net.corda.testing.driver.DriverParameters
import net.corda.testing.node.internal.CompatibilityZoneParams
import net.corda.testing.node.internal.DriverDSLImpl
import net.corda.testing.node.internal.genericDriver

fun <A> driverWithCompatZone(compatibilityZone: CompatibilityZoneParams, defaultParameters: DriverParameters = DriverParameters(), dsl: DriverDSL.() -> A): A {
  return genericDriver(
    driverDsl = DriverDSLImpl(
      portAllocation = defaultParameters.portAllocation,
      debugPortAllocation = defaultParameters.debugPortAllocation,
      systemProperties = defaultParameters.systemProperties,
      driverDirectory = defaultParameters.driverDirectory.toAbsolutePath(),
      useTestClock = defaultParameters.useTestClock,
      isDebug = defaultParameters.isDebug,
      startNodesInProcess = defaultParameters.startNodesInProcess,
      waitForAllNodesToFinish = defaultParameters.waitForAllNodesToFinish,
      notarySpecs = defaultParameters.notarySpecs,
      extraCordappPackagesToScan = defaultParameters.extraCordappPackagesToScan,
      jmxPolicy = defaultParameters.jmxPolicy,
      compatibilityZone = compatibilityZone,
      networkParameters = defaultParameters.networkParameters
    ),
    coerce = { it },
    dsl = dsl,
    initialiseSerialization = true
  )
}
