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
      val diagnostic: String,
      val fallbackEligible: Boolean,
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
    // An offline peer can lose its advertised routes. Keep its online state visible.
    val peers = map?.Peers.orEmpty()
    val primary = findFailoverPeer(peers, cfg.primaryID)
    val fallback = findFailoverPeer(peers, cfg.fallbackID)
    val vpnActive = App.get().getAppScopedViewModel().vpnActive.value
    val engine = Notifier.state.value
    val profileMatches =
        cfg.controlURL == prefs?.ControlURL && cfg.selfID == map?.SelfNode?.StableID
    val diagnostic =
        "enabled=${cfg.enabled}, valid=${cfg.valid}, profile=$profileMatches, ready=${ready.value}, " +
            "vpn=$vpnActive, engine=$engine, want=${prefs?.WantRunning}, " +
            "selected=${prefs?.activeExitNodeID == cfg.primaryID}/${prefs?.activeExitNodeID == cfg.fallbackID}, " +
            "primary=${primary?.Online}/${primary?.isExitNode}, " +
            "backup=${fallback?.Online}/${fallback?.isExitNode}"
    return Snapshot(
        cfg,
        prefs?.ControlURL,
        map?.SelfNode?.StableID,
        prefs?.activeExitNodeID,
        primary?.Online,
        fallback?.Online,
        ready.value &&
            prefs?.WantRunning == true &&
            Notifier.state.value == Ipn.State.Running &&
            vpnActive,
        diagnostic,
        fallback?.isExitNode == true,
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
            if (initial.config.enabled) {
              store.log(context.getString(R.string.sr_failover_diagnostic, initial.diagnostic))
            }
            if (initial.action == FailoverAction.CHECK_FALLBACK && !initial.fallbackEligible)
                return@collectLatest
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
                // The destination must still offer exit routes when the switch is issued.
                peer(targetID)?.takeIf { it.isExitNode }?.let { switchTo(it, initial) }
              }
              // Retry only while an incident remains unresolved, never in the healthy steady state.
              delay(60000)
            }
          }
    }
  }

  private fun peer(id: String): Tailcfg.Node? =
      findFailoverPeer(Notifier.netmap.value?.Peers.orEmpty(), id)

  private suspend fun probe(peer: Tailcfg.Node): Boolean =
      suspendCancellableCoroutine { continuation ->
        Client(scope).ping(peer) { result ->
          if (continuation.isActive) {
            val reachable = result.getOrNull()?.Err?.isEmpty() == true
            store.log(
                context.getString(
                    if (reachable) R.string.sr_failover_probe_ok
                    else R.string.sr_failover_probe_failed,
                    peer.displayName,
                )
            )
            continuation.resume(reachable)
          }
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

/** Monitoring an offline node must not depend on its current route advertisement. */
internal fun findFailoverPeer(peers: List<Tailcfg.Node>, id: String): Tailcfg.Node? = peers.find {
  it.StableID == id
}
