package com.portfolioos.mobile.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.glance.appwidget.updateAll
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.portfolioos.mobile.MainActivity
import com.portfolioos.mobile.api.SyncApiClient
import com.portfolioos.mobile.data.SnapshotCacheManager
import com.portfolioos.mobile.widget.PortfolioGlanceWidget
import java.util.concurrent.TimeUnit

class PortfolioSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        Log.i(TAG, "Starting portfolio sync worker (runAttemptCount=$runAttemptCount)")

        try {
            // Signal refreshing state to widget
            SnapshotCacheManager.setWidgetStatusOverride(applicationContext, "REFRESHING")
            try {
                PortfolioGlanceWidget().updateAll(applicationContext)
            } catch (e: Exception) {
                Log.d(TAG, "Glance update on start skipped: ${e.message}")
            }

            // Perform actual online network sync
            val snapshot = SyncApiClient.fetchSnapshotOnline(applicationContext)
            Log.i(TAG, "Successfully synced portfolio snapshot. Total invested: ${snapshot.syncInfo?.totalInvested}")

            // Reset any status override and mark online
            SnapshotCacheManager.setWidgetStatusOverride(applicationContext, null)
            SnapshotCacheManager.setFullyOffline(applicationContext, false)

            // CRITICAL: Reset disconnection notification flag so any future disconnect episode can notify once
            SnapshotCacheManager.setDisconnectionNotificationFired(applicationContext, false)

            try {
                PortfolioGlanceWidget().updateAll(applicationContext)
            } catch (e: Exception) {
                Log.w(TAG, "Failed updating widget after sync: ${e.message}")
            }

            return Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Sync failed in worker: ${e.message}", e)

            // Clear refreshing override
            SnapshotCacheManager.setWidgetStatusOverride(applicationContext, null)

            // Strict offline handling:
            // Check age of last successful snapshot:
            // < 36h -> OFFLINE (gray, quiet)
            // >= 36h -> DISCONNECTED (amber/warning)
            val lastSync = SnapshotCacheManager.getLastSyncTimestamp(applicationContext)
            val elapsedMillis = if (lastSync > 0L) System.currentTimeMillis() - lastSync else Long.MAX_VALUE
            val elapsedHours = elapsedMillis / (1000L * 60L * 60L)

            SnapshotCacheManager.setFullyOffline(applicationContext, true)

            if (elapsedHours >= 36) {
                Log.w(TAG, "Snapshot cache is DISCONNECTED (age=${elapsedHours}h >= 36h)")

                // Disconnect Notification Deduplication:
                // Only dispatch a notification ONCE per disconnect episode.
                // Do not spam every 6 hours if core-node remains unreachable.
                val alreadyNotified = SnapshotCacheManager.hasDisconnectionNotificationFired(applicationContext)
                if (!alreadyNotified) {
                    dispatchDisconnectNotification(applicationContext, elapsedHours)
                    SnapshotCacheManager.setDisconnectionNotificationFired(applicationContext, true)
                    Log.i(TAG, "Dispatched single disconnect warning notification for this episode")
                } else {
                    Log.d(TAG, "Suppressing duplicate disconnect notification (already notified for current disconnect episode)")
                }
            } else {
                Log.d(TAG, "Snapshot cache is OFFLINE (age=${elapsedHours}h < 36h)")
            }

            try {
                PortfolioGlanceWidget().updateAll(applicationContext)
            } catch (wEx: Exception) {
                Log.w(TAG, "Failed updating widget after failure: ${wEx.message}")
            }

            return if (runAttemptCount < 3) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }

    companion object {
        private const val TAG = "PortfolioSyncWorker"
        const val PERIODIC_WORK_NAME = "portfolio_os_periodic_sync"
        const val ONETIME_WORK_NAME = "portfolio_os_onetime_sync"
        private const val CHANNEL_ID = "portfolio_os_alerts"
        private const val NOTIFICATION_ID_DISCONNECT = 1001

        fun schedulePeriodicSync(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()

            val periodicRequest = PeriodicWorkRequestBuilder<PortfolioSyncWorker>(6, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                periodicRequest
            )
            Log.i(TAG, "Scheduled periodic portfolio sync (every 6h)")
        }

        fun runOneTimeSync(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val oneTimeRequest = OneTimeWorkRequestBuilder<PortfolioSyncWorker>()
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                ONETIME_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                oneTimeRequest
            )
            Log.i(TAG, "Enqueued one-time portfolio sync")
        }

        fun dispatchDisconnectNotification(context: Context, elapsedHours: Long) {
            try {
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                    ?: return

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val channel = NotificationChannel(
                        CHANNEL_ID,
                        "Portfolio OS Alerts",
                        NotificationManager.IMPORTANCE_DEFAULT
                    ).apply {
                        description = "Critical sync disconnection and valuation alerts"
                    }
                    notificationManager.createNotificationChannel(channel)
                }

                val intent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                val pendingIntent = PendingIntent.getActivity(
                    context,
                    0,
                    intent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )

                val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.stat_notify_error)
                    .setContentTitle("Portfolio OS · Sync Disconnected")
                    .setContentText("Snapshot is ${elapsedHours}h old (>36h). Connect to Wi-Fi/Tailscale to refresh.")
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setContentIntent(pendingIntent)
                    .setAutoCancel(true)

                notificationManager.notify(NOTIFICATION_ID_DISCONNECT, builder.build())
            } catch (e: Exception) {
                Log.e(TAG, "Could not dispatch disconnect notification: ${e.message}", e)
            }
        }
    }
}
