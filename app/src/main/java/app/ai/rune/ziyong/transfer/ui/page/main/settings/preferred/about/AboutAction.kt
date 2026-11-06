// SPDX-License-Identifier: GPL-3.0-only
package app.ai.rune.ziyong.transfer.ui.page.main.settings.preferred.about

sealed interface AboutAction {
    data object CheckUpdate : AboutAction

    data class SetAutoCheckUpdateOnStart(val enabled: Boolean) : AboutAction
}