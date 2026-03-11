package com.tlmc.player.ui.text

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.tlmc.player.databinding.ActivityTextBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class TextActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_FILE_PATH = "extra_file_path"
        const val EXTRA_FILE_NAME = "extra_file_name"
    }

    private lateinit var binding: ActivityTextBinding
    private val viewModel: TextViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTextBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        val filePath = intent.getStringExtra(EXTRA_FILE_PATH) ?: run {
            finish()
            return
        }
        val fileName = intent.getStringExtra(EXTRA_FILE_NAME) ?: "Text"
        supportActionBar?.title = fileName

        observeViewModel()
        viewModel.loadText(filePath)
    }

    private fun observeViewModel() {
        viewModel.textContent.observe(this) { content ->
            binding.textView.text = content
            binding.scrollView.visibility = View.VISIBLE
        }

        viewModel.encoding.observe(this) { encoding ->
            supportActionBar?.subtitle = "编码: $encoding"
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

