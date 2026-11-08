// Copyright (c) 2026 ScaleRules contributors
// SPDX-License-Identifier: BSD-3-Clause
package com.tailscale.ipn.rules

import com.tailscale.ipn.ui.model.Tailcfg
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ExitNodeFailoverTest {
  private val config =
      ExitNodeFailoverConfig(
          enabled = true,
          controlURL = "https://headscale.example",
          selfID = "phone",
          primaryID = "home",
          fallbackID = "vps",
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

  @Test
  fun offlinePrimaryRemainsMonitoredAfterRouteWithdrawal() {
    val primary = Tailcfg.Node(StableID = "home", Online = false, AllowedIPs = emptyList())
    val backup =
        Tailcfg.Node(StableID = "vps", Online = true, AllowedIPs = listOf("0.0.0.0/0", "::/0"))
    val peers = listOf(primary, backup)
    assertFalse(primary.isExitNode)
    assertEquals(primary, findFailoverPeer(peers, "home"))
    assertEquals(
        FailoverAction.CHECK_FALLBACK,
        action(
            primary = findFailoverPeer(peers, "home")?.Online,
            fallback = findFailoverPeer(peers, "vps")?.Online,
        ),
    )
    assertNull(findFailoverPeer(peers, "removed"))
  }

  @Test
  fun onlyKnownOfflinePrimaryAndOnlineBackupTriggerFailover() {
    assertEquals(FailoverAction.CHECK_FALLBACK, action())
    for (primary in listOf(true, null)) assertEquals(FailoverAction.NONE, action(primary = primary))
    for (fallback in listOf(false, null)) assertEquals(
        FailoverAction.NONE,
        action(fallback = fallback),
    )
  }

  @Test
  fun accountSwitchPausedVpnAndManualSelectionsPreventChanges() {
    assertEquals(FailoverAction.NONE, action(url = "https://another.example"))
    assertEquals(FailoverAction.NONE, action(self = "another-phone"))
    assertEquals(FailoverAction.NONE, action(running = false))
    assertEquals(FailoverAction.NONE, action(selected = "other-exit"))
    assertEquals(FailoverAction.NONE, action(selected = null))
    assertEquals(FailoverAction.NONE, action(cfg = config.copy(enabled = false)))
  }

  @Test
  fun returnIsOptionalAndNeedsAnExplicitOnlineSignal() {
    assertEquals(FailoverAction.CHECK_PRIMARY, action(selected = "vps", primary = true))
    assertEquals(FailoverAction.NONE, action(selected = "vps", primary = null))
    assertEquals(FailoverAction.NONE, action(selected = "vps", primary = false))
    assertEquals(
        FailoverAction.NONE,
        action(cfg = config.copy(autoReturn = false), selected = "vps", primary = true),
    )
  }

  @Test
  fun invalidAndLegacyConfigurationsAreDisabled() {
    assertFalse(ExitNodeFailoverConfig().valid)
    assertFalse(config.copy(fallbackID = "home").valid)
    assertFalse(config.copy(selfID = "").valid)
    assertFalse(config.copy(failureCount = 0).valid)
    assertFalse(config.copy(returnSeconds = 0).valid)
    assertFalse(config.copy(graceSeconds = -1).valid)
  }

  @Test
  fun confirmedFailureMustAlsoHaveAReachableBackup() = runTest {
    val calls = mutableListOf<String>()
    val confirmation =
        FailoverConfirmation(
            { id ->
              calls += id
              id == "vps"
            },
            { delay(it) },
        )
    assertTrue(confirmation.fallback(config))
    assertEquals(listOf("home", "home", "home", "vps"), calls)
    val unreachable = FailoverConfirmation({ false }, { delay(it) })
    assertFalse(unreachable.fallback(config))
  }

  @Test
  fun successfulPrimaryProbeCancelsFailoverBurst() = runTest {
    val calls = mutableListOf<String>()
    val confirmation =
        FailoverConfirmation(
            { id ->
              calls += id
              true
            },
            { delay(it) },
        )
    assertFalse(confirmation.fallback(config))
    assertEquals(listOf("home"), calls)
  }

  @Test
  fun returnWaitsAndRequiresTwoSuccessfulProbes() = runTest {
    var probes = 0
    val confirmation =
        FailoverConfirmation(
            {
              ++probes
              probes == 1
            },
            { delay(it) },
        )
    assertFalse(confirmation.primary(config))
    assertEquals(2, probes)
    assertEquals(120000L, testScheduler.currentTime)
  }

  @Test
  fun cancellationDuringNetworkOrAccountChangePreventsFurtherProbes() = runTest {
    val calls = mutableListOf<String>()
    val confirmation =
        FailoverConfirmation(
            { id ->
              calls += id
              false
            },
            { delay(it) },
        )
    var switched = false
    val task = launch { switched = confirmation.fallback(config) }
    runCurrent()
    task.cancel()
    advanceTimeBy(600000)
    runCurrent()
    assertEquals(listOf("home"), calls)
    assertFalse(switched)
  }

  @Test
  fun cancellationDuringReturnDelayPreventsSecondProbe() = runTest {
    var probes = 0
    val confirmation =
        FailoverConfirmation(
            {
              ++probes
              true
            },
            { delay(it) },
        )
    val task = launch { confirmation.primary(config) }
    runCurrent()
    task.cancel()
    advanceTimeBy(600000)
    runCurrent()
    assertEquals(1, probes)
  }

  private val triple
    get() = config.copy(secondaryID = "home2")

  @Test
  fun optionalSecondaryPreservesLegacyJsonAndRejectsDuplicates() {
    val legacy =
        kotlinx.serialization.json.Json.decodeFromString<ExitNodeFailoverConfig>(
            """{"enabled":true,"controlURL":"https://headscale.example","selfID":"phone","primaryID":"home","fallbackID":"vps"}"""
        )
    assertTrue(legacy.valid)
    assertEquals(listOf("home", "vps"), legacy.priorityIDs)
    assertEquals(listOf("home", "home2", "vps"), triple.priorityIDs)
    assertFalse(triple.copy(secondaryID = "home").valid)
    assertFalse(triple.copy(secondaryID = "vps").valid)
    assertFalse(triple.copy(secondaryID = " ").valid)
  }

  @Test
  fun priorityCandidatesRespectManualSelectionUnknownStatusAndReturnSetting() {
    fun candidates(selected: String?, status: Boolean?, cfg: ExitNodeFailoverConfig = triple) =
        PriorityFailoverPolicy.candidates(cfg, selected, mapOf(selected.orEmpty() to status))
    assertEquals(listOf("home2", "vps"), candidates("home", false))
    assertEquals(listOf("home", "vps"), candidates("home2", false))
    assertEquals(listOf("home", "home2"), candidates("vps", true))
    assertEquals(listOf("home"), candidates("home2", true))
    assertTrue(candidates("home", null).isEmpty())
    assertTrue(candidates("unmanaged", false).isEmpty())
    assertTrue(candidates(null, false).isEmpty())
    assertTrue(candidates("vps", true, triple.copy(autoReturn = false)).isEmpty())
    assertTrue(candidates("home", false, triple.copy(enabled = false)).isEmpty())
    assertEquals(listOf("home", "vps"), candidates("home2", false, triple.copy(autoReturn = false)))
  }

  @Test
  fun confirmedOutagesChooseFirstReachableCandidateInPriorityOrder() = runTest {
    for ((selected, reachable, expected) in
        listOf(
            Triple("home", setOf("home2", "vps"), "home2"),
            Triple("home", setOf("vps"), "vps"),
            Triple("home2", setOf("vps"), "vps"),
            Triple("home2", setOf("home", "vps"), "home"),
        )) {
      val calls = mutableListOf<String>()
      val candidates = triple.priorityIDs.filter { it != selected }
      val target =
          PriorityFailoverConfirmation(
                  {
                    calls += it
                    it in reachable
                  },
                  { delay(it) },
              )
              .target(triple, selected, true, candidates)
      assertEquals(expected, target)
      assertEquals(List(triple.failureCount) { selected }, calls.take(triple.failureCount))
    }
  }

  @Test
  fun recoveredActiveNodeAbortsAndTotalOutageNeverSelectsATarget() = runTest {
    val recovered = PriorityFailoverConfirmation({ true }, { delay(it) })
    assertNull(recovered.target(triple, "home", true, listOf("home2", "vps")))
    val unavailable = PriorityFailoverConfirmation({ false }, { delay(it) })
    assertNull(unavailable.target(triple, "home", true, listOf("home2", "vps")))
  }

  @Test
  fun returnsFromBothBackupsToHighestConfirmedReachablePriority() = runTest {
    for ((selected, reachable, expected) in
        listOf(
            Triple("vps", setOf("home2"), "home2"),
            Triple("vps", setOf("home", "home2"), "home"),
            Triple("home2", setOf("home"), "home"),
        )) {
      val before = testScheduler.currentTime
      val target =
          PriorityFailoverConfirmation({ it in reachable }, { delay(it) })
              .target(triple, selected, false, triple.priorityIDs.takeWhile { it != selected })
      assertEquals(expected, target)
      assertEquals(triple.returnSeconds * 1000L, testScheduler.currentTime - before)
    }
  }

  @Test
  fun higherPriorityRecoveryDuringSecondaryConfirmationDefersLowerReturn() = runTest {
    var homeCalls = 0
    val target =
        PriorityFailoverConfirmation(
                { id -> id == "home2" || (id == "home" && ++homeCalls > 1) },
                { delay(it) },
            )
            .target(triple, "vps", false, listOf("home", "home2"))
    assertNull(target)
  }

  @Test
  fun failedReturnConfirmationAndCancellationCannotSwitch() = runTest {
    var calls = 0
    val confirmation = PriorityFailoverConfirmation({ ++calls == 1 }, { delay(it) })
    assertNull(confirmation.target(triple, "vps", false, listOf("home")))
    var target: String? = null
    val task = launch {
      target =
          PriorityFailoverConfirmation({ true }, { delay(it) })
              .target(triple, "vps", false, listOf("home", "home2"))
    }
    runCurrent()
    task.cancel()
    advanceTimeBy(600000)
    runCurrent()
    assertNull(target)
  }
}
