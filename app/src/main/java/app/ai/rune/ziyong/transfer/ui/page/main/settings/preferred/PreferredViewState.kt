// SPDX-License-Identifier: GPL-3.0-only
package app.ai.rune.ziyong.transfer.ui.page.main.settings.preferred

import app.ai.rune.ziyong.transfer.domain.updater.model.UpdateInfo

data class PreferredViewState(
    val allowInternetAccess: Boolean = true,
    val hasUpdate: Boolean = false,
    val remoteVersion: String = "",
    val autoCheckUpdateOnStart: Boolean = false,
    val useBlur: Boolean = false,
    val updateInfo: UpdateInfo? = null,
)