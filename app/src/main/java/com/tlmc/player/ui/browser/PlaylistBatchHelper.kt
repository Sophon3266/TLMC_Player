package com.tlmc.player.ui.browser

import android.content.Context
import android.widget.EditText
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.tlmc.player.data.model.TrackRecord
import com.tlmc.player.data.repository.LibraryManager

/**
 * 批量红心收藏与批量添加到自定义歌单的共享交互逻辑。
 */
object PlaylistBatchHelper {

    fun favoriteTracks(
        context: Context,
        libraryManager: LibraryManager,
        tracks: List<TrackRecord>
    ) {
        if (tracks.isEmpty()) {
            Toast.makeText(context, "没有可收藏的曲目", Toast.LENGTH_SHORT).show()
            return
        }
        val added = libraryManager.addFavorites(tracks)
        val msg = if (added > 0) {
            "已批量收藏 $added 首曲目"
        } else {
            "所选曲目均已在红心收藏中"
        }
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    }

    fun showAddTracksToPlaylistDialog(
        context: Context,
        libraryManager: LibraryManager,
        tracks: List<TrackRecord>,
        title: String = "添加到歌单"
    ) {
        if (tracks.isEmpty()) {
            Toast.makeText(context, "没有可添加的曲目", Toast.LENGTH_SHORT).show()
            return
        }

        val playlists = libraryManager.getPlaylists()
        val names = playlists.map { "${it.name} (${it.tracks.size})" } + listOf("+ 新建歌单")
        MaterialAlertDialogBuilder(context)
            .setTitle(title)
            .setItems(names.toTypedArray()) { _, which ->
                if (which == playlists.size) {
                    val input = EditText(context).apply {
                        hint = "输入歌单名称"
                        setPadding(48, 24, 48, 24)
                    }
                    MaterialAlertDialogBuilder(context)
                        .setTitle("新建歌单")
                        .setView(input)
                        .setPositiveButton("创建") { _, _ ->
                            val name = input.text.toString().trim()
                            if (name.isNotEmpty()) {
                                val created = libraryManager.createPlaylist(name)
                                val added = libraryManager.addTracksToPlaylist(created.id, tracks)
                                Toast.makeText(
                                    context,
                                    "已创建「${created.name}」并添加 $added 首曲目",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                        .setNegativeButton("取消", null)
                        .show()
                } else {
                    val target = playlists[which]
                    val added = libraryManager.addTracksToPlaylist(target.id, tracks)
                    val msg = if (added > 0) {
                        "已添加 $added 首曲目到「${target.name}」"
                    } else {
                        "所选曲目均已在「${target.name}」中"
                    }
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
}
