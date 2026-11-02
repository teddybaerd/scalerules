// Copyright (c) 2026 ScaleRules contributors
// SPDX-License-Identifier: BSD-3-Clause
package com.tailscale.ipn.updates

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Test
import org.mockito.kotlin.*

@Suppress("DEPRECATION")
class UpdateClientTest {
  @Test
  fun checksSignerPackageVersionDebugFlagAndDownloadedBytes() {
    val file = File.createTempFile("scalerules-update", ".apk")
    try {
      file.writeText("APK fixture: package manager is mocked, Android verifies actual signatures")
      val digest =
          MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") {
            "%02x".format(it)
          }
      val release = Release(ReleaseVersion(0, 2, 0), "v0.2.0", "", "", file.length(), digest)
      val signature = mock<Signature> { on { toByteArray() } doReturn byteArrayOf(1, 2, 3) }
      val foreignSignature = mock<Signature> { on { toByteArray() } doReturn byteArrayOf(4, 5, 6) }
      val installed =
          mock<PackageInfo>().apply {
            packageName = "io.github.teddybaerd.scalerules"
            versionName = "0.1.0"
            versionCode = 100
            signatures = arrayOf(signature)
          }
      val archive =
          mock<PackageInfo>().apply {
            packageName = installed.packageName
            versionName = "0.2.0"
            versionCode = 101
            signatures = arrayOf(signature)
            applicationInfo = mock<ApplicationInfo>().apply { flags = 0 }
          }
      val manager =
          mock<PackageManager> {
            on { getPackageInfo(installed.packageName, PackageManager.GET_SIGNATURES) } doReturn
                installed
            on { getPackageArchiveInfo(file.absolutePath, PackageManager.GET_SIGNATURES) } doReturn
                archive
          }
      val preferences = mock<SharedPreferences>()
      val context =
          mock<Context> {
            on { packageName } doReturn installed.packageName
            on { packageManager } doReturn manager
            on { getSharedPreferences("scalerules_updates", Context.MODE_PRIVATE) } doReturn
                preferences
          }
      val client = UpdateClient(context)
      client.verifyApk(file, release)
      fun rejected() {
        try {
          client.verifyApk(file, release)
          fail("Invalid APK accepted")
        } catch (_: IOException) {
          /* expected */
        }
      }
      archive.signatures = arrayOf(foreignSignature)
      rejected()
      archive.signatures = arrayOf(signature)
      archive.packageName = "other.package"
      rejected()
      archive.packageName = installed.packageName
      archive.versionName = "0.3.0"
      rejected()
      archive.versionName = "0.2.0"
      archive.versionCode = 100
      rejected()
      archive.versionCode = 101
      archive.applicationInfo!!.flags = ApplicationInfo.FLAG_DEBUGGABLE
      rejected()
      archive.applicationInfo!!.flags = 0
      file.appendText("modified")
      rejected()
    } finally {
      file.delete()
    }
  }
}
