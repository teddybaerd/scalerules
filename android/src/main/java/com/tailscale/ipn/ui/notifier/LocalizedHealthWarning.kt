// Copyright (c) 2026 ScaleRules contributors
// SPDX-License-Identifier: BSD-3-Clause
package com.tailscale.ipn.ui.notifier

import android.content.Context
import com.tailscale.ipn.R
import com.tailscale.ipn.ui.model.Health

/** Localize known codes at display time; retain original diagnostics for copying. */
fun localizedHealthWarning(context: Context, warning: Health.UnhealthyState): Pair<String, String> =
    when (warning.WarnableCode) {
      "dns-forward-failing" ->
          context.getString(R.string.sr_dns_unavailable_title) to
              context.getString(R.string.sr_dns_unavailable_text)
      else -> warning.Title to warning.Text
    }
