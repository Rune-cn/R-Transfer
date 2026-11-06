// SPDX-License-Identifier: GPL-3.0-only
package app.ai.rune.ziyong.transfer.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import app.ai.rune.ziyong.transfer.domain.settings.model.preferences.theme.ThemeMode
import java.util.Locale

/**
 * 网关外观设置与 miuix 主题系统之间的适配层。
 *
 * 为什么需要这一层：网关内核自带的 `AppViewModel` 会读写一组「外观」概念
 * （明暗模式、强调色两端），而本工程的视觉完全由 miuix 主题系统承载。
 * 直接让内核依赖 ui.theme 的私有实现会在两边升级时同时编译失败，
 * 因此把桥接点集中放在这里，两边各自演进时只需改这一个文件。
 */

/**
 * 默认强调色（MIUI 蓝）。
 *
 * 引擎层只保存「用户是否改过颜色」，真正的渲染由 MiuixTheme 负责；
 * 这两个值同时用作网关侧 AppColors 的默认值，保证两侧读数一致。
 */
val defaultAppColors: AppColors = AppColors(
    goldStart = Color(0xFF3482FF),
    goldEnd = Color(0xFF1F6BE0),
)

/** 网关侧的外观色对（强调色渐变两端）。 */
data class AppColors(
    val goldStart: Color,
    val goldEnd: Color,
)

/**
 * 解析 `#RRGGBB` 或 `RRGGBB`（大小写不敏感）为不透明颜色；非法输入返回 null。
 *
 * 存储里可能留着历史版本写入的坏值，调用方据此回落到默认色。
 */
fun parseHexColor(input: String): Color? {
    val raw = input.trim().removePrefix("#")
    if (raw.length != 6) return null
    val value = raw.toLongOrNull(16) ?: return null
    return Color(0xFF000000L or value)
}

/** 输出 `#RRGGBB`（大写）。 */
fun formatHex(color: Color): String =
    String.format(Locale.US, "#%06X", color.toArgb() and 0xFFFFFF)
