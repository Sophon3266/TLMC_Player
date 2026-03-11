package com.tlmc.player.ui.browser

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tlmc.player.data.model.ServerConfig
import com.tlmc.player.data.model.WebDavFile
import com.tlmc.player.data.repository.ConfigManager
import com.tlmc.player.data.repository.WebDavRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BrowserViewModel @Inject constructor(
    private val repository: WebDavRepository,
    private val configManager: ConfigManager
) : ViewModel() {

    private val _files = MutableLiveData<List<WebDavFile>>(emptyList())
    val files: LiveData<List<WebDavFile>> = _files

    private val _directoryFiles = MutableLiveData<List<WebDavFile>>(emptyList())
    val directoryFiles: LiveData<List<WebDavFile>> = _directoryFiles

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _currentPath = MutableLiveData("/")
    val currentPath: LiveData<String> = _currentPath

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private val pathHistory = mutableListOf<String>()

    fun loadDirectory(path: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            val result = repository.listFiles(path)
            result.onSuccess { files ->
                _files.value = files
                _directoryFiles.value = files
                if (_currentPath.value != path) {
                    _currentPath.value?.let { pathHistory.add(it) }
                }
                _currentPath.value = path
            }.onFailure { e ->
                _error.value = "加载失败: ${e.message}"
            }

            _isLoading.value = false
        }
    }

    fun refresh() {
        _currentPath.value?.let { loadDirectory(it) }
    }

    fun navigateUp(): Boolean {
        if (pathHistory.isNotEmpty()) {
            val previousPath = pathHistory.removeAt(pathHistory.size - 1)
            _currentPath.value = previousPath
            viewModelScope.launch {
                _isLoading.value = true
                val result = repository.listFiles(previousPath)
                result.onSuccess { files ->
                    _files.value = files
                    _directoryFiles.value = files
                }.onFailure { e ->
                    _error.value = "加载失败: ${e.message}"
                }
                _isLoading.value = false
            }
            return true
        }

        val currentPath = _currentPath.value ?: return false
        if (currentPath != "/" && currentPath.isNotEmpty()) {
            val parentPath = com.tlmc.player.util.FileUtils.getParentPath(currentPath)
            loadDirectory(parentPath)
            return true
        }

        return false
    }

    fun getConfig(): ServerConfig = configManager.getConfig()

    fun saveConfig(config: ServerConfig) {
        configManager.saveConfig(config)
    }

    fun clearError() {
        _error.value = null
    }
}

