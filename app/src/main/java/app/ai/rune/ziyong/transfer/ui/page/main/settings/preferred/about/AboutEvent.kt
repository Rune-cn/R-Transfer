// SPDX-License-Identifier: GPL-3.0-only
package app.ai.rune.ziyong.transfer.ui.page.main.settings.preferred.about

sealed interface AboutEvent {
    data object ShowUpdateLoading : AboutEvent
    data object HideUpdateLoading : AboutEvent
    data object ShowUpdateSheet : AboutEvent
    data class ShowMessage(@androidx.annotation.StringRes val messageResId: Int) : AboutEvent
}