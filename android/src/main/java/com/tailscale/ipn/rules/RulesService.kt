// Copyright (c) 2026 ScaleRules contributors
// SPDX-License-Identifier: BSD-3-Clause
package com.tailscale.ipn.rules

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.VpnService
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.tailscale.ipn.App
import com.tailscale.ipn.NetworkCandidate
import com.tailscale.ipn.R
import com.tailscale.ipn.pickPreferredNetwork
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A separate foreground observer stays alive even when a rule stops the VPN service. */
class RulesService : Service() {
  private val handler = Handler(Looper.getMainLooper())
  private lateinit var store: RulesStore
  private lateinit var connectivity: ConnectivityManager
  private val failoverScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
  private lateinit var failover: ExitNodeFailoverMonitor
  private var registered = false
  private var lastDecision: Pair<ConnectionContext, RuleDecision>? = null
  private val evaluateTask = Runnable { evaluate() }
  private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
    if (key == "rules" || key == "failover") {
      lastDecision = null
      schedule()
    }
  }
  private val callback =
      object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = schedule()

        override fun onLost(network: Network) = schedule()

        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) =
            schedule()
      }

  override fun onCreate() {
    super.onCreate()
    store = RulesStore(this)
    connectivity = getSystemService(ConnectivityManager::class.java)
    failover = ExitNodeFailoverMonitor(this, store, failoverScope)
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    if (intent?.action == ACTION_PAUSE) {
      store.preferences.edit().putBoolean("resume_after_boot", false).apply()
      stopSelf()
      return START_NOT_STICKY
    }
    val needsSsid = store.rules().any { it.enabled && it.condition == RuleCondition.WIFI_NAME }
    if (
        needsSsid &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) !=
                PackageManager.PERMISSION_GRANTED
    ) {
      if (intent?.action == RulesBootReceiver.ACTION_BOOT) RulesBootReceiver.remind(this)
      store.log(getString(R.string.sr_automation_no_permission))
      stopSelf()
      return START_NOT_STICKY
    }
    getSystemService(NotificationManager::class.java)
        .createNotificationChannel(
            NotificationChannel(
                CHANNEL,
                getString(R.string.sr_automation_name),
                NotificationManager.IMPORTANCE_LOW,
            )
        )
    var types =
        if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
    if (needsSsid && Build.VERSION.SDK_INT >= 29)
        types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
    try {
      ServiceCompat.startForeground(
          this,
          NOTIFICATION_ID,
          notification(getString(R.string.sr_automation_evaluating)),
          types,
      )
      if (!registered) {
        connectivity.registerNetworkCallback(
            NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                .build(),
            callback,
        )
        store.preferences.registerOnSharedPreferenceChangeListener(listener)
        registered = true
        active.value = true
        failover.start()
        store.preferences.edit().putBoolean("resume_after_boot", true).apply()
        getSystemService(NotificationManager::class.java).cancel(7102)
        store.log(getString(R.string.sr_automation_started))
      }
      lastDecision = null
      schedule()
    } catch (e: RuntimeException) {
      if (intent?.action == RulesBootReceiver.ACTION_BOOT) RulesBootReceiver.remind(this)
      store.log(getString(R.string.sr_automation_failed, e.javaClass.simpleName))
      stopSelf()
    }
    // SSID access is a while-in-use permission. Resume explicitly from the visible activity.
    return START_NOT_STICKY
  }

  private fun schedule() {
    handler.post {
      // Cancel pending probes immediately on network/rule changes, before the debounce.
      failover.refresh(false)
      handler.removeCallbacks(evaluateTask)
      handler.postDelayed(evaluateTask, 2000)
    }
  }

  @Suppress("DEPRECATION")
  private fun context(): ConnectionContext {
    val candidates =
        connectivity.allNetworks.mapNotNull { network ->
          connectivity.getNetworkCapabilities(network)?.let { caps ->
            NetworkCandidate(
                value = caps,
                internet = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET),
                notVpn = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN),
                validated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
                hasDns = connectivity.getLinkProperties(network)?.dnsServers?.isNotEmpty() == true,
                nonMetered = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED),
            )
          }
        }
    val network = pickPreferredNetwork(candidates)
    return when {
      network?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> {
        val ssid =
            try {
              applicationContext
                  .getSystemService(WifiManager::class.java)
                  .connectionInfo
                  ?.ssid
                  ?.takeUnless { it == WifiManager.UNKNOWN_SSID || it.isBlank() }
                  ?.removeSurrounding("\"")
            } catch (_: SecurityException) {
              null
            }
        ConnectionContext(ConnectionKind.WIFI, ssid)
      }
      network?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true ->
          ConnectionContext(ConnectionKind.MOBILE)
      network == null -> ConnectionContext(ConnectionKind.OFFLINE)
      else -> ConnectionContext(ConnectionKind.OTHER)
    }
  }

  private fun evaluate() {
    if (!registered) return
    val context = context()
    val decision = RuleEngine.evaluate(store.rules(), context)
    failover.refresh(context.kind != ConnectionKind.OFFLINE && decision.action != RuleAction.DISCONNECT)
    val signature = context to decision
    if (signature == lastDecision) return
    lastDecision = signature
    val app = App.get()
    val result =
        when (decision.action) {
          RuleAction.KEEP -> getString(R.string.sr_vpn_unchanged)
          RuleAction.CONNECT -> {
            when {
              VpnService.prepare(this) != null -> getString(R.string.sr_vpn_permission)
              !app.isAbleToStartVPN() -> getString(R.string.sr_vpn_login)
              app.getAppScopedViewModel().vpnActive.value -> getString(R.string.sr_vpn_connected)
              else -> {
                app.startVPN()
                getString(R.string.sr_vpn_connect_requested)
              }
            }
          }
          RuleAction.DISCONNECT -> {
            if (app.getAppScopedViewModel().vpnActive.value) {
              app.stopVPN()
              getString(R.string.sr_vpn_disconnect_requested)
            } else getString(R.string.sr_vpn_disconnected)
          }
        }
    val reason =
        when (decision.reason) {
          DecisionReason.MATCHED -> decision.rule?.name.orEmpty()
          DecisionReason.NO_MATCH -> getString(R.string.sr_no_match)
          DecisionReason.SSID_UNAVAILABLE -> getString(R.string.sr_ssid_unknown)
        }
    val message = getString(R.string.sr_decision_message, reason, result)
    store.log(message)
    if (
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
    ) {
      getSystemService(NotificationManager::class.java)
          .notify(NOTIFICATION_ID, notification(message))
    }
  }

  private fun notification(message: String) =
      NotificationCompat.Builder(this, CHANNEL)
          .setSmallIcon(R.drawable.ic_tile)
          .setContentTitle(getString(R.string.sr_automation_name))
          .setContentText(message)
          .setStyle(NotificationCompat.BigTextStyle().bigText(message))
          .setOngoing(true)
          .setContentIntent(
              PendingIntent.getActivity(
                  this,
                  0,
                  Intent(this, RulesActivity::class.java),
                  PendingIntent.FLAG_IMMUTABLE,
              )
          )
          .addAction(
              0,
              getString(R.string.sr_pause),
              PendingIntent.getService(
                  this,
                  1,
                  Intent(this, RulesService::class.java).setAction(ACTION_PAUSE),
                  PendingIntent.FLAG_IMMUTABLE,
              ),
          )
          .build()

  override fun onDestroy() {
    if (registered) {
      connectivity.unregisterNetworkCallback(callback)
      store.preferences.unregisterOnSharedPreferenceChangeListener(listener)
      store.log(getString(R.string.sr_automation_stopped))
    }
    failover.stop()
    failoverScope.cancel()
    registered = false
    active.value = false
    handler.removeCallbacksAndMessages(null)
    super.onDestroy()
  }

  override fun onBind(intent: Intent?): IBinder? = null

  companion object {
    private const val CHANNEL = "scalerules_automation"
    private const val NOTIFICATION_ID = 7201
    const val ACTION_PAUSE = "io.github.teddybaerd.scalerules.PAUSE_RULES"
    private val active = MutableStateFlow(false)
    val running = active.asStateFlow()
  }
}
