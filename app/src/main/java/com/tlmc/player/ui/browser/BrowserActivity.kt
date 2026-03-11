package com.tlmc.player.ui.browser

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.tlmc.player.R
import com.tlmc.player.data.model.ServerConfig
import com.tlmc.player.data.model.WebDavFile
import com.tlmc.player.databinding.ActivityBrowserBinding
import com.tlmc.player.ui.image.ImageActivity
import com.tlmc.player.ui.player.PlayerActivity
import com.tlmc.player.ui.text.TextActivity
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class BrowserActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBrowserBinding
    private val viewModel: BrowserViewModel by viewModels()
    private lateinit var fileAdapter: FileAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBrowserBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        setupRecyclerView()
        setupSwipeRefresh()
        observeViewModel()
        requestNotificationPermission()

        if (savedInstanceState == null) {
            viewModel.loadDirectory("/")
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 100)
        }
    }

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

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setOnRefreshListener {
            viewModel.refresh()
        }
    }

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
        }

        viewModel.error.observe(this) { error ->
            error?.let {
                Toast.makeText(this, it, Toast.LENGTH_LONG).show()
                viewModel.clearError()
            }
        }

        viewModel.directoryFiles.observe(this) { /* Keep synced for CUE lookup */ }
    }

    private fun onFileClicked(file: WebDavFile) {
        when {
            file.isDirectory -> viewModel.loadDirectory(file.path)
            file.isAudio -> openPlayer(file)
            file.isCue -> openPlayerFromCue(file)
            file.isImage -> openImage(file)
            file.isText -> openText(file)
            else -> Toast.makeText(this, "不支持的文件类型: ${file.extension}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun onFileLongClicked(file: WebDavFile): Boolean {
        MaterialAlertDialogBuilder(this)
            .setTitle(file.name)
            .setMessage("大小: ${com.tlmc.player.util.FileUtils.formatFileSize(file.size)}\n路径: ${file.path}")
            .setPositiveButton("确定", null)
            .show()
        return true
    }

    private fun openPlayer(file: WebDavFile) {
        val intent = Intent(this, PlayerActivity::class.java).apply {
            putExtra(PlayerActivity.EXTRA_FILE_PATH, file.path)
            putExtra(PlayerActivity.EXTRA_FILE_NAME, file.name)
            putExtra(PlayerActivity.EXTRA_DIRECTORY_PATH, viewModel.currentPath.value ?: "/")
            // Check for matching CUE file
            val matchingCue = viewModel.directoryFiles.value?.find {
                it.isCue && it.nameWithoutExtension == file.nameWithoutExtension
            }
            matchingCue?.let {
                putExtra(PlayerActivity.EXTRA_CUE_PATH, it.path)
            }
        }
        startActivity(intent)
    }

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

    private fun openText(file: WebDavFile) {
        val intent = Intent(this, TextActivity::class.java).apply {
            putExtra(TextActivity.EXTRA_FILE_PATH, file.path)
            putExtra(TextActivity.EXTRA_FILE_NAME, file.name)
        }
        startActivity(intent)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_browser, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
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

