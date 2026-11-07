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
 * 设置类页面的排版约定——**与 miuix-example 模板保持一致**。
 *
 * 模板的卡片节奏是：
 * ```
 * SmallTitle("分组名")
 * Card(modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp)) {
 *     BasicComponent(...)   // 自带 16dp 内边距
 * }
 * ```
 *
 * 本文件把这个节奏收敛成 [SectionCard] 等几个入口，避免各页面各写一套
 * 导致观感不统一。**新页面请一律走这里，不要直接手写 Card。**
 *
 * 为什么 `Card` 不能直接塞裸 `Text`：miuix 的 `CardDefaults.InsideMargin` 是 0，
 * 它假设子项自带 padding（`BasicComponent` 有 16dp）。裸文本会贴边。
 * [SectionCard] 已为自定义内容补好水平边距。
 */

/** 卡片外水平边距：与模板一致（12dp）。 */
val PageHorizontalPadding = 12.dp

/** 卡片之间的垂直间距：与模板一致（12dp）。 */
val CardGap = 12.dp

/**
 * 模板风格的分组卡片。
 *
 * 内部可以混放：
 * - `BasicComponent`（自带 16dp，无需再补）
 * - 自定义内容（`Text` / `Row` / `TextField` 等，本卡片已补好水平边距）
 *
 * 注意：**水平边距由页面的 `LazyColumn.contentPadding` 提供**（各页已统一 12dp），
 * 这里只负责卡片之间的垂直间距，否则会叠加成 24dp。
 *
 * @param gap 与下方卡片的间距。
 */
@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    gap: androidx.compose.ui.unit.Dp = CardGap,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = gap),
        insideMargin = PaddingValues(0.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // 水平 16dp 对齐 BasicComponent；垂直方向留给各子项自己控制，
                // 否则「BasicComponent + 自定义内容」混排时会出现双重间距。
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
            content = content,
        )
    }
}

/**
 * 卡片内的自定义内容块：负责垂直呼吸，避免与 `BasicComponent` 的行高打架。
 *
 * 用法：`SectionCard { BasicComponent(...); CardBlock { Text(...) } }`
 */
@Composable
fun CardBlock(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

/**
 * 卡片内的并排按钮行。
 *
 * `TextButton` 自带 16dp 水平内边距 + 58dp 最小宽度，两个各占一半会互相挤压；
 * 这里统一压小内边距并留出间隔。
 */
@Composable
fun ButtonRow(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

/**
 * 并排用的紧凑按钮：宽度均分、内边距收窄。
 *
 * @param primary 主操作（保存/确认）用强调色底
 * @param danger 破坏性操作（删除）用错误色文字
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

/** 卡片内的小节标题。 */
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
