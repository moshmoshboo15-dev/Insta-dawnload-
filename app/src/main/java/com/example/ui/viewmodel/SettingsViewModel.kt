package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.MediaGrabApp
import com.example.model.ThemeMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = (application as MediaGrabApp).userPreferences

    val themeMode: StateFlow<ThemeMode> = prefs.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ThemeMode.DARK)

    val autoPasteClipboard: StateFlow<Boolean> = prefs.autoPasteClipboard
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val preferHighestQuality: StateFlow<Boolean> = prefs.preferHighestQuality
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { prefs.setThemeMode(mode) }
    }

    fun setAutoPasteClipboard(enabled: Boolean) {
        viewModelScope.launch { prefs.setAutoPasteClipboard(enabled) }
    }

    fun setPreferHighestQuality(enabled: Boolean) {
        viewModelScope.launch { prefs.setPreferHighestQuality(enabled) }
    }
}
