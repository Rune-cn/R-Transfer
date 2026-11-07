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
 * **一切以 miuix-example 模板为准**，不要自创样式。模板的规范是：
 *
 * ```
 * SmallTitle("分组名")                                  // 分组标题
 * Card(modifier = Modifier.padding(horizontal = 12.dp)  // 卡片，页级水平边距
 *                   .padding(bottom = 12.dp)) {
 *     BasicComponent(title = ..., summary = ...)         // 自带 16dp 内边距
 *     TextButton(text = ..., onClick = ...)              // 按钮：原生 TextButton
 * }
 * ```
 *
 * 本文件只把「卡片」这一层封装好，**按钮一律用原生 `TextButton`**——
 * 曾试过自定义 `CompactButton` 改 `insideMargin`/`minWidth`，
 * 结果是按钮高度、圆角、按压反馈都和模板不一致，属于不该做的“优化”。
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
 * 水平边距由页面的 `LazyColumn.contentPadding` 提供（各页统一 12dp），
 * 这里只负责卡片之间的垂直间距，避免叠加成 24dp。
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
                // 水平 16dp 对齐 BasicComponent；垂直方向交给各子项，避免混排时双重间距
                .padding(horizontal = 16.dp),
            content = content,
        )
    }
}

/**
 * 可点击的分组卡片（如「点进去看详情」的列表项）。
 *
 * 与 [SectionCard] 的差别只有 `onClick`：miuix 的 `Card` 只有在传入 onClick 时才具备
 * 按压反馈与水波纹，所以这类卡片必须走 `Card` 的重载，不能包一层 clickable。
 */
@Composable
fun ClickableSectionCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    gap: androidx.compose.ui.unit.Dp = CardGap,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = gap),
        insideMargin = PaddingValues(0.dp),
        onClick = onClick,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            content = content,
        )
    }
}

/**
 * 卡片内的自定义内容块：负责垂直呼吸。
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
 * 并排按钮行——**与模板一致，直接用原生 `TextButton` + `weight(1f)`**。
 *
 * 模板里 `MiuixDialog` / `MiuixBadge` 的按钮行就是这个写法：
 * ```
 * Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
 *     TextButton(text = "取消", onClick = ..., modifier = Modifier.weight(1f))
 *     TextButton(text = "保存", onClick = ..., modifier = Modifier.weight(1f))
 * }
 * ```
 * 不要改 `insideMargin` / `minWidth`：那会让高度与圆角偏离其余按钮。
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
