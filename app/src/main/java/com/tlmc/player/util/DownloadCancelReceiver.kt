package com.tlmc.player.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.tlmc.player.data.repository.OfflineManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * 响应下载通知栏中的「取消」操作。
 */
@AndroidEntryPoint
class DownloadCancelReceiver : BroadcastReceiver() {

    @Inject
    lateinit var offlineManager: OfflineManager

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_CANCEL_DOWNLOAD) return
        val notificationId = intent.getIntExtra(
            EXTRA_NOTIFICATION_ID,
            DownloadNotificationHelper.NOTIFICATION_ID_BATCH
        )
        if (notificationId == DownloadNotificationHelper.NOTIFICATION_ID_BATCH) {
            offlineManager.cancelDownload()
        }
        DownloadNotificationHelper.cancel(context, notificationId)
    }

    companion object {
        const val ACTION_CANCEL_DOWNLOAD = "com.tlmc.player.ACTION_CANCEL_DOWNLOAD"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
    }
}
