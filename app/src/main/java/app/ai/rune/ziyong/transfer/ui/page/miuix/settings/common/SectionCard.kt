// SPDX-License-Identifier: GPL-3.0-only
package app.ai.rune.ziyong.transfer.ui.page.miuix.settings.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 设置类页面的排版约定。
 *
 * 为什么需要这一层：miuix 的 `Card` 默认内边距是 **0**，它假设子项自己带 padding
 * （`BasicComponent` 就自带 16dp）。直接往 Card 里塞 `Text` 或 `Row` 而不补 padding，
 * 文字会贴着卡片边缘，看起来「错位」——这是本工程多个新页面出现过的同一问题。
 *
 * 这里把「卡片内水平边距」与「卡片内垂直节奏」收敛成常量，
 * 新页面只要走 [SectionCard] 就不会再犯。
 */

/** 卡片内容的水平内边距：与 BasicComponent 自带的 16dp 对齐。 */
val CardHorizontalPadding = 16.dp

/** 卡片内容的垂直内边距：比水平略小，让行与行之间更紧凑。 */
val CardVerticalPadding = 8.dp

/** 卡片内相邻区块的间距。 */
val CardBlockSpacing = 10.dp

/**
 * 统一的设置卡片：负责水平/垂直内边距与行间距。
 *
 * 内部可以混放 `BasicComponent`（自带 padding，无需再补）与自定义内容
 * （`Text`、`Row` 等，本卡片已补好水平边距）。
 */
@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        insideMargin = PaddingValues(0.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CardHorizontalPadding, vertical = CardVerticalPadding),
            verticalArrangement = Arrangement.spacedBy(CardBlockSpacing),
            content = content,
        )
    }
}

/**
 * 卡片内的并排按钮行。
 *
 * 为什么不直接用 `Row + weight(1f) + TextButton`：`TextButton` 自带
 * 16dp 水平内边距，两个各占一半宽度时，留给文字的空间会被内边距吃掉大半，
 * 看起来「太挤」。这里统一压小按钮内边距并留出间隔。
 */
@Composable
fun ButtonRow(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

/**
 * 并排用的紧凑按钮：宽度均分、内边距收窄。
 *
 * @param primary 主操作用强调色底（保存/确认），次操作用中性样式（取消）。
 */
@Composable
fun RowScope.CompactButton(
    text: String,
    onClick: () -> Unit,
    primary: Boolean = false,
    enabled: Boolean = true,
    danger: Boolean = false,
) {
    TextButton(
        text = text,
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.weight(1f),
        // 默认 minWidth 58dp 会让两个按钮并排时互相挤压，这里放开由 weight 决定
        minWidth = 0.dp,
        insideMargin = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
        colors = when {
            danger -> top.yukonga.miuix.kmp.basic.ButtonDefaults.textButtonColors(
                textColor = MiuixTheme.colorScheme.error,
            )

            primary -> top.yukonga.miuix.kmp.basic.ButtonDefaults.textButtonColorsPrimary()

            else -> top.yukonga.miuix.kmp.basic.ButtonDefaults.textButtonColors()
        },
    )
}

/** 卡片内的小节标题（相当于分组标题，但不另开卡片）。 */
@Composable
fun CardSectionTitle(text: String) {
    Text(
        text = text,
        style = MiuixTheme.textStyles.subtitle,
        color = MiuixTheme.colorScheme.onSurface,
    )
}

/** 卡片内的说明文字：统一走次级色，避免各页各写一个色值。 */
@Composable
fun CardHint(text: String, error: Boolean = false) {
    Text(
        text = text,
        style = MiuixTheme.textStyles.footnote2,
        color = if (error) {
            MiuixTheme.colorScheme.error
        } else {
            MiuixTheme.colorScheme.onSurfaceVariantSummary
        },
    )
}
