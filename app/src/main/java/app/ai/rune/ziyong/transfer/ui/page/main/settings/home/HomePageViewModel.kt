// SPDX-License-Identifier: GPL-3.0-only
package app.ai.rune.ziyong.transfer.ui.page.main.settings.home

import android.os.Build
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class HomePageViewModel : ViewModel() {
    val state: StateFlow<HomePageViewState> = MutableStateFlow(
        HomePageViewState(
            deviceName = buildString {
                if (Build.MANUFACTURER.isNotBlank()) {
                    append(Build.MANUFACTURER)
                    append(' ')
                }
                append(Build.MODEL)
            }.trim(),
        ),
    ).asStateFlow()

    fun dispatch(action: HomePageViewAction) {
        // No actions for the shell home page.
    }
}