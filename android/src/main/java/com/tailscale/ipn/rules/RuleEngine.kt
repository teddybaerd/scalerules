// Copyright (c) 2026 ScaleRules contributors
// SPDX-License-Identifier: BSD-3-Clause
package com.tailscale.ipn.rules

import kotlinx.serialization.Serializable

@Serializable
enum class ConnectionKind {
  WIFI,
  MOBILE,
  OFFLINE,
  OTHER,
}

@Serializable
enum class RuleCondition {
  WIFI_NAME,
  ANY_WIFI,
  MOBILE,
  OFFLINE,
  ANY_NETWORK,
}

@Serializable
enum class RuleAction {
  CONNECT,
  DISCONNECT,
  KEEP,
}

@Serializable
data class ConnectionRule(
    val id: String,
    val name: String,
    val condition: RuleCondition,
    val ssid: String = "",
    val action: RuleAction,
    val enabled: Boolean = true,
)

data class ConnectionContext(val kind: ConnectionKind, val ssid: String? = null)

data class RuleDecision(val rule: ConnectionRule?, val reason: String) {
  val action: RuleAction
    get() = rule?.action ?: RuleAction.KEEP
}

/** Ordered, stateless evaluation. Never creates or modifies rules. */
object RuleEngine {
  fun evaluate(rules: List<ConnectionRule>, context: ConnectionContext): RuleDecision {
    // An unreadable SSID must never let a generic rule bypass a specific Wi-Fi exception.
    if (
        context.kind == ConnectionKind.WIFI &&
            context.ssid == null &&
            rules.any { it.enabled && it.condition == RuleCondition.WIFI_NAME }
    ) {
      return RuleDecision(null, "WLAN-Name unbekannt: keine Änderung")
    }
    val rule = rules.firstOrNull { it.enabled && matches(it, context) }
    return RuleDecision(rule, rule?.name ?: "Keine passende Regel: keine Änderung")
  }

  private fun matches(rule: ConnectionRule, context: ConnectionContext): Boolean =
      when (rule.condition) {
        RuleCondition.WIFI_NAME -> context.kind == ConnectionKind.WIFI && context.ssid == rule.ssid
        RuleCondition.ANY_WIFI -> context.kind == ConnectionKind.WIFI
        RuleCondition.MOBILE -> context.kind == ConnectionKind.MOBILE
        RuleCondition.OFFLINE -> context.kind == ConnectionKind.OFFLINE
        RuleCondition.ANY_NETWORK -> context.kind != ConnectionKind.OFFLINE
      }

  private fun covers(earlier: ConnectionRule, later: ConnectionRule): Boolean =
      when (earlier.condition) {
        RuleCondition.ANY_NETWORK -> later.condition != RuleCondition.OFFLINE
        RuleCondition.ANY_WIFI ->
            later.condition in listOf(RuleCondition.ANY_WIFI, RuleCondition.WIFI_NAME)
        RuleCondition.WIFI_NAME ->
            later.condition == RuleCondition.WIFI_NAME && earlier.ssid == later.ssid
        else -> earlier.condition == later.condition
      }

  fun warnings(rules: List<ConnectionRule>): List<String> = rules.flatMapIndexed { index, rule ->
    if (!rule.enabled) emptyList()
    else
        rules
            .take(index)
            .filter { it.enabled && covers(it, rule) }
            .take(1)
            .map {
              val label =
                  if (it.action == rule.action) "verdeckt" else "widersprüchlich und verdeckt"
              "„${rule.name}“ ist durch „${it.name}“ $label. Die höhere Regel entscheidet."
            }
  }
}
