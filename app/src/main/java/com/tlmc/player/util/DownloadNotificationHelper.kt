package com.tlmc.player.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.tlmc.player.R
import com.tlmc.player.ui.browser.BrowserActivity

/**
 * 独立的下载进度通知工具：使用独立渠道与通知 ID，避免与 Media3 播放通知冲突，
 * 确保在播放音乐的同时可在通知栏查看实时下载进度。
 */
object DownloadNotificationHelper {

    private const val CHANNEL_ID = "tlmc_downloads"
    private const val CHANNEL_NAME = "文件与离线下载"
    const val NOTIFICATION_ID_BATCH = 2001
    const val NOTIFICATION_ID_SINGLE = 2002

    fun showProgress(
        context: Context,
        notificationId: Int,
        title: String,
        content: String,
        progress: Int,
        max: Int,
        indeterminate: Boolean = false
    ) {
        if (!canPostNotifications(context)) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        ensureChannel(manager)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_music)
            .setContentTitle(title)
            .setContentText(content)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setProgress(max, progress, indeterminate)
            .setContentIntent(createContentIntent(context))

        manager.notify(notificationId, builder.build())
    }

    fun showCompleted(
        context: Context,
        notificationId: Int,
        title: String,
        content: String
    ) {
        if (!canPostNotifications(context)) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        ensureChannel(manager)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_music)
            .setContentTitle(title)
            .setContentText(content)
            .setOngoing(false)
            .setAutoCancel(true)
            .setSilent(true)
            .setProgress(0, 0, false)
            .setContentIntent(createContentIntent(context))

        manager.notify(notificationId, builder.build())
    }

    private fun ensureChannel(manager: NotificationManager) {
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            setShowBadge(false)
            setSound(null, null)
            enableVibration(false)
        }
        manager.createNotificationChannel(channel)
    }

    private fun createContentIntent(context: Context): PendingIntent {
        val intent = Intent(context, BrowserActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        return PendingIntent.getActivity(
            context,
            notificationIdRequestCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun notificationIdRequestCode(): Int = 2000

    private fun canPostNotifications(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }
}
