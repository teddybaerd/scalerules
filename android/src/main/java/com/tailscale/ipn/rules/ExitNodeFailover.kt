// Copyright (c) 2026 ScaleRules contributors
// SPDX-License-Identifier: BSD-3-Clause
package com.tailscale.ipn.rules

import kotlinx.serialization.Serializable

@Serializable
data class ExitNodeFailoverConfig(
    val enabled: Boolean = false,
    val controlURL: String = "",
    val selfID: String = "",
    val primaryID: String = "",
    val fallbackID: String = "",
    val autoReturn: Boolean = true,
    val failureCount: Int = 3,
    val graceSeconds: Int = 30,
    val returnSeconds: Int = 120,
) {
  val valid: Boolean
    get() =
        enabled &&
            controlURL.isNotBlank() &&
            selfID.isNotBlank() &&
            primaryID.isNotBlank() &&
            fallbackID.isNotBlank() &&
            primaryID != fallbackID &&
            failureCount in 2..5 &&
            graceSeconds in 15..120 &&
            returnSeconds in 60..600
}

enum class FailoverAction {
  NONE,
  CHECK_FALLBACK,
  CHECK_PRIMARY,
}

/** Unknown status never means offline. Only act on the configured pair in its own profile. */
object ExitNodeFailoverPolicy {
  fun action(
      config: ExitNodeFailoverConfig,
      controlURL: String?,
      selfID: String?,
      running: Boolean,
      selectedID: String?,
      primaryOnline: Boolean?,
      fallbackOnline: Boolean?,
  ): FailoverAction {
    if (!config.valid || !running || config.controlURL != controlURL || config.selfID != selfID)
        return FailoverAction.NONE
    return when {
      selectedID == config.primaryID && primaryOnline == false && fallbackOnline == true ->
          FailoverAction.CHECK_FALLBACK
      selectedID == config.fallbackID && config.autoReturn && primaryOnline == true ->
          FailoverAction.CHECK_PRIMARY
      else -> FailoverAction.NONE
    }
  }
}

/** Bounded confirmation burst; the injected wait also makes cancellation testable. */
class FailoverConfirmation(
    private val probe: suspend (String) -> Boolean,
    private val wait: suspend (Long) -> Unit,
) {
  suspend fun fallback(config: ExitNodeFailoverConfig): Boolean {
    repeat(config.failureCount) {
      if (probe(config.primaryID)) return false
      if (it < config.failureCount - 1) wait(3000)
    }
    return probe(config.fallbackID)
  }

  suspend fun primary(config: ExitNodeFailoverConfig): Boolean {
    if (!probe(config.primaryID)) return false
    wait(config.returnSeconds * 1000L)
    return probe(config.primaryID)
  }
}
