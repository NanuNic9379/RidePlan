package io.github.nanunic9379.rideplan

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import io.github.nanunic9379.rideplan.nav.NavNotice

/**
 * Keeps navigation going with the screen off or another app open (#65). A foreground service of
 * type location with an ongoing notification (next turn, what is left, Stop): while it runs,
 * Android keeps delivering GPS fixes to the navigation and lets the voice speak. It does not read
 * the position itself, and it runs only from Navigate to Exit, the finish or the idle pause.
 * No Google services: the F-Droid build is the same.
 */
class NavigationService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            onStopRequested?.invoke()
            hide(this)
            return START_NOT_STICKY
        }
        val notice = current
        val type = if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0
        try {
            // Always called after startForegroundService, also when the notice is already gone.
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(this, notice ?: NavNotice("", "", "", "")), type)
        } catch (e: Exception) {
            // Location permission withdrawn, or started from the background: navigation goes on
            // while the app is on screen, as before #65.
            stopSelf()
            return START_NOT_STICKY
        }
        if (notice == null) stopSelf()
        return START_NOT_STICKY
    }

    /** The app was swiped away from the recent apps: navigation ends with it. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        onStopRequested?.invoke()
        hide(this)
        super.onTaskRemoved(rootIntent)
    }

    companion object {
        private const val CHANNEL = "navigation"
        private const val NOTIFICATION_ID = 65
        private const val ACTION_STOP = "io.github.nanunic9379.rideplan.STOP_NAVIGATION"

        /** What the notification shows; null when navigation is not running in the background. */
        @Volatile
        private var current: NavNotice? = null

        /** The Stop action of the notification, or the app swiped away. */
        @Volatile
        var onStopRequested: (() -> Unit)? = null

        /** Starts the service, or updates its notification when it already runs. */
        fun show(context: Context, notice: NavNotice) {
            val app = context.applicationContext
            val running = current != null
            current = notice
            if (running) {
                app.getSystemService(NotificationManager::class.java)?.notify(NOTIFICATION_ID, notification(app, notice))
            } else {
                runCatching { ContextCompat.startForegroundService(app, Intent(app, NavigationService::class.java)) }
                    .onFailure { current = null }
            }
        }

        fun hide(context: Context) {
            val app = context.applicationContext
            val wasRunning = current != null
            current = null
            if (!wasRunning) return
            app.stopService(Intent(app, NavigationService::class.java))
            app.getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION_ID)
        }

        private fun notification(context: Context, notice: NavNotice): Notification {
            val manager = context.getSystemService(NotificationManager::class.java)
            // Low importance: no sound and no pop-up, the voice already speaks. Creating it again
            // only renames it, so it follows the app language.
            manager?.createNotificationChannel(NotificationChannel(CHANNEL, notice.channel.ifEmpty { "Navigation" }, NotificationManager.IMPORTANCE_LOW))
            val open = context.packageManager.getLaunchIntentForPackage(context.packageName)
                ?.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            val stop = PendingIntent.getService(
                context, 1, Intent(context, NavigationService::class.java).setAction(ACTION_STOP), flags,
            )
            return NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_nav_notification)
                .setContentTitle(notice.title)
                .setContentText(notice.text.ifEmpty { null })
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setShowWhen(false)
                .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                .apply { if (open != null) setContentIntent(PendingIntent.getActivity(context, 0, open, flags)) }
                .addAction(0, notice.stop.ifEmpty { "Stop" }, stop)
                .build()
        }
    }
}
