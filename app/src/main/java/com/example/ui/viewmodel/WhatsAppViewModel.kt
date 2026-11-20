package com.example.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.MediaGrabApp
import com.example.model.MediaType
import com.example.model.WhatsAppStatusItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class StatusFilter {
    ALL,
    IMAGES,
    VIDEOS
}

data class WhatsAppUiState(
    val isBusinessTab: Boolean = false,
    val hasFolderAccess: Boolean = false,
    val isLoading: Boolean = false,
    val items: List<WhatsAppStatusItem> = emptyList(),
    val filter: StatusFilter = StatusFilter.ALL,
    val savedUris: Set<String> = emptySet(),
    val statusMessage: String? = null
) {
    val filteredItems: List<WhatsAppStatusItem>
        get() = when (filter) {
            StatusFilter.ALL -> items
            StatusFilter.IMAGES -> items.filter { it.mediaType == MediaType.IMAGE }
            StatusFilter.VIDEOS -> items.filter { it.mediaType == MediaType.VIDEO }
        }
}

class WhatsAppViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as MediaGrabApp
    private val repo = app.whatsAppStatusRepository
    private val prefs = app.userPreferences

    private val _uiState = MutableStateFlow(WhatsAppUiState())
    val uiState: StateFlow<WhatsAppUiState> = _uiState.asStateFlow()

    init {
        refreshStatuses()
    }

    fun getInitialUriForCurrentTab(): Uri? {
        return repo.buildInitialUri(_uiState.value.isBusinessTab)
    }

    fun switchApp(isBusiness: Boolean) {
        if (_uiState.value.isBusinessTab == isBusiness) return
        _uiState.update { it.copy(isBusinessTab = isBusiness, items = emptyList()) }
        refreshStatuses()
    }

    fun setFilter(filter: StatusFilter) {
        _uiState.update { it.copy(filter = filter) }
    }

    fun clearMessage() {
        _uiState.update { it.copy(statusMessage = null) }
    }

    fun onFolderGranted(treeUri: Uri?) {
        if (treeUri == null) return
        repo.persistTreePermission(treeUri)
        viewModelScope.launch {
            prefs.setWhatsappTreeUri(_uiState.value.isBusinessTab, treeUri.toString())
            refreshStatuses()
        }
    }

    fun refreshStatuses() {
        viewModelScope.launch {
            val isBusiness = _uiState.value.isBusinessTab
            val savedTreeUri = if (isBusiness) {
                prefs.whatsappBusinessTreeUri.first()
            } else {
                prefs.whatsappTreeUri.first()
            }

            if (!repo.hasValidPermission(savedTreeUri)) {
                _uiState.update {
                    it.copy(
                        hasFolderAccess = false,
                        isLoading = false,
                        items = emptyList()
                    )
                }
                return@launch
            }

            _uiState.update { it.copy(hasFolderAccess = true, isLoading = true) }
            val loaded = repo.loadStatuses(savedTreeUri!!)
            _uiState.update {
                it.copy(
                    hasFolderAccess = true,
                    isLoading = false,
                    items = loaded
                )
            }
        }
    }

    fun saveStatus(item: WhatsAppStatusItem) {
        viewModelScope.launch {
            val result = repo.saveStatusToGallery(item)
            result.fold(
                onSuccess = {
                    _uiState.update { state ->
                        state.copy(
                            savedUris = state.savedUris + item.uriString,
                            statusMessage = "تم حفظ الحالة في مجلد MediaGrab"
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update { state ->
                        state.copy(statusMessage = err.localizedMessage ?: "تعذر حفظ الحالة")
                    }
                }
            )
        }
    }

    fun saveAllStatuses() {
        val unsaved = _uiState.value.filteredItems.filter { it.uriString !in _uiState.value.savedUris }
        if (unsaved.isEmpty()) return
        viewModelScope.launch {
            var count = 0
            val newlySaved = mutableSetOf<String>()
            unsaved.forEach { item ->
                if (repo.saveStatusToGallery(item).isSuccess) {
                    count++
                    newlySaved.add(item.uriString)
                }
            }
            _uiState.update { state ->
                state.copy(
                    savedUris = state.savedUris + newlySaved,
                    statusMessage = "تم حفظ $count حالة في المعرض"
                )
            }
        }
    }
}
