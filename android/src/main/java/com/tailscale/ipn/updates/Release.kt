// Copyright (c) 2026 ScaleRules contributors
// SPDX-License-Identifier: BSD-3-Clause
package com.tailscale.ipn.updates

import kotlinx.serialization.json.*

/** Stable releases only; unknown or prerelease installed versions require a manual update. */
data class ReleaseVersion(val major: Long, val minor: Long, val patch: Long) :
    Comparable<ReleaseVersion> {
  override fun compareTo(other: ReleaseVersion): Int =
      compareValuesBy(this, other, { it.major }, { it.minor }, { it.patch })

  companion object {
    fun parse(value: String): ReleaseVersion? {
      val match =
          Regex("v?(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)").matchEntire(value)
              ?: return null
      val numbers = match.groupValues.drop(1).map { it.toLongOrNull() ?: return null }
      return ReleaseVersion(numbers[0], numbers[1], numbers[2])
    }
  }
}

data class Release(
    val version: ReleaseVersion,
    val tag: String,
    val notes: String,
    val downloadUrl: String,
    val size: Long,
    val sha256: String,
) {
  companion object {
    const val REPOSITORY = "https://github.com/teddybaerd/scalerules"
    const val API = "https://api.github.com/repos/teddybaerd/scalerules/releases/latest"
    const val MAX_APK_SIZE = 150L * 1024 * 1024

    fun parse(json: String): Release? {
      val root = Json.parseToJsonElement(json).jsonObject
      if (
          root["draft"]?.jsonPrimitive?.boolean != false ||
              root["prerelease"]?.jsonPrimitive?.boolean != false
      )
          return null
      val tag = root["tag_name"]?.jsonPrimitive?.content ?: return null
      val version = ReleaseVersion.parse(tag) ?: return null
      val name = "scalerules-${tag.removePrefix("v")}.apk"
      val asset =
          root["assets"]
              ?.jsonArray
              ?.map { it.jsonObject }
              ?.singleOrNull {
                it["name"]?.jsonPrimitive?.content == name
              } ?: return null
      val url = asset["browser_download_url"]?.jsonPrimitive?.content ?: return null
      if (url != "$REPOSITORY/releases/download/$tag/$name") return null
      val size = asset["size"]?.jsonPrimitive?.longOrNull ?: return null
      if (size !in 1..MAX_APK_SIZE) return null
      val digest = asset["digest"]?.jsonPrimitive?.content ?: return null
      if (!Regex("sha256:[0-9a-f]{64}").matches(digest)) return null
      return Release(
          version,
          tag,
          root["body"]?.jsonPrimitive?.contentOrNull.orEmpty().take(50000),
          url,
          size,
          digest.removePrefix("sha256:"),
      )
    }
  }
}
