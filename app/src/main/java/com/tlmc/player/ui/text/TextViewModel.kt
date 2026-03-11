package com.tlmc.player.ui.text

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tlmc.player.data.repository.WebDavRepository
import com.tlmc.player.util.EncodingDetector
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TextViewModel @Inject constructor(
    private val repository: WebDavRepository
) : ViewModel() {

    private val _textContent = MutableLiveData<String>()
    val textContent: LiveData<String> = _textContent

    private val _encoding = MutableLiveData<String>()
    val encoding: LiveData<String> = _encoding

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    fun loadText(path: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            val result = repository.downloadFile(path)
            result.onSuccess { bytes ->
                val detectedEncoding = EncodingDetector.detect(bytes)
                _encoding.value = detectedEncoding
                _textContent.value = String(bytes, charset(detectedEncoding))
            }.onFailure { e ->
                _error.value = "文本加载失败: ${e.message}"
            }

            _isLoading.value = false
        }
    }
}

