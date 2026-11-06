// SPDX-License-Identifier: GPL-3.0-only
package app.ai.rune.ziyong.transfer.data.settings.repository

import android.os.Build
import androidx.datastore.preferences.core.Preferences
import app.ai.rune.ziyong.transfer.data.settings.local.datastore.AppDataStore
import app.ai.rune.ziyong.transfer.domain.settings.model.preferences.AppPreferences
import app.ai.rune.ziyong.transfer.domain.settings.model.preferences.GithubUpdateChannel
import app.ai.rune.ziyong.transfer.domain.settings.model.preferences.PredictiveBackAnimation
import app.ai.rune.ziyong.transfer.domain.settings.model.preferences.PredictiveBackExitDirection
import app.ai.rune.ziyong.transfer.domain.settings.model.preferences.theme.PaletteStyle
import app.ai.rune.ziyong.transfer.domain.settings.model.preferences.theme.ThemeColorSpec
import app.ai.rune.ziyong.transfer.domain.settings.model.preferences.theme.ThemeMode
import app.ai.rune.ziyong.transfer.domain.settings.repository.AppSettingsRepository
import app.ai.rune.ziyong.transfer.domain.settings.repository.BooleanSetting
import app.ai.rune.ziyong.transfer.domain.settings.repository.IntSetting
import app.ai.rune.ziyong.transfer.domain.settings.repository.StringSetting
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn

class AppSettingsRepositoryImpl(
    private val appDataStore: AppDataStore,
    appScope: CoroutineScope,
) : AppSettingsRepository {

    // Replaced brittle combine(listOf(...)) with a safe mapping from the raw Preferences flow.
    override val preferencesFlow: Flow<AppPreferences> = appDataStore.data.map { prefs ->
        // Pre-compute values for Github update channel validation
        val rawGithubUpdateChannel = GithubUpdateChannel.fromValueOrDefault(
            prefs[AppDataStore.GITHUB_UPDATE_CHANNEL] ?: GithubUpdateChannel.OFFICIAL.name,
        )
        val customGithubProxyUrl = prefs[AppDataStore.CUSTOM_GITHUB_PROXY_URL] ?: ""
        val githubUpdateChannel = if (rawGithubUpdateChannel == GithubUpdateChannel.CUSTOM && customGithubProxyUrl.isBlank()) {
            GithubUpdateChannel.OFFICIAL
        } else {
            rawGithubUpdateChannel
        }

        // Map all preferences explicitly by key. Order no longer matters.
        AppPreferences(
            // Updater / Network
            allowInternetAccess = prefs[AppDataStore.ALLOW_INTERNET_ACCESS] ?: true,
            githubUpdateChannel = githubUpdateChannel,
            customGithubProxyUrl = customGithubProxyUrl,
            autoCheckUpdateOnStart = prefs[AppDataStore.AUTO_CHECK_UPDATE_ON_START] ?: false,
            // Debug
            enableFileLogging = prefs[AppDataStore.ENABLE_FILE_LOGGING] ?: true,
            // UI State
            showMiuixUI = prefs[AppDataStore.UI_USE_MIUIX] ?: true,
            themeMode = ThemeMode.fromValueOrDefault(prefs[AppDataStore.THEME_MODE] ?: ThemeMode.SYSTEM.name),
            paletteStyle = PaletteStyle.fromValueOrDefault(
                prefs[AppDataStore.THEME_PALETTE_STYLE] ?: PaletteStyle.TonalSpot.name,
            ),
            colorSpec = ThemeColorSpec.fromValueOrDefault(prefs[AppDataStore.THEME_COLOR_SPEC] ?: ThemeColorSpec.SPEC_2025.name),
            useDynamicColor = prefs[AppDataStore.THEME_USE_DYNAMIC_COLOR] ?: true,
            useMiuixMonet = prefs[AppDataStore.UI_USE_MIUIX_MONET] ?: false,
            useAppleFloatingBar = prefs[AppDataStore.UI_USE_APPLE_FLOATING_BAR] ?: false,
            seedColorInt = prefs[AppDataStore.THEME_SEED_COLOR] ?: DEFAULT_SEED_COLOR,
            wallpaperSeedColorInt = prefs[AppDataStore.THEME_WALLPAPER_SEED_COLOR] ?: 0,
            useBlur = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                prefs[AppDataStore.UI_USE_BLUR]
                    ?: true
            } else {
                false
            },
            predictiveBackAnimation = PredictiveBackAnimation.fromValueOrDefault(
                prefs[AppDataStore.PREDICTIVE_BACK_ANIMATION] ?: PredictiveBackAnimation.MIUIX.value,
            ),
            predictiveBackExitDirection = PredictiveBackExitDirection.fromValueOrDefault(
                prefs[AppDataStore.PREDICTIVE_BACK_EXIT_DIRECTION] ?: PredictiveBackExitDirection.ALWAYS_RIGHT.value,
            ),
        )
    }.shareIn(
        scope = appScope,
        started = SharingStarted.Eagerly,
        replay = 1,
    )

    override suspend fun putString(setting: StringSetting, value: String) = appDataStore.putString(stringKey(setting), value)

    override fun getString(setting: StringSetting, default: String): Flow<String> = appDataStore.getString(stringKey(setting), default)

    override suspend fun putInt(setting: IntSetting, value: Int) = appDataStore.putInt(intKey(setting), value)

    override fun getInt(setting: IntSetting, default: Int): Flow<Int> = appDataStore.getInt(intKey(setting), default)

    override suspend fun putBoolean(setting: BooleanSetting, value: Boolean) = appDataStore.putBoolean(booleanKey(setting), value)

    override fun getBoolean(setting: BooleanSetting, default: Boolean): Flow<Boolean> = appDataStore.getBoolean(booleanKey(setting), default)

    private fun stringKey(setting: StringSetting): Preferences.Key<String> = when (setting) {
        StringSetting.ThemeMode -> AppDataStore.THEME_MODE
        StringSetting.ThemePaletteStyle -> AppDataStore.THEME_PALETTE_STYLE
        StringSetting.ThemeColorSpec -> AppDataStore.THEME_COLOR_SPEC
        StringSetting.PredictiveBackAnimation -> AppDataStore.PREDICTIVE_BACK_ANIMATION
        StringSetting.PredictiveBackExitDirection -> AppDataStore.PREDICTIVE_BACK_EXIT_DIRECTION
        StringSetting.GithubUpdateChannel -> AppDataStore.GITHUB_UPDATE_CHANNEL
        StringSetting.CustomGithubProxyUrl -> AppDataStore.CUSTOM_GITHUB_PROXY_URL
    }

    private fun intKey(setting: IntSetting): Preferences.Key<Int> = when (setting) {
        IntSetting.ThemeSeedColor -> AppDataStore.THEME_SEED_COLOR
        IntSetting.ThemeWallpaperSeedColor -> AppDataStore.THEME_WALLPAPER_SEED_COLOR
    }

    private fun booleanKey(setting: BooleanSetting): Preferences.Key<Boolean> = when (setting) {
        BooleanSetting.AllowInternetAccess -> AppDataStore.ALLOW_INTERNET_ACCESS
        BooleanSetting.UiUseBlur -> AppDataStore.UI_USE_BLUR
        BooleanSetting.AutoCheckUpdateOnStart -> AppDataStore.AUTO_CHECK_UPDATE_ON_START
        BooleanSetting.ThemeUseDynamicColor -> AppDataStore.THEME_USE_DYNAMIC_COLOR
        BooleanSetting.UiUseMiuix -> AppDataStore.UI_USE_MIUIX
        BooleanSetting.UiUseMiuixMonet -> AppDataStore.UI_USE_MIUIX_MONET
        BooleanSetting.UiUseAppleFloatingBar -> AppDataStore.UI_USE_APPLE_FLOATING_BAR
        BooleanSetting.EnableFileLogging -> AppDataStore.ENABLE_FILE_LOGGING
    }

    private companion object {
        private const val DEFAULT_SEED_COLOR = 0xFF4A672D.toInt()
    }
}