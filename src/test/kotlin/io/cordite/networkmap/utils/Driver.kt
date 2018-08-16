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
