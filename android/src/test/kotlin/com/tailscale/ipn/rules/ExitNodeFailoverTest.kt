// Copyright (c) 2026 ScaleRules contributors
// SPDX-License-Identifier: BSD-3-Clause
package com.tailscale.ipn.rules

import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ExitNodeFailoverTest {
  private val config = ExitNodeFailoverConfig(
      enabled = true, controlURL = "https://headscale.example", selfID = "phone",
      primaryID = "home", fallbackID = "vps",
  )

  private fun action(
      cfg: ExitNodeFailoverConfig = config,
      url: String = config.controlURL,
      self: String = config.selfID,
      running: Boolean = true,
      selected: String? = config.primaryID,
      primary: Boolean? = false,
      fallback: Boolean? = true,
  ) = ExitNodeFailoverPolicy.action(cfg, url, self, running, selected, primary, fallback)

  @Test fun onlyKnownOfflinePrimaryAndOnlineBackupTriggerFailover() {
    assertEquals(FailoverAction.CHECK_FALLBACK, action())
    for (primary in listOf(true, null)) assertEquals(FailoverAction.NONE, action(primary = primary))
    for (fallback in listOf(false, null)) assertEquals(FailoverAction.NONE, action(fallback = fallback))
  }

  @Test fun accountSwitchPausedVpnAndManualSelectionsPreventChanges() {
    assertEquals(FailoverAction.NONE, action(url = "https://another.example"))
    assertEquals(FailoverAction.NONE, action(self = "another-phone"))
    assertEquals(FailoverAction.NONE, action(running = false))
    assertEquals(FailoverAction.NONE, action(selected = "other-exit"))
    assertEquals(FailoverAction.NONE, action(selected = null))
    assertEquals(FailoverAction.NONE, action(cfg = config.copy(enabled = false)))
  }

  @Test fun returnIsOptionalAndNeedsAnExplicitOnlineSignal() {
    assertEquals(FailoverAction.CHECK_PRIMARY, action(selected = "vps", primary = true))
    assertEquals(FailoverAction.NONE, action(selected = "vps", primary = null))
    assertEquals(FailoverAction.NONE, action(selected = "vps", primary = false))
    assertEquals(FailoverAction.NONE,
        action(cfg = config.copy(autoReturn = false), selected = "vps", primary = true))
  }

  @Test fun invalidAndLegacyConfigurationsAreDisabled() {
    assertFalse(ExitNodeFailoverConfig().valid)
    assertFalse(config.copy(fallbackID = "home").valid)
    assertFalse(config.copy(selfID = "").valid)
    assertFalse(config.copy(failureCount = 0).valid)
    assertFalse(config.copy(returnSeconds = 0).valid)
    assertFalse(config.copy(graceSeconds = -1).valid)
  }

  @Test fun confirmedFailureMustAlsoHaveAReachableBackup() = runTest {
    val calls = mutableListOf<String>()
    val confirmation = FailoverConfirmation({ id -> calls += id; id == "vps" }, { delay(it) })
    assertTrue(confirmation.fallback(config))
    assertEquals(listOf("home", "home", "home", "vps"), calls)
    val unreachable = FailoverConfirmation({ false }, { delay(it) })
    assertFalse(unreachable.fallback(config))
  }

  @Test fun successfulPrimaryProbeCancelsFailoverBurst() = runTest {
    val calls = mutableListOf<String>()
    val confirmation = FailoverConfirmation({ id -> calls += id; true }, { delay(it) })
    assertFalse(confirmation.fallback(config))
    assertEquals(listOf("home"), calls)
  }

  @Test fun returnWaitsAndRequiresTwoSuccessfulProbes() = runTest {
    var probes = 0
    val confirmation = FailoverConfirmation({ ++probes; probes == 1 }, { delay(it) })
    assertFalse(confirmation.primary(config))
    assertEquals(2, probes)
    assertEquals(120000L, testScheduler.currentTime)
  }

  @Test fun cancellationDuringNetworkOrAccountChangePreventsFurtherProbes() = runTest {
    val calls = mutableListOf<String>()
    val confirmation = FailoverConfirmation({ id -> calls += id; false }, { delay(it) })
    var switched = false
    val task = launch { switched = confirmation.fallback(config) }
    runCurrent()
    task.cancel()
    advanceTimeBy(600000)
    runCurrent()
    assertEquals(listOf("home"), calls)
    assertFalse(switched)
  }

  @Test fun cancellationDuringReturnDelayPreventsSecondProbe() = runTest {
    var probes = 0
    val confirmation = FailoverConfirmation({ ++probes; true }, { delay(it) })
    val task = launch { confirmation.primary(config) }
    runCurrent()
    task.cancel()
    advanceTimeBy(600000)
    runCurrent()
    assertEquals(1, probes)
  }
}
