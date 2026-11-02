// Copyright (c) 2026 ScaleRules contributors
// SPDX-License-Identifier: BSD-3-Clause
package com.tailscale.ipn.updates

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import com.tailscale.ipn.BuildConfig
import com.tailscale.ipn.R
import com.tailscale.ipn.ui.theme.AppTheme
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class UpdatesViewModel(application: Application) : AndroidViewModel(application) {
  val client = UpdateClient(application)
  var automatic by mutableStateOf(client.automatic)
    private set

  var release by mutableStateOf<Release?>(null)
    private set

  var busy by mutableStateOf(false)
    private set

  var progress by mutableIntStateOf(-1)
    private set

  var message by mutableIntStateOf(0)
    private set

  var apk by mutableStateOf<File?>(null)
    private set

  fun automatic(enabled: Boolean) {
    automatic = enabled
    client.preferences.edit().putBoolean("automatic", enabled).apply()
  }

  fun check() {
    if (busy) return
    busy = true
    message = 0
    release = null
    apk = null
    viewModelScope.launch {
      try {
        val latest = withContext(Dispatchers.IO) { client.latest() }
        val installed = ReleaseVersion.parse(BuildConfig.VERSION_NAME)
        if (installed == null) message = R.string.su_manual_version
        else if (latest != null && latest.version > installed) release = latest
        else message = R.string.su_current
      } catch (e: CancellationException) {
        throw e
      } catch (_: Exception) {
        message = R.string.su_check_failed
      } finally {
        busy = false
      }
    }
  }

  fun download() {
    val selected = release ?: return
    if (busy) return
    busy = true
    progress = 0
    message = 0
    viewModelScope.launch {
      try {
        apk =
            withContext(Dispatchers.IO) {
              client.download(selected) { value -> viewModelScope.launch { progress = value } }
            }
        message = R.string.su_ready
      } catch (e: CancellationException) {
        throw e
      } catch (_: Exception) {
        apk = null
        message = R.string.su_download_failed
      } finally {
        busy = false
        progress = -1
      }
    }
  }

  fun install(activity: ComponentActivity) {
    val file = apk ?: return
    val selected = release ?: return
    if (busy) return
    if (!activity.packageManager.canRequestPackageInstalls()) {
      try {
        activity.startActivity(
            Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${activity.packageName}"),
            )
        )
        message = R.string.su_permission_hint
      } catch (_: Exception) {
        message = R.string.su_install_failed
      }
      return
    }
    busy = true
    viewModelScope.launch {
      try {
        withContext(Dispatchers.IO) { client.verifyApk(file, selected) }
        val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.updates", file)
        activity.startActivity(
            Intent(Intent.ACTION_VIEW).apply {
              setDataAndType(uri, "application/vnd.android.package-archive")
              addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        )
      } catch (e: CancellationException) {
        throw e
      } catch (_: Exception) {
        apk = null
        file.delete()
        message = R.string.su_install_failed
      } finally {
        busy = false
      }
    }
  }

  fun skip() {
    release?.let { client.preferences.edit().putString("skipped", it.tag).apply() }
    release = null
    message = R.string.su_skipped
  }

  fun later() {
    client.preferences.edit().putLong("deferredUntil", System.currentTimeMillis() + DAY).apply()
  }

  companion object {
    const val DAY = 24L * 60 * 60 * 1000
  }
}

class UpdatesActivity : ComponentActivity() {
  private val model: UpdatesViewModel by viewModels()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    if (savedInstanceState == null) model.check()
    setContent {
      AppTheme {
        Surface {
          Column(
              Modifier.fillMaxSize()
                  .statusBarsPadding()
                  .navigationBarsPadding()
                  .verticalScroll(rememberScrollState())
                  .padding(16.dp),
              verticalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            Text(stringResource(R.string.su_title), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.su_installed, BuildConfig.VERSION_NAME))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
              Text(stringResource(R.string.su_automatic), Modifier.weight(1f))
              Switch(checked = model.automatic, onCheckedChange = model::automatic)
            }
            Text(stringResource(R.string.su_source), style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = model::check, enabled = !model.busy) {
              Text(stringResource(R.string.su_check))
            }
            if (model.busy) {
              LinearProgressIndicator(Modifier.fillMaxWidth())
              Text(
                  if (model.progress >= 0) stringResource(R.string.su_downloading, model.progress)
                  else stringResource(R.string.su_working)
              )
            }
            if (model.message != 0) Text(stringResource(model.message))
            model.release?.let { release ->
              Text(
                  stringResource(R.string.su_available, release.tag),
                  style = MaterialTheme.typography.titleLarge,
              )
              Text(
                  stringResource(R.string.su_changelog),
                  style = MaterialTheme.typography.titleMedium,
              )
              Text(release.notes.ifBlank { stringResource(R.string.su_no_notes) })
              Button(
                  onClick = {
                    if (model.apk == null) model.download() else model.install(this@UpdatesActivity)
                  },
                  enabled = !model.busy,
              ) {
                Text(
                    stringResource(
                        if (model.apk == null) R.string.su_download else R.string.su_install
                    )
                )
              }
              TextButton(
                  onClick = {
                    model.later()
                    finish()
                  },
                  enabled = !model.busy,
              ) {
                Text(stringResource(R.string.su_later))
              }
              TextButton(onClick = model::skip, enabled = !model.busy) {
                Text(stringResource(R.string.su_skip))
              }
            }
            TextButton(
                onClick = {
                  startActivity(
                      Intent(Intent.ACTION_VIEW, Uri.parse("${Release.REPOSITORY}/releases"))
                  )
                }
            ) {
              Text(stringResource(R.string.su_github))
            }
            TextButton(onClick = { finish() }) { Text(stringResource(R.string.su_back)) }
          }
        }
      }
    }
  }
}

/** Check once per day when opening the app. No background job or VPN dependency. */
@Composable
fun UpdatePrompt() {
  val context = LocalContext.current
  val client = remember { UpdateClient(context.applicationContext) }
  var available by remember { mutableStateOf<Release?>(null) }
  val lifecycleOwner = LocalLifecycleOwner.current
  LaunchedEffect(lifecycleOwner) {
    lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
      available = null
      val now = System.currentTimeMillis()
      val preferences = client.preferences
      if (
          !client.automatic ||
              now < preferences.getLong("deferredUntil", 0) ||
              now - preferences.getLong("lastAttempt", 0) < UpdatesViewModel.DAY
      )
          return@repeatOnLifecycle
      val installed = ReleaseVersion.parse(BuildConfig.VERSION_NAME) ?: return@repeatOnLifecycle
      preferences.edit().putLong("lastAttempt", now).apply()
      try {
        val latest = withContext(Dispatchers.IO) { client.latest() }
        if (
            latest != null &&
                latest.version > installed &&
                latest.tag != preferences.getString("skipped", null)
        )
            available = latest
      } catch (e: CancellationException) {
        throw e
      } catch (_: Exception) {
        /* Manual check reports failures. */
      }
    }
  }
  fun later() {
    client.preferences
        .edit()
        .putLong("deferredUntil", System.currentTimeMillis() + UpdatesViewModel.DAY)
        .apply()
    available = null
  }
  available?.let { release ->
    AlertDialog(
        onDismissRequest = { later() },
        title = { Text(stringResource(R.string.su_available, release.tag)) },
        text = {
          Column(Modifier.verticalScroll(rememberScrollState())) {
            Text(
                stringResource(R.string.su_changelog),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(release.notes.ifBlank { stringResource(R.string.su_no_notes) })
          }
        },
        confirmButton = {
          TextButton(
              onClick = {
                available = null
                context.startActivity(Intent(context, UpdatesActivity::class.java))
              }
          ) {
            Text(stringResource(R.string.su_now))
          }
        },
        dismissButton = {
          Row {
            TextButton(onClick = { later() }) { Text(stringResource(R.string.su_later)) }
            TextButton(
                onClick = {
                  client.preferences.edit().putString("skipped", release.tag).apply()
                  available = null
                }
            ) {
              Text(stringResource(R.string.su_skip))
            }
          }
        },
    )
  }
}
