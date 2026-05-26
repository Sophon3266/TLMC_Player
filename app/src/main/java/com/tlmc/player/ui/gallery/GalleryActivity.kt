package com.tlmc.player.ui.gallery

import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.tlmc.player.data.repository.WebDavRepository
import com.tlmc.player.databinding.ActivityGalleryBinding
import dagger.hilt.android.AndroidEntryPoint
import okhttp3.OkHttpClient
import javax.inject.Inject

@AndroidEntryPoint
class GalleryActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_FILE_PATHS = "extra_file_paths"
        const val EXTRA_FILE_NAMES = "extra_file_names"
        const val EXTRA_INITIAL_INDEX = "extra_initial_index"
    }

    @Inject
    lateinit var repository: WebDavRepository

    @Inject
    lateinit var okHttpClient: OkHttpClient

    private lateinit var binding: ActivityGalleryBinding
    private val viewModel: GalleryViewModel by viewModels()
    private lateinit var adapter: GalleryAdapter
    private var initialIndex = 0
    private val playbackPositions = mutableMapOf<String, Long>()

    private val pageChangeCallback = object : ViewPager2.OnPageChangeCallback() {
        private var previousPosition = -1

        override fun onPageSelected(position: Int) {
            super.onPageSelected(position)
            adapter.onPageChanged(previousPosition, position)
            previousPosition = position
            val realPosition = adapter.getRealPosition(position)
            viewModel.updateIndex(realPosition)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGalleryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        val paths = intent.getStringArrayListExtra(EXTRA_FILE_PATHS) ?: run {
            finish()
            return
        }
        val names = intent.getStringArrayListExtra(EXTRA_FILE_NAMES) ?: run {
            finish()
            return
        }

        if (paths.isEmpty()) {
            Toast.makeText(this, "没有可浏览的媒体文件", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val initialIndex = intent.getIntExtra(EXTRA_INITIAL_INDEX, 0).coerceIn(0, paths.size - 1)
        this.initialIndex = initialIndex

        // names 大小不足时使用路径最后一段作为 fallback
        val displayName = names.getOrElse(initialIndex) { paths[initialIndex].substringAfterLast('/') }
        supportActionBar?.title = displayName

        adapter = GalleryAdapter(repository, okHttpClient, lifecycleScope, playbackPositions)
        binding.viewPager2.offscreenPageLimit = 1
        binding.viewPager2.adapter = adapter
        binding.viewPager2.registerOnPageChangeCallback(pageChangeCallback)

        viewModel.setFiles(paths, names, initialIndex)

        observeViewModel()
    }

    private fun observeViewModel() {
        viewModel.files.observe(this) { files ->
            adapter.submitList(files)
            binding.viewPager2.setCurrentItem(adapter.getLoopStartPosition(initialIndex), false)
        }

        viewModel.currentIndex.observe(this) { index ->
            if (index != null && index >= 0) {
                supportActionBar?.title = viewModel.files.value?.getOrNull(index)?.name
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        binding.viewPager2.unregisterOnPageChangeCallback(pageChangeCallback)
    }
}
