// Copyright (c) 2026 ScaleRules contributors
// SPDX-License-Identifier: BSD-3-Clause
package com.tailscale.ipn.rules

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class RuleJournalTest {
  private val json = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
  }

  @Test
  fun oldAndFutureUnrecognizedEntriesRemainSeparateWithoutGuessingFromText() {
    val entries =
        json.decodeFromString<List<RuleJournalEntry>>(
            """[
      {"time":1,"message":"Exit-node failover: switched"},
      {"time":2,"message":"Regelentscheidung","category":"UNKNOWN_FUTURE"}
    ]"""
        )
    assertEquals(2, RuleJournal.select(entries, JournalCategory.LEGACY).size)
    assertTrue(RuleJournal.select(entries, JournalCategory.RULES).isEmpty())
    assertTrue(RuleJournal.select(entries, JournalCategory.FAILOVER).isEmpty())
    assertEquals(
        entries,
        json.decodeFromString<List<RuleJournalEntry>>(json.encodeToString(entries)),
    )
  }

  @Test
  fun persistedCategoriesWorkIndependentlyOfLanguageAndMessageContents() {
    val entries =
        listOf(
            RuleJournalEntry(3, "DNS-Warnung", JournalCategory.FAILOVER),
            RuleJournalEntry(2, "Failover mentioned in a rule name", JournalCategory.RULES),
            RuleJournalEntry(1, "Legacy English message"),
        )
    val restored = json.decodeFromString<List<RuleJournalEntry>>(json.encodeToString(entries))
    assertEquals(listOf(entries[0]), RuleJournal.select(restored, JournalCategory.FAILOVER))
    assertEquals(listOf(entries[1]), RuleJournal.select(restored, JournalCategory.RULES))
    assertEquals(listOf(entries[2]), RuleJournal.select(restored, JournalCategory.LEGACY))
  }

  @Test
  fun clearingOnePagePreservesOtherPageAndHistoricalEntries() {
    val entries =
        listOf(
            RuleJournalEntry(3, "probe", JournalCategory.FAILOVER),
            RuleJournalEntry(2, "start", JournalCategory.RULES),
            RuleJournalEntry(1, "old"),
        )
    for (category in JournalCategory.entries) {
      val remaining = RuleJournal.clear(entries, category)
      assertTrue(RuleJournal.select(remaining, category).isEmpty())
      assertEquals(entries.filter { it.category != category }, remaining)
    }
  }

  @Test
  fun frequentFailoverEventsCannotEvictOtherStreamsAndEachIsBounded() {
    var entries =
        listOf(
            RuleJournalEntry(2, "rule", JournalCategory.RULES),
            RuleJournalEntry(1, "old"),
        )
    repeat(250) {
      entries =
          RuleJournal.append(
              entries,
              RuleJournalEntry(3L + it, "probe $it", JournalCategory.FAILOVER),
          )
    }
    val failover = RuleJournal.select(entries, JournalCategory.FAILOVER)
    assertEquals(200, failover.size)
    assertEquals("probe 249", failover.first().message)
    assertEquals("probe 50", failover.last().message)
    assertEquals(1, RuleJournal.select(entries, JournalCategory.RULES).size)
    assertEquals(1, RuleJournal.select(entries, JournalCategory.LEGACY).size)
  }
}
