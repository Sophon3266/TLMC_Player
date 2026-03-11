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
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import javax.inject.Inject
import kotlinx.coroutines.currentCoroutineContext

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

    // Search
    private val _searchResults = MutableLiveData<List<WebDavFile>>(emptyList())
    val searchResults: LiveData<List<WebDavFile>> = _searchResults

    private val _isSearching = MutableLiveData(false)
    val isSearching: LiveData<Boolean> = _isSearching

    private val _searchStatus = MutableLiveData<String?>()
    val searchStatus: LiveData<String?> = _searchStatus

    private var searchJob: Job? = null

    companion object {
        private const val MAX_SEARCH_RESULTS = 200
        private const val MAX_CONCURRENT_REQUESTS = 8
    }

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

    // ==================== Search ====================

    fun searchFiles(query: String) {
        searchJob?.cancel()
        searchJob = null
        _searchResults.value = emptyList()
        _isSearching.value = true
        _searchStatus.value = "正在搜索..."

        val basePath = _currentPath.value ?: "/"
        val job = viewModelScope.launch {
            val results = mutableListOf<WebDavFile>()
            val semaphore = Semaphore(MAX_CONCURRENT_REQUESTS)
            try {
                searchRecursiveParallel(basePath, query.lowercase(), results, semaphore)
                _searchStatus.value = "搜索完成，找到 ${results.size} 个结果"
            } catch (e: kotlinx.coroutines.CancellationException) {
                _searchStatus.value = "搜索已取消，找到 ${results.size} 个结果"
                throw e
            } catch (e: Exception) {
                _searchStatus.value = "搜索出错: ${e.message}"
            } finally {
                _isSearching.value = false
            }
        }
        searchJob = job
    }

    private suspend fun searchRecursiveParallel(
        path: String,
        query: String,
        results: MutableList<WebDavFile>,
        semaphore: Semaphore
    ) {
        // Use currentCoroutineContext().ensureActive() to check the CURRENT coroutine's state,
        // not the searchJob field which may point to an old cancelled job.
        currentCoroutineContext().ensureActive()
        if (results.size >= MAX_SEARCH_RESULTS) return

        val fileList = semaphore.withPermit {
            repository.listFiles(path).getOrNull()
        } ?: return

        val subdirs = mutableListOf<WebDavFile>()

        for (file in fileList) {
            currentCoroutineContext().ensureActive()
            if (results.size >= MAX_SEARCH_RESULTS) {
                _searchStatus.value = "已达到最大结果数 ($MAX_SEARCH_RESULTS)"
                return
            }

            if (file.name.lowercase().contains(query)) {
                results.add(file)
                _searchResults.value = results.toList()
            }

            if (file.isDirectory) {
                subdirs.add(file)
            }
        }

        // Search subdirectories in parallel for significant speedup
        if (subdirs.isNotEmpty()) {
            _searchStatus.value = "正在搜索 (已找到 ${results.size} 个)"
            coroutineScope {
                for (dir in subdirs) {
                    launch {
                        searchRecursiveParallel(dir.path, query, results, semaphore)
                    }
                }
            }
        }
    }

    fun cancelSearch() {
        searchJob?.cancel()
        searchJob = null
        _isSearching.value = false
    }

    // ==================== Utility ====================

    fun getFileUrl(path: String): String {
        return repository.getFileUrl(path)
    }

    fun getConfig(): ServerConfig = configManager.getConfig()

    fun saveConfig(config: ServerConfig) {
        configManager.saveConfig(config)
    }

    fun clearError() {
        _error.value = null
    }
}

