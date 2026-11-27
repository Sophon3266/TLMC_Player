package com.tlmc.player.ui.library

import android.content.ComponentName
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.tlmc.player.R
import com.tlmc.player.data.model.CustomPlaylist
import com.tlmc.player.data.model.TrackRecord
import com.tlmc.player.data.repository.LibraryManager
import com.tlmc.player.data.repository.OfflineManager
import com.tlmc.player.databinding.ActivityLibraryBinding
import com.tlmc.player.ui.browser.FolderCoverLoader
import com.tlmc.player.ui.browser.PlaylistBatchHelper
import com.tlmc.player.ui.player.PlayerService
import com.tlmc.player.util.FileUtils
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
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

    private lateinit var binding: ActivityLibraryBinding
    private lateinit var adapter: LibraryGalleryAdapter
    private var currentTab = TAB_FAVORITES
    private var offlineAlbums: List<OfflineManager.OfflineAlbum> = emptyList()

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null
    private var pendingMediaItems: Pair<List<MediaItem>, Int>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLibraryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        setupTabs(intent.getIntExtra(EXTRA_INITIAL_TAB, TAB_FAVORITES))
        setupGallery()
        setupFab()
        observeLibrary()
        loadOfflineAlbums()
    }

    override fun onStart() {
        super.onStart()
        connectToPlayerService()
    }

    override fun onStop() {
        super.onStop()
        controllerFuture?.let { MediaController.releaseFuture(it) }
        mediaController = null
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        if (currentTab == TAB_HISTORY) {
            menu.add(0, 1001, 0, "清空历史").setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == 1001 && currentTab == TAB_HISTORY) {
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
        return super.onOptionsItemSelected(item)
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
            onItemLongClick = { item, position -> handleCardLongClick(item, position) }
        )
        val spanCount = if (resources.configuration.screenWidthDp >= 600) 3 else 2
        binding.galleryRecyclerView.layoutManager = GridLayoutManager(this, spanCount)
        binding.galleryRecyclerView.adapter = adapter
    }

    private fun setupFab() {
        binding.actionFab.setOnClickListener {
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
            if (currentTab == TAB_FAVORITES) renderCurrentTab()
        }
        libraryManager.playlists.observe(this) {
            if (currentTab == TAB_PLAYLISTS) renderCurrentTab()
        }
        libraryManager.history.observe(this) {
            if (currentTab == TAB_HISTORY) renderCurrentTab()
        }
    }

    private fun loadOfflineAlbums() {
        lifecycleScope.launch {
            offlineAlbums = offlineManager.getOfflineAlbums()
            if (currentTab == TAB_OFFLINE) renderCurrentTab()
        }
    }

    private fun renderCurrentTab() {
        when (currentTab) {
            TAB_FAVORITES -> {
                val list = libraryManager.getFavorites()
                adapter.submitList(list.map { it.toGalleryItem(R.drawable.ic_favorite) })
                binding.emptyView.text = "暂无红心收藏\n可在播放页点击红心或长按专辑目录批量收藏"
                binding.emptyView.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
                binding.actionFab.text = "全部播放"
                binding.actionFab.setIconResource(R.drawable.ic_play)
                binding.actionFab.visibility = if (list.isNotEmpty()) View.VISIBLE else View.GONE
            }
            TAB_PLAYLISTS -> {
                val list = libraryManager.getPlaylists()
                adapter.submitList(list.map { playlist ->
                    val firstDir = playlist.tracks.firstOrNull { it.directoryPath.isNotEmpty() }?.directoryPath.orEmpty()
                    LibraryGalleryItem(
                        id = "pl_${playlist.id}",
                        title = playlist.name,
                        subtitle = if (playlist.tracks.isEmpty()) "空歌单" else "${playlist.tracks.size} 首曲目",
                        badge = "${playlist.tracks.size} 首",
                        remoteDirectoryPath = firstDir,
                        placeholderIconRes = R.drawable.ic_playlist,
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
                        id = "off_${album.directory.absolutePath}",
                        title = album.name,
                        subtitle = FileUtils.formatFileSize(album.totalBytes),
                        badge = if (audioCount > 0) "$audioCount 首" else "${album.files.size} 文件",
                        localCoverFile = album.coverFile,
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
            findLocalAlbumCover(audioFile?.parentFile)
        } else {
            null
        }
        return LibraryGalleryItem(
            id = "${currentTab}_${key}",
            title = title,
            subtitle = artist.ifEmpty { albumTitle.ifEmpty { "未知艺术家" } },
            badge = if (clipEndMs > 0 && clipEndMs > clipStartMs) "CUE" else "",
            localCoverFile = localCover,
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

    private fun handleCardClick(item: LibraryGalleryItem, position: Int) {
        when (currentTab) {
            TAB_FAVORITES -> playTrackRecords(libraryManager.getFavorites(), position)
            TAB_PLAYLISTS -> (item.payload as? CustomPlaylist)?.let { showPlaylistDetailDialog(it) }
            TAB_HISTORY -> playTrackRecords(libraryManager.getHistory(), position)
            TAB_OFFLINE -> (item.payload as? OfflineManager.OfflineAlbum)?.let { showOfflineAlbumDialog(it) }
        }
    }

    private fun handleCardLongClick(item: LibraryGalleryItem, position: Int): Boolean {
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
                val tracks = buildOfflineTrackRecords(album)
                MaterialAlertDialogBuilder(this)
                    .setTitle(album.name)
                    .setItems(arrayOf("全部播放", "全部红心收藏", "添加到歌单", "删除离线专辑")) { _, which ->
                        when (which) {
                            0 -> if (tracks.isNotEmpty()) playTrackRecords(tracks, 0)
                            1 -> PlaylistBatchHelper.favoriteTracks(this, libraryManager, tracks)
                            2 -> PlaylistBatchHelper.showAddTracksToPlaylistDialog(
                                this, libraryManager, tracks, "将「${album.name}」添加到歌单"
                            )
                            3 -> confirmDeleteOfflineAlbum(album)
                        }
                    }
                    .show()
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
        val tracks = buildOfflineTrackRecords(album)
        if (tracks.isEmpty()) {
            MaterialAlertDialogBuilder(this)
                .setTitle(album.name)
                .setMessage("该离线目录中没有可播放的音频文件（共 ${album.files.size} 个文件）。")
                .setNeutralButton("删除离线专辑") { _, _ -> confirmDeleteOfflineAlbum(album) }
                .setNegativeButton("关闭", null)
                .show()
            return
        }

        val labels = tracks.map { it.title }.toTypedArray()
        MaterialAlertDialogBuilder(this)
            .setTitle("${album.name} (${tracks.size} 首)")
            .setItems(labels) { _, index ->
                playTrackRecords(tracks, index)
            }
            .setPositiveButton("全部播放") { _, _ ->
                playTrackRecords(tracks, 0)
            }
            .setNeutralButton("批量收藏/歌单") { _, _ ->
                MaterialAlertDialogBuilder(this)
                    .setTitle(album.name)
                    .setItems(arrayOf("全部红心收藏", "添加到自定义歌单", "删除离线专辑")) { _, idx ->
                        when (idx) {
                            0 -> PlaylistBatchHelper.favoriteTracks(this, libraryManager, tracks)
                            1 -> PlaylistBatchHelper.showAddTracksToPlaylistDialog(
                                this, libraryManager, tracks, "将「${album.name}」添加到歌单"
                            )
                            2 -> confirmDeleteOfflineAlbum(album)
                        }
                    }
                    .show()
            }
            .setNegativeButton("关闭", null)
            .show()
    }

    private fun buildOfflineTrackRecords(album: OfflineManager.OfflineAlbum): List<TrackRecord> {
        return album.files
            .filter { it.extension.lowercase() in AUDIO_EXTENSIONS }
            .map { file ->
                TrackRecord(
                    key = file.absolutePath,
                    mediaId = file.absolutePath,
                    uri = Uri.fromFile(file).toString(),
                    title = file.nameWithoutExtension,
                    albumTitle = album.name
                )
            }
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
                    pendingMediaItems?.let { (items, idx) ->
                        pendingMediaItems = null
                        playMediaItems(items, idx)
                    }
                } catch (_: Exception) {
                    // 忽略连接失败
                }
            }, MoreExecutors.directExecutor())
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
        Toast.makeText(
            this,
            "正在播放：${items[startIndex].mediaMetadata.title ?: "未知曲目"}",
            Toast.LENGTH_SHORT
        ).show()
    }
}
