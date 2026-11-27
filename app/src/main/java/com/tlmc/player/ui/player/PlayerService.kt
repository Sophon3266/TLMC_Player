package com.tlmc.player.ui.player

import android.app.PendingIntent
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.tlmc.player.data.model.TrackRecord
import com.tlmc.player.data.repository.ConfigManager
import com.tlmc.player.data.repository.LibraryManager
import com.tlmc.player.data.repository.WebDavRepository
import com.tlmc.player.util.FileUtils
import com.tlmc.player.util.FlacCueExtractor
import com.tlmc.player.util.FlacMetadata
import com.tlmc.player.ui.browser.BrowserActivity
import com.tlmc.player.data.model.ServerConfig
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.Credentials
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import java.util.Locale
import javax.inject.Inject

@OptIn(UnstableApi::class)
@AndroidEntryPoint
class PlayerService : MediaSessionService() {

    @Inject
    lateinit var okHttpClient: OkHttpClient

    @Inject
    lateinit var configManager: ConfigManager

    @Inject
    lateinit var audioEqualizer: AudioEqualizer

    @Inject
    lateinit var mediaCache: SimpleCache

    @Inject
    lateinit var repository: WebDavRepository

    @Inject
    lateinit var libraryManager: LibraryManager

    private var lastRecordedHistoryKey: String? = null

    private var mediaSession: MediaSession? = null
    private lateinit var notificationProvider: PlayerMediaNotificationProvider
    private val artworkScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val artworkCache = HashMap<String, ByteArray?>()
    private val artworkLoading = HashSet<String>()
    private val flacMetadataCache = HashMap<String, FlacMetadata?>()
    private val flacMetadataLoading = HashSet<String>()
    private val flacMetadataSemaphore = Semaphore(4)
    private val coverNames = setOf("cover", "folder", "front", "album", "albumart", "artwork")
    private val handler = Handler(Looper.getMainLooper())
    private val saveProgressRunnable = object : Runnable {
        override fun run() {
            mediaSession?.player?.let { player ->
                savePlaybackState(player)
                // 播放中定期刷新通知进度（Media3 默认通知不会随位置持续更新）
                if (player.isPlaying) {
                    refreshNotification()
                }
            }
            handler.postDelayed(this, SAVE_PROGRESS_INTERVAL_MS)
        }
    }
    private var skipNextAutomaticPositionRestore = false
    private var playbackRecoveryAttempts = 0
    private val recoveryRunnable = Runnable { retryPlayback() }
    private val bufferingWatchdog = Runnable { recoverStuckBuffering() }

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()

        notificationProvider = PlayerMediaNotificationProvider(this)
        setMediaNotificationProvider(notificationProvider)

        val config = configManager.getConfig()

        // Create authenticated OkHttpClient for media streaming
        val authenticatedClient = okHttpClient.newBuilder()
            // 媒体流单独使用更短的超时，卡死时尽快失败并触发自动恢复
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("Authorization", Credentials.basic(config.username, config.password))
                    .build()
                chain.proceed(request)
            }
            .build()

        val upstreamFactory = OkHttpDataSource.Factory(authenticatedClient)
        val cacheDataSourceFactory = CacheDataSource.Factory()
            .setCache(mediaCache)
            .setUpstreamDataSourceFactory(upstreamFactory)
            // 缓存异常时回退到网络，避免影响播放
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        // 让本地 content:// 与 file:// 也能播放；http(s) 继续走缓存 + WebDAV 认证
        val dataSourceFactory = DefaultDataSource.Factory(this, cacheDataSourceFactory)
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        val handleAudioFocus = !configManager.isParallelAudioPlayback()

        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .setAudioAttributes(audioAttributes, handleAudioFocus)
            .setHandleAudioBecomingNoisy(true)
            // 后台播放（息屏/切换应用）时保持 CPU 与网络唤醒
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .setSeekBackIncrementMs(15_000L)
            .setSeekForwardIncrementMs(30_000L)
            .build()

        player.addListener(object : Player.Listener {
            @OptIn(UnstableApi::class)
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                audioEqualizer.attach(audioSessionId)
            }

            override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
                saveQueueState(player)
                requestFlacMetadata(player)
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                handler.removeCallbacks(bufferingWatchdog)
                if (skipNextAutomaticPositionRestore) {
                    skipNextAutomaticPositionRestore = false
                } else {
                    restoreSavedPositionForCurrentItem(player)
                }
                // 切换后仍处于缓冲时重新计时，避免新曲目卡住却没有超时保护
                if (player.playWhenReady && player.playbackState == Player.STATE_BUFFERING) {
                    scheduleBufferingWatchdog()
                }
                savePlaybackState(player)
                requestArtwork(player)
                requestFlacMetadata(player)
            }

            override fun onPlayerError(error: PlaybackException) {
                schedulePlaybackRecovery()
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                // 通知栏/蓝牙等后台控制发来的播放指令：若已停止则重新准备
                if (playWhenReady &&
                    player.playbackState == Player.STATE_IDLE &&
                    player.mediaItemCount > 0
                ) {
                    try {
                        player.prepare()
                    } catch (_: Exception) {
                        // 忽略，等待下一次状态回调
                    }
                }
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                if (reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION) {
                    clearCompletedPositionForIndex(player, oldPosition.mediaItemIndex)
                }
                savePlaybackState(player)
            }

            @OptIn(UnstableApi::class)
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_ENDED -> {
                        handler.removeCallbacks(bufferingWatchdog)
                        clearCompletedPositionForCurrentItem(player)
                    }
                    Player.STATE_READY -> {
                        // AudioTrack 已就绪，此时绑定均衡器才能生效
                        audioEqualizer.attach(player.audioSessionId)
                        resetPlaybackRecovery()
                        requestArtwork(player)
                        requestFlacMetadata(player)
                    }
                    Player.STATE_BUFFERING -> {
                        // 切轨/网络波动时通常几秒；超时仍未恢复则重建播放源
                        if (player.playWhenReady) {
                            scheduleBufferingWatchdog()
                        }
                    }
                    Player.STATE_IDLE -> {
                        handler.removeCallbacks(bufferingWatchdog)
                        if (player.playWhenReady && player.mediaItemCount > 0) {
                            schedulePlaybackRecovery()
                        }
                    }
                }
                savePlaybackState(player)
            }

            @OptIn(UnstableApi::class)
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) {
                    audioEqualizer.attach(player.audioSessionId)
                    resetPlaybackRecovery()
                    player.currentMediaItem?.let { item ->
                        TrackRecord.fromMediaItem(item)?.let { record ->
                            if (record.key != lastRecordedHistoryKey) {
                                lastRecordedHistoryKey = record.key
                                libraryManager.recordHistory(record)
                            }
                        }
                    }
                }
            }

            override fun onRepeatModeChanged(repeatMode: Int) {
                saveQueueState(player)
                refreshNotification()
            }

            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                saveQueueState(player)
                refreshNotification()
            }
        })

        restoreQueueState(player)
        requestFlacMetadata(player)

        // 点击通知回到应用，后台播放时仍可继续控制
        val sessionActivity = PendingIntent.getActivity(
            this,
            0,
            Intent(this, BrowserActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(sessionActivity)
            .build()

        handler.post(saveProgressRunnable)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            PlayerMediaNotificationProvider.ACTION_CYCLE_PLAY_ORDER -> {
                mediaSession?.player?.let { cyclePlayOrder(it) }
            }
            ACTION_UPDATE_AUDIO_FOCUS -> {
                val player = mediaSession?.player as? ExoPlayer
                if (player != null) {
                    val audioAttributes = AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                        .build()
                    player.setAudioAttributes(audioAttributes, !configManager.isParallelAudioPlayback())
                }
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        artworkScope.cancel()
        handler.removeCallbacks(saveProgressRunnable)
        handler.removeCallbacks(recoveryRunnable)
        handler.removeCallbacks(bufferingWatchdog)
        mediaSession?.run {
            savePlaybackState(player)
            player.release()
            release()
        }
        mediaSession = null
        audioEqualizer.detach()
        super.onDestroy()
    }

    // ==================== Playback Recovery ====================

    /**
     * 播放出错（多为网络中断/超时）后延迟重试。仅在用户要求播放时进行，
     * 且限制重试次数，避免无限循环。
     */
    private fun schedulePlaybackRecovery() {
        val player = mediaSession?.player ?: return
        if (!player.playWhenReady || player.mediaItemCount == 0) return
        if (playbackRecoveryAttempts >= MAX_PLAYBACK_RECOVERY_ATTEMPTS) return
        handler.removeCallbacks(recoveryRunnable)
        handler.postDelayed(recoveryRunnable, PLAYBACK_RECOVERY_DELAY_MS)
    }

    private fun retryPlayback() {
        val player = mediaSession?.player ?: return
        if (!player.playWhenReady || player.mediaItemCount == 0) return
        // 只处理真正停止的状态；缓冲中或已恢复时交给正常流程
        if (player.playbackState != Player.STATE_IDLE) return
        playbackRecoveryAttempts++
        try {
            player.prepare()
        } catch (_: Exception) {
            // 忽略，下一次状态回调仍会触发重试
        }
    }

    private fun scheduleBufferingWatchdog() {
        handler.removeCallbacks(bufferingWatchdog)
        handler.postDelayed(bufferingWatchdog, STUCK_BUFFERING_TIMEOUT_MS)
    }

    /**
     * 卡在缓冲状态超过阈值时，用当前播放位置重建播放列表并重新准备媒体源，
     * 相当于自动完成“手动点一下恢复”的操作。
     */
    private fun recoverStuckBuffering() {
        val player = mediaSession?.player ?: return
        if (!player.playWhenReady || player.mediaItemCount == 0) return
        if (player.playbackState != Player.STATE_BUFFERING) return
        if (playbackRecoveryAttempts >= MAX_PLAYBACK_RECOVERY_ATTEMPTS) return

        playbackRecoveryAttempts++
        val index = player.currentMediaItemIndex.coerceAtLeast(0)
        val position = player.currentPosition.coerceAtLeast(0L)
        val items = (0 until player.mediaItemCount).map { player.getMediaItemAt(it) }
        try {
            player.setMediaItems(items, index, position)
            player.prepare()
        } catch (_: Exception) {
            // 忽略，下一次状态回调仍会触发重试
        }
    }

    /**
     * 主动刷新通知：播放顺序图标与播放进度不在 Media3 默认的通知刷新事件里。
     */
    private fun refreshNotification() {
        val session = mediaSession ?: return
        notificationProvider.refresh(session)
    }

    private fun resetPlaybackRecovery() {
        playbackRecoveryAttempts = 0
        handler.removeCallbacks(recoveryRunnable)
        handler.removeCallbacks(bufferingWatchdog)
    }

    /**
     * 处理通知栏“播放顺序”按钮：顺序 → 列表循环 → 单曲循环 → 随机 → 顺序。
     */
    private fun cyclePlayOrder(player: Player) {
        try {
            when {
                player.shuffleModeEnabled -> {
                    player.shuffleModeEnabled = false
                    player.repeatMode = Player.REPEAT_MODE_OFF
                }
                player.repeatMode == Player.REPEAT_MODE_ONE -> {
                    player.shuffleModeEnabled = true
                    player.repeatMode = Player.REPEAT_MODE_ALL
                }
                player.repeatMode == Player.REPEAT_MODE_ALL -> {
                    player.repeatMode = Player.REPEAT_MODE_ONE
                }
                else -> {
                    player.repeatMode = Player.REPEAT_MODE_ALL
                }
            }
        } catch (_: Exception) {
            // 忽略不支持该命令的播放器状态
        }
    }

    // ==================== Artwork ====================

    /**
     * 为当前曲目补充封面：
     * - 本地 content/file：读取音频内嵌封面；
     * - WebDAV / CUE：查找同目录 cover/folder/front/album 图片。
     */
    private fun requestArtwork(player: Player) {
        val index = player.currentMediaItemIndex
        val mediaItem = player.currentMediaItem ?: return
        if (mediaItem.mediaMetadata.artworkData != null) return
        val key = artworkKey(mediaItem) ?: return

        var shouldLoad = false
        var cached: ByteArray? = null
        synchronized(artworkCache) {
            if (artworkCache.containsKey(key)) {
                cached = artworkCache[key]
            } else if (artworkLoading.add(key)) {
                shouldLoad = true
            }
        }

        if (!shouldLoad) {
            cached?.let { applyArtwork(player, index, key, it) }
            return
        }

        artworkScope.launch {
            val bytes = try {
                loadArtwork(mediaItem, key)
            } catch (_: Exception) {
                null
            }
            handler.post {
                synchronized(artworkCache) {
                    artworkCache[key] = bytes
                    artworkLoading.remove(key)
                }
                if (bytes != null) {
                    val currentPlayer = mediaSession?.player ?: return@post
                    applyArtwork(currentPlayer, index, key, bytes)
                }
            }
        }
    }

    private fun artworkKey(mediaItem: MediaItem): String? {
        val mediaId = mediaItem.mediaId
        if (mediaId.startsWith("content://") || mediaId.startsWith("file://")) {
            return "local:$mediaId"
        }
        val directory = if (mediaId.isNotEmpty() && mediaId.contains('/')) {
            FileUtils.getDirectoryPath(mediaId)
        } else {
            mediaItem.mediaMetadata.extras
                ?.getString(PlayerViewModel.KEY_DIRECTORY_PATH)
                .orEmpty()
        }
        return directory.takeIf { it.isNotEmpty() }?.let { "remote:$it" }
    }

    private suspend fun loadArtwork(mediaItem: MediaItem, key: String): ByteArray? {
        val mediaId = mediaItem.mediaId
        if (mediaId.startsWith("content://") || mediaId.startsWith("file://")) {
            return extractEmbeddedArtwork(Uri.parse(mediaId))
        }
        return loadRemoteCover(key.removePrefix("remote:"))
    }

    private fun extractEmbeddedArtwork(uri: Uri): ByteArray? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(this, uri)
            retriever.embeddedPicture
        } catch (_: Exception) {
            null
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
                // 忽略
            }
        }
    }

    private suspend fun loadRemoteCover(directory: String): ByteArray? {
        val files = repository.listFiles(directory).getOrNull() ?: return null
        val cover = files
            .filter {
                it.isImage &&
                    it.nameWithoutExtension.lowercase(Locale.ROOT) in coverNames
            }
            .sortedBy { it.name.lowercase(Locale.ROOT) }
            .firstOrNull() ?: return null
        return repository.downloadFile(cover.path).getOrNull()
    }

    private fun applyArtwork(
        player: Player,
        mediaItemIndex: Int,
        key: String,
        bytes: ByteArray
    ) {
        if (player.currentMediaItemIndex != mediaItemIndex) return
        val mediaItem = player.currentMediaItem ?: return
        if (artworkKey(mediaItem) != key) return
        if (mediaItem.mediaMetadata.artworkData != null) return

        val metadata = mediaItem.mediaMetadata.buildUpon()
            .setArtworkData(bytes, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
            .build()
        val updated = mediaItem.buildUpon()
            .setMediaMetadata(metadata)
            .build()
        try {
            player.replaceMediaItem(mediaItemIndex, updated)
        } catch (_: Exception) {
            // 封面更新失败不影响播放
        }
    }

    // ==================== FLAC Metadata ====================

    /**
     * 为播放列表中的非 CUE FLAC 曲目异步提取内嵌曲名、歌手与专辑信息，
     * 使播放列表与播放界面优先显示内嵌标签而非文件名；若无内嵌曲名则保留文件名。
     */
    private fun requestFlacMetadata(player: Player) {
        val count = player.mediaItemCount
        if (count == 0) return

        val currentIdx = player.currentMediaItemIndex
        val orderedIndices = buildList {
            if (currentIdx in 0 until count) add(currentIdx)
            for (i in 0 until count) {
                if (i != currentIdx) add(i)
            }
        }

        for (index in orderedIndices) {
            val mediaItem = player.getMediaItemAt(index)
            if (isCueClippedItem(mediaItem)) continue
            if (mediaItem.mediaMetadata.extras?.getBoolean(KEY_FLAC_METADATA_RESOLVED, false) == true) continue

            val key = flacMetadataKey(mediaItem) ?: continue

            var shouldLoad = false
            var hasCached = false
            var cached: FlacMetadata? = null
            synchronized(flacMetadataCache) {
                if (flacMetadataCache.containsKey(key)) {
                    hasCached = true
                    cached = flacMetadataCache[key]
                } else if (flacMetadataLoading.add(key)) {
                    shouldLoad = true
                }
            }

            if (hasCached) {
                applyFlacMetadata(player, index, key, cached)
                continue
            }

            if (!shouldLoad) continue

            artworkScope.launch {
                val metadata = flacMetadataSemaphore.withPermit {
                    try {
                        loadFlacMetadata(mediaItem, key)
                    } catch (_: Exception) {
                        null
                    }
                }
                handler.post {
                    synchronized(flacMetadataCache) {
                        flacMetadataCache[key] = metadata
                        flacMetadataLoading.remove(key)
                    }
                    val currentPlayer = mediaSession?.player ?: return@post
                    for (i in 0 until currentPlayer.mediaItemCount) {
                        val item = currentPlayer.getMediaItemAt(i)
                        if (flacMetadataKey(item) == key) {
                            applyFlacMetadata(currentPlayer, i, key, metadata)
                        }
                    }
                }
            }
        }
    }

    private fun isCueClippedItem(mediaItem: MediaItem): Boolean {
        val clip = mediaItem.clippingConfiguration
        return clip.startPositionMs > 0L || clip.endPositionMs != C.TIME_END_OF_SOURCE
    }

    private fun flacMetadataKey(mediaItem: MediaItem): String? {
        val mediaId = mediaItem.mediaId
        val uriStr = mediaItem.localConfiguration?.uri?.toString().orEmpty()
        val mimeType = mediaItem.localConfiguration?.mimeType.orEmpty()
        val title = mediaItem.mediaMetadata.title?.toString().orEmpty()

        val isFlac = mimeType.contains("flac", ignoreCase = true) ||
            mediaId.lowercase(Locale.ROOT).endsWith(".flac") ||
            uriStr.lowercase(Locale.ROOT).endsWith(".flac") ||
            title.lowercase(Locale.ROOT).endsWith(".flac")

        if (mediaId.startsWith("content://") || mediaId.startsWith("file://")) {
            return if (isFlac || mediaItem.mediaMetadata.artist.isNullOrEmpty()) "local:$mediaId" else null
        }
        if (mediaId.isNotEmpty() && isFlac) {
            return "remote:$mediaId"
        }
        return null
    }

    private suspend fun loadFlacMetadata(mediaItem: MediaItem, key: String): FlacMetadata? {
        if (key.startsWith("local:")) {
            val uri = Uri.parse(key.removePrefix("local:"))
            val headerBytes = try {
                contentResolver.openInputStream(uri)?.use { input ->
                    val buf = ByteArray(2_097_152)
                    val read = input.read(buf)
                    if (read > 0) buf.copyOf(read) else ByteArray(0)
                }
            } catch (_: Exception) {
                null
            }
            val flacMeta = headerBytes?.let { FlacCueExtractor.extractMetadata(it) }
            if (flacMeta != null && (flacMeta.title.isNotEmpty() || flacMeta.artist.isNotEmpty())) {
                return flacMeta
            }
            return extractLocalMetadataWithRetriever(uri)
        }
        val remotePath = key.removePrefix("remote:")
        return repository.loadFlacMetadata(remotePath).getOrNull()
    }

    private fun extractLocalMetadataWithRetriever(uri: Uri): FlacMetadata? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(this, uri)
            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.trim().orEmpty()
            val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)?.trim()
                ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)?.trim().orEmpty()
            val album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)?.trim().orEmpty()
            FlacMetadata(title = title, artist = artist, album = album)
        } catch (_: Exception) {
            null
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
                // 忽略
            }
        }
    }

    private fun applyFlacMetadata(
        player: Player,
        mediaItemIndex: Int,
        key: String,
        metadata: FlacMetadata?
    ) {
        if (mediaItemIndex !in 0 until player.mediaItemCount) return
        val mediaItem = player.getMediaItemAt(mediaItemIndex)
        if (flacMetadataKey(mediaItem) != key) return
        if (mediaItem.mediaMetadata.extras?.getBoolean(KEY_FLAC_METADATA_RESOLVED, false) == true) return

        val metaBuilder = mediaItem.mediaMetadata.buildUpon()
        if (metadata != null) {
            if (metadata.title.isNotBlank()) {
                metaBuilder.setTitle(metadata.title)
            }
            if (metadata.artist.isNotBlank()) {
                metaBuilder.setArtist(metadata.artist)
            }
            if (metadata.album.isNotBlank() && mediaItem.mediaMetadata.albumTitle.isNullOrEmpty()) {
                metaBuilder.setAlbumTitle(metadata.album)
            }
        }
        val newExtras = Bundle(mediaItem.mediaMetadata.extras ?: Bundle()).apply {
            putBoolean(KEY_FLAC_METADATA_RESOLVED, true)
        }
        metaBuilder.setExtras(newExtras)

        val updated = mediaItem.buildUpon()
            .setMediaMetadata(metaBuilder.build())
            .build()
        try {
            player.replaceMediaItem(mediaItemIndex, updated)
        } catch (_: Exception) {
            // 元数据更新失败不影响播放
        }
    }

    private fun savePlaybackState(player: Player) {
        saveQueueState(player)

        val mediaItem = player.currentMediaItem ?: return
        val playbackKey = mediaItem.toPlaybackKey()
        if (playbackKey.isBlank()) return

        val position = player.currentPosition.coerceAtLeast(0L)
        configManager.saveAudioPlaybackPosition(playbackKey, position)
    }

    private fun saveQueueState(player: Player) {
        if (player.mediaItemCount == 0) {
            configManager.clearAudioQueueState()
            return
        }

        val items = buildList {
            for (index in 0 until player.mediaItemCount) {
                add(player.getMediaItemAt(index).toSavedAudioItem())
            }
        }

        configManager.saveAudioQueueState(
            items = items,
            currentIndex = player.currentMediaItemIndex.coerceAtLeast(0)
        )
    }

    private fun restoreQueueState(player: ExoPlayer) {
        val queueState = configManager.getAudioQueueState() ?: return
        if (queueState.items.isEmpty()) return

        val mediaItems = queueState.items.map { it.toMediaItem() }
        val currentIndex = queueState.currentIndex.coerceIn(0, mediaItems.lastIndex)
        val currentItem = mediaItems[currentIndex]
        val savedPosition = configManager.getAudioPlaybackPosition(currentItem.toPlaybackKey())

        skipNextAutomaticPositionRestore = savedPosition > 0L
        player.setMediaItems(mediaItems, currentIndex, savedPosition)
        player.prepare()
    }

    private fun restoreSavedPositionForCurrentItem(player: Player) {
        val mediaItem = player.currentMediaItem ?: return
        if (player.currentPosition > POSITION_RESTORE_MAX_CURRENT_MS) return

        val savedPosition = configManager.getAudioPlaybackPosition(mediaItem.toPlaybackKey())
        if (savedPosition <= 0L) return

        // 上次播放停在结尾附近时不再恢复，避免切轨后立刻结束、来回跳转
        val duration = player.duration
        if (duration > 0L && savedPosition >= duration - POSITION_RESTORE_END_MARGIN_MS) {
            configManager.clearAudioPlaybackPosition(mediaItem.toPlaybackKey())
            return
        }

        player.seekTo(savedPosition)
    }

    private fun clearCompletedPositionForCurrentItem(player: Player) {
        val mediaItem = player.currentMediaItem ?: return
        if (player.playbackState == Player.STATE_ENDED) {
            configManager.clearAudioPlaybackPosition(mediaItem.toPlaybackKey())
        }
    }

    private fun clearCompletedPositionForIndex(player: Player, mediaItemIndex: Int) {
        if (mediaItemIndex !in 0 until player.mediaItemCount) return
        val mediaItem = player.getMediaItemAt(mediaItemIndex)
        configManager.clearAudioPlaybackPosition(mediaItem.toPlaybackKey())
    }

    private fun MediaItem.toSavedAudioItem(): ConfigManager.SavedAudioItem {
        val clip = clippingConfiguration
        return ConfigManager.SavedAudioItem(
            mediaId = mediaId,
            uri = localConfiguration?.uri?.toString().orEmpty(),
            title = mediaMetadata.title?.toString().orEmpty(),
            artist = mediaMetadata.artist?.toString().orEmpty(),
            albumTitle = mediaMetadata.albumTitle?.toString().orEmpty(),
            trackNumber = mediaMetadata.trackNumber,
            clipStartMs = clip.startPositionMs,
            clipEndMs = clip.endPositionMs,
            directoryPath = mediaMetadata.extras
                ?.getString(PlayerViewModel.KEY_DIRECTORY_PATH)
                .orEmpty()
        )
    }

    private fun ConfigManager.SavedAudioItem.toMediaItem(): MediaItem {
        val builder = MediaItem.Builder()
            .setMediaId(mediaId)
            .setUri(uri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(artist)
                    .setAlbumTitle(albumTitle)
                    .apply {
                        trackNumber?.let { setTrackNumber(it) }
                        if (directoryPath.isNotEmpty()) {
                            setExtras(Bundle().apply {
                                putString(PlayerViewModel.KEY_DIRECTORY_PATH, directoryPath)
                            })
                        }
                    }
                    .build()
            )

        if (clipStartMs > 0L || clipEndMs != C.TIME_END_OF_SOURCE) {
            val clippingBuilder = MediaItem.ClippingConfiguration.Builder()
                .setStartPositionMs(clipStartMs.coerceAtLeast(0L))
            if (clipEndMs != C.TIME_END_OF_SOURCE) {
                clippingBuilder.setEndPositionMs(clipEndMs)
            }
            builder.setClippingConfiguration(clippingBuilder.build())
        }

        return builder.build()
    }

    private fun MediaItem.toPlaybackKey(): String {
        val clip = clippingConfiguration
        val base = if (mediaId.isNotBlank()) mediaId else localConfiguration?.uri?.toString().orEmpty()
        return listOf(base, clip.startPositionMs.toString(), clip.endPositionMs.toString()).joinToString("|")
    }

    companion object {
        const val ACTION_UPDATE_AUDIO_FOCUS = "com.tlmc.player.ACTION_UPDATE_AUDIO_FOCUS"
        private const val SAVE_PROGRESS_INTERVAL_MS = 5_000L
        private const val POSITION_RESTORE_MAX_CURRENT_MS = 1_000L
        private const val POSITION_RESTORE_END_MARGIN_MS = 5_000L
        private const val MAX_PLAYBACK_RECOVERY_ATTEMPTS = 3
        private const val PLAYBACK_RECOVERY_DELAY_MS = 1_500L
        private const val STUCK_BUFFERING_TIMEOUT_MS = 20_000L
        private const val KEY_FLAC_METADATA_RESOLVED = "tlmc_flac_metadata_resolved"
    }
}

