// SPDX-License-Identifier: GPL-3.0-only
package app.ai.rune.ziyong.transfer.ui.page.main.settings.preferred.theme

import android.os.Build
import androidx.compose.ui.graphics.Color
import app.ai.rune.ziyong.transfer.domain.settings.model.preferences.PredictiveBackAnimation
import app.ai.rune.ziyong.transfer.domain.settings.model.preferences.PredictiveBackExitDirection
import app.ai.rune.ziyong.transfer.domain.settings.model.preferences.theme.PaletteStyle
import app.ai.rune.ziyong.transfer.domain.settings.model.preferences.theme.ThemeColorSpec
import app.ai.rune.ziyong.transfer.domain.settings.model.preferences.theme.ThemeMode
import app.ai.rune.ziyong.transfer.ui.theme.material.PresetColors
import app.ai.rune.ziyong.transfer.ui.theme.material.RawColor

data class ThemeSettingsState(
    val showMiuixUI: Boolean = false,
    val useBlur: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val paletteStyle: PaletteStyle = PaletteStyle.TonalSpot,
    val colorSpec: ThemeColorSpec = ThemeColorSpec.SPEC_2025,
    val useDynamicColor: Boolean = true,
    val useMiuixMonet: Boolean = false,
    val useAppleFloatingBar: Boolean = false,
    val seedColor: Color = PresetColors[0].color,
    val availableColors: List<RawColor> = PresetColors,
    val showLiveActivity: Boolean = false,
    val predictiveBackAnimation: PredictiveBackAnimation = PredictiveBackAnimation.None,
    val predictiveBackExitDirection: PredictiveBackExitDirection = PredictiveBackExitDirection.FOLLOW_GESTURE,
)
