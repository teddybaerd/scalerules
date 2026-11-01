// Copyright (c) 2026 ScaleRules contributors
// SPDX-License-Identifier: BSD-3-Clause
package com.tailscale.ipn.rules

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable data class RuleJournalEntry(val time: Long, val message: String)

class RulesStore(context: Context) {
  val preferences = context.getSharedPreferences("scalerules_manual_rules_v1", Context.MODE_PRIVATE)
  private val json = Json { ignoreUnknownKeys = true }

  fun rules(): List<ConnectionRule> = json.decodeFromString(preferences.getString("rules", "[]")!!)

  fun save(rules: List<ConnectionRule>) {
    preferences.edit().putString("rules", json.encodeToString(rules)).apply()
  }

  fun journal(): List<RuleJournalEntry> =
      json.decodeFromString(preferences.getString("journal", "[]")!!)

  fun log(message: String) {
    val entries =
        (listOf(RuleJournalEntry(System.currentTimeMillis(), message)) + journal()).take(200)
    preferences.edit().putString("journal", json.encodeToString(entries)).apply()
  }

  fun clearJournal() {
    preferences.edit().remove("journal").apply()
  }
}
