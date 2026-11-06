// SPDX-License-Identifier: GPL-3.0-only
package app.ai.rune.ziyong.transfer.core.env

import app.ai.rune.ziyong.transfer.BuildConfig
import app.ai.rune.ziyong.transfer.core.device.model.Level

object AppConfig {
    val LEVEL: Level = when (BuildConfig.BUILD_LEVEL) {
        1 -> Level.PREVIEW
        2 -> Level.STABLE
        else -> Level.UNSTABLE
    }

    val isDebug: Boolean = BuildConfig.DEBUG
    const val VERSION_NAME: String = BuildConfig.VERSION_NAME
    const val VERSION_CODE: Int = BuildConfig.VERSION_CODE

    val isRespectPlatformInstallPolicyAvailable = LEVEL == Level.UNSTABLE
    val isLogEnabled = LEVEL == Level.PREVIEW || LEVEL == Level.UNSTABLE || isDebug
}
