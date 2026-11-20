package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.MediaGrabApp
import com.example.data.local.DownloadEntity
import com.example.extractor.UrlUtilities
import com.example.model.DownloadStatus
import com.example.model.ExtractError
import com.example.model.ExtractResult
import com.example.model.MediaPreview
import com.example.model.QualityOption
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface AnalysisState {
    data object Idle : AnalysisState
    data object Loading : AnalysisState
    data class SinglePreview(
        val preview: MediaPreview,
        val selectedQuality: QualityOption
    ) : AnalysisState

    data class BatchPreview(
        val items: List<BatchItemResult>
    ) : AnalysisState

    data class Error(val error: ExtractError) : AnalysisState
}

data class BatchItemResult(
    val sourceUrl: String,
    val preview: MediaPreview? = null,
    val error: ExtractError? = null,
    val selected: Boolean = true
)

data class DownloaderUiState(
    val inputText: String = "",
    val isBatchMode: Boolean = false,
    val clipboardBannerUrl: String? = null,
    val analysisState: AnalysisState = AnalysisState.Idle,
    val messageToast: String? = null
)

class DownloaderViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as MediaGrabApp
    private val extractorRegistry = app.extractorRegistry
    private val downloadRepository = app.downloadRepository
    private val prefs = app.userPreferences

    private val _uiState = MutableStateFlow(DownloaderUiState())
    val uiState: StateFlow<DownloaderUiState> = _uiState.asStateFlow()

    val activeDownloads: StateFlow<List<DownloadEntity>> = downloadRepository.downloads
        .map { list ->
            list.filter {
                it.statusEnum == DownloadStatus.DOWNLOADING ||
                    it.statusEnum == DownloadStatus.QUEUED ||
                    it.statusEnum == DownloadStatus.PAUSED
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun onInputChanged(value: String) {
        _uiState.update { it.copy(inputText = value) }
    }

    fun toggleBatchMode(enabled: Boolean) {
        _uiState.update {
            it.copy(
                isBatchMode = enabled,
                analysisState = AnalysisState.Idle
            )
        }
    }

    fun clearInput() {
        _uiState.update {
            it.copy(
                inputText = "",
                clipboardBannerUrl = null,
                analysisState = AnalysisState.Idle
            )
        }
    }

    fun clearToast() {
        _uiState.update { it.copy(messageToast = null) }
    }

    fun onSharedTextReceived(sharedText: String) {
        val urls = UrlUtilities.extractAllUrls(sharedText)
        if (urls.isEmpty()) return
        if (urls.size > 1) {
            _uiState.update {
                it.copy(
                    isBatchMode = true,
                    inputText = urls.joinToString("\n"),
                    clipboardBannerUrl = null
                )
            }
            analyzeCurrentInput()
        } else {
            val url = urls.first()
            _uiState.update {
                it.copy(
                    isBatchMode = false,
                    inputText = url,
                    clipboardBannerUrl = null
                )
            }
            analyzeCurrentInput()
        }
    }

    fun handleClipboardCandidate(rawClipboard: String?) {
        val url = UrlUtilities.extractFirstUrl(rawClipboard) ?: return
        viewModelScope.launch {
            val autoPaste = prefs.autoPasteClipboard.first()
            val lastSeen = prefs.lastAutoPastedUrl.first()
            if (!autoPaste || url == lastSeen) return@launch

            prefs.setLastAutoPastedUrl(url)
            if (_uiState.value.inputText.isBlank()) {
                _uiState.update {
                    it.copy(
                        inputText = url,
                        clipboardBannerUrl = url
                    )
                }
                analyzeCurrentInput()
            } else {
                _uiState.update { it.copy(clipboardBannerUrl = url) }
            }
        }
    }

    fun pasteUrlDirectly(rawClipboard: String?) {
        val urls = UrlUtilities.extractAllUrls(rawClipboard)
        if (urls.isEmpty()) {
            _uiState.update { it.copy(messageToast = "الحافظة لا تحتوي على رابط صالح") }
            return
        }
        if (urls.size > 1) {
            _uiState.update {
                it.copy(
                    isBatchMode = true,
                    inputText = urls.joinToString("\n"),
                    clipboardBannerUrl = null
                )
            }
        } else {
            _uiState.update {
                it.copy(
                    inputText = urls.first(),
                    clipboardBannerUrl = null
                )
            }
            analyzeCurrentInput()
        }
    }

    fun dismissClipboardBanner() {
        _uiState.update { it.copy(clipboardBannerUrl = null) }
    }

    fun analyzeCurrentInput() {
        val raw = _uiState.value.inputText.trim()
        if (raw.isBlank()) {
            _uiState.update { it.copy(analysisState = AnalysisState.Error(ExtractError.InvalidUrl)) }
            return
        }

        if (_uiState.value.isBatchMode) {
            val urls = UrlUtilities.extractAllUrls(raw)
            if (urls.isEmpty()) {
                _uiState.update { it.copy(analysisState = AnalysisState.Error(ExtractError.InvalidUrl)) }
                return
            }
            viewModelScope.launch {
                _uiState.update { it.copy(analysisState = AnalysisState.Loading) }
                val results = coroutineScope {
                    urls.take(15).map { url ->
                        async {
                            when (val res = extractorRegistry.extract(url)) {
                                is ExtractResult.Success -> BatchItemResult(url, preview = res.preview, selected = true)
                                is ExtractResult.Error -> BatchItemResult(url, error = res.error, selected = false)
                            }
                        }
                    }.awaitAll()
                }
                _uiState.update { it.copy(analysisState = AnalysisState.BatchPreview(results)) }
            }
        } else {
            viewModelScope.launch {
                _uiState.update { it.copy(analysisState = AnalysisState.Loading) }
                when (val result = extractorRegistry.extract(raw)) {
                    is ExtractResult.Success -> {
                        val defaultQuality = result.preview.qualityOptions.first()
                        _uiState.update {
                            it.copy(analysisState = AnalysisState.SinglePreview(result.preview, defaultQuality))
                        }
                    }
                    is ExtractResult.Error -> {
                        _uiState.update { it.copy(analysisState = AnalysisState.Error(result.error)) }
                    }
                }
            }
        }
    }

    fun selectQuality(option: QualityOption) {
        val current = _uiState.value.analysisState
        if (current is AnalysisState.SinglePreview) {
            _uiState.update { it.copy(analysisState = current.copy(selectedQuality = option)) }
        }
    }

    fun toggleBatchItemSelection(sourceUrl: String) {
        val current = _uiState.value.analysisState
        if (current is AnalysisState.BatchPreview) {
            val updated = current.items.map { item ->
                if (item.sourceUrl == sourceUrl && item.preview != null) {
                    item.copy(selected = !item.selected)
                } else {
                    item
                }
            }
            _uiState.update { it.copy(analysisState = AnalysisState.BatchPreview(updated)) }
        }
    }

    fun startSingleDownload() {
        val current = _uiState.value.analysisState
        if (current !is AnalysisState.SinglePreview) return
        viewModelScope.launch {
            downloadRepository.enqueueDownload(current.preview, current.selectedQuality)
            _uiState.update {
                it.copy(messageToast = "بدأ التنزيل في الخلفية")
            }
        }
    }

    fun startBatchDownload() {
        val current = _uiState.value.analysisState
        if (current !is AnalysisState.BatchPreview) return
        val selectedItems = current.items.filter { it.selected && it.preview != null }
        if (selectedItems.isEmpty()) return

        viewModelScope.launch {
            val preferHd = prefs.preferHighestQuality.first()
            selectedItems.forEach { item ->
                val preview = item.preview ?: return@forEach
                val chosenQuality = if (preferHd) {
                    preview.qualityOptions.first()
                } else {
                    preview.qualityOptions.last()
                }
                downloadRepository.enqueueDownload(preview, chosenQuality)
            }
            _uiState.update {
                it.copy(messageToast = "تمت إضافة ${selectedItems.size} ملفات إلى طابور التنزيل")
            }
        }
    }

    fun pauseDownload(id: String) {
        viewModelScope.launch { downloadRepository.pauseDownload(id) }
    }

    fun resumeDownload(id: String) {
        viewModelScope.launch { downloadRepository.resumeDownload(id) }
    }

    fun cancelDownload(id: String) {
        viewModelScope.launch { downloadRepository.cancelDownload(id) }
    }
}
