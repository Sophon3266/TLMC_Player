package com.tlmc.player.ui.player

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.tlmc.player.R
import com.tlmc.player.data.model.PlayMode
import com.tlmc.player.data.model.TrackRecord
import com.tlmc.player.data.repository.LibraryManager
import com.tlmc.player.databinding.ActivityPlayerBinding
import com.tlmc.player.ui.browser.BrowserActivity
import com.tlmc.player.ui.browser.BrowserPlaylistAdapter
import com.tlmc.player.ui.browser.PlaylistItem
import com.tlmc.player.util.ArtworkUtils
import com.tlmc.player.util.FileUtils
import com.tlmc.player.util.LrcParser
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class PlayerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_FILE_PATH = "extra_file_path"
        const val EXTRA_FILE_NAME = "extra_file_name"
        const val EXTRA_CUE_PATH = "extra_cue_path"
        const val EXTRA_DIRECTORY_PATH = "extra_directory_path"
    }

    private lateinit var binding: ActivityPlayerBinding
    private val viewModel: PlayerViewModel by viewModels()

    @Inject
    lateinit var audioEqualizer: AudioEqualizer

    @Inject
    lateinit var libraryManager: LibraryManager

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null
    private lateinit var trackAdapter: TrackAdapter
    private lateinit var lyricsAdapter: LyricsAdapter
    private var currentLyricsLineIndex = -1

    private var currentPlayMode: PlayMode = PlayMode.SEQUENTIAL
    private var isSeeking = false
    private var eqDialog: AlertDialog? = null

    // true when opened from CUE file (needs to load & set media items)
    private var isCueMode = false

    private val handler = Handler(Looper.getMainLooper())
    private val updateProgressRunnable = object : Runnable {
        override fun run() {
            updateProgress()
            handler.postDelayed(this, 500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        val cuePath = intent.getStringExtra(EXTRA_CUE_PATH)
        isCueMode = cuePath != null

        if (isCueMode) {
            val filePath = intent.getStringExtra(EXTRA_FILE_PATH)
            val fileName = intent.getStringExtra(EXTRA_FILE_NAME) ?: "Unknown"
            val directoryPath = intent.getStringExtra(EXTRA_DIRECTORY_PATH) ?: "/"
            supportActionBar?.title = fileName
            viewModel.initialize(filePath, cuePath, directoryPath)
        } else {
            supportActionBar?.title = "正在播放"
        }

        setupTrackList()
        setupControls()
        setupFavoriteButton()
        observeViewModel()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_player, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_add_to_playlist -> {
                showAddToPlaylistDialog()
                true
            }
            R.id.action_pick_lyrics -> {
                showPickLyricsDialog()
                true
            }
            R.id.action_equalizer -> {
                showEqualizerDialog()
                true
            }
            R.id.action_navigate_to_folder -> {
                navigateToCurrentFolder()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onStart() {
        super.onStart()
        connectToService()
    }

    override fun onStop() {
        super.onStop()
        eqDialog?.dismiss()
        handler.removeCallbacks(updateProgressRunnable)
        controllerFuture?.let { MediaController.releaseFuture(it) }
        mediaController = null
    }

    private fun connectToService() {
        val sessionToken = SessionToken(this, ComponentName(this, PlayerService::class.java))
        controllerFuture = MediaController.Builder(this, sessionToken).buildAsync().also { future ->
            future.addListener({
                try {
                    mediaController = future.get()
                    onControllerConnected()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }, MoreExecutors.directExecutor())
        }
    }

    private fun onControllerConnected() {
        mediaController?.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                runOnUiThread { updatePlayPauseButton(isPlaying) }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                runOnUiThread { updateCurrentTrackInfo() }
            }

            override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                runOnUiThread { updateCurrentTrackInfo() }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                runOnUiThread { updatePlaybackState(playbackState) }
            }

            override fun onRepeatModeChanged(repeatMode: Int) {
                runOnUiThread { syncPlayModeFromController() }
            }

            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                runOnUiThread { syncPlayModeFromController() }
            }

            override fun onTracksChanged(tracks: Tracks) {
                runOnUiThread { updateAudioFormatInfo(tracks) }
            }
        })

        if (isCueMode) {
            // CUE mode: if ViewModel already has media items, set them
            viewModel.mediaItems.value?.let { items ->
                if (items.isNotEmpty()) {
                    setMediaItems(items)
                }
            }
        } else {
            // Pure UI mode: just read current state from controller
            runOnUiThread {
                updateCurrentTrackInfo()
                mediaController?.let { updatePlayPauseButton(it.isPlaying) }
            }
        }

        runOnUiThread { syncPlayModeFromController() }
        handler.post(updateProgressRunnable)
    }

    private fun setupTrackList() {
        trackAdapter = TrackAdapter { position ->
            mediaController?.let { controller ->
                controller.seekTo(position, 0)
                if (controller.playbackState == Player.STATE_IDLE) {
                    controller.prepare()
                }
                controller.play()
            }
        }
        binding.trackRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@PlayerActivity)
            adapter = trackAdapter
        }

        // Setup lyrics RecyclerView
        lyricsAdapter = LyricsAdapter { lineIndex ->
            // Click on a lyrics line to seek to that time
            val lines = viewModel.lyricsLines.value ?: return@LyricsAdapter
            if (lineIndex in lines.indices) {
                mediaController?.seekTo(lines[lineIndex].timeMs)
            }
        }
        binding.lyricsRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@PlayerActivity)
            adapter = lyricsAdapter
        }
    }

    private fun setupControls() {
        binding.btnPlayPause.setOnClickListener {
            mediaController?.let { controller ->
                if (controller.isPlaying) {
                    controller.pause()
                } else {
                    if (controller.playbackState == Player.STATE_IDLE) {
                        controller.prepare()
                    }
                    controller.play()
                }
            }
        }

        binding.btnPrevious.setOnClickListener {
            mediaController?.let { controller ->
                controller.seekToPreviousMediaItem()
                if (controller.playbackState == Player.STATE_IDLE) {
                    controller.prepare()
                }
            }
        }

        binding.btnNext.setOnClickListener {
            mediaController?.let { controller ->
                controller.seekToNextMediaItem()
                if (controller.playbackState == Player.STATE_IDLE) {
                    controller.prepare()
                }
            }
        }

        binding.btnPlayMode.setOnClickListener {
            togglePlayMode()
        }

        binding.btnPlaylist.setOnClickListener {
            showPlaylistDialog()
        }

        binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    binding.currentTime.text = formatTime(progress.toLong())
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                isSeeking = true
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                isSeeking = false
                seekBar?.let {
                    mediaController?.seekTo(it.progress.toLong())
                }
            }
        })
    }

    private fun observeViewModel() {
        viewModel.tracks.observe(this) { tracks ->
            if (tracks.isNotEmpty()) {
                binding.trackRecyclerView.visibility = View.VISIBLE
                trackAdapter.submitList(tracks)
            } else {
                binding.trackRecyclerView.visibility = View.GONE
            }
        }

        viewModel.mediaItems.observe(this) { items ->
            if (isCueMode && items.isNotEmpty()) {
                mediaController?.let { setMediaItems(items) }
            }
        }

        viewModel.albumTitle.observe(this) { title ->
            binding.albumTitle.text = title
        }

        viewModel.albumArtist.observe(this) { artist ->
            binding.albumArtist.text = artist
        }

        viewModel.isLoading.observe(this) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        }

        viewModel.error.observe(this) { error ->
            error?.let {
                binding.errorText.text = it
                binding.errorText.visibility = View.VISIBLE
            } ?: run {
                binding.errorText.visibility = View.GONE
            }
        }

        // Observe lyrics
        viewModel.lyricsLines.observe(this) { lines ->
            if (lines.isNotEmpty()) {
                binding.lyricsRecyclerView.visibility = View.VISIBLE
                lyricsAdapter.setLyrics(lines)
                currentLyricsLineIndex = -1
            } else {
                binding.lyricsRecyclerView.visibility = View.GONE
                lyricsAdapter.setLyrics(emptyList())
                currentLyricsLineIndex = -1
            }
        }
    }

    private fun setMediaItems(items: List<MediaItem>) {
        mediaController?.let { controller ->
            controller.setMediaItems(items)
            controller.prepare()
            controller.play()
        }
    }

    private fun updateProgress() {
        mediaController?.let { controller ->
            val position = controller.currentPosition
            val duration = controller.duration
            if (duration > 0 && !isSeeking) {
                binding.seekBar.max = duration.toInt()
                binding.seekBar.progress = position.toInt()
                binding.currentTime.text = formatTime(position)
                binding.totalTime.text = formatTime(duration)
            }
            // Update lyrics highlight
            updateLyricsPosition(position)
        }
    }

    private fun updatePlayPauseButton(isPlaying: Boolean) {
        binding.btnPlayPause.setImageResource(
            if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
        )
    }

    private fun updateCurrentTrackInfo() {
        mediaController?.let { controller ->
            val mediaItem = controller.currentMediaItem
            val title = mediaItem?.mediaMetadata?.title?.toString() ?: ""
            val artist = mediaItem?.mediaMetadata?.artist?.toString() ?: ""
            binding.trackTitle.text = title.ifEmpty { "Unknown" }
            binding.trackArtist.text = artist

            val artwork = ArtworkUtils.decodeArtwork(mediaItem?.mediaMetadata?.artworkData)
            if (artwork != null) {
                binding.albumArtwork.setImageBitmap(artwork)
                binding.albumArtwork.visibility = View.VISIBLE
            } else {
                binding.albumArtwork.setImageDrawable(null)
                binding.albumArtwork.visibility = View.GONE
            }

            // Update toolbar title
            if (!isCueMode) {
                supportActionBar?.title = title.ifEmpty { "正在播放" }
            }

            // Highlight current track in list (CUE mode)
            trackAdapter.setCurrentTrack(controller.currentMediaItemIndex)

            // Load lyrics for current track
            viewModel.loadLyricsFromMediaItem(mediaItem)

            updateFavoriteButton(mediaItem)
            updateAudioFormatInfo(controller.currentTracks)
        }
    }

    private fun setupFavoriteButton() {
        binding.btnFavorite.setOnClickListener { toggleCurrentFavorite() }
    }

    private fun toggleCurrentFavorite() {
        val mediaItem = mediaController?.currentMediaItem ?: return
        val record = TrackRecord.fromMediaItem(mediaItem) ?: return
        val isFav = libraryManager.toggleFavorite(record)
        updateFavoriteButton(mediaItem)
        Toast.makeText(
            this,
            if (isFav) "已添加到红心收藏" else "已取消红心收藏",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun updateFavoriteButton(mediaItem: MediaItem?) {
        val key = mediaItem?.let { TrackRecord.buildKey(it) }.orEmpty()
        val isFav = key.isNotEmpty() && libraryManager.isFavorite(key)
        binding.btnFavorite.setImageResource(
            if (isFav) R.drawable.ic_favorite else R.drawable.ic_favorite_border
        )
        if (isFav) {
            binding.btnFavorite.imageTintList = null
        } else {
            val typedValue = android.util.TypedValue()
            theme.resolveAttribute(com.google.android.material.R.attr.colorOnSurfaceVariant, typedValue, true)
            binding.btnFavorite.imageTintList = android.content.res.ColorStateList.valueOf(typedValue.data)
        }
    }

    private fun updateAudioFormatInfo(tracks: Tracks?) {
        val audioFormat = tracks?.groups
            ?.firstOrNull { it.type == C.TRACK_TYPE_AUDIO && it.isSelected }
            ?.let { group ->
                (0 until group.length)
                    .firstOrNull { group.isTrackSelected(it) }
                    ?.let { group.getTrackFormat(it) }
            }

        if (audioFormat == null) {
            binding.audioFormatInfo.visibility = View.GONE
            return
        }

        val parts = mutableListOf<String>()
        formatAudioCodecName(audioFormat.sampleMimeType)?.let { parts.add(it) }
        if (audioFormat.sampleRate != Format.NO_VALUE && audioFormat.sampleRate > 0) {
            val khz = audioFormat.sampleRate / 1000.0
            val formattedKhz = if (khz % 1.0 == 0.0) {
                "${khz.toInt()} kHz"
            } else {
                String.format(java.util.Locale.US, "%.1f kHz", khz)
            }
            parts.add(formattedKhz)
        }
        formatBitDepth(audioFormat.pcmEncoding)?.let { parts.add(it) }
        if (audioFormat.bitrate != Format.NO_VALUE && audioFormat.bitrate > 0) {
            parts.add("${audioFormat.bitrate / 1000} kbps")
        }

        if (parts.isEmpty()) {
            binding.audioFormatInfo.visibility = View.GONE
        } else {
            binding.audioFormatInfo.text = parts.joinToString(" · ")
            binding.audioFormatInfo.visibility = View.VISIBLE
        }
    }

    private fun formatAudioCodecName(mimeType: String?): String? {
        return when (mimeType?.lowercase()) {
            "audio/flac" -> "FLAC"
            "audio/mpeg" -> "MP3"
            "audio/mp4a-latm", "audio/aac" -> "AAC"
            "audio/vorbis", "audio/ogg" -> "OGG"
            "audio/opus" -> "OPUS"
            "audio/raw", "audio/wav" -> "PCM/WAV"
            "audio/alac" -> "ALAC"
            null -> null
            else -> mimeType.substringAfter('/').uppercase()
        }
    }

    private fun formatBitDepth(pcmEncoding: Int): String? {
        return when (pcmEncoding) {
            C.ENCODING_PCM_8BIT -> "8 bit"
            C.ENCODING_PCM_16BIT -> "16 bit"
            C.ENCODING_PCM_24BIT, C.ENCODING_PCM_24BIT_BIG_ENDIAN -> "24 bit"
            C.ENCODING_PCM_32BIT, C.ENCODING_PCM_32BIT_BIG_ENDIAN -> "32 bit"
            C.ENCODING_PCM_FLOAT -> "32-bit float"
            else -> null
        }
    }

    private fun showAddToPlaylistDialog() {
        val mediaItem = mediaController?.currentMediaItem
        val record = mediaItem?.let { TrackRecord.fromMediaItem(it) }
        if (record == null) {
            Toast.makeText(this, "当前没有可添加的曲目", Toast.LENGTH_SHORT).show()
            return
        }

        val playlists = libraryManager.getPlaylists()
        val names = playlists.map { "${it.name} (${it.tracks.size})" } + listOf("+ 新建歌单")
        MaterialAlertDialogBuilder(this)
            .setTitle("添加到歌单")
            .setItems(names.toTypedArray()) { _, which ->
                if (which == playlists.size) {
                    val input = EditText(this).apply {
                        hint = "输入歌单名称"
                        setPadding(48, 24, 48, 24)
                    }
                    MaterialAlertDialogBuilder(this)
                        .setTitle("新建歌单")
                        .setView(input)
                        .setPositiveButton("创建") { _, _ ->
                            val name = input.text.toString().trim()
                            if (name.isNotEmpty()) {
                                val created = libraryManager.createPlaylist(name)
                                libraryManager.addTrackToPlaylist(created.id, record)
                                Toast.makeText(this, "已添加到「${created.name}」", Toast.LENGTH_SHORT).show()
                            }
                        }
                        .setNegativeButton("取消", null)
                        .show()
                } else {
                    val target = playlists[which]
                    val added = libraryManager.addTrackToPlaylist(target.id, record)
                    Toast.makeText(
                        this,
                        if (added) "已添加到「${target.name}」" else "该曲目已在「${target.name}」中",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun updatePlaybackState(state: Int) {
        when (state) {
            Player.STATE_BUFFERING -> binding.progressBar.visibility = View.VISIBLE
            else -> binding.progressBar.visibility = View.GONE
        }
    }

    // ==================== Play Mode ====================

    private fun togglePlayMode() {
        currentPlayMode = currentPlayMode.next()
        applyPlayModeToController()
        updatePlayModeIcon()
        val modeName = when (currentPlayMode) {
            PlayMode.SEQUENTIAL -> "顺序播放"
            PlayMode.REPEAT_ALL -> "列表循环"
            PlayMode.REPEAT_ONE -> "单曲循环"
            PlayMode.SHUFFLE -> "随机播放"
        }
        Toast.makeText(this, modeName, Toast.LENGTH_SHORT).show()
    }

    private fun applyPlayModeToController() {
        mediaController?.let { controller ->
            when (currentPlayMode) {
                PlayMode.SEQUENTIAL -> {
                    controller.repeatMode = Player.REPEAT_MODE_OFF
                    controller.shuffleModeEnabled = false
                }
                PlayMode.REPEAT_ALL -> {
                    controller.repeatMode = Player.REPEAT_MODE_ALL
                    controller.shuffleModeEnabled = false
                }
                PlayMode.REPEAT_ONE -> {
                    controller.repeatMode = Player.REPEAT_MODE_ONE
                    controller.shuffleModeEnabled = false
                }
                PlayMode.SHUFFLE -> {
                    controller.repeatMode = Player.REPEAT_MODE_ALL
                    controller.shuffleModeEnabled = true
                }
            }
        }
    }

    private fun syncPlayModeFromController() {
        mediaController?.let { controller ->
            currentPlayMode = when {
                controller.shuffleModeEnabled -> PlayMode.SHUFFLE
                controller.repeatMode == Player.REPEAT_MODE_ONE -> PlayMode.REPEAT_ONE
                controller.repeatMode == Player.REPEAT_MODE_ALL -> PlayMode.REPEAT_ALL
                else -> PlayMode.SEQUENTIAL
            }
            updatePlayModeIcon()
        }
    }

    private fun updatePlayModeIcon() {
        val iconRes = when (currentPlayMode) {
            PlayMode.SEQUENTIAL -> R.drawable.ic_sequential
            PlayMode.REPEAT_ALL -> R.drawable.ic_repeat
            PlayMode.REPEAT_ONE -> R.drawable.ic_repeat_one
            PlayMode.SHUFFLE -> R.drawable.ic_shuffle
        }
        binding.btnPlayMode.setImageResource(iconRes)
    }

    // ==================== Playlist Dialog ====================

    private fun showPlaylistDialog() {
        val controller = mediaController ?: return

        val dialogView = layoutInflater.inflate(R.layout.dialog_playlist, null)
        val recyclerView = dialogView.findViewById<RecyclerView>(R.id.playlistRecyclerView)
        val emptyView = dialogView.findViewById<TextView>(R.id.playlistEmpty)

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle("播放列表 (${controller.mediaItemCount})")
            .setView(dialogView)
            .setPositiveButton("关闭", null)
            .create()

        val playlistAdapter = BrowserPlaylistAdapter(
            onItemClick = { index ->
                controller.seekTo(index, 0)
                if (controller.playbackState == Player.STATE_IDLE) {
                    controller.prepare()
                }
                controller.play()
                dialog.dismiss()
            },
            onRemoveClick = { index ->
                controller.removeMediaItem(index)
                refreshPlaylistDialog(controller, recyclerView, emptyView)
                dialog.setTitle("播放列表 (${controller.mediaItemCount})")
            },
            onMoveUp = { index ->
                if (index > 0) {
                    controller.moveMediaItem(index, index - 1)
                    refreshPlaylistDialog(controller, recyclerView, emptyView)
                    dialog.setTitle("播放列表 (${controller.mediaItemCount})")
                }
            },
            onMoveDown = { index ->
                if (index < controller.mediaItemCount - 1) {
                    controller.moveMediaItem(index, index + 1)
                    refreshPlaylistDialog(controller, recyclerView, emptyView)
                    dialog.setTitle("播放列表 (${controller.mediaItemCount})")
                }
            }
        )

        recyclerView.apply {
            layoutManager = LinearLayoutManager(this@PlayerActivity)
            adapter = playlistAdapter
        }

        refreshPlaylistDialog(controller, recyclerView, emptyView)
        dialog.show()

        // 清空播放列表按钮
        val clearButton = dialogView.findViewById<Button>(R.id.clearPlaylistButton)
        clearButton.setOnClickListener {
            AlertDialog.Builder(this@PlayerActivity)
                .setTitle("清空播放列表")
                .setMessage("确定要清空全部歌曲吗？")
                .setPositiveButton("确定") { _, _ ->
                    controller.clearMediaItems()
                    refreshPlaylistDialog(controller, recyclerView, emptyView)
                    dialog.setTitle("播放列表 (${controller.mediaItemCount})")
                }
                .setNegativeButton("取消", null)
                .show()
        }
    }

    private fun refreshPlaylistDialog(
        controller: MediaController,
        recyclerView: RecyclerView,
        emptyView: TextView
    ) {
        val itemCount = controller.mediaItemCount
        val items = mutableListOf<PlaylistItem>()
        for (i in 0 until itemCount) {
            val mediaItem = controller.getMediaItemAt(i)
            val clip = mediaItem.clippingConfiguration
            val itemKey = mediaItem.mediaId.ifEmpty {
                "${mediaItem.localConfiguration?.uri}|${clip.startPositionMs}|${clip.endPositionMs}|${mediaItem.mediaMetadata.title}"
            }
            items.add(
                PlaylistItem(
                    index = i,
                    title = mediaItem.mediaMetadata.title?.toString() ?: "未知",
                    artist = mediaItem.mediaMetadata.artist?.toString() ?: "",
                    isCurrent = i == controller.currentMediaItemIndex,
                    mediaId = itemKey,
                    canMoveUp = i > 0,
                    canMoveDown = i < itemCount - 1
                )
            )
        }

        if (items.isEmpty()) {
            emptyView.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
        } else {
            emptyView.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
        }

        (recyclerView.adapter as? BrowserPlaylistAdapter)?.submitList(items)
    }

    // ==================== Lyrics ====================

    private fun updateLyricsPosition(positionMs: Long) {
        val lines = viewModel.lyricsLines.value
        if (lines.isNullOrEmpty()) return

        val newIndex = LrcParser.findCurrentLineIndex(lines, positionMs)
        if (newIndex != currentLyricsLineIndex) {
            currentLyricsLineIndex = newIndex
            lyricsAdapter.setCurrentLine(newIndex)

            // Smooth scroll to keep the current line centered
            if (newIndex >= 0) {
                val layoutManager = binding.lyricsRecyclerView.layoutManager as? LinearLayoutManager
                layoutManager?.let { lm ->
                    // Scroll so the current line is roughly in the center of the view
                    val recyclerHeight = binding.lyricsRecyclerView.height
                    val offset = recyclerHeight / 2 - 40 // approximate half-line height offset
                    lm.scrollToPositionWithOffset(newIndex, offset)
                }
            }
        }
    }

    // ==================== Equalizer ====================

    private fun showEqualizerDialog() {
        if (eqDialog?.isShowing == true) return

        val dialogView = layoutInflater.inflate(R.layout.dialog_equalizer, null)
        val enabledSwitch = dialogView.findViewById<MaterialSwitch>(R.id.eqEnabledSwitch)
        val presetGroup = dialogView.findViewById<MaterialButtonToggleGroup>(R.id.eqPresetGroup)
        val presetScroll = dialogView.findViewById<View>(R.id.eqPresetScroll)
        val bandsHint = dialogView.findViewById<View>(R.id.eqBandsHint)
        val bandContainer = dialogView.findViewById<LinearLayout>(R.id.eqBandContainer)
        val resetButton = dialogView.findViewById<MaterialButton>(R.id.eqResetButton)
        val unavailableText = dialogView.findViewById<View>(R.id.eqUnavailableText)

        var syncing = false
        var renderedBandCount = -1
        val presetButtons = mutableMapOf<EqualizerPreset, MaterialButton>()

        presetGroup.clearChecked()
        EqualizerPreset.values().forEach { preset ->
            val button = MaterialButton(
                this,
                null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle
            ).apply {
                id = View.generateViewId()
                text = preset.label
                isSingleLine = true
                minimumWidth = 0
                minWidth = 0
                tag = preset
            }
            presetButtons[preset] = button
            presetGroup.addView(
                button,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { marginEnd = (8 * resources.displayMetrics.density).toInt() }
            )
        }

        fun updateAvailability(available: Boolean) {
            enabledSwitch.isEnabled = available
            presetScroll.visibility = if (available) View.VISIBLE else View.GONE
            bandsHint.visibility = if (available) View.VISIBLE else View.GONE
            bandContainer.visibility = if (available) View.VISIBLE else View.GONE
            resetButton.isEnabled = available
            unavailableText.visibility = if (available) View.GONE else View.VISIBLE
        }

        fun render(state: AudioEqualizer.State) {
            updateAvailability(state.available)

            syncing = true
            enabledSwitch.isChecked = state.enabled
            syncing = false

            if (!state.available) return

            if (state.bands.size != renderedBandCount) {
                bandContainer.removeAllViews()
                state.bands.forEach { band ->
                    val row = layoutInflater.inflate(
                        R.layout.item_equalizer_band,
                        bandContainer,
                        false
                    )
                    row.tag = band.index
                    val seekBar = row.findViewById<SeekBar>(R.id.bandSeekBar)
                    val levelText = row.findViewById<TextView>(R.id.bandLevel)
                    seekBar.max = (band.maxLevelMb - band.minLevelMb).coerceAtLeast(1)
                    seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                        override fun onProgressChanged(
                            seekBar: SeekBar?,
                            progress: Int,
                            fromUser: Boolean
                        ) {
                            if (!fromUser) return
                            val level = band.minLevelMb + progress
                            levelText.text = formatEqLevel(level)
                            syncing = true
                            presetGroup.clearChecked()
                            syncing = false
                            audioEqualizer.setBandLevel(band.index, level)
                        }

                        override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit

                        override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
                    })
                    bandContainer.addView(row)
                }
                renderedBandCount = state.bands.size
            }

            state.bands.forEachIndexed { index, band ->
                val row = bandContainer.getChildAt(index) ?: return@forEachIndexed
                row.findViewById<TextView>(R.id.bandFrequency).text =
                    formatEqFrequency(band.centerFrequencyHz)
                row.findViewById<TextView>(R.id.bandLevel).text = formatEqLevel(band.levelMb)
                row.findViewById<SeekBar>(R.id.bandSeekBar).progress =
                    band.levelMb - band.minLevelMb
            }

            syncing = true
            presetGroup.clearChecked()
            state.activePreset?.let { preset ->
                presetButtons[preset]?.let { presetGroup.check(it.id) }
            }
            syncing = false
        }

        enabledSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (!syncing) {
                audioEqualizer.setEnabled(isChecked)
            }
        }
        presetGroup.addOnButtonCheckedListener { group, checkedId, isChecked ->
            if (!isChecked || syncing) return@addOnButtonCheckedListener
            val preset = group.findViewById<View>(checkedId)?.tag as? EqualizerPreset
                ?: return@addOnButtonCheckedListener
            audioEqualizer.applyPreset(preset)
        }
        resetButton.setOnClickListener { audioEqualizer.reset() }

        val observer = Observer<AudioEqualizer.State> { state -> render(state) }

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle("均衡器")
            .setView(dialogView)
            .setPositiveButton("完成", null)
            .create()

        dialog.setOnDismissListener {
            audioEqualizer.state.removeObserver(observer)
            if (eqDialog === dialog) {
                eqDialog = null
            }
        }
        eqDialog = dialog
        audioEqualizer.state.observeForever(observer)
        audioEqualizer.state.value?.let { render(it) }
        dialog.show()
    }

    // ==================== Pick Lyrics ====================

    /**
     * 获取当前曲目所在目录。
     *
     * 优先读取媒体项 metadata extras 中记录的目录（CUE 分轨没有 mediaId）；
     * 普通单曲的 mediaId 就是 WebDAV 完整路径，直接取父目录；
     * 最后才回退到启动 Intent 传入的目录。
     */
    private fun currentTrackDirPath(): String? {
        val mediaItem = mediaController?.currentMediaItem ?: return null

        mediaItem.mediaMetadata.extras
            ?.getString(PlayerViewModel.KEY_DIRECTORY_PATH)
            ?.takeIf { it.isNotEmpty() }
            ?.let { return it }

        val mediaId = mediaItem.mediaId
        // 本地 content:// 文件不属于 WebDAV 目录，无法定位
        if (mediaId.startsWith("content://") || mediaId.startsWith("file://")) {
            return null
        }
        if (mediaId.isNotEmpty() && mediaId.contains('/')) {
            return FileUtils.getDirectoryPath(mediaId).ifEmpty { "/" }
        }

        return intent.getStringExtra(EXTRA_DIRECTORY_PATH)?.takeIf { it.isNotEmpty() }
    }

    /**
     * 导航至当前播放曲目所在的文件夹：
     * 使用 CLEAR_TOP + SINGLE_TOP 复用已有的 BrowserActivity 实例。
     */
    private fun navigateToCurrentFolder() {
        val dirPath = currentTrackDirPath()
        if (dirPath == null) {
            Toast.makeText(this, "无法定位曲目所在文件夹", Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(this, BrowserActivity::class.java).apply {
            putExtra(BrowserActivity.EXTRA_NAVIGATE_PATH, dirPath)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        startActivity(intent)
    }

    private fun showPickLyricsDialog() {
        val mediaItem = mediaController?.currentMediaItem
        if (mediaItem == null) {
            Toast.makeText(this, "当前没有播放曲目", Toast.LENGTH_SHORT).show()
            return
        }
        val dirPath = currentTrackDirPath()
        if (dirPath == null) {
            Toast.makeText(this, "无法定位曲目所在文件夹", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            val lrcFiles = viewModel.listLrcFiles(dirPath)
            if (lrcFiles.isEmpty()) {
                Toast.makeText(this@PlayerActivity, "当前目录没有歌词文件", Toast.LENGTH_SHORT).show()
                return@launch
            }

            val autoMatchLabel = "自动匹配"
            val names = listOf(autoMatchLabel) + lrcFiles.map { it.name }
            MaterialAlertDialogBuilder(this@PlayerActivity)
                .setTitle("选择歌词")
                .setItems(names.toTypedArray()) { _, which ->
                    if (which == 0) {
                        // 清除手动指定，恢复自动匹配
                        viewModel.setManualLrc(mediaItem, null)
                        viewModel.loadLyricsFromMediaItem(mediaItem)
                        Toast.makeText(this@PlayerActivity, "已恢复自动匹配", Toast.LENGTH_SHORT).show()
                    } else {
                        val file = lrcFiles[which - 1]
                        viewModel.setManualLrc(mediaItem, file.path)
                        viewModel.loadLyricsFromMediaItem(mediaItem)
                        Toast.makeText(this@PlayerActivity, "已切换歌词: ${file.name}", Toast.LENGTH_SHORT).show()
                    }
                }
                .show()
        }
    }

    // ==================== Utility ====================

    private fun formatEqFrequency(frequencyHz: Int): String {
        if (frequencyHz < 1000) return "$frequencyHz Hz"
        val khz = frequencyHz / 1000f
        return if (khz % 1f == 0f) "${khz.toInt()} kHz" else "%.1f kHz".format(khz)
    }

    private fun formatEqLevel(levelMb: Int): String {
        val db = levelMb / 100f
        return if (db > 0) "+%.1f dB".format(db) else "%.1f dB".format(db)
    }

    private fun formatTime(ms: Long): String {
        val totalSeconds = ms / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "%02d:%02d".format(minutes, seconds)
    }
}
