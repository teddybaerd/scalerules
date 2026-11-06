// Copyright (c) 2026 ScaleRules contributors
// SPDX-License-Identifier: BSD-3-Clause
package com.tailscale.ipn.rules

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tailscale.ipn.R
import com.tailscale.ipn.ui.notifier.Notifier

@Composable
fun ExitNodeFailoverSettings(
    saved: ExitNodeFailoverConfig,
    onSave: (ExitNodeFailoverConfig) -> Unit,
) {
  val netmap by Notifier.netmap.collectAsState()
  val prefs by Notifier.prefs.collectAsState()
  val selfID = netmap?.SelfNode?.StableID.orEmpty()
  val controlURL = prefs?.ControlURL.orEmpty()
  val sameProfile = saved.selfID == selfID && saved.controlURL == controlURL
  val config = if (sameProfile) saved else ExitNodeFailoverConfig(controlURL = controlURL, selfID = selfID)
  val nodes = netmap?.Peers.orEmpty().filter { it.isExitNode }.sortedBy { it.displayName }
  val available = selfID.isNotBlank() && controlURL.isNotBlank()
  Card {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Text(stringResource(R.string.sr_failover_title), style = MaterialTheme.typography.titleLarge)
      Text(stringResource(R.string.sr_failover_hint))
      if (!available) Text(stringResource(R.string.sr_failover_login))
      if (saved.enabled && !sameProfile) Text(stringResource(R.string.sr_failover_other_profile))
      FailoverChoice(
          stringResource(R.string.sr_failover_primary),
          nodes.find { it.StableID == config.primaryID }?.displayName
              ?: stringResource(R.string.sr_failover_choose),
          nodes.filter { it.StableID != config.fallbackID }.map { it.StableID to it.displayName },
      ) { onSave(config.copy(primaryID = it, enabled = false)) }
      FailoverChoice(
          stringResource(R.string.sr_failover_fallback),
          nodes.find { it.StableID == config.fallbackID }?.displayName
              ?: stringResource(R.string.sr_failover_choose),
          nodes.filter { it.StableID != config.primaryID }.map { it.StableID to it.displayName },
      ) { onSave(config.copy(fallbackID = it, enabled = false)) }
      Row(verticalAlignment = Alignment.CenterVertically) {
        Switch(
            checked = config.enabled,
            enabled = available && config.copy(enabled = true).valid &&
                nodes.any { it.StableID == config.primaryID } &&
                nodes.any { it.StableID == config.fallbackID },
            onCheckedChange = { onSave(config.copy(enabled = it)) },
        )
        Text(stringResource(R.string.sr_failover_enable))
      }
      Row(verticalAlignment = Alignment.CenterVertically) {
        Switch(checked = config.autoReturn,
            onCheckedChange = { onSave(config.copy(autoReturn = it)) })
        Text(stringResource(R.string.sr_failover_auto_return))
      }
      FailoverChoice(
          stringResource(R.string.sr_failover_failures), config.failureCount.toString(),
          (2..5).map { it.toString() to it.toString() },
      ) { onSave(config.copy(failureCount = it.toInt())) }
      FailoverChoice(
          stringResource(R.string.sr_failover_grace),
          stringResource(R.string.sr_failover_seconds, config.graceSeconds),
          listOf(15, 30, 60, 120).map {
            it.toString() to stringResource(R.string.sr_failover_seconds, it)
          },
      ) { onSave(config.copy(graceSeconds = it.toInt())) }
      FailoverChoice(
          stringResource(R.string.sr_failover_return_delay),
          stringResource(R.string.sr_failover_seconds, config.returnSeconds),
          listOf(60, 120, 300, 600).map {
            it.toString() to stringResource(R.string.sr_failover_seconds, it)
          },
      ) { onSave(config.copy(returnSeconds = it.toInt())) }
      Text(stringResource(R.string.sr_failover_safety))
    }
  }
}

@Composable
private fun FailoverChoice(
    label: String,
    value: String,
    choices: List<Pair<String, String>>,
    onSelect: (String) -> Unit,
) {
  var expanded by remember { mutableStateOf(false) }
  Box {
    OutlinedButton(onClick = { expanded = true }, enabled = choices.isNotEmpty()) {
      Text("$label: $value")
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
      choices.forEach { (id, name) ->
        DropdownMenuItem(text = { Text(name) }, onClick = {
          expanded = false
          onSelect(id)
        })
      }
    }
  }
}
