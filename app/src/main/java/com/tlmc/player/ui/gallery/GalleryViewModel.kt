package com.tlmc.player.ui.gallery

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.tlmc.player.data.model.WebDavFile
import com.tlmc.player.data.repository.WebDavRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class GalleryViewModel @Inject constructor(
    private val repository: WebDavRepository
) : ViewModel() {

    private val _files = MutableLiveData<List<WebDavFile>>()
    val files: LiveData<List<WebDavFile>> = _files

    private val _currentIndex = MutableLiveData<Int>()
    val currentIndex: LiveData<Int> = _currentIndex

    fun setFiles(paths: List<String>, names: List<String>, initialIndex: Int) {
        val fileList = paths.mapIndexed { index, path ->
            WebDavFile(
                name = names.getOrElse(index) { path.substringAfterLast('/') },
                path = path,
                isDirectory = false
            )
        }
        _files.value = fileList
        _currentIndex.value = initialIndex
    }

    fun getCurrentFile(): WebDavFile? {
        val index = _currentIndex.value ?: return null
        return _files.value?.getOrNull(index)
    }

    fun updateIndex(index: Int) {
        _currentIndex.value = index
    }
}