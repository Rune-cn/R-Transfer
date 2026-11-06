// SPDX-License-Identifier: GPL-3.0-only
package app.ai.rune.ziyong.transfer.ui.page.main.settings.preferred

import androidx.annotation.StringRes

sealed interface PreferredViewEvent {
    data class ShowMessage(@param:StringRes val messageResId: Int) : PreferredViewEvent

    /** Start update check (legado AboutDialog.CheckingUpdate). */
    data object ShowUpdateLoading : PreferredViewEvent

    /** End update check (loading fallback close, legado onFinally). */
    data object HideUpdateLoading : PreferredViewEvent

    /** New version found: open UpdateSheet (data read from state.updateInfo). */
    data object ShowUpdateSheet : PreferredViewEvent
}