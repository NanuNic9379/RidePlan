package io.github.nanunic9379.rideplan.update

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
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import io.github.nanunic9379.rideplan.BuildConfig
import io.github.nanunic9379.rideplan.MainActivity
import io.github.nanunic9379.rideplan.R
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Checks GitHub for a release every 12 hours when online, only after the rider opts in. */
class UpdateCheckWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        if (BuildConfig.DEBUG) return Result.success()
        val release = try {
            GithubUpdater.latestNewer()
        } catch (_: IOException) {
            return Result.retry()
        } catch (_: Exception) {
            return Result.success()
        } ?: return Result.success()

        if (UpdateStore.lastBackgroundNotification(applicationContext) == release.tag || UpdateStore.wasDismissed(applicationContext, release.versionName)) return Result.success()
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            UpdateScheduler.cancel(applicationContext)
            return Result.success()
        }
        UpdateStore.savePending(applicationContext, release)
        try {
            showNotification(release)
        } catch (_: SecurityException) {
            UpdateScheduler.cancel(applicationContext)
            return Result.success()
        }
        UpdateStore.markBackgroundNotification(applicationContext, release.tag)
        return Result.success()
    }

    private fun showNotification(release: ReleaseInfo) {
        val context = applicationContext
        val manager = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, context.getString(R.string.update_notification_channel), NotificationManager.IMPORTANCE_DEFAULT),
            )
        }
        val openApp = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_nav_notification)
            .setContentTitle(context.getString(R.string.update_notification_title))
            .setContentText(context.getString(R.string.update_notification_text, release.versionName))
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    companion object {
        const val INTERVAL_HOURS = 12L
        internal const val CHANNEL = "updates"
        internal const val NOTIFICATION_ID = 34
        private const val WORK_NAME = "background-update-check"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequest.Builder(UpdateCheckWorker::class.java, INTERVAL_HOURS, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setInitialDelay(INTERVAL_HOURS, TimeUnit.HOURS)
                .build()
            WorkManager.getInstance(context.applicationContext)
                .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context.applicationContext).cancelUniqueWork(WORK_NAME)
        }
    }
}
