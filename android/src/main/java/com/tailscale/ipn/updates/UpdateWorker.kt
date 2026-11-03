// Copyright (c) 2026 ScaleRules contributors
// SPDX-License-Identifier: BSD-3-Clause
package com.tailscale.ipn.updates

import android.content.Context
import android.content.Intent
import androidx.work.*
import com.tailscale.ipn.BuildConfig
import com.tailscale.ipn.R
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class UpdateWorker(context: Context, parameters: WorkerParameters) :
    CoroutineWorker(context, parameters) {
  override suspend fun doWork(): Result =
      withContext(Dispatchers.IO) {
        val client = UpdateClient(applicationContext)
        if (!client.automatic) return@withContext Result.success()
        try {
          val installed =
              ReleaseVersion.parse(BuildConfig.VERSION_NAME) ?: return@withContext Result.success()
          val latest = client.latest() ?: return@withContext Result.success()
          if (
              latest.version > installed &&
                  client.automatic &&
                  client.preferences.getString("notified", null) != latest.tag
          ) {
            if (
                com.tailscale.ipn.util.ScaleRulesNotice.show(
                    applicationContext,
                    "scalerules_updates",
                    7101,
                    R.string.su_notification_title,
                    applicationContext.getString(R.string.su_notification_body, latest.tag),
                    Intent(applicationContext, UpdatesActivity::class.java),
                )
            ) {
              client.preferences.edit().putString("notified", latest.tag).apply()
            }
          }
          Result.success()
        } catch (e: CancellationException) {
          throw e
        } catch (_: Exception) {
          Result.retry()
        }
      }

  companion object {
    fun schedule(context: Context) {
      val work = WorkManager.getInstance(context)
      if (!UpdateClient(context).automatic) {
        work.cancelUniqueWork("scalerules_update_check")
        androidx.core.app.NotificationManagerCompat.from(context).cancel(7101)
        return
      }
      val request =
          PeriodicWorkRequestBuilder<UpdateWorker>(12, TimeUnit.HOURS)
              .setConstraints(
                  Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
              )
              .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
              .build()
      work.enqueueUniquePeriodicWork(
          "scalerules_update_check",
          ExistingPeriodicWorkPolicy.KEEP,
          request,
      )
    }
  }
}
