// SPDX-License-Identifier: GPL-3.0-only
package app.ai.rune.ziyong.transfer.data.settings.local.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class AppDataStore(private val dataStore: DataStore<Preferences>) {
    // Expose the raw data flow for synchronous mapping in the repository layer
    val data: Flow<Preferences> = dataStore.data

    enum class PreferenceValueType {
        STRING,
        INT,
        BOOLEAN,
    }

    data class SupportedPreferenceKey<T>(val key: Preferences.Key<T>, val type: PreferenceValueType)

    companion object {
        private val mutableSupportedKeys = linkedMapOf<String, SupportedPreferenceKey<*>>()

        val supportedKeys: Map<String, SupportedPreferenceKey<*>>
            get() = mutableSupportedKeys

        private fun <T> register(key: Preferences.Key<T>, type: PreferenceValueType): Preferences.Key<T> {
            mutableSupportedKeys[key.name] = SupportedPreferenceKey(key, type)
            return key
        }

        // UI Related
        val UI_USE_BLUR = register(booleanPreferencesKey("ui_use_blur"), PreferenceValueType.BOOLEAN)
        val AUTO_CHECK_UPDATE_ON_START = register(booleanPreferencesKey("auto_check_update_on_start"), PreferenceValueType.BOOLEAN)
        val THEME_MODE = register(stringPreferencesKey("theme_mode"), PreferenceValueType.STRING)
        val THEME_PALETTE_STYLE = register(stringPreferencesKey("theme_palette_style"), PreferenceValueType.STRING)
        val THEME_COLOR_SPEC = register(stringPreferencesKey("theme_color_spec"), PreferenceValueType.STRING)
        val THEME_USE_DYNAMIC_COLOR = register(booleanPreferencesKey("theme_use_dynamic_color"), PreferenceValueType.BOOLEAN)
        val THEME_SEED_COLOR = register(intPreferencesKey("theme_seed_color"), PreferenceValueType.INT)
        val THEME_WALLPAPER_SEED_COLOR = register(intPreferencesKey("theme_wallpaper_seed_color"), PreferenceValueType.INT)
        val UI_USE_MIUIX = register(booleanPreferencesKey("ui_use_miui_x"), PreferenceValueType.BOOLEAN)
        val UI_USE_MIUIX_MONET = register(booleanPreferencesKey("ui_use_miui_x_monet"), PreferenceValueType.BOOLEAN)
        val UI_USE_APPLE_FLOATING_BAR = register(booleanPreferencesKey("ui_use_apple_floating_bar"), PreferenceValueType.BOOLEAN)
        val PREDICTIVE_BACK_ANIMATION = register(stringPreferencesKey("predictive_back_animation"), PreferenceValueType.STRING)
        val PREDICTIVE_BACK_EXIT_DIRECTION =
            register(stringPreferencesKey("predictive_back_exit_direction"), PreferenceValueType.STRING)

        // Network
        val ALLOW_INTERNET_ACCESS = register(booleanPreferencesKey("allow_internet_access"), PreferenceValueType.BOOLEAN)

        // Debug
        val ENABLE_FILE_LOGGING = register(booleanPreferencesKey("enable_file_logging"), PreferenceValueType.BOOLEAN)

        // Updater
        val GITHUB_UPDATE_CHANNEL = register(stringPreferencesKey("github_update_channel"), PreferenceValueType.STRING)
        val CUSTOM_GITHUB_PROXY_URL = register(stringPreferencesKey("custom_github_proxy_url"), PreferenceValueType.STRING)
    }

    suspend fun putString(key: Preferences.Key<String>, value: String) {
        dataStore.edit { it[key] = value }
    }

    fun getString(key: Preferences.Key<String>, default: String = ""): Flow<String> = dataStore.data.map { it[key] ?: default }

    suspend fun putInt(key: Preferences.Key<Int>, value: Int) {
        dataStore.edit { it[key] = value }
    }

    fun getInt(key: Preferences.Key<Int>, default: Int = 0): Flow<Int> = dataStore.data.map { it[key] ?: default }

    suspend fun putBoolean(key: Preferences.Key<Boolean>, value: Boolean) {
        dataStore.edit { it[key] = value }
    }

    fun getBoolean(key: Preferences.Key<Boolean>, default: Boolean = false): Flow<Boolean> = dataStore.data.map { it[key] ?: default }
}