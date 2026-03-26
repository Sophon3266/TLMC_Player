package com.tlmc.player.ui.browser

import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.tlmc.player.R
import com.tlmc.player.data.model.PlayMode
import com.tlmc.player.data.model.ServerConfig
import com.tlmc.player.data.model.WebDavFile
import com.tlmc.player.databinding.ActivityBrowserBinding
import com.tlmc.player.ui.image.ImageActivity
import com.tlmc.player.ui.player.PlayerActivity
import com.tlmc.player.ui.player.PlayerService
import com.tlmc.player.ui.text.TextActivity
import com.tlmc.player.ui.video.VideoActivity
import com.tlmc.player.util.FileUtils
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class BrowserActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBrowserBinding
    private val viewModel: BrowserViewModel by viewModels()
    private lateinit var fileAdapter: FileAdapter

    // Mini Player
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null
    private val handler = Handler(Looper.getMainLooper())
    private val updateProgressRunnable = object : Runnable {
        override fun run() {
            updateMiniPlayer()
            handler.postDelayed(this, 500)
        }
    }
    private var pendingMediaItem: MediaItem? = null
    private var currentPlayMode: PlayMode = PlayMode.SEQUENTIAL
    private var isMiniPlayerSeeking = false

    // Search dialog references
    private var searchDialog: AlertDialog? = null
    private var searchResultAdapter: SearchResultAdapter? = null
    private var searchProgressBar: ProgressBar? = null
    private var searchStatusText: TextView? = null

    // Fast scroll
    private var isDraggingScrollbar = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBrowserBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        setupRecyclerView()
        setupFastScroller()
        setupSwipeRefresh()
        setupMiniPlayer()
        observeViewModel()
        requestNotificationPermission()

        if (savedInstanceState == null) {
            viewModel.loadDirectory("/")
        }
    }

    override fun onStart() {
        super.onStart()
        connectToPlayerService()
    }

    override fun onStop() {
        super.onStop()
        handler.removeCallbacks(updateProgressRunnable)
        controllerFuture?.let { MediaController.releaseFuture(it) }
        mediaController = null
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 100)
        }
    }

    // ==================== RecyclerView ====================

    private fun setupRecyclerView() {
        fileAdapter = FileAdapter(
            onFileClick = { file -> onFileClicked(file) },
            onFileLongClick = { file -> onFileLongClicked(file) }
        )
        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(this@BrowserActivity)
            adapter = fileAdapter
        }
    }

    @android.annotation.SuppressLint("ClickableViewAccessibility")
    private fun setupFastScroller() {
        // Set thumb height to 5% of screen height
        binding.fastScrollThumb.post {
            val screenHeight = resources.displayMetrics.heightPixels
            val thumbHeight = (screenHeight * 0.05f).toInt()
            binding.fastScrollThumb.layoutParams = binding.fastScrollThumb.layoutParams.apply {
                height = thumbHeight
            }
            updateFastScrollThumb()
        }

        // Update thumb position on scroll
        binding.recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (!isDraggingScrollbar) {
                    updateFastScrollThumb()
                }
            }
        })

        // Handle drag on track area
        binding.fastScrollTrack.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    isDraggingScrollbar = true
                    binding.fastScrollThumb.isPressed = true
                    binding.swipeRefresh.isEnabled = false
                    handleScrollbarDrag(event.y)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    handleScrollbarDrag(event.y)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isDraggingScrollbar = false
                    binding.fastScrollThumb.isPressed = false
                    binding.swipeRefresh.isEnabled = true
                    true
                }
                else -> false
            }
        }
    }

    private fun updateFastScrollThumb() {
        val rv = binding.recyclerView
        val range = rv.computeVerticalScrollRange()
        val extent = rv.computeVerticalScrollExtent()

        if (range <= extent) {
            binding.fastScrollThumb.visibility = View.INVISIBLE
            return
        }

        binding.fastScrollThumb.visibility = View.VISIBLE
        val offset = rv.computeVerticalScrollOffset()
        val trackHeight = binding.fastScrollTrack.height
        val thumbHeight = binding.fastScrollThumb.height
        val maxThumbTop = trackHeight - thumbHeight
        if (maxThumbTop > 0) {
            val fraction = offset.toFloat() / (range - extent).toFloat()
            binding.fastScrollThumb.translationY = fraction * maxThumbTop
        }
    }

    private fun handleScrollbarDrag(touchY: Float) {
        val trackHeight = binding.fastScrollTrack.height
        val thumbHeight = binding.fastScrollThumb.height
        val maxThumbTop = trackHeight - thumbHeight
        if (maxThumbTop <= 0) return

        val thumbTop = (touchY - thumbHeight / 2f).coerceIn(0f, maxThumbTop.toFloat())
        val fraction = thumbTop / maxThumbTop

        binding.fastScrollThumb.translationY = thumbTop

        val itemCount = binding.recyclerView.adapter?.itemCount ?: 0
        if (itemCount > 0) {
            val targetPosition = (fraction * (itemCount - 1)).toInt().coerceIn(0, itemCount - 1)
            (binding.recyclerView.layoutManager as? LinearLayoutManager)
                ?.scrollToPositionWithOffset(targetPosition, 0)
        }
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setOnRefreshListener {
            viewModel.refresh()
        }
    }

    // ==================== ViewModel Observation ====================

    private fun observeViewModel() {
        viewModel.files.observe(this) { files ->
            fileAdapter.submitList(files)
            binding.emptyView.visibility = if (files.isEmpty()) View.VISIBLE else View.GONE
        }

        viewModel.isLoading.observe(this) { isLoading ->
            binding.swipeRefresh.isRefreshing = isLoading
            binding.progressBar.visibility = if (isLoading && fileAdapter.itemCount == 0) View.VISIBLE else View.GONE
        }

        viewModel.currentPath.observe(this) { path ->
            supportActionBar?.subtitle = if (path == "/") "Root" else path
            updateBreadcrumb(path)
        }

        viewModel.error.observe(this) { error ->
            error?.let {
                Toast.makeText(this, it, Toast.LENGTH_LONG).show()
                viewModel.clearError()
            }
        }

        viewModel.directoryFiles.observe(this) { /* Keep synced for CUE lookup */ }

        // Search observers (always active, update dialog views if showing)
        viewModel.searchResults.observe(this) { results ->
            searchResultAdapter?.submitList(results.toList())
        }

        viewModel.isSearching.observe(this) { isSearching ->
            searchProgressBar?.visibility = if (isSearching) View.VISIBLE else View.GONE
        }

        viewModel.searchStatus.observe(this) { status ->
            searchStatusText?.let { tv ->
                if (status.isNullOrEmpty()) {
                    tv.visibility = View.GONE
                } else {
                    tv.visibility = View.VISIBLE
                    tv.text = status
                }
            }
        }
    }

    // ==================== Breadcrumb Navigation ====================

    private fun updateBreadcrumb(path: String) {
        binding.breadcrumbContainer.removeAllViews()

        // Root chip
        addBreadcrumbChip("根目录", "/")

        if (path != "/" && path.isNotEmpty()) {
            val segments = path.trimStart('/').trimEnd('/').split("/")
            var accumulated = ""
            for (segment in segments) {
                accumulated += "/$segment"
                addBreadcrumbSeparator()
                addBreadcrumbChip(segment, accumulated)
            }
        }

        // Scroll to end
        binding.breadcrumbScroll.post {
            binding.breadcrumbScroll.fullScroll(HorizontalScrollView.FOCUS_RIGHT)
        }
    }

    private fun addBreadcrumbChip(label: String, path: String) {
        val chip = Chip(this).apply {
            text = label
            isClickable = true
            isCheckable = false
            textSize = 13f
            chipMinHeight = 32f * resources.displayMetrics.density
            setEnsureMinTouchTargetSize(false)
            setOnClickListener {
                viewModel.loadDirectory(path)
            }
        }
        binding.breadcrumbContainer.addView(chip)
    }

    private fun addBreadcrumbSeparator() {
        val separator = TextView(this).apply {
            text = "›"
            textSize = 18f
            setTextColor(getColor(R.color.md_theme_onSurfaceVariant))
            val px = (4 * resources.displayMetrics.density).toInt()
            setPadding(px, 0, px, 0)
            gravity = Gravity.CENTER
        }
        binding.breadcrumbContainer.addView(separator)
    }

    // ==================== File Click Handlers ====================

    private fun onFileClicked(file: WebDavFile) {
        when {
            file.isDirectory -> viewModel.loadDirectory(file.path)
            file.isAudio -> handleAudioFileClick(file)
            file.isCue -> openPlayerFromCue(file)
            file.isVideo -> openVideo(file)
            file.isImage -> openImage(file)
            file.isText -> openText(file)
            else -> Toast.makeText(this, "不支持的文件类型: ${file.extension}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleAudioFileClick(file: WebDavFile) {
        val controller = mediaController

        // Check if this file is already in the playlist
        if (controller != null && controller.mediaItemCount > 0) {
            val fileUrl = viewModel.getFileUrl(file.path)
            for (i in 0 until controller.mediaItemCount) {
                val itemUri = controller.getMediaItemAt(i).localConfiguration?.uri?.toString() ?: ""
                if (itemUri == fileUrl) {
                    // Already in playlist, just switch to it
                    controller.seekTo(i, 0)
                    controller.play()
                    updateMiniPlayerVisibility()
                    updateMiniPlayerInfo()
                    Toast.makeText(this, "切换到: ${file.name}", Toast.LENGTH_SHORT).show()
                    return
                }
            }
        }

        // Not in playlist, ask user what to do
        val audioFiles = viewModel.directoryFiles.value?.filter { it.isAudio } ?: emptyList()
        if (audioFiles.size > 1) {
            val options = arrayOf("仅播放此曲", "播放文件夹全部音频 (${audioFiles.size}首)")
            MaterialAlertDialogBuilder(this)
                .setTitle(file.name)
                .setItems(options) { _, which ->
                    when (which) {
                        0 -> playSingleFile(file)
                        1 -> playFolderAudioFiles(file, audioFiles)
                    }
                }
                .show()
        } else {
            playSingleFile(file)
        }
    }

    private fun playSingleFile(file: WebDavFile) {
        val mediaItem = buildMediaItemForFile(file)
        val controller = mediaController
        if (controller != null) {
            controller.addMediaItem(mediaItem)
            val newIndex = controller.mediaItemCount - 1
            controller.seekTo(newIndex, 0)
            if (controller.playbackState == Player.STATE_IDLE) {
                controller.prepare()
            }
            controller.play()
            updateMiniPlayerVisibility()
            updateMiniPlayerInfo()
        } else {
            pendingMediaItem = mediaItem
            connectToPlayerService()
        }
    }

    private fun playFolderAudioFiles(clickedFile: WebDavFile, audioFiles: List<WebDavFile>) {
        val mediaItems = audioFiles.map { buildMediaItemForFile(it) }
        val clickedIndex = audioFiles.indexOf(clickedFile).coerceAtLeast(0)

        val controller = mediaController
        if (controller != null) {
            // Append all to existing playlist
            val startIndex = controller.mediaItemCount
            controller.addMediaItems(mediaItems)
            controller.seekTo(startIndex + clickedIndex, 0)
            if (controller.playbackState == Player.STATE_IDLE) {
                controller.prepare()
            }
            controller.play()
            updateMiniPlayerVisibility()
            updateMiniPlayerInfo()
            Toast.makeText(this, "已添加 ${audioFiles.size} 首到播放列表", Toast.LENGTH_SHORT).show()
        } else {
            // No controller yet; set items as pending and connect
            pendingMediaItem = mediaItems[clickedIndex]
            connectToPlayerService()
        }
    }

    private fun buildMediaItemForFile(file: WebDavFile): MediaItem {
        val url = viewModel.getFileUrl(file.path)
        return MediaItem.Builder()
            .setMediaId(file.path)
            .setUri(url)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(file.name)
                    .build()
            )
            .build()
    }

    private fun onFileLongClicked(file: WebDavFile): Boolean {
        if (file.isAudio || file.isCue) {
            val options = arrayOf("文件信息", "添加到播放列表", "立即播放")
            MaterialAlertDialogBuilder(this)
                .setTitle(file.name)
                .setItems(options) { _, which ->
                    when (which) {
                        0 -> showFileInfo(file)
                        1 -> addToPlaylist(file)
                        2 -> {
                            if (file.isCue) openPlayerFromCue(file) else playSingleFile(file)
                        }
                    }
                }
                .show()
        } else {
            showFileInfo(file)
        }
        return true
    }

    private fun showFileInfo(file: WebDavFile) {
        MaterialAlertDialogBuilder(this)
            .setTitle(file.name)
            .setMessage("大小: ${FileUtils.formatFileSize(file.size)}\n路径: ${file.path}")
            .setPositiveButton("确定", null)
            .show()
    }

    // ==================== Player Navigation ====================

    private fun openPlayerFromCue(cueFile: WebDavFile) {
        val intent = Intent(this, PlayerActivity::class.java).apply {
            putExtra(PlayerActivity.EXTRA_CUE_PATH, cueFile.path)
            putExtra(PlayerActivity.EXTRA_FILE_NAME, cueFile.name)
            putExtra(PlayerActivity.EXTRA_DIRECTORY_PATH, viewModel.currentPath.value ?: "/")
        }
        startActivity(intent)
    }

    private fun openImage(file: WebDavFile) {
        val intent = Intent(this, ImageActivity::class.java).apply {
            putExtra(ImageActivity.EXTRA_FILE_PATH, file.path)
            putExtra(ImageActivity.EXTRA_FILE_NAME, file.name)
        }
        startActivity(intent)
    }

    private fun openVideo(file: WebDavFile) {
        val intent = Intent(this, VideoActivity::class.java).apply {
            putExtra(VideoActivity.EXTRA_FILE_PATH, file.path)
            putExtra(VideoActivity.EXTRA_FILE_NAME, file.name)
        }
        startActivity(intent)
    }

    private fun openText(file: WebDavFile) {
        val intent = Intent(this, TextActivity::class.java).apply {
            putExtra(TextActivity.EXTRA_FILE_PATH, file.path)
            putExtra(TextActivity.EXTRA_FILE_NAME, file.name)
        }
        startActivity(intent)
    }

    // ==================== Mini Player ====================

    private fun connectToPlayerService() {
        try {
            val sessionToken = SessionToken(this, ComponentName(this, PlayerService::class.java))
            controllerFuture = MediaController.Builder(this, sessionToken).buildAsync().also { future ->
                future.addListener({
                    try {
                        mediaController = future.get()
                        onControllerConnected()
                    } catch (_: Exception) {
                        // Service not running yet
                        runOnUiThread { binding.miniPlayerCard.visibility = View.GONE }
                    }
                }, MoreExecutors.directExecutor())
            }
        } catch (_: Exception) {
            binding.miniPlayerCard.visibility = View.GONE
        }
    }

    private fun onControllerConnected() {
        mediaController?.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                runOnUiThread { updateMiniPlayerControls() }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                runOnUiThread { updateMiniPlayerInfo() }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                runOnUiThread { updateMiniPlayerVisibility() }
            }

            override fun onRepeatModeChanged(repeatMode: Int) {
                runOnUiThread { syncPlayModeFromController() }
            }

            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                runOnUiThread { syncPlayModeFromController() }
            }
        })

        // Process pending item
        pendingMediaItem?.let { item ->
            mediaController?.let { controller ->
                controller.addMediaItem(item)
                if (controller.playbackState == Player.STATE_IDLE) {
                    controller.prepare()
                }
                runOnUiThread {
                    Toast.makeText(this, "已添加到播放列表", Toast.LENGTH_SHORT).show()
                }
            }
            pendingMediaItem = null
        }

        runOnUiThread {
            updateMiniPlayerVisibility()
            updateMiniPlayerInfo()
            updateMiniPlayerControls()
            syncPlayModeFromController()
        }
        handler.post(updateProgressRunnable)
    }

    private fun setupMiniPlayer() {
        binding.miniPlayerPlayPause.setOnClickListener {
            mediaController?.let { controller ->
                if (controller.isPlaying) controller.pause() else controller.play()
            }
        }

        binding.miniPlayerPrev.setOnClickListener {
            mediaController?.seekToPreviousMediaItem()
        }

        binding.miniPlayerNext.setOnClickListener {
            mediaController?.seekToNextMediaItem()
        }

        binding.miniPlayerPlaylist.setOnClickListener {
            showPlaylistDialog()
        }

        binding.miniPlayerPlayMode.setOnClickListener {
            togglePlayMode()
        }

        binding.miniPlayerProgress.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                // No-op, update is handled in onStopTrackingTouch
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                isMiniPlayerSeeking = true
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                isMiniPlayerSeeking = false
                seekBar?.let { sb ->
                    mediaController?.let { controller ->
                        val duration = controller.duration
                        if (duration > 0) {
                            val seekPosition = (sb.progress.toLong() * duration) / 1000L
                            controller.seekTo(seekPosition)
                        }
                    }
                }
            }
        })

        binding.miniPlayerInfo.setOnClickListener {
            // Tap track info area to open full PlayerActivity
            mediaController?.let { controller ->
                if (controller.mediaItemCount > 0) {
                    val intent = Intent(this, PlayerActivity::class.java)
                    startActivity(intent)
                }
            }
        }
    }

    private fun updateMiniPlayerVisibility() {
        val controller = mediaController
        if (controller != null && controller.mediaItemCount > 0) {
            binding.miniPlayerCard.visibility = View.VISIBLE
        } else {
            binding.miniPlayerCard.visibility = View.GONE
        }
    }

    private fun updateMiniPlayerInfo() {
        val controller = mediaController ?: return
        val mediaItem = controller.currentMediaItem
        binding.miniPlayerTitle.text = mediaItem?.mediaMetadata?.title?.toString() ?: "未知曲目"
        binding.miniPlayerArtist.text = mediaItem?.mediaMetadata?.artist?.toString() ?: ""
    }

    private fun updateMiniPlayerControls() {
        val controller = mediaController ?: return
        binding.miniPlayerPlayPause.setImageResource(
            if (controller.isPlaying) R.drawable.ic_pause else R.drawable.ic_play
        )
    }

    private fun updateMiniPlayer() {
        val controller = mediaController ?: return
        val duration = controller.duration
        if (duration > 0 && !isMiniPlayerSeeking) {
            val progress = (controller.currentPosition * 1000 / duration).toInt()
            binding.miniPlayerProgress.progress = progress
        }
        updateMiniPlayerVisibility()
    }

    // ==================== Playlist Management ====================

    private fun addToPlaylist(file: WebDavFile) {
        val mediaItem = buildMediaItemForFile(file)

        val controller = mediaController
        if (controller != null) {
            controller.addMediaItem(mediaItem)
            if (controller.playbackState == Player.STATE_IDLE) {
                controller.prepare()
            }
            Toast.makeText(this, "已添加: ${file.name}", Toast.LENGTH_SHORT).show()
            updateMiniPlayerVisibility()
        } else {
            // Controller not connected, save pending item and try connecting
            pendingMediaItem = mediaItem
            connectToPlayerService()
            Toast.makeText(this, "正在连接播放器...", Toast.LENGTH_SHORT).show()
        }
    }

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
                controller.play()
                dialog.dismiss()
            },
            onRemoveClick = { index ->
                controller.removeMediaItem(index)
                refreshPlaylistDialog(controller, recyclerView, emptyView)
                dialog.setTitle("播放列表 (${controller.mediaItemCount})")
                updateMiniPlayerVisibility()
            }
        )

        recyclerView.apply {
            layoutManager = LinearLayoutManager(this@BrowserActivity)
            adapter = playlistAdapter
        }

        refreshPlaylistDialog(controller, recyclerView, emptyView)
        dialog.show()
    }

    private fun refreshPlaylistDialog(controller: MediaController, recyclerView: RecyclerView, emptyView: TextView) {
        val items = mutableListOf<PlaylistItem>()
        for (i in 0 until controller.mediaItemCount) {
            val mediaItem = controller.getMediaItemAt(i)
            items.add(
                PlaylistItem(
                    index = i,
                    title = mediaItem.mediaMetadata.title?.toString() ?: "未知",
                    artist = mediaItem.mediaMetadata.artist?.toString() ?: "",
                    isCurrent = i == controller.currentMediaItemIndex
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
        binding.miniPlayerPlayMode.setImageResource(iconRes)
    }

    // ==================== Search ====================

    private fun showSearchDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_search, null)
        val searchInput = dialogView.findViewById<EditText>(R.id.searchInput)
        searchProgressBar = dialogView.findViewById(R.id.searchProgress)
        searchStatusText = dialogView.findViewById(R.id.searchStatus)
        val resultsRecyclerView = dialogView.findViewById<RecyclerView>(R.id.searchResultsRecyclerView)

        searchResultAdapter = SearchResultAdapter(
            onResultClick = { file ->
                if (file.isDirectory) {
                    viewModel.loadDirectory(file.path)
                } else {
                    onFileClicked(file)
                }
                searchDialog?.dismiss()
            },
            onNavigateClick = { file ->
                val parentPath = FileUtils.getDirectoryPath(file.path)
                viewModel.loadDirectory(if (parentPath.isEmpty()) "/" else parentPath)
                searchDialog?.dismiss()
            }
        )

        resultsRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@BrowserActivity)
            adapter = searchResultAdapter
        }

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle("搜索文件")
            .setView(dialogView)
            .setPositiveButton("搜索", null) // Override below to prevent auto-dismiss
            .setNegativeButton("取消") { _, _ ->
                viewModel.cancelSearch()
            }
            .setOnDismissListener {
                searchResultAdapter = null
                searchProgressBar = null
                searchStatusText = null
                searchDialog = null
            }
            .create()

        searchDialog = dialog

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val query = searchInput.text.toString().trim()
                if (query.isNotEmpty()) {
                    viewModel.searchFiles(query)
                } else {
                    Toast.makeText(this, "请输入搜索关键词", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // Handle keyboard search action
        searchInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                val query = searchInput.text.toString().trim()
                if (query.isNotEmpty()) {
                    viewModel.searchFiles(query)
                }
                true
            } else false
        }

        dialog.show()
        searchInput.requestFocus()
    }

    // ==================== Menu ====================

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_browser, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_search -> {
                showSearchDialog()
                true
            }
            R.id.action_settings -> {
                showSettingsDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showSettingsDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_settings, null)
        val config = viewModel.getConfig()

        val urlEdit = dialogView.findViewById<EditText>(R.id.editUrl)
        val usernameEdit = dialogView.findViewById<EditText>(R.id.editUsername)
        val passwordEdit = dialogView.findViewById<EditText>(R.id.editPassword)

        urlEdit.setText(config.url)
        usernameEdit.setText(config.username)
        passwordEdit.setText(config.password)

        MaterialAlertDialogBuilder(this)
            .setTitle("服务器设置")
            .setView(dialogView)
            .setPositiveButton("保存") { _, _ ->
                val newConfig = ServerConfig(
                    url = urlEdit.text.toString().trim(),
                    username = usernameEdit.text.toString().trim(),
                    password = passwordEdit.text.toString().trim()
                )
                viewModel.saveConfig(newConfig)
                viewModel.loadDirectory("/")
            }
            .setNegativeButton("取消", null)
            .show()
    }

    @Deprecated("Deprecated in API")
    override fun onBackPressed() {
        if (!viewModel.navigateUp()) {
            super.onBackPressed()
        }
    }
}

