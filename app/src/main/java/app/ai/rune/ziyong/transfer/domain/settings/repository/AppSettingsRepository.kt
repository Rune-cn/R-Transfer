// SPDX-License-Identifier: GPL-3.0-only
package app.ai.rune.ziyong.transfer.domain.settings.repository

import app.ai.rune.ziyong.transfer.domain.settings.model.preferences.AppPreferences
import kotlinx.coroutines.flow.Flow

enum class StringSetting {
    ThemeMode,
    ThemePaletteStyle,
    ThemeColorSpec,
    PredictiveBackAnimation,
    PredictiveBackExitDirection,
    GithubUpdateChannel,
    CustomGithubProxyUrl,
}

enum class IntSetting {
    ThemeSeedColor,
    ThemeWallpaperSeedColor,
}

enum class BooleanSetting {
    AllowInternetAccess,
    UiUseBlur,
    AutoCheckUpdateOnStart,
    ThemeUseDynamicColor,
    UiUseMiuix,
    UiUseMiuixMonet,
    UiUseAppleFloatingBar,
    EnableFileLogging,
}

interface AppSettingsRepository {
    val preferencesFlow: Flow<AppPreferences>

    suspend fun putString(setting: StringSetting, value: String)
    fun getString(setting: StringSetting, default: String = ""): Flow<String>

    suspend fun putInt(setting: IntSetting, value: Int)
    fun getInt(setting: IntSetting, default: Int = 0): Flow<Int>

    suspend fun putBoolean(setting: BooleanSetting, value: Boolean)
    fun getBoolean(setting: BooleanSetting, default: Boolean = false): Flow<Boolean>
}