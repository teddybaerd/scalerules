// Copyright (c) 2026 ScaleRules contributors
// SPDX-License-Identifier: BSD-3-Clause
package com.tailscale.ipn.updates

import org.junit.Assert.*
import org.junit.Test

class ReleaseTest {
  private val digest = "a".repeat(64)

  private fun metadata(
      tag: String = "v0.2.0",
      name: String = "scalerules-0.2.0.apk",
      url: String = "${Release.REPOSITORY}/releases/download/$tag/$name",
      hash: String = "sha256:$digest",
      size: Long = 40000000,
      extra: String = "",
  ) =
      """{"draft":false,"prerelease":false,"tag_name":"$tag","body":"Changes","assets":[
    {"name":"$name","browser_download_url":"$url","size":$size,"digest":"$hash"}$extra]}"""

  @Test
  fun comparesVersionsNumerically() {
    assertTrue(ReleaseVersion.parse("0.10.0")!! > ReleaseVersion.parse("v0.2.9")!!)
    assertTrue(ReleaseVersion.parse("1.0.0")!! > ReleaseVersion.parse("0.99.99")!!)
    assertEquals(ReleaseVersion.parse("v0.2.0"), ReleaseVersion.parse("0.2.0"))
    for (invalid in
        listOf(
            "01.2.0",
            "0.2",
            "0.2.0-beta",
            "0.2.0+build",
            " 0.2.0",
            "9999999999999999999999999.0.0",
        )) {
      assertNull(invalid, ReleaseVersion.parse(invalid))
    }
  }

  @Test
  fun acceptsOnlyNamedApkFromOurRepository() {
    val result = Release.parse(metadata())!!
    assertEquals("v0.2.0", result.tag)
    assertEquals(digest, result.sha256)
    assertEquals("Changes", result.notes)
    assertNull(Release.parse(metadata(name = "other.apk")))
    assertNull(Release.parse(metadata(url = "https://evil.example/update.apk")))
    assertNull(
        Release.parse(
            metadata(
                url =
                    "${Release.REPOSITORY}/releases/download/v0.2.0/scalerules-0.2.0.apk?redirect=evil"
            )
        )
    )
    assertNull(Release.parse(metadata().replace("\"draft\":false", "\"draft\":true")))
    assertNull(Release.parse(metadata().replace("\"prerelease\":false", "\"prerelease\":true")))
  }

  @Test
  fun rejectsAmbiguousAssetsMissingDigestAndExcessiveSize() {
    assertNull(Release.parse(metadata(hash = "")))
    assertNull(Release.parse(metadata(hash = "sha256:abc")))
    assertNull(Release.parse(metadata(size = 0)))
    assertNull(Release.parse(metadata(size = Release.MAX_APK_SIZE + 1)))
    assertNull(
        Release.parse(
            metadata(
                extra = "," + metadata().substringAfter("\"assets\":[").substringBeforeLast("]}")
            )
        )
    )
    assertNull(
        Release.parse("""{"draft":false,"prerelease":false,"tag_name":"v0.2.0","assets":[]}""")
    )
  }
}
