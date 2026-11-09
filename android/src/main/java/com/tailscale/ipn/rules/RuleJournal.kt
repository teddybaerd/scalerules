// Copyright (c) 2026 ScaleRules contributors
// SPDX-License-Identifier: BSD-3-Clause
package com.tailscale.ipn.rules

import kotlinx.serialization.Serializable

@Serializable
enum class JournalCategory {
  RULES,
  FAILOVER,
  LEGACY,
}

@Serializable
data class RuleJournalEntry(
    val time: Long,
    val message: String,
    val category: JournalCategory = JournalCategory.LEGACY,
)

/** Categorization is persisted, never inferred from translated message text. */
object RuleJournal {
  fun select(entries: List<RuleJournalEntry>, category: JournalCategory) = entries.filter {
    it.category == category
  }

  fun clear(entries: List<RuleJournalEntry>, category: JournalCategory) = entries.filterNot {
    it.category == category
  }

  /** Bound each stream separately so frequent failover probes cannot evict rule decisions. */
  fun append(entries: List<RuleJournalEntry>, entry: RuleJournalEntry): List<RuleJournalEntry> {
    val counts = mutableMapOf<JournalCategory, Int>()
    return (listOf(entry) + entries).filter {
      val count = counts.getOrDefault(it.category, 0) + 1
      counts[it.category] = count
      count <= 200
    }
  }
}
