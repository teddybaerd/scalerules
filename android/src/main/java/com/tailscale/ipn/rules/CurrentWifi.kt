// Copyright (c) 2026 ScaleRules contributors
// SPDX-License-Identifier: BSD-3-Clause
package com.tailscale.ipn.rules

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat

internal enum class WifiReadError {
  NO_WIFI,
  PERMISSION,
  LOCATION,
  UNAVAILABLE,
}

internal data class CurrentWifi(val ssid: String? = null, val error: WifiReadError? = null)

/** Preserve significant spaces and remove only Android's enclosing quotation marks. */
internal fun normalizeWifiSsid(raw: String?): String? =
    raw?.takeUnless { it == "<unknown ssid>" }?.removeSurrounding("\"")?.takeUnless { it.isBlank() }

/** One explicit read; no scans, observer registration, persistence or VPN changes. */
@Suppress("DEPRECATION")
internal fun readCurrentWifi(context: Context): CurrentWifi {
  try {
    val connectivity = context.getSystemService(ConnectivityManager::class.java)
    val connected =
        connectivity.allNetworks.any { network ->
          connectivity.getNetworkCapabilities(network)?.let {
            it.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
                it.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
          } == true
        }
    if (!connected) return CurrentWifi(error = WifiReadError.NO_WIFI)
    if (
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED
    )
        return CurrentWifi(error = WifiReadError.PERMISSION)
    if (
        !LocationManagerCompat.isLocationEnabled(
            context.getSystemService(LocationManager::class.java)
        )
    )
        return CurrentWifi(error = WifiReadError.LOCATION)
    val wifi = context.applicationContext.getSystemService(WifiManager::class.java)
    val ssid = normalizeWifiSsid(wifi.connectionInfo?.ssid)
    return if (ssid == null) CurrentWifi(error = WifiReadError.UNAVAILABLE)
    else CurrentWifi(ssid = ssid)
  } catch (_: SecurityException) {
    return CurrentWifi(error = WifiReadError.PERMISSION)
  } catch (_: RuntimeException) {
    return CurrentWifi(error = WifiReadError.UNAVAILABLE)
  }
}
