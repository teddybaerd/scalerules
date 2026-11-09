// Copyright (c) 2026 ScaleRules contributors
// SPDX-License-Identifier: BSD-3-Clause
package com.tailscale.ipn.ui.localapi

import java.util.Base64
import org.junit.Assert.*
import org.junit.Test

class DNSProbeTest {
  private fun response(flags: Int) =
      Base64.getEncoder()
          .encodeToString(
              ByteArray(12).apply {
                this[2] = (flags shr 8).toByte()
                this[3] = flags.toByte()
              }
          )

  @Test
  fun dnsSuccessAndNxDomainConfirmResolverAvailability() {
    assertTrue(dnsReplyAvailable(response(0x8180)))
    assertTrue(dnsReplyAvailable(response(0x8183)))
  }

  @Test
  fun failedRefusedTruncatedAndMalformedRepliesCannotReportHealthy() {
    for (flags in listOf(0x8182, 0x8185, 0x8380, 0x0100)) {
      assertFalse(dnsReplyAvailable(response(flags)))
    }
    assertFalse(dnsReplyAvailable(null))
    assertFalse(dnsReplyAvailable("not base64!"))
    assertFalse(dnsReplyAvailable(Base64.getEncoder().encodeToString(byteArrayOf(0, 1))))
  }
}
