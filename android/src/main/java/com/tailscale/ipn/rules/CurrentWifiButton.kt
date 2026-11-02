// Copyright (c) 2026 ScaleRules contributors
// SPDX-License-Identifier: BSD-3-Clause
package com.tailscale.ipn.rules

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.tailscale.ipn.R

@Composable
internal fun CurrentWifiButton(onSsid: (String) -> Unit) {
  val context = LocalContext.current
  var error by remember { mutableStateOf<WifiReadError?>(null) }
  fun applyRead() {
    val result = readCurrentWifi(context)
    error = result.error
    result.ssid?.let(onSsid)
  }
  val permission =
      rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        applyRead()
      }
  Column {
    OutlinedButton(
        onClick = {
          val result = readCurrentWifi(context)
          error = result.error
          if (result.error == WifiReadError.PERMISSION) {
            permission.launch(
                arrayOf(
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                )
            )
          } else result.ssid?.let(onSsid)
        }
    ) {
      Text(stringResource(R.string.sr_use_current_wifi))
    }
    Text(stringResource(R.string.sr_use_current_wifi_hint))
    error?.let {
      Text(
          stringResource(
              when (it) {
                WifiReadError.NO_WIFI -> R.string.sr_current_wifi_none
                WifiReadError.PERMISSION -> R.string.sr_current_wifi_permission
                WifiReadError.LOCATION -> R.string.sr_current_wifi_location
                WifiReadError.UNAVAILABLE -> R.string.sr_current_wifi_unavailable
              }
          )
      )
      if (it == WifiReadError.PERMISSION || it == WifiReadError.LOCATION) {
        TextButton(
            onClick = {
              val intent =
                  if (it == WifiReadError.LOCATION) Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                  else
                      Intent(
                          Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                          Uri.parse("package:${context.packageName}"),
                      )
              try {
                context.startActivity(intent)
              } catch (_: android.content.ActivityNotFoundException) {}
            }
        ) {
          Text(stringResource(R.string.sr_current_wifi_settings))
        }
      }
    }
  }
}
