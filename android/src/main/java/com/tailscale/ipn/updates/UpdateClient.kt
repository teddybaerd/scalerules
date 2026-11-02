// Copyright (c) 2026 ScaleRules contributors
// SPDX-License-Identifier: BSD-3-Clause
package com.tailscale.ipn.updates

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

class UpdateClient(private val context: Context) {
  val preferences = context.getSharedPreferences("scalerules_updates", Context.MODE_PRIVATE)
  val automatic: Boolean
    get() = preferences.getBoolean("automatic", true)

  private fun connection(url: String): HttpURLConnection {
    var target = URL(url)
    repeat(6) {
      require(target.protocol == "https" && target.userInfo == null)
      require(
          target.host in
              setOf(
                  "api.github.com",
                  "github.com",
                  "release-assets.githubusercontent.com",
                  "objects.githubusercontent.com",
              )
      )
      val connection =
          (target.openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = false
            connectTimeout = 15000
            readTimeout = 30000
            setRequestProperty("User-Agent", "ScaleRules-Updater")
            setRequestProperty(
                "Accept",
                if (target.host == "api.github.com") "application/vnd.github+json"
                else "application/octet-stream",
            )
          }
      if (connection.responseCode in setOf(301, 302, 303, 307, 308)) {
        val next = connection.getHeaderField("Location")
        connection.disconnect()
        if (next == null) throw IOException("Missing redirect")
        target = URL(target, next)
      } else return connection
    }
    throw IOException("Too many redirects")
  }

  fun latest(): Release? {
    val connection = connection(Release.API)
    try {
      if (connection.responseCode == 404) return null
      if (connection.responseCode != 200) throw IOException("Release lookup failed")
      val bytes =
          connection.inputStream.use { input ->
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (output.size() <= 1024 * 1024) {
              val count = input.read(buffer)
              if (count < 0) break
              output.write(buffer, 0, count)
            }
            output.toByteArray()
          }
      if (bytes.size > 1024 * 1024) throw IOException("Release response too large")
      // A published release without a usable APK/digest is an error, not 'up to date'.
      return Release.parse(bytes.toString(Charsets.UTF_8))
          ?: throw IOException("Invalid release metadata")
    } finally {
      connection.disconnect()
    }
  }

  suspend fun download(release: Release, progress: (Int) -> Unit): File {
    val directory = File(context.cacheDir, "updates").apply { mkdirs() }
    val temporary = File(directory, "update.part")
    val output = File(directory, "update.apk")
    output.delete()
    temporary.delete()
    val connection = connection(release.downloadUrl)
    try {
      if (connection.responseCode != 200) throw IOException("Download failed")
      val digest = MessageDigest.getInstance("SHA-256")
      var total = 0L
      connection.inputStream.use { input ->
        temporary.outputStream().use { sink ->
          val buffer = ByteArray(65536)
          while (true) {
            currentCoroutineContext().ensureActive()
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            if (total > release.size || total > Release.MAX_APK_SIZE)
                throw IOException("APK too large")
            digest.update(buffer, 0, count)
            sink.write(buffer, 0, count)
            progress((total * 100 / release.size).toInt())
          }
        }
      }
      if (total != release.size || hex(digest.digest()) != release.sha256)
          throw IOException("APK checksum mismatch")
      verifyApk(temporary, release)
      if (!temporary.renameTo(output)) throw IOException("Cannot store update")
      return output
    } finally {
      connection.disconnect()
      temporary.delete()
    }
  }

  @Suppress("DEPRECATION")
  fun verifyApk(file: File, release: Release) {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
      val buffer = ByteArray(65536)
      while (true) {
        val n = input.read(buffer)
        if (n < 0) break
        digest.update(buffer, 0, n)
      }
    }
    if (file.length() != release.size || hex(digest.digest()) != release.sha256)
        throw IOException("APK checksum mismatch")
    val flags =
        if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES
        else PackageManager.GET_SIGNATURES
    val manager = context.packageManager
    val archive =
        manager.getPackageArchiveInfo(file.absolutePath, flags) ?: throw IOException("Invalid APK")
    val installed = manager.getPackageInfo(context.packageName, flags)
    fun code(info: PackageInfo): Long =
        if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
    fun signers(info: PackageInfo): Set<String> =
        (if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners else info.signatures)
            ?.map { hex(MessageDigest.getInstance("SHA-256").digest(it.toByteArray())) }
            ?.toSet()
            .orEmpty()
    val installedSigners = signers(installed)
    if (
        archive.packageName != context.packageName ||
            ReleaseVersion.parse(archive.versionName.orEmpty()) != release.version ||
            code(archive) <= code(installed) ||
            installedSigners.isEmpty() ||
            signers(archive) != installedSigners ||
            ((archive.applicationInfo?.flags ?: 0) and
                android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
    ) {
      throw IOException("APK identity, version or signer mismatch")
    }
    // Android's package installer performs the final cryptographic APK signature verification.
  }

  private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it) }
}
