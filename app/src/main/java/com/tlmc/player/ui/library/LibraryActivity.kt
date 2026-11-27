package com.tlmc.player.ui.library

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.tlmc.player.R
import com.tlmc.player.data.model.CustomPlaylist
import com.tlmc.player.data.model.PlayMode
import com.tlmc.player.data.model.TrackRecord
import com.tlmc.player.data.model.toTrackRecords
import com.tlmc.player.data.repository.ConfigManager
import com.tlmc.player.data.repository.LibraryManager
import com.tlmc.player.data.repository.OfflineManager
import com.tlmc.player.databinding.ActivityLibraryBinding
import com.tlmc.player.ui.browser.BrowserPlaylistAdapter
import com.tlmc.player.ui.browser.FolderCoverLoader
import com.tlmc.player.ui.browser.PlaylistBatchHelper
import com.tlmc.player.ui.browser.PlaylistItem
import com.tlmc.player.ui.player.PlayerActivity
import com.tlmc.player.ui.player.PlayerService
import com.tlmc.player.util.ArtworkUtils
import com.tlmc.player.util.CueParser
import com.tlmc.player.util.EncodingDetector
import com.tlmc.player.util.FileUtils
import com.tlmc.player.util.FlacCueExtractor
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

@AndroidEntryPoint
class LibraryActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_INITIAL_TAB = "extra_initial_tab"
        const val TAB_FAVORITES = 0
        const val TAB_PLAYLISTS = 1
        const val TAB_HISTORY = 2
        const val TAB_OFFLINE = 3
        private val AUDIO_EXTENSIONS = setOf("flac", "mp3", "wav", "ogg", "m4a", "aac")
    }

    @Inject
    lateinit var libraryManager: LibraryManager

    @Inject
    lateinit var offlineManager: OfflineManager

    @Inject
    lateinit var folderCoverLoader: FolderCoverLoader

    @Inject
    lateinit var configManager: ConfigManager

    private lateinit var binding: ActivityLibraryBinding
    private lateinit var adapter: LibraryGalleryAdapter
    private var currentTab = TAB_FAVORITES
    private var offlineAlbums: List<OfflineManager.OfflineAlbum> = emptyList()
    private var activePlaylistId: String? = null
    private var activeOfflineAlbum: OfflineManager.OfflineAlbum? = null
    private var activeDetailTracks: List<TrackRecord> = emptyList()

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null
    private var pendingMediaItems: Pair<List<MediaItem>, Int>? = null
    private var currentPlayMode: PlayMode = PlayMode.SEQUENTIAL
    private var isMiniPlayerSeeking = false
    private val handler = Handler(Looper.getMainLooper())
    private val updateProgressRunnable = object : Runnable {
        override fun run() {
            updateMiniPlayerProgress()
            handler.postDelayed(this, 500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLibraryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener {
            if (!exitDetailMode()) finish()
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (!exitDetailMode()) {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        setupTabs(intent.getIntExtra(EXTRA_INITIAL_TAB, TAB_FAVORITES))
        setupGallery()
        setupFab()
        setupMiniPlayer()
        observeLibrary()
        loadOfflineAlbums()
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

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val isGrid = configManager.isLibraryGridView()
        menu.add(0, 1002, 0, "切换视图")
            .setIcon(if (isGrid) R.drawable.ic_view_list else R.drawable.ic_view_grid)
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
        when {
            activePlaylistId != null -> {
                menu.add(0, 1003, 1, "全部收藏").setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
                menu.add(0, 1004, 2, "管理歌单").setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
            }
            activeOfflineAlbum != null -> {
                menu.add(0, 1005, 1, "全部收藏").setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
                menu.add(0, 1006, 2, "添加到歌单").setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
                menu.add(0, 1007, 3, "删除离线专辑").setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
            }
            currentTab == TAB_HISTORY -> {
                menu.add(0, 1001, 1, "清空历史").setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
            }
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            1002 -> {
                val nextGrid = !configManager.isLibraryGridView()
                configManager.setLibraryGridView(nextGrid)
                applyViewMode(nextGrid)
                invalidateOptionsMenu()
                return true
            }
            1001 -> if (currentTab == TAB_HISTORY && !isInDetailMode()) {
                MaterialAlertDialogBuilder(this)
                    .setTitle("清空播放历史")
                    .setMessage("确定要清空全部最近播放记录吗？")
                    .setPositiveButton("清空") { _, _ ->
                        libraryManager.clearHistory()
                        Toast.makeText(this, "播放历史已清空", Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("取消", null)
                    .show()
                return true
            }
            1003 -> {
                PlaylistBatchHelper.favoriteTracks(this, libraryManager, activeDetailTracks)
                return true
            }
            1004 -> {
                val playlist = libraryManager.getPlaylists().firstOrNull { it.id == activePlaylistId }
                if (playlist != null) {
                    MaterialAlertDialogBuilder(this)
                        .setTitle("管理「${playlist.name}」")
                        .setItems(arrayOf("重命名歌单", "删除歌单")) { _, which ->
                            when (which) {
                                0 -> showRenamePlaylistDialog(playlist)
                                1 -> confirmDeletePlaylist(playlist)
                            }
                        }
                        .show()
                }
                return true
            }
            1005 -> {
                PlaylistBatchHelper.favoriteTracks(this, libraryManager, activeDetailTracks)
                return true
            }
            1006 -> {
                val albumName = activeOfflineAlbum?.name ?: "离线专辑"
                PlaylistBatchHelper.showAddTracksToPlaylistDialog(
                    this, libraryManager, activeDetailTracks, "将「$albumName」添加到歌单"
                )
                return true
            }
            1007 -> {
                activeOfflineAlbum?.let { confirmDeleteOfflineAlbum(it) }
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun isInDetailMode(): Boolean = activePlaylistId != null || activeOfflineAlbum != null

    private fun exitDetailMode(): Boolean {
        if (!isInDetailMode()) return false
        activePlaylistId = null
        activeOfflineAlbum = null
        activeDetailTracks = emptyList()
        binding.tabLayout.visibility = View.VISIBLE
        supportActionBar?.title = "音乐库"
        invalidateOptionsMenu()
        renderCurrentTab()
        return true
    }

    private fun setupTabs(initialTab: Int) {
        val titles = listOf("红心收藏", "自定义歌单", "播放历史", "离线曲库")
        titles.forEach { title ->
            binding.tabLayout.addTab(binding.tabLayout.newTab().setText(title))
        }
        currentTab = initialTab.coerceIn(0, titles.lastIndex)
        binding.tabLayout.getTabAt(currentTab)?.select()

        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                currentTab = tab.position
                invalidateOptionsMenu()
                if (currentTab == TAB_OFFLINE) {
                    loadOfflineAlbums()
                }
                renderCurrentTab()
            }

            override fun onTabUnselected(tab: TabLayout.Tab) = Unit
            override fun onTabReselected(tab: TabLayout.Tab) = Unit
        })
    }

    private fun setupGallery() {
        adapter = LibraryGalleryAdapter(
            folderCoverLoader = folderCoverLoader,
            coroutineScope = lifecycleScope,
            onItemClick = { item, position -> handleCardClick(item, position) },
            onItemLongClick = { item, position -> handleCardLongClick(item, position) },
            onMoveUp = { _, position -> handleMoveItem(position, position - 1) },
            onMoveDown = { _, position -> handleMoveItem(position, position + 1) },
            onRemoveClick = { item, _ -> handleRemoveItem(item) }
        )
        applyViewMode(configManager.isLibraryGridView())
        binding.galleryRecyclerView.adapter = adapter
    }

    private fun handleMoveItem(fromIndex: Int, toIndex: Int) {
        activePlaylistId?.let { playlistId ->
            libraryManager.moveTrackInPlaylist(playlistId, fromIndex, toIndex)
            return
        }
        when (currentTab) {
            TAB_FAVORITES -> libraryManager.moveFavorite(fromIndex, toIndex)
            TAB_PLAYLISTS -> libraryManager.movePlaylist(fromIndex, toIndex)
        }
    }

    private fun handleRemoveItem(item: LibraryGalleryItem) {
        activePlaylistId?.let { playlistId ->
            val track = item.payload as? TrackRecord ?: return
            libraryManager.removeTrackFromPlaylist(playlistId, track.key)
            Toast.makeText(this, "已移除: ${track.title}", Toast.LENGTH_SHORT).show()
            return
        }
        when (currentTab) {
            TAB_FAVORITES -> {
                val track = item.payload as? TrackRecord ?: return
                libraryManager.removeFavorite(track.key)
                Toast.makeText(this, "已取消收藏: ${track.title}", Toast.LENGTH_SHORT).show()
            }
            TAB_PLAYLISTS -> {
                val playlist = item.payload as? CustomPlaylist ?: return
                confirmDeletePlaylist(playlist)
            }
        }
    }

    private fun applyViewMode(isGrid: Boolean) {
        adapter.isGridMode = isGrid
        binding.galleryRecyclerView.layoutManager = if (isGrid) {
            val spanCount = if (resources.configuration.screenWidthDp >= 600) 3 else 2
            GridLayoutManager(this, spanCount)
        } else {
            LinearLayoutManager(this)
        }
    }

    private fun setupFab() {
        binding.actionFab.setOnClickListener {
            if (isInDetailMode()) {
                if (activeDetailTracks.isNotEmpty()) playTrackRecords(activeDetailTracks, 0)
                return@setOnClickListener
            }
            when (currentTab) {
                TAB_FAVORITES -> {
                    val tracks = libraryManager.getFavorites()
                    if (tracks.isNotEmpty()) playTrackRecords(tracks, 0)
                }
                TAB_PLAYLISTS -> showCreatePlaylistDialog()
                TAB_HISTORY -> {
                    val tracks = libraryManager.getHistory()
                    if (tracks.isNotEmpty()) playTrackRecords(tracks, 0)
                }
                TAB_OFFLINE -> loadOfflineAlbums()
            }
        }
    }

    private fun observeLibrary() {
        libraryManager.favorites.observe(this) {
            if (!isInDetailMode() && currentTab == TAB_FAVORITES) renderCurrentTab()
        }
        libraryManager.playlists.observe(this) {
            if (activePlaylistId != null || (!isInDetailMode() && currentTab == TAB_PLAYLISTS)) {
                renderCurrentTab()
            }
        }
        libraryManager.history.observe(this) {
            if (!isInDetailMode() && currentTab == TAB_HISTORY) renderCurrentTab()
        }
    }

    private fun loadOfflineAlbums() {
        lifecycleScope.launch {
            offlineAlbums = offlineManager.getOfflineAlbums()
            if (!isInDetailMode() && currentTab == TAB_OFFLINE) renderCurrentTab()
        }
    }

    private fun renderCurrentTab() {
        activePlaylistId?.let { playlistId ->
            val playlist = libraryManager.getPlaylists().firstOrNull { it.id == playlistId }
            if (playlist == null) {
                exitDetailMode()
                return
            }
            binding.tabLayout.visibility = View.GONE
            supportActionBar?.title = playlist.name
            activeDetailTracks = playlist.tracks
            adapter.submitList(playlist.tracks.mapIndexed { index, track ->
                track.toGalleryItem(R.drawable.ic_music).copy(
                    showManageControls = true,
                    canMoveUp = index > 0,
                    canMoveDown = index < playlist.tracks.lastIndex
                )
            })
            binding.emptyView.text = "歌单暂无曲目\n可在播放页或目录长按菜单批量添加"
            binding.emptyView.visibility = if (playlist.tracks.isEmpty()) View.VISIBLE else View.GONE
            binding.actionFab.text = "全部播放"
            binding.actionFab.setIconResource(R.drawable.ic_play)
            binding.actionFab.visibility = if (playlist.tracks.isNotEmpty()) View.VISIBLE else View.GONE
            return
        }

        activeOfflineAlbum?.let { album ->
            binding.tabLayout.visibility = View.GONE
            supportActionBar?.title = album.name
            val tracks = activeDetailTracks
            adapter.submitList(tracks.map { track ->
                track.toGalleryItem(R.drawable.ic_music).let { item ->
                    if (item.localCoverUri == null && album.coverUri != null) {
                        item.copy(localCoverUri = album.coverUri)
                    } else {
                        item
                    }
                }
            })
            binding.emptyView.text = "该离线目录中没有可播放的音频文件"
            binding.emptyView.visibility = if (tracks.isEmpty()) View.VISIBLE else View.GONE
            binding.actionFab.text = "全部播放"
            binding.actionFab.setIconResource(R.drawable.ic_play)
            binding.actionFab.visibility = if (tracks.isNotEmpty()) View.VISIBLE else View.GONE
            return
        }

        binding.tabLayout.visibility = View.VISIBLE
        supportActionBar?.title = "音乐库"

        when (currentTab) {
            TAB_FAVORITES -> {
                val list = libraryManager.getFavorites()
                adapter.submitList(list.mapIndexed { index, track ->
                    track.toGalleryItem(R.drawable.ic_favorite).copy(
                        showManageControls = true,
                        canMoveUp = index > 0,
                        canMoveDown = index < list.lastIndex
                    )
                })
                binding.emptyView.text = "暂无红心收藏\n可在播放页点击红心或长按专辑目录批量收藏"
                binding.emptyView.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
                binding.actionFab.text = "全部播放"
                binding.actionFab.setIconResource(R.drawable.ic_play)
                binding.actionFab.visibility = if (list.isNotEmpty()) View.VISIBLE else View.GONE
            }
            TAB_PLAYLISTS -> {
                val list = libraryManager.getPlaylists()
                adapter.submitList(list.mapIndexed { index, playlist ->
                    val firstDir = playlist.tracks.firstOrNull { it.directoryPath.isNotEmpty() }?.directoryPath.orEmpty()
                    LibraryGalleryItem(
                        id = "pl_${playlist.id}",
                        title = playlist.name,
                        subtitle = if (playlist.tracks.isEmpty()) "空歌单" else "${playlist.tracks.size} 首曲目",
                        badge = "${playlist.tracks.size} 首",
                        remoteDirectoryPath = firstDir,
                        placeholderIconRes = R.drawable.ic_playlist,
                        showManageControls = true,
                        canMoveUp = index > 0,
                        canMoveDown = index < list.lastIndex,
                        payload = playlist
                    )
                })
                binding.emptyView.text = "暂无自定义歌单\n点击右下角「新建歌单」创建"
                binding.emptyView.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
                binding.actionFab.text = "新建歌单"
                binding.actionFab.setIconResource(R.drawable.ic_playlist)
                binding.actionFab.visibility = View.VISIBLE
            }
            TAB_HISTORY -> {
                val list = libraryManager.getHistory()
                adapter.submitList(list.map { it.toGalleryItem(R.drawable.ic_music) })
                binding.emptyView.text = "暂无播放历史"
                binding.emptyView.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
                binding.actionFab.text = "全部播放"
                binding.actionFab.setIconResource(R.drawable.ic_play)
                binding.actionFab.visibility = if (list.isNotEmpty()) View.VISIBLE else View.GONE
            }
            TAB_OFFLINE -> {
                val list = offlineAlbums
                adapter.submitList(list.map { album ->
                    val audioCount = album.files.count { it.extension.lowercase() in AUDIO_EXTENSIONS }
                    LibraryGalleryItem(
                        id = "off_${album.id}",
                        title = album.name,
                        subtitle = FileUtils.formatFileSize(album.totalBytes),
                        badge = if (audioCount > 0) "$audioCount 首" else "${album.files.size} 文件",
                        localCoverUri = album.coverUri,
                        placeholderIconRes = R.drawable.ic_folder,
                        payload = album
                    )
                })
                binding.emptyView.text = "暂无离线专辑\n长按 WebDAV 目录选择「批量离线下载」即可保存"
                binding.emptyView.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
                binding.actionFab.visibility = View.GONE
            }
        }
    }

    private fun TrackRecord.toGalleryItem(placeholderRes: Int): LibraryGalleryItem {
        val localCover = if (uri.startsWith("file://")) {
            val audioFile = Uri.parse(uri).path?.let { File(it) }
            findLocalAlbumCover(audioFile?.parentFile)?.let { Uri.fromFile(it) }
        } else {
            null
        }
        return LibraryGalleryItem(
            id = "${currentTab}_${key}",
            title = title,
            subtitle = artist.ifEmpty { albumTitle.ifEmpty { "未知艺术家" } },
            badge = if (clipEndMs > 0 && clipEndMs > clipStartMs) "CUE" else "",
            localCoverUri = localCover,
            remoteDirectoryPath = directoryPath,
            placeholderIconRes = placeholderRes,
            payload = this
        )
    }

    private fun findLocalAlbumCover(dir: File?): File? {
        if (dir == null || !dir.isDirectory) return null
        val imageExts = setOf("png", "jpg", "jpeg", "bmp", "webp")
        val coverNames = setOf("cover", "folder", "front", "album", "albumart", "artwork")
        val files = dir.listFiles() ?: return null
        return files.firstOrNull {
            it.isFile && it.extension.lowercase() in imageExts && it.nameWithoutExtension.lowercase() in coverNames
        } ?: files.firstOrNull { it.isFile && it.extension.lowercase() in imageExts }
    }

    private fun openPlaylistDetail(playlist: CustomPlaylist) {
        activePlaylistId = playlist.id
        activeOfflineAlbum = null
        activeDetailTracks = playlist.tracks
        invalidateOptionsMenu()
        renderCurrentTab()
    }

    private fun openOfflineAlbumDetail(album: OfflineManager.OfflineAlbum) {
        lifecycleScope.launch {
            val tracks = buildOfflineTrackRecords(album)
            activeOfflineAlbum = album
            activePlaylistId = null
            activeDetailTracks = tracks
            invalidateOptionsMenu()
            renderCurrentTab()
        }
    }

    private fun handleCardClick(item: LibraryGalleryItem, position: Int) {
        if (isInDetailMode()) {
            if (activeDetailTracks.isNotEmpty()) {
                playTrackRecords(activeDetailTracks, position)
            }
            return
        }
        when (currentTab) {
            TAB_FAVORITES -> playTrackRecords(libraryManager.getFavorites(), position)
            TAB_PLAYLISTS -> (item.payload as? CustomPlaylist)?.let { openPlaylistDetail(it) }
            TAB_HISTORY -> playTrackRecords(libraryManager.getHistory(), position)
            TAB_OFFLINE -> (item.payload as? OfflineManager.OfflineAlbum)?.let { openOfflineAlbumDetail(it) }
        }
    }

    private fun handleCardLongClick(item: LibraryGalleryItem, position: Int): Boolean {
        activePlaylistId?.let { playlistId ->
            val track = item.payload as? TrackRecord ?: return false
            MaterialAlertDialogBuilder(this)
                .setTitle(track.title)
                .setItems(arrayOf("立即播放", "红心收藏", "从歌单移除")) { _, which ->
                    when (which) {
                        0 -> playTrackRecords(activeDetailTracks, position)
                        1 -> PlaylistBatchHelper.favoriteTracks(this, libraryManager, listOf(track))
                        2 -> libraryManager.removeTrackFromPlaylist(playlistId, track.key)
                    }
                }
                .show()
            return true
        }
        if (activeOfflineAlbum != null) {
            val track = item.payload as? TrackRecord ?: return false
            MaterialAlertDialogBuilder(this)
                .setTitle(track.title)
                .setItems(arrayOf("立即播放", "红心收藏", "添加到歌单")) { _, which ->
                    when (which) {
                        0 -> playTrackRecords(activeDetailTracks, position)
                        1 -> PlaylistBatchHelper.favoriteTracks(this, libraryManager, listOf(track))
                        2 -> PlaylistBatchHelper.showAddTracksToPlaylistDialog(this, libraryManager, listOf(track))
                    }
                }
                .show()
            return true
        }
        when (currentTab) {
            TAB_FAVORITES -> {
                val track = item.payload as? TrackRecord ?: return false
                MaterialAlertDialogBuilder(this)
                    .setTitle(track.title)
                    .setItems(arrayOf("立即播放", "添加到歌单", "取消红心收藏")) { _, which ->
                        when (which) {
                            0 -> playTrackRecords(libraryManager.getFavorites(), position)
                            1 -> PlaylistBatchHelper.showAddTracksToPlaylistDialog(this, libraryManager, listOf(track))
                            2 -> libraryManager.removeFavorite(track.key)
                        }
                    }
                    .show()
                return true
            }
            TAB_PLAYLISTS -> {
                val playlist = item.payload as? CustomPlaylist ?: return false
                MaterialAlertDialogBuilder(this)
                    .setTitle(playlist.name)
                    .setItems(arrayOf("全部播放", "全部红心收藏", "重命名歌单", "删除歌单")) { _, which ->
                        when (which) {
                            0 -> if (playlist.tracks.isNotEmpty()) playTrackRecords(playlist.tracks, 0)
                            1 -> PlaylistBatchHelper.favoriteTracks(this, libraryManager, playlist.tracks)
                            2 -> showRenamePlaylistDialog(playlist)
                            3 -> confirmDeletePlaylist(playlist)
                        }
                    }
                    .show()
                return true
            }
            TAB_HISTORY -> {
                val track = item.payload as? TrackRecord ?: return false
                MaterialAlertDialogBuilder(this)
                    .setTitle(track.title)
                    .setItems(arrayOf("立即播放", "红心收藏", "添加到歌单")) { _, which ->
                        when (which) {
                            0 -> playTrackRecords(libraryManager.getHistory(), position)
                            1 -> PlaylistBatchHelper.favoriteTracks(this, libraryManager, listOf(track))
                            2 -> PlaylistBatchHelper.showAddTracksToPlaylistDialog(this, libraryManager, listOf(track))
                        }
                    }
                    .show()
                return true
            }
            TAB_OFFLINE -> {
                val album = item.payload as? OfflineManager.OfflineAlbum ?: return false
                lifecycleScope.launch {
                    val tracks = buildOfflineTrackRecords(album)
                    MaterialAlertDialogBuilder(this@LibraryActivity)
                        .setTitle(album.name)
                        .setItems(arrayOf("全部播放", "全部红心收藏", "添加到歌单", "删除离线专辑")) { _, which ->
                            when (which) {
                                0 -> if (tracks.isNotEmpty()) playTrackRecords(tracks, 0)
                                1 -> PlaylistBatchHelper.favoriteTracks(this@LibraryActivity, libraryManager, tracks)
                                2 -> PlaylistBatchHelper.showAddTracksToPlaylistDialog(
                                    this@LibraryActivity, libraryManager, tracks, "将「${album.name}」添加到歌单"
                                )
                                3 -> confirmDeleteOfflineAlbum(album)
                            }
                        }
                        .show()
                }
                return true
            }
            else -> return false
        }
    }

    private fun showPlaylistDetailDialog(playlist: CustomPlaylist) {
        if (playlist.tracks.isEmpty()) {
            MaterialAlertDialogBuilder(this)
                .setTitle(playlist.name)
                .setMessage("歌单暂无曲目，可在播放页或目录长按菜单批量添加。")
                .setPositiveButton("重命名") { _, _ -> showRenamePlaylistDialog(playlist) }
                .setNeutralButton("删除歌单") { _, _ -> confirmDeletePlaylist(playlist) }
                .setNegativeButton("关闭", null)
                .show()
            return
        }

        val labels = playlist.tracks.map { track ->
            if (track.artist.isNotEmpty()) "${track.title} - ${track.artist}" else track.title
        }.toTypedArray()

        MaterialAlertDialogBuilder(this)
            .setTitle("${playlist.name} (${playlist.tracks.size})")
            .setItems(labels) { _, index ->
                playTrackRecords(playlist.tracks, index)
            }
            .setPositiveButton("全部播放") { _, _ ->
                playTrackRecords(playlist.tracks, 0)
            }
            .setNeutralButton("全部收藏") { _, _ ->
                PlaylistBatchHelper.favoriteTracks(this, libraryManager, playlist.tracks)
            }
            .setNegativeButton("管理") { _, _ ->
                MaterialAlertDialogBuilder(this)
                    .setTitle("管理「${playlist.name}」")
                    .setItems(arrayOf("重命名歌单", "移除曲目", "删除歌单")) { _, action ->
                        when (action) {
                            0 -> showRenamePlaylistDialog(playlist)
                            1 -> {
                                val removeLabels = playlist.tracks.map { "移除：${it.title}" }.toTypedArray()
                                MaterialAlertDialogBuilder(this)
                                    .setTitle("移除曲目")
                                    .setItems(removeLabels) { _, idx ->
                                        libraryManager.removeTrackFromPlaylist(playlist.id, playlist.tracks[idx].key)
                                    }
                                    .setNegativeButton("完成", null)
                                    .show()
                            }
                            2 -> confirmDeletePlaylist(playlist)
                        }
                    }
                    .show()
            }
            .show()
    }

    private fun showOfflineAlbumDialog(album: OfflineManager.OfflineAlbum) {
        lifecycleScope.launch {
            val tracks = buildOfflineTrackRecords(album)
            if (tracks.isEmpty()) {
                MaterialAlertDialogBuilder(this@LibraryActivity)
                    .setTitle(album.name)
                    .setMessage("该离线目录中没有可播放的音频文件（共 ${album.files.size} 个文件）。")
                    .setNeutralButton("删除离线专辑") { _, _ -> confirmDeleteOfflineAlbum(album) }
                    .setNegativeButton("关闭", null)
                    .show()
                return@launch
            }

            val labels = tracks.map { track ->
                if (track.trackNumber != null && track.trackNumber > 0) {
                    "%02d. %s".format(track.trackNumber, track.title)
                } else {
                    track.title
                }
            }.toTypedArray()

            MaterialAlertDialogBuilder(this@LibraryActivity)
                .setTitle("${album.name} (${tracks.size} 首)")
                .setItems(labels) { _, index ->
                    playTrackRecords(tracks, index)
                }
                .setPositiveButton("全部播放") { _, _ ->
                    playTrackRecords(tracks, 0)
                }
                .setNeutralButton("批量收藏/歌单") { _, _ ->
                    MaterialAlertDialogBuilder(this@LibraryActivity)
                        .setTitle(album.name)
                        .setItems(arrayOf("全部红心收藏", "添加到自定义歌单", "删除离线专辑")) { _, idx ->
                            when (idx) {
                                0 -> PlaylistBatchHelper.favoriteTracks(this@LibraryActivity, libraryManager, tracks)
                                1 -> PlaylistBatchHelper.showAddTracksToPlaylistDialog(
                                    this@LibraryActivity, libraryManager, tracks, "将「${album.name}」添加到歌单"
                                )
                                2 -> confirmDeleteOfflineAlbum(album)
                            }
                        }
                        .show()
                }
                .setNegativeButton("关闭", null)
                .show()
        }
    }

    private suspend fun buildOfflineTrackRecords(album: OfflineManager.OfflineAlbum): List<TrackRecord> =
        withContext(Dispatchers.IO) {
            val audioFiles = album.files.filter { it.extension in AUDIO_EXTENSIONS }
            val cueFiles = album.files.filter { it.extension == "cue" }
            val result = mutableListOf<TrackRecord>()
            val coveredUris = HashSet<String>()

            for (cueFile in cueFiles) {
                val cueSheet = runCatching {
                    val bytes = contentResolver.openInputStream(cueFile.uri)?.use { it.readBytes() }
                        ?: return@runCatching null
                    val encoding = EncodingDetector.detect(bytes)
                    CueParser.parse(String(bytes, charset(encoding)))
                }.getOrNull() ?: continue
                if (cueSheet.tracks.isEmpty()) continue

                val matchedAudio = audioFiles.firstOrNull {
                    it.name.equals(cueSheet.file, ignoreCase = true)
                } ?: audioFiles.firstOrNull {
                    it.nameWithoutExtension.equals(cueFile.nameWithoutExtension, ignoreCase = true)
                } ?: audioFiles.singleOrNull()

                if (matchedAudio != null) {
                    val audioUriStr = matchedAudio.uri.toString()
                    coveredUris.add(audioUriStr)
                    result.addAll(
                        cueSheet.toTrackRecords(
                            audioUri = audioUriStr,
                            keyPrefix = audioUriStr,
                            fallbackAlbumTitle = album.name
                        )
                    )
                }
            }

            for (file in audioFiles) {
                val uriStr = file.uri.toString()
                if (uriStr in coveredUris) continue
                if (file.extension == "flac") {
                    val embedded = runCatching {
                        val headerBytes = contentResolver.openInputStream(file.uri)?.use { input ->
                            val buf = ByteArray(2_097_152)
                            val read = input.read(buf)
                            if (read > 0) buf.copyOf(read) else ByteArray(0)
                        } ?: ByteArray(0)
                        FlacCueExtractor.extract(headerBytes, file.nameWithoutExtension)
                    }.getOrNull()
                    if (embedded != null && embedded.tracks.size > 1) {
                        result.addAll(
                            embedded.toTrackRecords(
                                audioUri = uriStr,
                                keyPrefix = uriStr,
                                fallbackAlbumTitle = album.name
                            )
                        )
                        continue
                    }
                }
                result.add(
                    TrackRecord(
                        key = uriStr,
                        mediaId = uriStr,
                        uri = uriStr,
                        title = file.nameWithoutExtension,
                        albumTitle = album.name
                    )
                )
            }

            result
        }

    private fun showCreatePlaylistDialog() {
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
                    libraryManager.createPlaylist(name)
                    Toast.makeText(this, "已创建歌单「$name」", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showRenamePlaylistDialog(playlist: CustomPlaylist) {
        val input = EditText(this).apply {
            setText(playlist.name)
            setPadding(48, 24, 48, 24)
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("重命名歌单")
            .setView(input)
            .setPositiveButton("保存") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    libraryManager.renamePlaylist(playlist.id, name)
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun confirmDeletePlaylist(playlist: CustomPlaylist) {
        MaterialAlertDialogBuilder(this)
            .setTitle("删除歌单")
            .setMessage("确定要删除歌单「${playlist.name}」吗？")
            .setPositiveButton("删除") { _, _ ->
                libraryManager.deletePlaylist(playlist.id)
                Toast.makeText(this, "已删除「${playlist.name}」", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun confirmDeleteOfflineAlbum(album: OfflineManager.OfflineAlbum) {
        MaterialAlertDialogBuilder(this)
            .setTitle("删除离线专辑")
            .setMessage("确定要删除「${album.name}」的全部离线文件吗？")
            .setPositiveButton("删除") { _, _ ->
                lifecycleScope.launch {
                    offlineManager.deleteOfflineAlbum(album)
                    Toast.makeText(this@LibraryActivity, "已删除「${album.name}」", Toast.LENGTH_SHORT).show()
                    if (activeOfflineAlbum?.id == album.id) {
                        exitDetailMode()
                    }
                    loadOfflineAlbums()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun connectToPlayerService() {
        val sessionToken = SessionToken(this, ComponentName(this, PlayerService::class.java))
        controllerFuture = MediaController.Builder(this, sessionToken).buildAsync().also { future ->
            future.addListener({
                try {
                    mediaController = future.get()
                    onControllerConnected()
                } catch (_: Exception) {
                    runOnUiThread { updateMiniPlayerVisibility() }
                }
            }, MoreExecutors.directExecutor())
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

            override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
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

        pendingMediaItems?.let { (items, idx) ->
            pendingMediaItems = null
            playMediaItems(items, idx)
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
        binding.miniPlayerCard.setOnClickListener {
            startActivity(Intent(this, PlayerActivity::class.java))
        }
        binding.miniPlayerInfo.setOnClickListener {
            startActivity(Intent(this, PlayerActivity::class.java))
        }
        binding.miniPlayerPlayPause.setOnClickListener {
            mediaController?.let { if (it.isPlaying) it.pause() else it.play() }
        }
        binding.miniPlayerPrev.setOnClickListener {
            mediaController?.seekToPrevious()
        }
        binding.miniPlayerNext.setOnClickListener {
            mediaController?.seekToNext()
        }
        binding.miniPlayerPlayMode.setOnClickListener {
            togglePlayMode()
        }
        binding.miniPlayerPlaylist.setOnClickListener {
            showPlaylistDialog()
        }
        binding.miniPlayerProgress.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    mediaController?.let { controller ->
                        val duration = controller.duration
                        if (duration > 0) {
                            controller.seekTo(duration * progress / 1000L)
                        }
                    }
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                isMiniPlayerSeeking = true
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                isMiniPlayerSeeking = false
            }
        })
    }

    private fun updateMiniPlayerVisibility() {
        val controller = mediaController
        val visible = controller != null && controller.mediaItemCount > 0
        binding.miniPlayerCard.visibility = if (visible) View.VISIBLE else View.GONE
    }

    private fun updateMiniPlayerInfo() {
        mediaController?.let { controller ->
            val metadata = controller.mediaMetadata
            val itemTitle = controller.currentMediaItem?.mediaMetadata?.title?.toString()
            val title = metadata.title?.toString()?.takeIf { it.isNotEmpty() }
                ?: itemTitle?.takeIf { it.isNotEmpty() }
                ?: "Unknown"
            val artist = metadata.artist?.toString().orEmpty()
            binding.miniPlayerTitle.text = title
            binding.miniPlayerArtist.text = artist

            val artwork = ArtworkUtils.decodeArtwork(
                metadata.artworkData ?: controller.currentMediaItem?.mediaMetadata?.artworkData
            )
            if (artwork != null) {
                binding.miniPlayerArtwork.setImageBitmap(artwork)
                binding.miniPlayerArtwork.visibility = View.VISIBLE
            } else {
                binding.miniPlayerArtwork.setImageDrawable(null)
                binding.miniPlayerArtwork.visibility = View.GONE
            }
            updateMiniPlayerVisibility()
        }
    }

    private fun updateMiniPlayerControls() {
        mediaController?.let { controller ->
            binding.miniPlayerPlayPause.setImageResource(
                if (controller.isPlaying) R.drawable.ic_pause else R.drawable.ic_play
            )
        }
    }

    private fun updateMiniPlayerProgress() {
        mediaController?.let { controller ->
            val duration = controller.duration
            val position = controller.currentPosition
            if (duration > 0 && !isMiniPlayerSeeking) {
                binding.miniPlayerProgress.progress = ((position * 1000L) / duration).toInt()
            }
        }
    }

    private fun togglePlayMode() {
        currentPlayMode = currentPlayMode.next()
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
        updatePlayModeIcon()
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
                updateMiniPlayerVisibility()
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
            layoutManager = LinearLayoutManager(this@LibraryActivity)
            adapter = playlistAdapter
        }

        dialogView.findViewById<Button>(R.id.clearPlaylistButton).setOnClickListener {
            AlertDialog.Builder(this@LibraryActivity)
                .setTitle("清空播放列表")
                .setMessage("确定要清空全部歌曲吗？")
                .setPositiveButton("确定") { _, _ ->
                    controller.clearMediaItems()
                    refreshPlaylistDialog(controller, recyclerView, emptyView)
                    dialog.setTitle("播放列表 (${controller.mediaItemCount})")
                    updateMiniPlayerVisibility()
                }
                .setNegativeButton("取消", null)
                .show()
        }

        dialogView.findViewById<Button>(R.id.favoriteAllButton).setOnClickListener {
            val tracks = collectControllerTracks(controller)
            PlaylistBatchHelper.favoriteTracks(this@LibraryActivity, libraryManager, tracks)
        }

        dialogView.findViewById<Button>(R.id.addAllToPlaylistButton).setOnClickListener {
            val tracks = collectControllerTracks(controller)
            PlaylistBatchHelper.showAddTracksToPlaylistDialog(
                this@LibraryActivity,
                libraryManager,
                tracks,
                "将当前播放列表添加到歌单"
            )
        }

        refreshPlaylistDialog(controller, recyclerView, emptyView)
        dialog.show()
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

    private fun collectControllerTracks(controller: MediaController): List<TrackRecord> {
        return buildList {
            for (i in 0 until controller.mediaItemCount) {
                TrackRecord.fromMediaItem(controller.getMediaItemAt(i))?.let(::add)
            }
        }
    }

    private fun playTrackRecords(records: List<TrackRecord>, startIndex: Int) {
        if (records.isEmpty()) return
        val items = records.map { it.toMediaItem() }
        val safeIndex = startIndex.coerceIn(0, items.lastIndex)
        val controller = mediaController
        if (controller == null) {
            pendingMediaItems = items to safeIndex
            connectToPlayerService()
            Toast.makeText(this, "正在连接播放器...", Toast.LENGTH_SHORT).show()
            return
        }
        playMediaItems(items, safeIndex)
    }

    private fun playMediaItems(items: List<MediaItem>, startIndex: Int) {
        val controller = mediaController ?: return
        controller.setMediaItems(items, startIndex, 0L)
        controller.prepare()
        controller.play()
        updateMiniPlayerVisibility()
        updateMiniPlayerInfo()
        Toast.makeText(
            this,
            "正在播放：${items[startIndex].mediaMetadata.title ?: "未知曲目"}",
            Toast.LENGTH_SHORT
        ).show()
    }
}
