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
    val afterUpdate = intent.action == Intent.ACTION_MY_PACKAGE_REPLACED
    if (!afterUpdate && intent.action != Intent.ACTION_BOOT_COMPLETED) return
    val store = RulesStore(context)
    if (
        !shouldResumeRules(
            afterUpdate,
            store.preferences.getBoolean("resume_after_boot", false),
            store.preferences.getBoolean("start_on_boot", false),
        )
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
      remind(context, afterUpdate)
      return
    }
    try {
      ContextCompat.startForegroundService(
          context,
          Intent(context, RulesService::class.java)
              .setAction(if (afterUpdate) ACTION_UPDATE else ACTION_BOOT),
      )
    } catch (_: RuntimeException) {
      remind(context, afterUpdate)
    }
  }

  companion object {
    const val ACTION_BOOT = "io.github.teddybaerd.scalerules.RESUME_AFTER_BOOT"

    const val ACTION_UPDATE = "io.github.teddybaerd.scalerules.RESUME_AFTER_UPDATE"

    fun remind(context: Context, afterUpdate: Boolean = false) {
      ScaleRulesNotice.show(
          context,
          "scalerules_boot",
          7102,
          if (afterUpdate) R.string.sr_update_resume_title else R.string.sr_boot_title,
          context.getString(
              if (afterUpdate) R.string.sr_update_resume else R.string.sr_boot_resume
          ),
          Intent(context, RulesActivity::class.java),
      )
    }
  }
}

/** Updates restore the saved active state independently of the optional boot setting. */
internal fun shouldResumeRules(afterUpdate: Boolean, wasActive: Boolean, startOnBoot: Boolean) =
    wasActive && (afterUpdate || startOnBoot)
