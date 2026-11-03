// Copyright (c) 2026 ScaleRules contributors
// SPDX-License-Identifier: BSD-3-Clause
package com.tailscale.ipn.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.tailscale.ipn.R

object ScaleRulesNotice {
  fun show(
      context: Context,
      channel: String,
      id: Int,
      title: Int,
      text: String,
      intent: Intent,
  ): Boolean {
    if (
        Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
    )
        return false
    val manager = context.getSystemService(NotificationManager::class.java)
    manager.createNotificationChannel(
        NotificationChannel(
            channel,
            context.getString(title),
            NotificationManager.IMPORTANCE_DEFAULT,
        )
    )
    if (
        !NotificationManagerCompat.from(context).areNotificationsEnabled() ||
            manager.getNotificationChannel(channel).importance ==
                NotificationManager.IMPORTANCE_NONE
    )
        return false
    val pending =
        PendingIntent.getActivity(
            context,
            id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    try {
      manager.notify(
          id,
          NotificationCompat.Builder(context, channel)
              .setSmallIcon(R.drawable.ic_tile)
              .setContentTitle(context.getString(title))
              .setContentText(text)
              .setStyle(NotificationCompat.BigTextStyle().bigText(text))
              .setContentIntent(pending)
              .setAutoCancel(true)
              .build(),
      )
      return true
    } catch (_: SecurityException) {
      return false
    }
  }
}
