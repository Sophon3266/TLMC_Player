package com.tlmc.player.ui.player

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Parcelable
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.media.app.NotificationCompat.MediaStyle
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import com.google.common.collect.ImmutableList
import com.tlmc.player.R

/**
 * 播放通知：在默认 Media3 通知基础上补充：
 * - 锁屏公开可见的独立通知渠道、标题/艺术家/专辑与大图标
 * - 播放顺序（顺序/列表循环/单曲循环/随机）切换按钮
 * - 通过标准 MediaStyle + MediaSession 暴露系统可拖动的进度条
 *
 * 播放顺序变化时通过 [refresh] 重建通知以立即更新按钮图标。
 */
@OptIn(UnstableApi::class)
class PlayerMediaNotificationProvider(
    context: Context
) : DefaultMediaNotificationProvider(
    context,
    DefaultMediaNotificationProvider.NotificationIdProvider { NOTIFICATION_ID },
    CHANNEL_ID,
    R.string.media_notification_channel_name
) {

    private val appContext = context.applicationContext

    private var lastSession: MediaSession? = null
    private var lastActionFactory: MediaNotification.ActionFactory? = null

    private val fallbackLargeIcon: Bitmap by lazy(LazyThreadSafetyMode.NONE) {
        val size = (48 * appContext.resources.displayMetrics.density).toInt().coerceAtLeast(48)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        runCatching {
            val drawable = appContext.packageManager.getApplicationIcon(appContext.applicationInfo)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, size, size)
            drawable.draw(canvas)
        }
        bitmap
    }

    init {
        ensurePublicChannel()
    }

    override fun getMediaButtons(
        session: MediaSession,
        playerCommands: Player.Commands,
        customLayout: ImmutableList<CommandButton>,
        showPauseButton: Boolean
    ): ImmutableList<CommandButton> {
        val buttons = ImmutableList.builder<CommandButton>()
        if (playerCommands.containsAny(
                Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
                Player.COMMAND_SEEK_TO_PREVIOUS
            )
        ) {
            buttons.add(
                playerButton(
                    Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
                    R.drawable.ic_previous,
                    "上一首"
                )
            )
        }
        if (playerCommands.contains(Player.COMMAND_PLAY_PAUSE)) {
            buttons.add(
                playerButton(
                    Player.COMMAND_PLAY_PAUSE,
                    if (showPauseButton) R.drawable.ic_pause else R.drawable.ic_play,
                    if (showPauseButton) "暂停" else "播放"
                )
            )
        }
        if (playerCommands.containsAny(
                Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                Player.COMMAND_SEEK_TO_NEXT
            )
        ) {
            buttons.add(
                playerButton(
                    Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                    R.drawable.ic_next,
                    "下一首"
                )
            )
        }
        return buttons.build()
    }

    override fun addNotificationActions(
        mediaSession: MediaSession,
        mediaButtons: ImmutableList<CommandButton>,
        builder: NotificationCompat.Builder,
        actionFactory: MediaNotification.ActionFactory
    ): IntArray {
        lastSession = mediaSession
        lastActionFactory = actionFactory

        val player = mediaSession.player
        applyProgress(builder, player)

        builder.setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        builder.setCategory(NotificationCompat.CATEGORY_TRANSPORT)
        builder.setLargeIcon(fallbackLargeIcon)
        if (player.isCommandAvailable(Player.COMMAND_GET_METADATA)) {
            player.mediaMetadata.albumTitle?.let { builder.setSubText(it) }
        }

        val compactViewIndices = super.addNotificationActions(
            mediaSession,
            mediaButtons,
            builder,
            actionFactory
        )
        builder.addAction(
            NotificationCompat.Action(
                orderIconRes(player),
                "播放顺序",
                playOrderPendingIntent()
            )
        )
        return compactViewIndices
    }

    /**
     * 主动重建并更新通知：用于播放顺序切换后立即刷新按钮图标。
     */
    fun refresh(session: MediaSession) {
        val actionFactory = lastActionFactory ?: return
        if (lastSession !== session) return

        val player = session.player
        // 通知已被系统移除且未在播放时，不要重新弹出来
        if (!isNotificationActive() && !player.playWhenReady) return

        try {
            val notification = buildRefreshedNotification(session, actionFactory)
            NotificationManagerCompat.from(appContext).notify(NOTIFICATION_ID, notification)
        } catch (_: Exception) {
            // 通知刷新失败不应影响播放
        }
    }

    /**
     * 重新构建完整通知（含新的按钮图标），避免复用旧 Builder 导致播放顺序图标不更新。
     */
    private fun buildRefreshedNotification(
        session: MediaSession,
        actionFactory: MediaNotification.ActionFactory
    ): Notification {
        val player = session.player
        val metadata = if (player.isCommandAvailable(Player.COMMAND_GET_METADATA)) {
            player.mediaMetadata
        } else {
            null
        }
        val showPause = player.playWhenReady && player.playbackState != Player.STATE_ENDED

        val builder = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_music)
            .setContentTitle(
                metadata?.title?.toString()?.ifEmpty { "未知曲目" } ?: "未知曲目"
            )
            .setContentText(
                metadata?.artist?.toString()
                    ?: metadata?.albumTitle?.toString()
                    ?: ""
            )
            .setSubText(metadata?.albumTitle?.toString())
            .setLargeIcon(fallbackLargeIcon)
            .setContentIntent(session.sessionActivity)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setOnlyAlertOnce(true)
            .setOngoing(false)
            .setShowWhen(false)
            .setStyle(
                MediaStyle()
                    .setMediaSession(session.getSessionCompatToken())
                    .setShowActionsInCompactView(0, 1, 2)
            )

        applyProgress(builder, player)

        builder.addAction(
            NotificationCompat.Action(
                R.drawable.ic_previous,
                "上一首",
                actionFactory.createMediaActionPendingIntent(
                    session,
                    Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM.toLong()
                )
            )
        )
        builder.addAction(
            NotificationCompat.Action(
                if (showPause) R.drawable.ic_pause else R.drawable.ic_play,
                if (showPause) "暂停" else "播放",
                actionFactory.createMediaActionPendingIntent(
                    session,
                    Player.COMMAND_PLAY_PAUSE.toLong()
                )
            )
        )
        builder.addAction(
            NotificationCompat.Action(
                R.drawable.ic_next,
                "下一首",
                actionFactory.createMediaActionPendingIntent(
                    session,
                    Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM.toLong()
                )
            )
        )
        builder.addAction(
            NotificationCompat.Action(
                orderIconRes(player),
                "播放顺序",
                playOrderPendingIntent()
            )
        )

        val notification = builder.build()
        val token = session.getSessionCompatToken().token
        if (token is Parcelable) {
            notification.extras.putParcelable(Notification.EXTRA_MEDIA_SESSION, token)
        }
        return notification
    }

    private fun applyProgress(builder: NotificationCompat.Builder, player: Player) {
        val duration = player.duration
        if (duration <= 0L) return
        val max = duration.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        val position = player.currentPosition
            .coerceIn(0L, duration)
            .coerceAtMost(max.toLong())
            .toInt()
        builder.setProgress(max, position, false)
    }

    private fun playerButton(
        command: Int,
        iconRes: Int,
        label: String
    ): CommandButton {
        return CommandButton.Builder()
            .setPlayerCommand(command)
            .setIconResId(iconRes)
            .setDisplayName(label)
            .build()
    }

    private fun playOrderPendingIntent(): PendingIntent {
        val intent = Intent(appContext, PlayerService::class.java)
            .setAction(ACTION_CYCLE_PLAY_ORDER)
        return PendingIntent.getService(
            appContext,
            1002,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun orderIconRes(player: Player): Int {
        return when {
            player.shuffleModeEnabled -> R.drawable.ic_shuffle
            player.repeatMode == Player.REPEAT_MODE_ONE -> R.drawable.ic_repeat_one
            player.repeatMode == Player.REPEAT_MODE_ALL -> R.drawable.ic_repeat
            else -> R.drawable.ic_sequential
        }
    }

    private fun isNotificationActive(): Boolean {
        return try {
            appContext.getSystemService(NotificationManager::class.java)
                ?.activeNotifications
                ?.any { it.id == NOTIFICATION_ID } == true
        } catch (_: Exception) {
            true
        }
    }

    private fun ensurePublicChannel() {
        val manager = appContext.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            appContext.getString(R.string.media_notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setSound(null, null)
            enableVibration(false)
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val ACTION_CYCLE_PLAY_ORDER = "com.tlmc.player.CYCLE_PLAY_ORDER"
        private const val CHANNEL_ID = "tlmc_playback"
        private const val NOTIFICATION_ID = 1001
    }
}
