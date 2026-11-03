// Copyright (c) 2026 ScaleRules contributors
// SPDX-License-Identifier: BSD-3-Clause
package com.tailscale.ipn.rules

import org.junit.Assert.*
import org.junit.Test

class CurrentWifiTest {
  @Test
  fun rejectsUnavailableNames() {
    for (raw in listOf(null, "", " ", "\"\"", "<unknown ssid>")) {
      assertNull(normalizeWifiSsid(raw))
    }
  }

  @Test
  fun removesOnlyEnclosingAndroidQuotes() {
    assertEquals("Home Wi-Fi", normalizeWifiSsid("\"Home Wi-Fi\""))
    assertEquals("Home Wi-Fi", normalizeWifiSsid("Home Wi-Fi"))
    assertEquals("  home  ", normalizeWifiSsid("\"  home  \""))
    assertEquals("Müller \"Home\"", normalizeWifiSsid("\"Müller \"Home\"\""))
  }
}
