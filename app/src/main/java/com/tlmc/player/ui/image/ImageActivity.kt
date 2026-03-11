package com.tlmc.player.ui.image

import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.tlmc.player.databinding.ActivityImageBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class ImageActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_FILE_PATH = "extra_file_path"
        const val EXTRA_FILE_NAME = "extra_file_name"
    }

    private lateinit var binding: ActivityImageBinding
    private val viewModel: ImageViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityImageBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        val filePath = intent.getStringExtra(EXTRA_FILE_PATH) ?: run {
            finish()
            return
        }
        val fileName = intent.getStringExtra(EXTRA_FILE_NAME) ?: "Image"
        supportActionBar?.title = fileName

        observeViewModel()
        viewModel.loadImage(filePath)
    }

    private fun observeViewModel() {
        viewModel.imageData.observe(this) { bytes ->
            bytes?.let {
                try {
                    val bitmap = BitmapFactory.decodeByteArray(it, 0, it.size)
                    if (bitmap != null) {
                        binding.photoView.setImageBitmap(bitmap)
                        binding.photoView.visibility = View.VISIBLE
                    } else {
                        Toast.makeText(this, "无法解码图片", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(this, "图片加载失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }

        viewModel.isLoading.observe(this) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        }

        viewModel.error.observe(this) { error ->
            error?.let {
                Toast.makeText(this, it, Toast.LENGTH_LONG).show()
            }
        }
    }
}

