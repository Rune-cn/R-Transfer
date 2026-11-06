// SPDX-License-Identifier: GPL-3.0-only
package app.ai.rune.ziyong.transfer.domain.settings.model.preferences

import app.ai.rune.ziyong.transfer.domain.settings.model.preferences.theme.PaletteStyle
import app.ai.rune.ziyong.transfer.domain.settings.model.preferences.theme.ThemeColorSpec
import app.ai.rune.ziyong.transfer.domain.settings.model.preferences.theme.ThemeMode

/**
 * The aggregated state of the shell application preferences.
 *
 * Only the settings the shell app actually exposes are kept (theme,
 * update check, logging, predictive back, UI family).
 */
data class AppPreferences(
    // Updater / Network
    val allowInternetAccess: Boolean,
    val githubUpdateChannel: GithubUpdateChannel,
    val customGithubProxyUrl: String,
    val autoCheckUpdateOnStart: Boolean,
    // Debug
    val enableFileLogging: Boolean,
    // Theme Settings
    val themeMode: ThemeMode,
    val paletteStyle: PaletteStyle,
    val colorSpec: ThemeColorSpec,
    val useDynamicColor: Boolean,
    val useMiuixMonet: Boolean,
    val useAppleFloatingBar: Boolean,
    val seedColorInt: Int, // Stored as raw Int from DataStore
    val wallpaperSeedColorInt: Int,
    val useBlur: Boolean,
    val showMiuixUI: Boolean,
    // Predictive Back Settings
    val predictiveBackAnimation: PredictiveBackAnimation,
    val predictiveBackExitDirection: PredictiveBackExitDirection,
)