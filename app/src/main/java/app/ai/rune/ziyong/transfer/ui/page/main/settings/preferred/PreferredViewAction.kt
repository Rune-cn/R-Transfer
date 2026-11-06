// SPDX-License-Identifier: GPL-3.0-only
package app.ai.rune.ziyong.transfer.ui.page.main.settings.preferred

sealed interface PreferredViewAction {
    data class ChangeInternetAccess(val enabled: Boolean) : PreferredViewAction

    data object CheckUpdate : PreferredViewAction

    data class SetAutoCheckUpdateOnStart(val enabled: Boolean) : PreferredViewAction

    data class SetUseBlur(val enabled: Boolean) : PreferredViewAction
}