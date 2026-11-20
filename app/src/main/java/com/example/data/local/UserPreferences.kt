package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "mediagrab_prefs")

class UserPreferences(private val context: Context) {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val AUTO_PASTE = booleanPreferencesKey("auto_paste_clipboard")
        val PREFER_HIGHEST_QUALITY = booleanPreferencesKey("prefer_highest_quality")
        val WHATSAPP_TREE_URI = stringPreferencesKey("whatsapp_tree_uri")
        val WHATSAPP_BUSINESS_TREE_URI = stringPreferencesKey("whatsapp_business_tree_uri")
        val LAST_AUTO_PASTED_URL = stringPreferencesKey("last_auto_pasted_url")
    }

    val themeMode: Flow<ThemeMode> = context.dataStore.data.map { prefs ->
        val raw = prefs[Keys.THEME_MODE] ?: ThemeMode.DARK.name
        runCatching { ThemeMode.valueOf(raw) }.getOrDefault(ThemeMode.DARK)
    }

    val autoPasteClipboard: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.AUTO_PASTE] ?: true
    }

    val preferHighestQuality: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.PREFER_HIGHEST_QUALITY] ?: true
    }

    val whatsappTreeUri: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[Keys.WHATSAPP_TREE_URI]
    }

    val whatsappBusinessTreeUri: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[Keys.WHATSAPP_BUSINESS_TREE_URI]
    }

    val lastAutoPastedUrl: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[Keys.LAST_AUTO_PASTED_URL]
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setAutoPasteClipboard(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_PASTE] = enabled }
    }

    suspend fun setPreferHighestQuality(enabled: Boolean) {
        context.dataStore.edit { it[Keys.PREFER_HIGHEST_QUALITY] = enabled }
    }

    suspend fun setWhatsappTreeUri(isBusiness: Boolean, uri: String?) {
        val key = if (isBusiness) Keys.WHATSAPP_BUSINESS_TREE_URI else Keys.WHATSAPP_TREE_URI
        context.dataStore.edit { prefs ->
            if (uri == null) prefs.remove(key) else prefs[key] = uri
        }
    }

    suspend fun setLastAutoPastedUrl(url: String) {
        context.dataStore.edit { it[Keys.LAST_AUTO_PASTED_URL] = url }
    }
}
