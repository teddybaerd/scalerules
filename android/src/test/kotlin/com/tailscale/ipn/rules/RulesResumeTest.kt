// Copyright (c) 2026 ScaleRules contributors
// SPDX-License-Identifier: BSD-3-Clause
package com.tailscale.ipn.rules

import org.junit.Assert.assertEquals
import org.junit.Test

class RulesResumeTest {
  @Test
  fun updatesRestoreOnlyPreviouslyActiveAutomationRegardlessOfBootSetting() {
    for (wasActive in listOf(false, true)) {
      for (startOnBoot in listOf(false, true)) {
        assertEquals(wasActive, shouldResumeRules(true, wasActive, startOnBoot))
      }
    }
  }

  @Test
  fun rebootStillRequiresBothOptInAndPreviouslyActiveAutomation() {
    for (wasActive in listOf(false, true)) {
      for (startOnBoot in listOf(false, true)) {
        assertEquals(wasActive && startOnBoot, shouldResumeRules(false, wasActive, startOnBoot))
      }
    }
  }
}
