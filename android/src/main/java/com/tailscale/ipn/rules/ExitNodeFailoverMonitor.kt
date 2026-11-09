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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

/** Observes the existing IPN bus. Timers and probes run only during a failover incident. */
class ExitNodeFailoverMonitor(
    private val context: Context,
    private val store: RulesStore,
    private val scope: CoroutineScope,
) {
  private val ready = MutableStateFlow(false)
  private val config = MutableStateFlow(store.failover())
  private var job: Job? = null
  private var dnsJob: Job? = null

  private data class Snapshot(
      val config: ExitNodeFailoverConfig,
      val controlURL: String?,
      val selfID: String?,
      val selectedID: String?,
      val online: Map<String, Boolean?>,
      val eligible: Set<String>,
      val running: Boolean,
  ) {
    val candidates: List<String>
      get() =
          PriorityFailoverPolicy.candidates(config, selectedID, online, controlURL, selfID, running)
  }

  fun refresh(available: Boolean) {
    val updated = store.failover()
    if (!available || updated != config.value) dnsJob?.cancel()
    config.value = updated
    ready.value = available
  }

  private fun snapshot(): Snapshot {
    val cfg = config.value
    val map = Notifier.netmap.value
    val prefs = Notifier.prefs.value
    // An offline peer can lose its advertised routes. Keep its online state visible.
    val peers = map?.Peers.orEmpty()
    val vpnActive = App.get().getAppScopedViewModel().vpnActive.value
    return Snapshot(
        cfg,
        prefs?.ControlURL,
        map?.SelfNode?.StableID,
        prefs?.activeExitNodeID,
        cfg.priorityIDs.associateWith { findFailoverPeer(peers, it)?.Online },
        cfg.priorityIDs.filter { findFailoverPeer(peers, it)?.isExitNode == true }.toSet(),
        ready.value &&
            prefs?.WantRunning == true &&
            Notifier.state.value == Ipn.State.Running &&
            vpnActive,
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
            if (initial.config.primaryID.isNotBlank()) {
              store.log(
                  context.getString(
                      R.string.sr_failover_diagnostic,
                      "running=${initial.running}, profile=${initial.config.controlURL == initial.controlURL && initial.config.selfID == initial.selfID}, " +
                          "priority=${initial.config.priorityIDs.indexOf(initial.selectedID) + 1}, online=${initial.config.priorityIDs.map { initial.online[it] }}",
                  )
              )
            }
            if (initial.candidates.isEmpty()) return@collectLatest
            delay(initial.config.graceSeconds * 1000L)
            while (currentCoroutineContext().isActive && snapshot() == initial) {
              val confirmation =
                  PriorityFailoverConfirmation(
                      probe = { id -> peer(id)?.let { probe(it) } ?: false },
                      wait = { delay(it) },
                  )
              val targetID =
                  confirmation.target(
                      initial.config,
                      initial.selectedID ?: return@collectLatest,
                      initial.online[initial.selectedID] == false,
                      initial.candidates.filter {
                        initial.online[it] == true && it in initial.eligible
                      },
                  )
              if (targetID == null && initial.online[initial.selectedID] == false) {
                store.log(context.getString(R.string.sr_failover_no_target))
              }
              if (targetID != null && snapshot() == initial) {
                // The destination must still offer exit routes when the switch is issued.
                peer(targetID)?.takeIf { it.isExitNode }?.let { switchTo(it, initial) }
              }
              // Retry only while an incident remains unresolved, never in the healthy steady
              // state.
              delay(initial.config.graceSeconds * 1000L)
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
      if (result.isSuccess) checkDNSAfterSwitch(peer.StableID, initial)
    }
  }

  private fun checkDNSAfterSwitch(targetID: String, initial: Snapshot) {
    dnsJob?.cancel()
    dnsJob = scope.launch {
      fun current(): Boolean {
        val now = snapshot()
        return now.running &&
            now.config == initial.config &&
            now.controlURL == initial.controlURL &&
            now.selfID == initial.selfID &&
            now.selectedID == targetID
      }
      // Wait for the selected-node event, rather than assuming preference acknowledgement
      // means the new DNS configuration is already applied.
      val applied =
          withTimeoutOrNull(10000) {
            Notifier.prefs.first { it?.activeExitNodeID == targetID }
            true
          } == true
      if (!applied) return@launch
      // Give the applied exit-node/DNS configuration time to settle. Never reconnect the VPN.
      delay(2000)
      repeat(3) { attempt ->
        if (!current()) return@launch
        val available =
            withTimeoutOrNull(6000) {
              val requestScope = CoroutineScope(currentCoroutineContext())
              suspendCancellableCoroutine<Boolean> { continuation ->
                Client(requestScope).checkDNS { result ->
                  if (continuation.isActive)
                      continuation.resume(result.getOrNull()?.available == true)
                }
              }
            } == true
        if (!current()) return@launch
        store.log(
            context.getString(
                if (available) R.string.sr_failover_dns_ok else R.string.sr_failover_dns_failed
            )
        )
        if (available) return@launch
        if (attempt < 2) delay(5000)
      }
    }
  }

  fun stop() {
    ready.value = false
    job?.cancel()
    job = null
    dnsJob?.cancel()
    dnsJob = null
  }
}

/** Monitoring an offline node must not depend on its current route advertisement. */
internal fun findFailoverPeer(peers: List<Tailcfg.Node>, id: String): Tailcfg.Node? = peers.find {
  it.StableID == id
}
