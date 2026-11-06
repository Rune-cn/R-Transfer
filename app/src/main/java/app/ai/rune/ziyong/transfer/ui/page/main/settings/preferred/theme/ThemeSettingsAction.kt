// SPDX-License-Identifier: GPL-3.0-only
package app.ai.rune.ziyong.transfer.ui.page.main.settings.preferred.theme

import androidx.compose.ui.graphics.Color
import app.ai.rune.ziyong.transfer.domain.settings.model.preferences.PredictiveBackAnimation
import app.ai.rune.ziyong.transfer.domain.settings.model.preferences.PredictiveBackExitDirection
import app.ai.rune.ziyong.transfer.domain.settings.model.preferences.theme.PaletteStyle
import app.ai.rune.ziyong.transfer.domain.settings.model.preferences.theme.ThemeColorSpec
import app.ai.rune.ziyong.transfer.domain.settings.model.preferences.theme.ThemeMode

sealed interface ThemeSettingsAction {
    data class ChangeUseMiuix(val useMiuix: Boolean) : ThemeSettingsAction
    data class SetUseBlur(val enable: Boolean) : ThemeSettingsAction
    data class SetThemeMode(val mode: ThemeMode) : ThemeSettingsAction
    data class SetPaletteStyle(val style: PaletteStyle) : ThemeSettingsAction
    data class SetColorSpec(val spec: ThemeColorSpec) : ThemeSettingsAction
    data class SetUseDynamicColor(val use: Boolean) : ThemeSettingsAction
    data class SetUseMiuixMonet(val use: Boolean) : ThemeSettingsAction
    data class SetUseAppleFloatingBar(val use: Boolean) : ThemeSettingsAction
    data class SetSeedColor(val color: Color, val isWallpaperSeedColor: Boolean) : ThemeSettingsAction
    data class SetPredictiveBackAnimation(val animation: PredictiveBackAnimation) : ThemeSettingsAction
    data class SetPredictiveBackExitDirection(val direction: PredictiveBackExitDirection) : ThemeSettingsAction
}
