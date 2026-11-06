// SPDX-License-Identifier: GPL-3.0-only
package app.ai.rune.ziyong.transfer.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import top.yukonga.miuix.kmp.basic.CardColors
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.MiuixTheme.isDynamicColor

@Composable
fun miuixSheetCardColor(): Color = if (isDynamicColor) {
    MiuixTheme.colorScheme.surfaceContainer
} else if (AppTheme.isDark) {
    miuixSheetCardColorDark
} else {
    Color.White
}

@Composable
fun miuixSheetCardColors(contentColor: Color = MiuixTheme.colorScheme.onSurface): CardColors = CardColors(
    color = miuixSheetCardColor(),
    contentColor = contentColor,
)
