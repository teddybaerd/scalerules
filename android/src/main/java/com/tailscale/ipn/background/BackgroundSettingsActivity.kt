// Copyright (c) 2026 ScaleRules contributors
// SPDX-License-Identifier: BSD-3-Clause
package com.tailscale.ipn.background

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tailscale.ipn.R
import com.tailscale.ipn.mdm.MDMSettings
import com.tailscale.ipn.ui.theme.AppTheme

private fun isSamsung() = Build.MANUFACTURER.equals("samsung", ignoreCase = true)

private fun appSettings(context: Context) =
    Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.parse("package:${context.packageName}"),
    )

/** Only opens settings. The user changes app-specific restrictions, never a global power policy. */
private fun openSettings(context: Context, intent: Intent): Boolean {
  try {
    context.startActivity(intent)
    return true
  } catch (_: android.content.ActivityNotFoundException) {
    // Device manufacturers may not implement every settings screen.
  } catch (_: SecurityException) {
    // Some OEM screens are not available to third-party apps.
  }
  try {
    context.startActivity(appSettings(context))
    return true
  } catch (_: android.content.ActivityNotFoundException) {
    return false
  } catch (_: SecurityException) {
    return false
  }
}

class BackgroundSettingsActivity : ComponentActivity() {
  private var exempt by mutableStateOf(false)

  override fun onResume() {
    super.onResume()
    exempt = getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContent {
      AppTheme {
        Surface {
          var unavailable by remember { mutableStateOf(false) }
          Column(
              Modifier.fillMaxSize()
                  .statusBarsPadding()
                  .navigationBarsPadding()
                  .verticalScroll(rememberScrollState())
                  .padding(16.dp),
              verticalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            Text(
                stringResource(R.string.sr_background_title),
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(stringResource(R.string.sr_background_explanation))
            Text(
                stringResource(
                    if (exempt) R.string.sr_background_exempt else R.string.sr_background_optimized
                )
            )
            Text(stringResource(R.string.sr_background_unused_steps))
            Text(stringResource(R.string.sr_background_android_steps))
            Button(
                onClick = {
                  unavailable =
                      !openSettings(
                          this@BackgroundSettingsActivity,
                          Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
                      )
                }
            ) {
              Text(stringResource(R.string.sr_background_battery_button))
            }
            OutlinedButton(
                onClick = {
                  unavailable =
                      !openSettings(
                          this@BackgroundSettingsActivity,
                          appSettings(this@BackgroundSettingsActivity),
                      )
                }
            ) {
              Text(stringResource(R.string.sr_background_app_button))
            }
            if (isSamsung()) {
              Text(stringResource(R.string.sr_background_samsung))
              OutlinedButton(
                  onClick = {
                    val intent =
                        Intent("com.samsung.android.sm.ACTION_OPEN_CHECKABLE_LISTACTIVITY")
                            .setPackage("com.samsung.android.lool")
                            .putExtra("activity_type", 2)
                    unavailable = !openSettings(this@BackgroundSettingsActivity, intent)
                  }
              ) {
                Text(stringResource(R.string.sr_background_samsung_button))
              }
            }
            Text(stringResource(R.string.sr_background_limits))
            if (unavailable) Text(stringResource(R.string.sr_background_unavailable))
            TextButton(onClick = { finish() }) { Text(stringResource(R.string.sr_background_back)) }
          }
        }
      }
    }
  }
}

/** Once per installation/upgrade introduction; dismissing never blocks VPN setup or rules. */
@Composable
fun BackgroundSetupPrompt(enabled: Boolean = true): Boolean {
  val context = LocalContext.current
  val prefs = remember {
    context.getSharedPreferences("scalerules_background_setup", Context.MODE_PRIVATE)
  }
  var seen by remember { mutableStateOf(prefs.getBoolean("shown_v2_app_info", false)) }
  DisposableEffect(prefs) {
    val listener =
        android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
          if (key == "shown_v2_app_info") seen = prefs.getBoolean("shown_v2_app_info", false)
        }
    prefs.registerOnSharedPreferenceChangeListener(listener)
    onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
  }
  var unavailable by remember { mutableStateOf(false) }
  val visible = enabled && !MDMSettings.isMDMConfigured && !seen
  fun dismiss() {
    prefs.edit().putBoolean("shown_v2_app_info", true).apply()
    seen = true
  }
  if (visible) {
    AlertDialog(
        onDismissRequest = { dismiss() },
        title = { Text(stringResource(R.string.sr_background_title)) },
        text = {
          Column(Modifier.verticalScroll(rememberScrollState())) {
            Text(stringResource(R.string.sr_background_explanation))
            Text(stringResource(R.string.sr_background_unused_steps))
            Text(stringResource(R.string.sr_background_later_note))
            if (unavailable) Text(stringResource(R.string.sr_background_unavailable))
          }
        },
        confirmButton = {
          TextButton(
              onClick = {
                if (
                    openSettings(
                        context,
                        appSettings(context),
                    )
                )
                    dismiss()
                else unavailable = true
              }
          ) {
            Text(stringResource(R.string.sr_background_app_button))
          }
        },
        dismissButton = {
          TextButton(onClick = { dismiss() }) { Text(stringResource(R.string.sr_background_later)) }
        },
    )
  }
  return visible
}
