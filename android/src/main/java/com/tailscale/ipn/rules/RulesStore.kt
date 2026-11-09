// Copyright (c) 2026 ScaleRules contributors
// SPDX-License-Identifier: BSD-3-Clause
package com.tailscale.ipn.rules

import android.content.Context
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class RulesStore(context: Context) {
  val preferences = context.getSharedPreferences("scalerules_manual_rules_v1", Context.MODE_PRIVATE)
  private val json = Json { ignoreUnknownKeys = true }
  private val journalJson = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
  }

  fun failover(): ExitNodeFailoverConfig = runCatching {
    json.decodeFromString<ExitNodeFailoverConfig>(preferences.getString("failover", "{}")!!)
  }
      .getOrDefault(ExitNodeFailoverConfig())

  fun saveFailover(config: ExitNodeFailoverConfig) {
    preferences.edit().putString("failover", json.encodeToString(config)).apply()
  }

  fun rules(): List<ConnectionRule> = json.decodeFromString(preferences.getString("rules", "[]")!!)

  fun save(rules: List<ConnectionRule>) {
    preferences.edit().putString("rules", json.encodeToString(rules)).apply()
  }

  fun journal(): List<RuleJournalEntry> =
      journalJson.decodeFromString(preferences.getString("journal", "[]")!!)

  fun log(message: String, category: JournalCategory = JournalCategory.RULES) {
    saveJournal(
        RuleJournal.append(
            journal(),
            RuleJournalEntry(System.currentTimeMillis(), message, category),
        )
    )
  }

  fun clearJournal(category: JournalCategory) {
    saveJournal(RuleJournal.clear(journal(), category))
  }

  private fun saveJournal(entries: List<RuleJournalEntry>) {
    preferences.edit().putString("journal", journalJson.encodeToString(entries)).apply()
  }
}
