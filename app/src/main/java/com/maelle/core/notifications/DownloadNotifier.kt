package com.maelle.core.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    fun notifyProgress(
        jobId: String,
        title: String,
        stateLabel: String,
        bytesDownloaded: Long,
        bytesTotal: Long?,
    ): Notification {
        val builder = baseBuilder(android.R.drawable.stat_sys_download, title)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)

        if (bytesTotal != null && bytesTotal > 0L) {
            builder.setProgress(100, ((bytesDownloaded * 100) / bytesTotal).toInt(), false)
            builder.setContentText("$stateLabel - ${percent(bytesDownloaded, bytesTotal)} of ${formatBytes(bytesTotal)}")
        } else {
            builder.setProgress(0, 0, true)
            builder.setContentText(stateLabel)
        }

        return builder.build().also { notify(jobId, it) }
    }

    fun notifyCompleted(jobId: String, title: String) {
        val notification = baseBuilder(android.R.drawable.stat_sys_download_done, title)
            .setContentText("Download complete")
            .setContentIntent(launchAppIntent())
            .setAutoCancel(true)
            .build()
        notify(jobId, notification)
    }

    fun notifyFailed(jobId: String, title: String, reason: String) {
        val notification = baseBuilder(android.R.drawable.stat_notify_error, title)
            .setContentText("Download failed: $reason")
            .setStyle(NotificationCompat.BigTextStyle().bigText("Download failed: $reason"))
            .setContentIntent(launchAppIntent())
            .setAutoCancel(true)
            .build()
        notify(jobId, notification)
    }

    private fun baseBuilder(icon: Int, title: String): NotificationCompat.Builder {
        return NotificationCompat.Builder(context, CHANNEL_DOWNLOADS)
            .setSmallIcon(icon)
            .setContentTitle(title)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
    }

    private fun notify(jobId: String, notification: Notification) {
        runCatching {
            notificationManager().notify(notificationId(jobId), notification)
        }
    }

    private fun launchAppIntent(): android.app.PendingIntent? {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: return null
        return android.app.PendingIntent.getActivity(
            context,
            0,
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            android.app.PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun notificationManager(): NotificationManager {
        return context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    private fun percent(done: Long, total: Long): String {
        val pct = if (total <= 0L) 0 else ((done * 100) / total).coerceIn(0, 100)
        return "$pct%"
    }

    private fun formatBytes(bytes: Long): String {
        val gib = bytes / (1024.0 * 1024.0 * 1024.0)
        val mib = bytes / (1024.0 * 1024.0)
        return if (gib >= 1.0) "%.2f GiB".format(gib) else "%.0f MiB".format(mib)
    }

    companion object {
        const val CHANNEL_DOWNLOADS = "downloads"

        fun notificationId(jobId: String): Int = jobId.hashCode()

        fun ensureChannels(context: Context) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_DOWNLOADS,
                "Downloads",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Download progress and completion"
                setShowBadge(false)
            }
            manager.createNotificationChannel(channel)
        }
    }
}
