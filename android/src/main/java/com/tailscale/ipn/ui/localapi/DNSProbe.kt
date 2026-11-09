// Copyright (c) 2026 ScaleRules contributors
// SPDX-License-Identifier: BSD-3-Clause
package com.tailscale.ipn.ui.localapi

import java.util.Base64
import kotlinx.serialization.Serializable

@Serializable
data class DNSProbeResponse(val Bytes: String? = null) {
  val available: Boolean
    get() = dnsReplyAvailable(Bytes)
}

/** NXDOMAIN is a working DNS response too; SERVFAIL/REFUSED and malformed replies are not. */
internal fun dnsReplyAvailable(encoded: String?): Boolean {
  val packet =
      runCatching { Base64.getDecoder().decode(encoded ?: return false) }.getOrNull()
          ?: return false
  if (packet.size < 12) return false
  val response = packet[2].toInt() and 0x80 != 0
  val truncated = packet[2].toInt() and 0x02 != 0
  val code = packet[3].toInt() and 0x0f
  return response && !truncated && (code == 0 || code == 3)
}
