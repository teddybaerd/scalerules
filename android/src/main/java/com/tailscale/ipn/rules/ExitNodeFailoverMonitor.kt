// Copyright (c) 2026 ScaleRules contributors
// SPDX-License-Identifier: BSD-3-Clause
package com.tailscale.ipn.rules

import android.content.Context
import com.tailscale.ipn.App
import com.tailscale.ipn.R
import com.tailscale.ipn.ui.localapi.Client
import com.tailscale.ipn.ui.model.Ipn
import com.tailscale.ipn.ui.model.Tailcfg
import com.tailscale.ipn.ui.notifier.Notifier
import kotlin.coroutines.resume
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

/** Observes the existing IPN bus. Timers and probes run only during a failover incident. */
class ExitNodeFailoverMonitor(
    private val context: Context,
    private val store: RulesStore,
    private val scope: CoroutineScope,
) {
  private val ready = MutableStateFlow(false)
  private val config = MutableStateFlow(store.failover())
  private var job: Job? = null

  private data class Snapshot(
      val config: ExitNodeFailoverConfig,
      val controlURL: String?,
      val selfID: String?,
      val selectedID: String?,
      val primaryOnline: Boolean?,
      val fallbackOnline: Boolean?,
      val running: Boolean,
  ) {
    val action: FailoverAction
      get() =
          ExitNodeFailoverPolicy.action(
              config,
              controlURL,
              selfID,
              running,
              selectedID,
              primaryOnline,
              fallbackOnline,
          )
  }

  fun refresh(available: Boolean) {
    config.value = store.failover()
    ready.value = available
  }

  private fun snapshot(): Snapshot {
    val cfg = config.value
    val map = Notifier.netmap.value
    val prefs = Notifier.prefs.value
    val peers = map?.Peers.orEmpty().filter { it.isExitNode }
    return Snapshot(
        cfg,
        prefs?.ControlURL,
        map?.SelfNode?.StableID,
        prefs?.activeExitNodeID,
        peers.find { it.StableID == cfg.primaryID }?.Online,
        peers.find { it.StableID == cfg.fallbackID }?.Online,
        ready.value &&
            prefs?.WantRunning == true &&
            Notifier.state.value == Ipn.State.Running &&
            App.get().getAppScopedViewModel().vpnActive.value,
    )
  }

  fun start() {
    if (job != null) return
    job = scope.launch {
      val engine = combine(Notifier.netmap, Notifier.prefs, Notifier.state) { _, _, _ -> Unit }
      combine(engine, App.get().getAppScopedViewModel().vpnActive, config, ready) { _, _, _, _ ->
            snapshot()
          }
          .distinctUntilChanged()
          .collectLatest { initial ->
            if (initial.action == FailoverAction.NONE) return@collectLatest
            delay(initial.config.graceSeconds * 1000L)
            while (currentCoroutineContext().isActive && snapshot() == initial) {
              val confirmation =
                  FailoverConfirmation(
                      probe = { id -> peer(id)?.let { probe(it) } ?: false },
                      wait = { delay(it) },
                  )
              val targetID =
                  when (initial.action) {
                    FailoverAction.CHECK_FALLBACK ->
                        if (confirmation.fallback(initial.config)) initial.config.fallbackID
                        else null
                    FailoverAction.CHECK_PRIMARY ->
                        if (confirmation.primary(initial.config)) initial.config.primaryID else null
                    FailoverAction.NONE -> null
                  }
              if (targetID != null && snapshot() == initial) {
                peer(targetID)?.let { switchTo(it, initial) }
              }
              // Retry only while an incident remains unresolved, never in the healthy steady state.
              delay(60000)
            }
          }
    }
  }

  private fun peer(id: String): Tailcfg.Node? =
      Notifier.netmap.value?.Peers?.find { it.StableID == id && it.isExitNode }

  private suspend fun probe(peer: Tailcfg.Node): Boolean =
      suspendCancellableCoroutine { continuation ->
        Client(scope).ping(peer) { result ->
          if (continuation.isActive) continuation.resume(result.getOrNull()?.Err?.isEmpty() == true)
        }
      }

  private fun switchTo(peer: Tailcfg.Node, initial: Snapshot) {
    if (snapshot() != initial) return
    val prefs = Ipn.MaskedPrefs()
    prefs.ExitNodeID = peer.StableID
    Client(scope).editPrefs(prefs) { result ->
      // The resulting preference event cancels the confirmation collector. Keep the
      // acknowledgement in the service scope so successful switches are still journaled.
      store.log(
          context.getString(
              if (result.isSuccess) R.string.sr_failover_switched else R.string.sr_failover_failed,
              peer.displayName,
          )
      )
    }
  }

  fun stop() {
    ready.value = false
    job?.cancel()
    job = null
  }
}
