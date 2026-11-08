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
    val secondaryID: String = "",
    val autoReturn: Boolean = true,
    val failureCount: Int = 3,
    val graceSeconds: Int = 30,
    val returnSeconds: Int = 120,
) {
  val priorityIDs: List<String>
    get() = listOf(primaryID, secondaryID, fallbackID).filter { it.isNotBlank() }

  val valid: Boolean
    get() =
        enabled &&
            controlURL.isNotBlank() &&
            selfID.isNotBlank() &&
            primaryID.isNotBlank() &&
            fallbackID.isNotBlank() &&
            primaryID != fallbackID &&
            (secondaryID.isEmpty() ||
                (secondaryID.isNotBlank() &&
                    secondaryID != primaryID &&
                    secondaryID != fallbackID)) &&
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

/** Ordered, confirmed selection; unknown peer status never counts as an outage. */
object PriorityFailoverPolicy {
  fun candidates(
      config: ExitNodeFailoverConfig,
      selected: String?,
      online: Map<String, Boolean?>,
      controlURL: String? = config.controlURL,
      selfID: String? = config.selfID,
      running: Boolean = true,
  ): List<String> {
    val index = config.priorityIDs.indexOf(selected)
    if (
        !config.valid ||
            index < 0 ||
            !running ||
            config.controlURL != controlURL ||
            config.selfID != selfID
    )
        return emptyList()
    return if (online[selected] == false) config.priorityIDs.filter { it != selected }
    else if (config.autoReturn) config.priorityIDs.take(index) else emptyList()
  }
}

class PriorityFailoverConfirmation(
    private val probe: suspend (String) -> Boolean,
    private val wait: suspend (Long) -> Unit,
) {
  suspend fun target(
      config: ExitNodeFailoverConfig,
      selected: String,
      offline: Boolean,
      candidates: List<String>,
  ): String? {
    if (offline) {
      repeat(config.failureCount) {
        if (probe(selected)) return null
        if (it < config.failureCount - 1) wait(3000)
      }
    }
    for (id in candidates) {
      if (!probe(id)) continue
      if (!offline) {
        wait(config.returnSeconds * 1000L)
        if (!probe(id)) continue
        // Reconsider higher priorities after the delay before committing a lower target.
        for (higher in candidates.takeWhile { it != id }) {
          if (probe(higher)) return null
        }
      }
      return id
    }
    return null
  }
}
