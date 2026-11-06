// SPDX-License-Identifier: GPL-3.0-only
package app.ai.rune.ziyong.transfer.ui.page.main.settings.preferred.about

import androidx.compose.ui.graphics.ImageBitmap
import app.ai.rune.ziyong.transfer.domain.updater.model.UpdateInfo

data class AboutState(
    val hasUpdate: Boolean = false,
    val remoteVersion: String = "",
    val autoCheckUpdateOnStart: Boolean = false,
    val appIcon: ImageBitmap? = null,
    val updateInfo: UpdateInfo? = null,
    /** First line (timestamp) of the latest crash dump, null when none. */
    val latestCrashSummary: String? = null,
)