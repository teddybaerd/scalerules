// Copyright (c) 2026 ScaleRules contributors
// SPDX-License-Identifier: BSD-3-Clause
package com.tailscale.ipn.rules

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import androidx.core.content.ContextCompat
import com.tailscale.ipn.R
import com.tailscale.ipn.util.ScaleRulesNotice

class RulesBootReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
    val store = RulesStore(context)
    if (
        !store.preferences.getBoolean("start_on_boot", false) ||
            !store.preferences.getBoolean("resume_after_boot", false)
    )
        return
    val rules = runCatching {
      store.rules()
    }
        .getOrElse {
          return
        }
    if (rules.none { it.enabled }) return
    val needsSsid = rules.any { it.enabled && it.condition == RuleCondition.WIFI_NAME }
    val backgroundAllowed =
        !needsSsid ||
            Build.VERSION.SDK_INT < 29 ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED
    if (!backgroundAllowed || VpnService.prepare(context) != null) {
      remind(context)
      return
    }
    try {
      ContextCompat.startForegroundService(
          context,
          Intent(context, RulesService::class.java).setAction(ACTION_BOOT),
      )
    } catch (_: RuntimeException) {
      remind(context)
    }
  }

  companion object {
    const val ACTION_BOOT = "io.github.teddybaerd.scalerules.RESUME_AFTER_BOOT"

    fun remind(context: Context) {
      ScaleRulesNotice.show(
          context,
          "scalerules_boot",
          7102,
          R.string.sr_boot_title,
          context.getString(R.string.sr_boot_resume),
          Intent(context, RulesActivity::class.java),
      )
    }
  }
}
