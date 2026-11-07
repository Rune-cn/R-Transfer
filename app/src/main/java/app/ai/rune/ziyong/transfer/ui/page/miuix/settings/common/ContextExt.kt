// SPDX-License-Identifier: GPL-3.0-only
package app.ai.rune.ziyong.transfer.ui.page.miuix.settings.common

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper

/**
 * 从任意 Context 向上找到宿主 Activity。
 *
 * 有些系统 API（权限请求、窗口外观、recreate）只接受 Activity，
 * 而 Compose 里拿到的是可能被包装过的 Context，必须逐层解包。
 * 找不到时返回 null，由调用方决定退路，而不是强转崩溃。
 */
tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
