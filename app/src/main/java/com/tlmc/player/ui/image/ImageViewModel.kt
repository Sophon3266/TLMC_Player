package com.tlmc.player.ui.image

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tlmc.player.data.repository.WebDavRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ImageViewModel @Inject constructor(
    private val repository: WebDavRepository
) : ViewModel() {

    private val _imageData = MutableLiveData<ByteArray?>()
    val imageData: LiveData<ByteArray?> = _imageData

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    fun loadImage(path: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            val result = repository.downloadFile(path)
            result.onSuccess { bytes ->
                _imageData.value = bytes
            }.onFailure { e ->
                _error.value = "图片加载失败: ${e.message}"
            }

            _isLoading.value = false
        }
    }
}

