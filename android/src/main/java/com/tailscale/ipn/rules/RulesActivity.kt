// Copyright (c) 2026 ScaleRules contributors
// SPDX-License-Identifier: BSD-3-Clause
package com.tailscale.ipn.rules

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.tailscale.ipn.ui.theme.AppTheme
import java.text.DateFormat
import java.util.Date
import java.util.UUID

class RulesActivity : ComponentActivity() {
  private val permissionRequest =
      registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        val needsSsid =
            RulesStore(this).rules().any { rule ->
              rule.enabled && rule.condition == RuleCondition.WIFI_NAME
            }
        if (
            needsSsid &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) !=
                    PackageManager.PERMISSION_GRANTED
        ) {
          Toast.makeText(
                  this,
                  "WLAN-Regeln benötigen die Freigabe für den genauen Standort, um WLAN-Namen zu lesen.",
                  Toast.LENGTH_LONG,
              )
              .show()
        } else prepareVpn()
      }
  private val vpnRequest =
      registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (VpnService.prepare(this) == null) startObserver()
      }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContent { AppTheme { Surface { RulesScreen() } } }
  }

  private fun resumeRules() {
    val permissions = mutableListOf<String>()
    if (RulesStore(this).rules().any { it.enabled && it.condition == RuleCondition.WIFI_NAME }) {
      permissions += Manifest.permission.ACCESS_COARSE_LOCATION
      permissions += Manifest.permission.ACCESS_FINE_LOCATION
    }
    if (Build.VERSION.SDK_INT >= 33) permissions += Manifest.permission.POST_NOTIFICATIONS
    val missing = permissions.filter {
      ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
    }
    if (missing.isEmpty()) prepareVpn() else permissionRequest.launch(missing.toTypedArray())
  }

  private fun prepareVpn() {
    val intent = VpnService.prepare(this)
    if (intent == null) startObserver() else vpnRequest.launch(intent)
  }

  private fun startObserver() {
    ContextCompat.startForegroundService(this, Intent(this, RulesService::class.java))
  }

  @Composable
  private fun RulesScreen() {
    val store = remember { RulesStore(this) }
    var revision by remember { mutableIntStateOf(0) }
    DisposableEffect(store) {
      val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> revision++ }
      store.preferences.registerOnSharedPreferenceChangeListener(listener)
      onDispose { store.preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    val rules = remember(revision) { store.rules() }
    val journal = remember(revision) { store.journal() }
    val running by RulesService.running.collectAsState()
    var editing by remember { mutableStateOf<ConnectionRule?>(null) }
    var deleting by remember { mutableStateOf<ConnectionRule?>(null) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Text("Verbindungsregeln", style = MaterialTheme.typography.headlineMedium)
      Text(
          "Änderungen pausieren die Automatik. Die erste aktive, passende Regel entscheidet. Ohne Treffer bleibt das VPN unverändert. Es werden keine Regeln automatisch gelernt oder angelegt."
      )
      Button(
          onClick = {
            if (running) stopService(Intent(this@RulesActivity, RulesService::class.java))
            else resumeRules()
          }
      ) {
        Text(if (running) "Automatik pausieren" else "Automatik starten")
      }
      Text(
          if (running) "Automatik aktiv" else "Automatik pausiert",
          style = MaterialTheme.typography.titleMedium,
      )
      Text(
          "Für WLAN-Namen: genauen Standort freigeben und Android-Standort einschalten. Keine GPS-Ortung. Nach Neustart oder Beenden durch Android die Automatik hier erneut starten."
      )
      Button(
          onClick = {
            editing =
                ConnectionRule(
                    UUID.randomUUID().toString(),
                    "",
                    RuleCondition.WIFI_NAME,
                    action = RuleAction.KEEP,
                )
          }
      ) {
        Text("Regel hinzufügen")
      }
      RuleEngine.warnings(rules).forEach { Text(it, color = MaterialTheme.colorScheme.error) }
      if (rules.isEmpty())
          Text(
              "Noch keine Regeln. Lege selbst fest, wann das VPN ein- oder ausgeschaltet werden soll."
          )
      rules.forEachIndexed { index, rule ->
        Card(Modifier.fillMaxWidth()) {
          Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("${index + 1}. ${rule.name}", style = MaterialTheme.typography.titleMedium)
            Text(
                "${conditionLabel(rule.condition)}${if (rule.condition == RuleCondition.WIFI_NAME) ": ${rule.ssid}" else ""} → ${actionLabel(rule.action)}"
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              Switch(
                  checked = rule.enabled,
                  onCheckedChange = { enabled ->
                    stopService(Intent(this@RulesActivity, RulesService::class.java))
                    store.save(
                        rules.map { if (it.id == rule.id) it.copy(enabled = enabled) else it }
                    )
                  },
              )
              TextButton(onClick = { editing = rule }) { Text("Bearbeiten") }
              TextButton(onClick = { deleting = rule }) { Text("Löschen") }
            }
            Row {
              TextButton(enabled = index > 0, onClick = { move(store, rules, index, index - 1) }) {
                Text("Höher ↑")
              }
              TextButton(
                  enabled = index < rules.lastIndex,
                  onClick = { move(store, rules, index, index + 1) },
              ) {
                Text("Tiefer ↓")
              }
            }
          }
        }
      }
      HorizontalDivider()
      Text("Entscheidungsprotokoll", style = MaterialTheme.typography.titleLarge)
      TextButton(onClick = { store.clearJournal() }) { Text("Protokoll leeren") }
      journal.forEach {
        Text(
            "${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM).format(Date(it.time))}\n${it.message}"
        )
      }
      TextButton(onClick = { finish() }) { Text("Zurück") }
    }
    deleting?.let { rule ->
      AlertDialog(
          onDismissRequest = { deleting = null },
          title = { Text("Regel löschen?") },
          text = { Text(rule.name) },
          confirmButton = {
            TextButton(
                onClick = {
                  stopService(Intent(this@RulesActivity, RulesService::class.java))
                  store.save(rules.filterNot { it.id == rule.id })
                  deleting = null
                }
            ) {
              Text("Löschen")
            }
          },
          dismissButton = { TextButton(onClick = { deleting = null }) { Text("Abbrechen") } },
      )
    }
    editing?.let { rule ->
      key(rule.id) {
        RuleEditor(
            rule,
            onDismiss = { editing = null },
            onSave = { updated ->
              // Pause before editing so permission/service types can be rechecked on resume.
              stopService(Intent(this@RulesActivity, RulesService::class.java))
              store.save(
                  if (rules.any { it.id == updated.id })
                      rules.map { if (it.id == updated.id) updated else it }
                  else rules + updated
              )
              editing = null
            },
        )
      }
    }
  }

  private fun move(store: RulesStore, rules: List<ConnectionRule>, from: Int, to: Int) {
    stopService(Intent(this, RulesService::class.java))
    val updated = rules.toMutableList()
    updated.add(to, updated.removeAt(from))
    store.save(updated)
  }
}

private fun conditionLabel(condition: RuleCondition): String =
    when (condition) {
      RuleCondition.WIFI_NAME -> "Bestimmtes WLAN (SSID)"
      RuleCondition.ANY_WIFI -> "Beliebiges WLAN"
      RuleCondition.MOBILE -> "Mobilfunk"
      RuleCondition.OFFLINE -> "Kein Netzwerk"
      RuleCondition.ANY_NETWORK -> "Beliebiges Netzwerk"
    }

private fun actionLabel(action: RuleAction): String =
    when (action) {
      RuleAction.CONNECT -> "VPN einschalten"
      RuleAction.DISCONNECT -> "VPN ausschalten"
      RuleAction.KEEP -> "VPN unverändert lassen"
    }

@Composable
private fun RuleEditor(
    rule: ConnectionRule,
    onDismiss: () -> Unit,
    onSave: (ConnectionRule) -> Unit,
) {
  var name by remember { mutableStateOf(rule.name) }
  var ssid by remember { mutableStateOf(rule.ssid) }
  var condition by remember { mutableStateOf(rule.condition) }
  var action by remember { mutableStateOf(rule.action) }
  var conditionMenu by remember { mutableStateOf(false) }
  var actionMenu by remember { mutableStateOf(false) }
  AlertDialog(
      onDismissRequest = onDismiss,
      title = { Text("Regel bearbeiten") },
      text = {
        Column(
            Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          OutlinedTextField(
              value = name,
              onValueChange = { name = it },
              label = { Text("Regelname") },
              singleLine = true,
          )
          Box {
            TextButton(onClick = { conditionMenu = true }) { Text(conditionLabel(condition)) }
            DropdownMenu(expanded = conditionMenu, onDismissRequest = { conditionMenu = false }) {
              RuleCondition.entries.forEach { item ->
                DropdownMenuItem(
                    text = { Text(conditionLabel(item)) },
                    onClick = {
                      condition = item
                      conditionMenu = false
                    },
                )
              }
            }
          }
          if (condition == RuleCondition.WIFI_NAME) {
            OutlinedTextField(
                value = ssid,
                onValueChange = { ssid = it },
                label = { Text("WLAN-Name (exakt, Groß-/Kleinschreibung)") },
                singleLine = true,
            )
          }
          Box {
            TextButton(onClick = { actionMenu = true }) { Text(actionLabel(action)) }
            DropdownMenu(expanded = actionMenu, onDismissRequest = { actionMenu = false }) {
              RuleAction.entries.forEach { item ->
                DropdownMenuItem(
                    text = { Text(actionLabel(item)) },
                    onClick = {
                      action = item
                      actionMenu = false
                    },
                )
              }
            }
          }
          Text(
              "Speichern pausiert die Automatik. Prüfe danach die Reihenfolge und starte sie erneut."
          )
        }
      },
      confirmButton = {
        TextButton(
            enabled =
                name.isNotBlank() && (condition != RuleCondition.WIFI_NAME || ssid.isNotBlank()),
            onClick = {
              onSave(
                  rule.copy(
                      name = name.trim(),
                      condition = condition,
                      ssid = if (condition == RuleCondition.WIFI_NAME) ssid else "",
                      action = action,
                  )
              )
            },
        ) {
          Text("Speichern")
        }
      },
      dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
  )
}
