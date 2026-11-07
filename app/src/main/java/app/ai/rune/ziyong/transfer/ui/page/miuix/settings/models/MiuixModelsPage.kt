// SPDX-License-Identifier: GPL-3.0-only
package app.ai.rune.ziyong.transfer.ui.page.miuix.settings.models

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.ai.rune.ziyong.transfer.gateway.AppViewModel
import app.ai.rune.ziyong.transfer.gateway.AppUiState
import app.ai.rune.ziyong.transfer.gateway.ModelLoadState
import app.ai.rune.ziyong.transfer.gateway.formatTokens
import app.ai.rune.ziyong.transfer.ui.theme.getMiuixAppBarColor
import app.ai.rune.ziyong.transfer.ui.theme.miuixBlurEffect
import app.ai.rune.ziyong.transfer.ui.theme.rememberMiuixBlurBackdrop
import dev.aigw.core.provider.RoutedModel
import org.koin.androidx.compose.koinViewModel
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import androidx.compose.ui.state.ToggleableState

/**
 * 模型页：网关对外暴露的模型清单。
 *
 * 这一页的核心用途是「告诉使用者模型名怎么写」——客户端里填的就是
 * `routePrefix/model.id`（即 [RoutedModel.fullId]）。因此每条都把完整
 * 模型名放在最显眼的位置，而不是只显示人类可读的展示名。
 *
 * 分组按 routePrefix：区域型供应商（如国内/国际版）会各成一组，
 * 因为它们的模型名前缀不同，混在一起会让使用者分不清该填哪个。
 */
@Composable
fun MiuixModelsPage(
    enableBlur: Boolean,
    viewModel: AppViewModel = koinViewModel(),
    title: String,
    outerPadding: PaddingValues,
) {
    val uiState by viewModel.state.collectAsStateWithLifecycle()
    val scrollBehavior = MiuixScrollBehavior()
    val layoutDirection = LocalLayoutDirection.current
    val backdrop = rememberMiuixBlurBackdrop(enableBlur)
    var collapsed by remember { mutableStateOf(emptySet<String>()) }

    // 首次进入拉一次目录：供应商多时拉取耗时明显，不该让冷启动付这笔开销
    LaunchedEffect(Unit) { viewModel.ensureModelsLoaded() }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                modifier = Modifier.miuixBlurEffect(backdrop),
                color = backdrop.getMiuixAppBarColor(),
                title = title,
                subtitle = subtitleOf(uiState),
                navigationIcon = {},
                actions = {
                    IconButton(onClick = { viewModel.refreshModels(forceRefresh = true) }) {
                        Icon(
                            imageVector = MiuixIcons.Regular.Refresh,
                            contentDescription = "刷新模型目录",
                            tint = MiuixTheme.colorScheme.primary,
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .then(backdrop?.let { Modifier.layerBackdrop(it) } ?: Modifier)
                .scrollEndHaptic()
                .overScrollVertical()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(
                start = innerPadding.calculateStartPadding(layoutDirection) + 12.dp,
                top = innerPadding.calculateTopPadding() + 8.dp,
                end = innerPadding.calculateEndPadding(layoutDirection) + 12.dp,
                bottom = outerPadding.calculateBottomPadding(),
            ),
            overscrollEffect = null,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    BasicComponent(
                        title = "模型名怎么填",
                        summary = "客户端里填「供应商前缀/模型 id」，例如 trae/solo-1.0；下方每条都给出了完整写法",
                    )
                    SwitchRowInline(
                        title = "只暴露勾选的模型",
                        subtitle = "开启后 /v1/models 仅返回下方勾选的模型",
                        checked = uiState.modelExposure.filterEnabled,
                        onCheckedChange = {
                            viewModel.updateModelExposure(
                                uiState.modelExposure.copy(filterEnabled = it),
                            )
                        },
                    )
                }
            }

            if (uiState.modelsError.isNotEmpty()) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        BasicComponent(
                            title = "拉取失败",
                            summary = uiState.modelsError,
                        )
                    }
                }
            }

            if (uiState.models.isEmpty()) {
                item { SmallTitle("模型") }
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        BasicComponent(
                            title = if (uiState.modelsLoading) "正在拉取模型目录…" else "还没有模型",
                            summary = if (uiState.modelsLoading) {
                                "各供应商就绪后会增量出现在这里"
                            } else {
                                "无可用账号的供应商不会返回模型；可点右上角刷新重试"
                            },
                        )
                    }
                }
            } else {
                val grouped = remember(uiState.models) { uiState.models.groupBy { it.routePrefix } }
                for ((routePrefix, models) in grouped) {
                    val isCollapsed = routePrefix in collapsed
                    item(key = "header-$routePrefix") {
                        GroupHeader(
                            title = models.firstOrNull()?.providerName ?: routePrefix,
                            prefix = routePrefix,
                            count = models.size,
                            collapsed = isCollapsed,
                            onToggle = {
                                collapsed = if (isCollapsed) collapsed - routePrefix else collapsed + routePrefix
                            },
                        )
                    }
                    if (!isCollapsed) {
                        items(models.size, key = { models[it].fullId }) { index ->
                            ModelRow(
                                model = models[index],
                                exposureEnabled = uiState.modelExposure.filterEnabled,
                                exposed = uiState.modelExposure.allows(models[index].fullId),
                                onToggleExposed = {
                                    viewModel.toggleModelExposed(models[index].fullId, it)
                                },
                            )
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.navigationBarsPadding()) }
        }
    }
}

/** 副标题：把加载进度说清楚，避免用户以为卡住了。 */
private fun subtitleOf(state: AppUiState): String {
    if (!state.modelsLoading) return "共 ${state.models.size} 个模型"
    val total = state.modelLoadStates.size
    val done = state.modelLoadStates.values.count { it !is ModelLoadState.Loading }
    return if (total > 0) {
        "共 ${state.models.size} 个 · 载入中 $done/$total"
    } else {
        "共 ${state.models.size} 个 · 更新中"
    }
}

/** 分组头：折叠 + 供应商名 + 模型前缀 + 数量。 */
@Composable
private fun GroupHeader(
    title: String,
    prefix: String,
    count: Int,
    collapsed: Boolean,
    onToggle: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onToggle,
        insideMargin = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
        cornerRadius = 16.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MiuixTheme.textStyles.subtitle,
                    color = MiuixTheme.colorScheme.onSurface,
                )
                Text(
                    text = "前缀 $prefix/ · $count 个",
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
            Text(
                text = if (collapsed) "展开" else "收起",
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.primary,
            )
        }
    }
}

/** 单个模型：完整模型名 + 展示名/上下文 + 开放名单勾选。 */
@Composable
private fun ModelRow(
    model: RoutedModel,
    exposureEnabled: Boolean,
    exposed: Boolean,
    onToggleExposed: (Boolean) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
        cornerRadius = 14.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (exposureEnabled) {
                Checkbox(
                    state = if (exposed) ToggleableState.On else ToggleableState.Off,
                    onClick = { onToggleExposed(!exposed) },
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = model.fullId,
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurface,
                )
                Text(
                    text = buildString {
                        append(model.model.name)
                        if (model.model.contextWindow > 0) {
                            append(" · 上下文 ")
                            append(formatTokens(model.model.contextWindow))
                        }
                        if (model.cached) append(" · 缓存目录")
                    },
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                // 能力标记只在供应商明确给出时才显示（不同供应商的 key 命名不一致）
                val tags = capabilityTags(model)
                if (tags.isNotEmpty()) {
                    Text(
                        text = tags.joinToString(" · "),
                        style = MiuixTheme.textStyles.footnote2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            }
        }
    }
}

/**
 * 能力标签。
 *
 * 各家写 extra 的口径不统一（CodeBuddy 用 tools/vision="true"，Loomy 用
 * tool_call/attachment="1"），这里把同义 key 与两种真假写法都认下；
 * 没有就不显示——不猜能力，猜错会让用户以为能传图。
 */
private fun capabilityTags(model: RoutedModel): List<String> {
    val extra = model.model.extra
    fun flag(vararg keys: String): Boolean = keys.any { key ->
        val v = extra[key]?.trim()?.lowercase()
        v == "true" || v == "1"
    }
    return buildList {
        if (flag("reasoning")) add("思考")
        if (flag("search", "web_search", "online")) add("联网")
        if (flag("vision", "image", "attachment")) add("识图")
        if (flag("tools", "tool_call", "toolcall")) add("工具")
        extra["maxOutput"]?.takeIf { it.isNotBlank() }?.let { add("输出上限 $it") }
        extra["rate"]?.takeIf { it.isNotBlank() }?.let { add("倍率 $it") }
    }
}

/** 行内开关（标题 + 说明 + Switch），与页面其他行保持同一栅格。 */
@Composable
private fun SwitchRowInline(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MiuixTheme.textStyles.subtitle,
                color = MiuixTheme.colorScheme.onSurface,
            )
            Text(
                text = subtitle,
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
