// SPDX-License-Identifier: GPL-3.0-only
package app.ai.rune.ziyong.transfer.domain.settings.provider

import app.ai.rune.ziyong.transfer.domain.settings.model.preferences.ThemeState
import kotlinx.coroutines.flow.StateFlow

interface ThemeStateProvider {
    val themeStateFlow: StateFlow<ThemeState>
}
