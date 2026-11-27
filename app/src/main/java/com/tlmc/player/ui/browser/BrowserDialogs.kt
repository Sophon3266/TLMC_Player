package com.tlmc.player.ui.browser

import android.net.Uri
import android.widget.EditText
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.tlmc.player.data.model.CustomPlaylist
import com.tlmc.player.data.model.TrackRecord
import com.tlmc.player.data.repository.OfflineManager
import com.tlmc.player.util.FileUtils
import kotlinx.coroutines.launch
import java.io.File

/**
 * 音乐库（红心收藏 / 自定义歌单 / 播放历史）、离线曲库与存储管理弹窗。
 */
internal fun BrowserActivity.showLibraryDialog() {
    val sections = arrayOf("红心收藏", "自定义歌单", "最近播放历史")
    MaterialAlertDialogBuilder(this)
        .setTitle("音乐库")
        .setItems(sections) { _, which ->
            when (which) {
                0 -> showFavoritesDialog()
                1 -> showCustomPlaylistsDialog()
                2 -> showHistoryDialog()
            }
        }
        .setNegativeButton("关闭", null)
        .show()
}

private fun BrowserActivity.showFavoritesDialog() {
    val favorites = libraryManager.getFavorites()
    if (favorites.isEmpty()) {
        MaterialAlertDialogBuilder(this)
            .setTitle("红心收藏")
            .setMessage("暂无收藏曲目，可在播放页点击红心图标收藏当前歌曲。")
            .setPositiveButton("确定", null)
            .show()
        return
    }

    val labels = favorites.map { track ->
        if (track.artist.isNotEmpty()) "${track.title} - ${track.artist}" else track.title
    }.toTypedArray()

    MaterialAlertDialogBuilder(this)
        .setTitle("红心收藏 (${favorites.size})")
        .setItems(labels) { _, index ->
            playTrackRecords(favorites, index)
        }
        .setPositiveButton("全部播放") { _, _ ->
            playTrackRecords(favorites, 0)
        }
        .setNeutralButton("管理") { _, _ ->
            showManageTracksDialog("红心收藏", favorites) { track ->
                libraryManager.removeFavorite(track.key)
            }
        }
        .setNegativeButton("关闭", null)
        .show()
}

private fun BrowserActivity.showCustomPlaylistsDialog() {
    val playlists = libraryManager.getPlaylists()
    val items = playlists.map { "${it.name} (${it.tracks.size} 首)" } + listOf("+ 新建歌单")

    MaterialAlertDialogBuilder(this)
        .setTitle("自定义歌单")
        .setItems(items.toTypedArray()) { _, which ->
            if (which == playlists.size) {
                showCreatePlaylistDialog { showCustomPlaylistsDialog() }
            } else {
                showSinglePlaylistDialog(playlists[which])
            }
        }
        .setNegativeButton("关闭", null)
        .show()
}

private fun BrowserActivity.showCreatePlaylistDialog(onCreated: () -> Unit) {
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
                onCreated()
            }
        }
        .setNegativeButton("取消", null)
        .show()
}

private fun BrowserActivity.showSinglePlaylistDialog(playlist: CustomPlaylist) {
    if (playlist.tracks.isEmpty()) {
        MaterialAlertDialogBuilder(this)
            .setTitle(playlist.name)
            .setMessage("歌单暂无曲目，可在播放页右上角菜单选择「添加到歌单」。")
            .setPositiveButton("重命名") { _, _ ->
                showRenamePlaylistDialog(playlist)
            }
            .setNeutralButton("删除歌单") { _, _ ->
                libraryManager.deletePlaylist(playlist.id)
                Toast.makeText(this, "已删除歌单「${playlist.name}」", Toast.LENGTH_SHORT).show()
            }
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
        .setNeutralButton("管理") { _, _ ->
            val actions = arrayOf("重命名歌单", "移除曲目", "删除歌单")
            MaterialAlertDialogBuilder(this)
                .setTitle("管理「${playlist.name}」")
                .setItems(actions) { _, actionIndex ->
                    when (actionIndex) {
                        0 -> showRenamePlaylistDialog(playlist)
                        1 -> showManageTracksDialog("移除曲目", playlist.tracks) { track ->
                            libraryManager.removeTrackFromPlaylist(playlist.id, track.key)
                        }
                        2 -> {
                            libraryManager.deletePlaylist(playlist.id)
                            Toast.makeText(this, "已删除歌单「${playlist.name}」", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                .show()
        }
        .setNegativeButton("关闭", null)
        .show()
}

private fun BrowserActivity.showRenamePlaylistDialog(playlist: CustomPlaylist) {
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

private fun BrowserActivity.showManageTracksDialog(
    title: String,
    tracks: List<TrackRecord>,
    onRemove: (TrackRecord) -> Unit
) {
    val labels = tracks.map { "移除：${it.title}" }.toTypedArray()
    MaterialAlertDialogBuilder(this)
        .setTitle(title)
        .setItems(labels) { _, index ->
            val target = tracks[index]
            onRemove(target)
            Toast.makeText(this, "已移除: ${target.title}", Toast.LENGTH_SHORT).show()
        }
        .setNegativeButton("完成", null)
        .show()
}

private fun BrowserActivity.showHistoryDialog() {
    val history = libraryManager.getHistory()
    if (history.isEmpty()) {
        MaterialAlertDialogBuilder(this)
            .setTitle("最近播放历史")
            .setMessage("暂无播放历史。")
            .setPositiveButton("确定", null)
            .show()
        return
    }

    val labels = history.map { track ->
        if (track.artist.isNotEmpty()) "${track.title} - ${track.artist}" else track.title
    }.toTypedArray()

    MaterialAlertDialogBuilder(this)
        .setTitle("最近播放历史 (${history.size})")
        .setItems(labels) { _, index ->
            playTrackRecords(history, index)
        }
        .setPositiveButton("全部播放") { _, _ ->
            playTrackRecords(history, 0)
        }
        .setNeutralButton("清空历史") { _, _ ->
            libraryManager.clearHistory()
            Toast.makeText(this, "播放历史已清空", Toast.LENGTH_SHORT).show()
        }
        .setNegativeButton("关闭", null)
        .show()
}

internal fun BrowserActivity.showOfflineLibraryDialog() {
    lifecycleScope.launch {
        val albums = offlineManager.getOfflineAlbums()
        if (albums.isEmpty()) {
            val progress = offlineManager.downloadProgress.value
            val msg = if (progress != null && progress.active) {
                "正在离线下载「${progress.albumName}」(${progress.completed}/${progress.total})..."
            } else {
                "暂无离线专辑。\n长按 WebDAV 文件夹并选择「批量离线下载」即可离线保存。"
            }
            MaterialAlertDialogBuilder(this@showOfflineLibraryDialog)
                .setTitle("离线曲库")
                .setMessage(msg)
                .setPositiveButton("确定", null)
                .show()
            return@launch
        }

        val labels = albums.map { album ->
            "${album.name} (${album.files.size} 个文件 · ${FileUtils.formatFileSize(album.totalBytes)})"
        }.toTypedArray()

        MaterialAlertDialogBuilder(this@showOfflineLibraryDialog)
            .setTitle("离线曲库 (${albums.size} 张专辑)")
            .setItems(labels) { _, index ->
                showOfflineAlbumDetailDialog(albums[index])
            }
            .setNegativeButton("关闭", null)
            .show()
    }
}

private fun BrowserActivity.showOfflineAlbumDetailDialog(album: OfflineManager.OfflineAlbum) {
    val audioExtensions = setOf("flac", "mp3", "wav", "ogg", "m4a", "aac")
    val playable = album.files.filter { it.extension.lowercase() in audioExtensions }
    val labels = playable.map { it.name }.toTypedArray()

    if (playable.isEmpty()) {
        MaterialAlertDialogBuilder(this)
            .setTitle(album.name)
            .setMessage("该离线目录中没有音频文件（共 ${album.files.size} 个文件）。")
            .setNeutralButton("删除离线专辑") { _, _ ->
                lifecycleScope.launch {
                    offlineManager.deleteOfflineAlbum(album)
                    Toast.makeText(this@showOfflineAlbumDetailDialog, "已删除「${album.name}」", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("关闭", null)
            .show()
        return
    }

    MaterialAlertDialogBuilder(this)
        .setTitle("${album.name} (${playable.size} 首)")
        .setItems(labels) { _, index ->
            playOfflineFiles(playable, index)
        }
        .setPositiveButton("全部播放") { _, _ ->
            playOfflineFiles(playable, 0)
        }
        .setNeutralButton("删除离线专辑") { _, _ ->
            MaterialAlertDialogBuilder(this)
                .setTitle("删除离线专辑")
                .setMessage("确定要删除「${album.name}」的全部离线文件吗？")
                .setPositiveButton("删除") { _, _ ->
                    lifecycleScope.launch {
                        offlineManager.deleteOfflineAlbum(album)
                        Toast.makeText(this@showOfflineAlbumDetailDialog, "已删除「${album.name}」", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("取消", null)
                .show()
        }
        .setNegativeButton("关闭", null)
        .show()
}

private fun BrowserActivity.playOfflineFiles(files: List<File>, startIndex: Int) {
    val items = files.map { file ->
        MediaItem.Builder()
            .setMediaId(file.absolutePath)
            .setUri(Uri.fromFile(file))
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(file.nameWithoutExtension)
                    .setAlbumTitle(file.parentFile?.name ?: "")
                    .build()
            )
            .build()
    }
    playMediaItemsDirectly(items, startIndex)
}

internal fun BrowserActivity.showStorageDialog() {
    lifecycleScope.launch {
        val stats = offlineManager.getStorageStats()
        val message = buildString {
            appendLine("媒体播放缓存：${FileUtils.formatFileSize(stats.mediaCacheBytes)} / ${FileUtils.formatFileSize(stats.mediaCacheLimitBytes)}")
            appendLine("封面与缩略图缓存：${FileUtils.formatFileSize(stats.artworkCacheBytes)}")
            append("离线曲库占用：${FileUtils.formatFileSize(stats.offlineLibraryBytes)}")
        }

        MaterialAlertDialogBuilder(this@showStorageDialog)
            .setTitle("存储与缓存管理")
            .setMessage(message)
            .setPositiveButton("清理媒体缓存") { _, _ ->
                lifecycleScope.launch {
                    offlineManager.clearMediaCache()
                    Toast.makeText(this@showStorageDialog, "媒体缓存已清理", Toast.LENGTH_SHORT).show()
                }
            }
            .setNeutralButton("清理封面缓存") { _, _ ->
                lifecycleScope.launch {
                    folderCoverLoader.clearMemoryCache()
                    offlineManager.clearArtworkCache()
                    Toast.makeText(this@showStorageDialog, "封面缓存已清理", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("关闭", null)
            .show()
    }
}
