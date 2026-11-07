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
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 页面骨架与行的统一约定——**严格对齐 miuix-example 的「个性化」「关于」页**。
 *
 * 模板那两页的真实写法：
 * ```
 * item { Spacer(Modifier.size(12.dp)) }
 * item { SmallTitle("分组名") }
 * item {
 *     Card(Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp)) {
 *         MiuixNavigationItemWidget(title, description, onClick)  // = ArrowPreference
 *         MiuixSwitchWidget(title, description, checked, onChange) // = SwitchPreference
 *     }
 * }
 * ```
 *
 * 三个关键点，之前都做错了：
 * 1. **卡片必须带 `padding(horizontal = 12.dp)`**——模板把它写在每张 Card 上，
 *    而不是靠 LazyColumn 的 contentPadding；
 * 2. **可点击的行用 `ArrowPreference`**（自带右侧箭头），不是 `BasicComponent`；
 * 3. **开关行用 `SwitchPreference`**（自带开关与整行点击），不是手写 Row + Switch。
 */

/** 卡片水平外边距：模板固定 12dp。 */
val CardHorizontalMargin = 12.dp

/** 卡片下方间距：模板固定 12dp。 */
val CardBottomMargin = 12.dp

/**
 * 模板风格的分组卡片。
 *
 * 自带 `padding(horizontal = 12.dp).padding(bottom = 12.dp)`，
 * 与模板「关于」「个性化」两页完全一致。
 * 内部直接放 `ArrowPreference` / `SwitchPreference` / `BasicComponent`（它们自带 16dp 内边距）。
 */
@Composable
fun PreferenceCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = CardHorizontalMargin)
            .padding(bottom = CardBottomMargin),
        colors = CardDefaults.defaultColors(MiuixTheme.colorScheme.surfaceContainer),
        content = content,
    )
}

/**
 * 可点击的分组卡片（如模型分组头、「点进去看详情」的列表项）。
 *
 * miuix 的 `Card` 只有传 `onClick` 才有按压反馈与水波纹，
 * 所以这类卡片必须走 Card 的重载，不能包一层 `clickable`。
 */
@Composable
fun PreferenceCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = CardHorizontalMargin)
            .padding(bottom = CardBottomMargin),
        colors = CardDefaults.defaultColors(MiuixTheme.colorScheme.surfaceContainer),
        onClick = onClick,
        content = content,
    )
}

/**
 * 可跳转/可点击的设置行——等价于模板的 `MiuixNavigationItemWidget`。
 *
 * 用 `ArrowPreference`：右侧自带箭头，这是模板里所有「点进去」的行的一致外观。
 * 不要用 `BasicComponent` 手写 onClick——那没有箭头，用户不知道点进去有东西。
 */
@Composable
fun NavigationRow(
    title: String,
    summary: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    ArrowPreference(
        title = title,
        summary = summary,
        modifier = modifier,
        onClick = onClick,
    )
}

/**
 * 开关行——等价于模板的 `MiuixSwitchWidget`。
 *
 * `SwitchPreference` 自带「整行可点 + 右侧开关」，比自己拼 Row + Switch 更符合规范
 * （自拼版本的点按区域只有开关本身，误触率高）。
 */
@Composable
fun SwitchRow(
    title: String,
    summary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    SwitchPreference(
        checked = checked,
        onCheckedChange = onCheckedChange,
        title = title,
        summary = summary,
        modifier = modifier,
        enabled = enabled,
    )
}

/**
 * 卡片内的说明文字：统一走次级色。
 *
 * 只用于「卡片里必须解释一段话」的场景（如凭证风险提示）；
 * 普通的行说明请用 `NavigationRow` / `SwitchRow` 的 summary 参数。
 */
@Composable
fun PreferenceHint(text: String, error: Boolean = false) {
    Text(
        text = text,
        style = MiuixTheme.textStyles.footnote2,
        color = if (error) {
            MiuixTheme.colorScheme.error
        } else {
            MiuixTheme.colorScheme.onSurfaceVariantSummary
        },
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

/**
 * 卡片内的并排按钮行——与模板一致：原生 `TextButton` + `weight(1f)`，不改任何样式参数。
 *
 * 模板里 `MiuixDialog` / `MiuixBadge` 的按钮行就是这个写法。
 */
@Composable
fun PreferenceButtonRow(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

/**
 * 卡片内的整行按钮：原生 `TextButton`，样式全默认。
 *
 * 用于「保存」「添加账号」这类主操作；不要改 `insideMargin` / `minWidth`，
 * 那会让高度与圆角偏离其余按钮。
 */
@Composable
fun PreferenceActionRow(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    TextButton(
        text = text,
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

/** 卡片内的自定义内容块：负责垂直呼吸，与 Preference 行之间留出间距。 */
@Composable
fun PreferenceBlock(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}
