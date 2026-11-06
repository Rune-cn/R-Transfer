// SPDX-License-Identifier: GPL-3.0-only
package app.ai.rune.ziyong.transfer.ui.page.main.settings.preferred.theme

import android.os.Build
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kieronquinn.monetcompat.core.MonetCompat
import app.ai.rune.ziyong.transfer.domain.settings.repository.AppSettingsRepository
import app.ai.rune.ziyong.transfer.domain.settings.repository.BooleanSetting
import app.ai.rune.ziyong.transfer.domain.settings.repository.IntSetting
import app.ai.rune.ziyong.transfer.domain.settings.repository.StringSetting
import app.ai.rune.ziyong.transfer.domain.settings.usecase.settings.UpdateSettingUseCase
import app.ai.rune.ziyong.transfer.ui.theme.material.PresetColors
import app.ai.rune.ziyong.transfer.ui.theme.material.RawColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber

class ThemeSettingsViewModel(
    appSettingsRepo: AppSettingsRepository,
    private val updateSetting: UpdateSettingUseCase,
) : ViewModel() {

    val state: StateFlow<ThemeSettingsState> = combine(
        appSettingsRepo.preferencesFlow,
        getWallpaperColorsFlow().onStart { emit(emptyList()) },
    ) { prefs, wallpaperColors ->
        val manualSeedColor =
            if (PresetColors.any { it.color.toArgb() == prefs.seedColorInt }) {
                Color(prefs.seedColorInt)
            } else {
                PresetColors[0].color
            }
        val manualWallpaperSeedColor = Color(prefs.wallpaperSeedColorInt)

        val effectiveSeedColor: Color =
            if (prefs.useDynamicColor && Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                if (!wallpaperColors.isNullOrEmpty()) {
                    if (wallpaperColors.contains(manualWallpaperSeedColor.toArgb())) {
                        manualWallpaperSeedColor
                    } else {
                        Color(wallpaperColors[0])
                    }
                } else {
                    manualSeedColor
                }
            } else {
                manualSeedColor
            }

        val availableColors: List<RawColor> =
            if (prefs.useDynamicColor && Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                if (!wallpaperColors.isNullOrEmpty()) {
                    wallpaperColors.map { colorInt ->
                        RawColor(key = colorInt.toHexString(), color = Color(colorInt))
                    }
                } else {
                    PresetColors
                }
            } else {
                PresetColors
            }

        ThemeSettingsState(
            showMiuixUI = prefs.showMiuixUI,
            useBlur = prefs.useBlur,
            themeMode = prefs.themeMode,
            paletteStyle = prefs.paletteStyle,
            colorSpec = prefs.colorSpec,
            useDynamicColor = prefs.useDynamicColor,
            useMiuixMonet = prefs.useMiuixMonet,
            useAppleFloatingBar = prefs.useAppleFloatingBar,
            seedColor = effectiveSeedColor,
            availableColors = availableColors,
            predictiveBackAnimation = prefs.predictiveBackAnimation,
            predictiveBackExitDirection = prefs.predictiveBackExitDirection,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = ThemeSettingsState(),
    )

    fun dispatch(action: ThemeSettingsAction) {
        when (action) {
            is ThemeSettingsAction.ChangeUseMiuix -> viewModelScope.launch {
                updateSetting(BooleanSetting.UiUseMiuix, action.useMiuix)
            }

            is ThemeSettingsAction.SetUseBlur -> viewModelScope.launch { updateSetting(BooleanSetting.UiUseBlur, action.enable) }

            is ThemeSettingsAction.SetThemeMode -> viewModelScope.launch {
                updateSetting(StringSetting.ThemeMode, action.mode.name)
            }

            is ThemeSettingsAction.SetPaletteStyle -> viewModelScope.launch {
                updateSetting(StringSetting.ThemePaletteStyle, action.style.name)
            }

            is ThemeSettingsAction.SetColorSpec -> viewModelScope.launch {
                updateSetting(StringSetting.ThemeColorSpec, action.spec.name)
            }

            is ThemeSettingsAction.SetUseDynamicColor -> viewModelScope.launch {
                updateSetting(BooleanSetting.ThemeUseDynamicColor, action.use)
            }

            is ThemeSettingsAction.SetUseMiuixMonet -> viewModelScope.launch {
                updateSetting(BooleanSetting.UiUseMiuixMonet, action.use)
            }

            is ThemeSettingsAction.SetUseAppleFloatingBar -> viewModelScope.launch {
                updateSetting(
                    BooleanSetting.UiUseAppleFloatingBar,
                    action.use,
                )
            }

            is ThemeSettingsAction.SetSeedColor -> viewModelScope.launch {
                updateSetting(
                    if (action.isWallpaperSeedColor) {
                        IntSetting.ThemeWallpaperSeedColor
                    } else {
                        IntSetting.ThemeSeedColor
                    },
                    action.color.toArgb(),
                )
            }

            is ThemeSettingsAction.SetPredictiveBackAnimation -> viewModelScope.launch {
                updateSetting(
                    StringSetting.PredictiveBackAnimation,
                    action.animation.value,
                )
            }

            is ThemeSettingsAction.SetPredictiveBackExitDirection -> viewModelScope.launch {
                updateSetting(
                    StringSetting.PredictiveBackExitDirection,
                    action.direction.value,
                )
            }
        }
    }
}

private fun getWallpaperColorsFlow(): Flow<List<Int>?> = flow {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
        val colors = try {
            MonetCompat.getInstance().getAvailableWallpaperColors()
        } catch (e: Exception) {
            Timber.e(e, "Failed to get Monet colors in ThemeSettings")
            null
        }
        emit(colors)
    } else {
        emit(null)
    }
}.flowOn(Dispatchers.IO)
