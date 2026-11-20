package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.MediaGrabApp
import com.example.data.local.DownloadEntity
import com.example.model.DownloadStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class HistoryFilter {
    ALL,
    ACTIVE,
    COMPLETED,
    PAUSED
}

class HistoryViewModel(application: Application) : AndroidViewModel(application) {

    private val downloadRepository = (application as MediaGrabApp).downloadRepository

    private val _selectedFilter = MutableStateFlow(HistoryFilter.ALL)
    val selectedFilter: StateFlow<HistoryFilter> = _selectedFilter.asStateFlow()

    val downloads: StateFlow<List<DownloadEntity>> = combine(
        downloadRepository.downloads,
        _selectedFilter
    ) { all, filter ->
        when (filter) {
            HistoryFilter.ALL -> all
            HistoryFilter.ACTIVE -> all.filter {
                it.statusEnum == DownloadStatus.DOWNLOADING || it.statusEnum == DownloadStatus.QUEUED
            }
            HistoryFilter.COMPLETED -> all.filter { it.statusEnum == DownloadStatus.COMPLETED }
            HistoryFilter.PAUSED -> all.filter {
                it.statusEnum == DownloadStatus.PAUSED || it.statusEnum == DownloadStatus.FAILED
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setFilter(filter: HistoryFilter) {
        _selectedFilter.value = filter
    }

    fun pause(id: String) {
        viewModelScope.launch { downloadRepository.pauseDownload(id) }
    }

    fun resume(id: String) {
        viewModelScope.launch { downloadRepository.resumeDownload(id) }
    }

    fun cancel(id: String) {
        viewModelScope.launch { downloadRepository.cancelDownload(id) }
    }

    fun delete(entity: DownloadEntity, deleteFileFromDevice: Boolean) {
        viewModelScope.launch { downloadRepository.deleteDownload(entity, deleteFileFromDevice) }
    }

    fun clearFinished() {
        viewModelScope.launch { downloadRepository.clearFinishedHistory() }
    }
}
