// Copyright (c) 2026 ScaleRules contributors
// SPDX-License-Identifier: BSD-3-Clause
package com.tailscale.ipn.rules

import org.junit.Assert.*
import org.junit.Test

class RuleEngineTest {
  private val home =
      ConnectionRule("home", "Zuhause", RuleCondition.WIFI_NAME, "Heimnetz", RuleAction.DISCONNECT)
  private val wifi =
      ConnectionRule("wifi", "WLAN", RuleCondition.ANY_WIFI, action = RuleAction.CONNECT)
  private val mobile =
      ConnectionRule("mobile", "Mobilfunk", RuleCondition.MOBILE, action = RuleAction.CONNECT)

  @Test
  fun specificExceptionWinsBeforeGenericWifi() {
    assertEquals(
        home,
        RuleEngine.evaluate(listOf(home, wifi), ConnectionContext(ConnectionKind.WIFI, "Heimnetz"))
            .rule,
    )
    assertEquals(
        wifi,
        RuleEngine.evaluate(listOf(home, wifi), ConnectionContext(ConnectionKind.WIFI, "Hotel"))
            .rule,
    )
  }

  @Test
  fun explicitOrderWinsAndReportsConflict() {
    val rules = listOf(wifi, home)
    assertEquals(
        wifi,
        RuleEngine.evaluate(rules, ConnectionContext(ConnectionKind.WIFI, "Heimnetz")).rule,
    )
    assertEquals(1, RuleEngine.warnings(rules).size)
  }

  @Test
  fun disabledRulesNeitherMatchNorHideOthers() {
    val rules = listOf(wifi.copy(enabled = false), home)
    assertTrue(RuleEngine.warnings(rules).isEmpty())
    assertEquals(
        home,
        RuleEngine.evaluate(rules, ConnectionContext(ConnectionKind.WIFI, "Heimnetz")).rule,
    )
  }

  @Test
  fun missingSsidDoesNotFallThroughToGenericDisconnect() {
    val rules =
        listOf(home.copy(action = RuleAction.CONNECT), wifi.copy(action = RuleAction.DISCONNECT))
    assertEquals(
        RuleAction.KEEP,
        RuleEngine.evaluate(rules, ConnectionContext(ConnectionKind.WIFI)).action,
    )
    assertEquals(
        mobile,
        RuleEngine.evaluate(rules + mobile, ConnectionContext(ConnectionKind.MOBILE)).rule,
    )
  }

  @Test
  fun emptyAndUnmatchedRulesPreserveVpn() {
    assertEquals(
        RuleAction.KEEP,
        RuleEngine.evaluate(emptyList(), ConnectionContext(ConnectionKind.MOBILE)).action,
    )
    assertEquals(
        RuleAction.KEEP,
        RuleEngine.evaluate(listOf(mobile), ConnectionContext(ConnectionKind.OFFLINE)).action,
    )
  }

  @Test
  fun exactSsidComparisonAndNoInputMutation() {
    val rules = listOf(home, wifi)
    assertEquals(
        wifi,
        RuleEngine.evaluate(rules, ConnectionContext(ConnectionKind.WIFI, "heimnetz")).rule,
    )
    assertEquals(listOf(home, wifi), rules)
  }

  @Test
  fun keepActionCanOverrideLowerRules() {
    val keep = home.copy(action = RuleAction.KEEP)
    assertEquals(
        keep,
        RuleEngine.evaluate(listOf(keep, wifi), ConnectionContext(ConnectionKind.WIFI, "Heimnetz"))
            .rule,
    )
  }

  @Test
  fun anyNetworkDoesNotCoverOffline() {
    val any = wifi.copy(condition = RuleCondition.ANY_NETWORK)
    val offline = home.copy(condition = RuleCondition.OFFLINE)
    assertTrue(RuleEngine.warnings(listOf(any, offline)).isEmpty())
    assertEquals(
        offline,
        RuleEngine.evaluate(listOf(any, offline), ConnectionContext(ConnectionKind.OFFLINE)).rule,
    )
  }
}
