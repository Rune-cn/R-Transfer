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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.ai.rune.ziyong.transfer.gateway.AppUiState
import app.ai.rune.ziyong.transfer.gateway.AppViewModel
import app.ai.rune.ziyong.transfer.gateway.ModelLoadState
import app.ai.rune.ziyong.transfer.gateway.formatTokens
import app.ai.rune.ziyong.transfer.ui.page.miuix.settings.common.NavigationRow
import app.ai.rune.ziyong.transfer.ui.page.miuix.settings.common.PreferenceBlock
import app.ai.rune.ziyong.transfer.ui.page.miuix.settings.common.PreferenceCard
import app.ai.rune.ziyong.transfer.ui.page.miuix.settings.common.PreferenceHint
import app.ai.rune.ziyong.transfer.ui.page.miuix.settings.common.SwitchRow
import app.ai.rune.ziyong.transfer.ui.theme.getMiuixAppBarColor
import app.ai.rune.ziyong.transfer.ui.theme.miuixBlurEffect
import app.ai.rune.ziyong.transfer.ui.theme.rememberMiuixBlurBackdrop
import dev.aigw.core.provider.RoutedModel
import org.koin.androidx.compose.koinViewModel
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/**
 * 模型页：网关对外暴露的模型清单。
 *
 * 排版对齐模板「关于」页——**每个供应商一张卡，卡内逐行列出模型**，
 * 而不是「每个模型一张卡」（那样一屏只放得下三四个，滚动起来非常碎）。
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

    // 分组必须在 Composable 作用域算好：LazyListScope 的 lambda 不是 @Composable 上下文
    val grouped = remember(uiState.models) { uiState.models.groupBy { it.routePrefix } }

    LaunchedEffect(Unit) { viewModel.ensureModelsLoaded() }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                modifier = Modifier.miuixBlurEffect(backdrop),
                color = backdrop.getMiuixAppBarColor(),
                title = title,
                subtitle = subtitleOf(uiState),
                scrollBehavior = scrollBehavior,
                actions = {
                    IconButton(onClick = { viewModel.refreshModels(forceRefresh = true) }) {
                        Icon(
                            imageVector = MiuixIcons.Regular.Refresh,
                            contentDescription = "刷新模型目录",
                            tint = MiuixTheme.colorScheme.primary,
                        )
                    }
                },
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
                start = innerPadding.calculateStartPadding(layoutDirection),
                top = innerPadding.calculateTopPadding(),
                end = innerPadding.calculateEndPadding(layoutDirection),
                bottom = outerPadding.calculateBottomPadding(),
            ),
            overscrollEffect = null,
        ) {
            item { Spacer(modifier = Modifier.size(12.dp)) }

            item { SmallTitle("说明") }
            item {
                PreferenceCard {
                    PreferenceHint("客户端里填「供应商前缀/模型 id」，例如 trae/solo-1.0；下方每组都标了前缀。")
                    SwitchRow(
                        title = "只暴露勾选的模型",
                        summary = "开启后 /v1/models 仅返回下方勾选的模型",
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
                item { SmallTitle("拉取失败") }
                item {
                    PreferenceCard {
                        PreferenceHint(uiState.modelsError, error = true)
                    }
                }
            }

            if (uiState.models.isEmpty()) {
                item { SmallTitle("模型") }
                item {
                    PreferenceCard {
                        PreferenceHint(
                            if (uiState.modelsLoading) {
                                "正在拉取模型目录，各供应商就绪后会增量出现…"
                            } else {
                                "还没有模型。无可用账号的供应商不会返回模型；可点右上角刷新重试。"
                            },
                        )
                    }
                }
            } else {
                for ((routePrefix, models) in grouped) {
                    val isCollapsed = routePrefix in collapsed
                    item(key = "title-$routePrefix") {
                        SmallTitle(models.firstOrNull()?.providerName ?: routePrefix)
                    }
                    item(key = "card-$routePrefix") {
                        PreferenceCard {
                            NavigationRow(
                                title = "前缀 $routePrefix/",
                                summary = if (isCollapsed) {
                                    "${models.size} 个模型 · 点击展开"
                                } else {
                                    "${models.size} 个模型 · 点击收起"
                                },
                                onClick = {
                                    collapsed = if (isCollapsed) {
                                        collapsed - routePrefix
                                    } else {
                                        collapsed + routePrefix
                                    }
                                },
                            )
                            // 折叠/展开带垂直展开动画，与模板条件行的节奏一致
                            AnimatedVisibility(
                                visible = !isCollapsed,
                                enter = fadeIn() + expandVertically(),
                                exit = fadeOut() + shrinkVertically(),
                            ) {
                                Column {
                                    for (model in models) {
                                        ModelRow(
                                            model = model,
                                            exposureEnabled = uiState.modelExposure.filterEnabled,
                                            exposed = uiState.modelExposure.allows(model.fullId),
                                            onToggleExposed = {
                                                viewModel.toggleModelExposed(model.fullId, it)
                                            },
                                        )
                                    }
                                }
                            }
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

/**
 * 单个模型：卡内的一行（不是一张卡）。
 *
 * 完整模型名放主标题位（用户要复制/输入的就是它），
 * 展示名与上下文放副标题位，能力标签只在供应商明确给出时才显示。
 */
@Composable
private fun ModelRow(
    model: RoutedModel,
    exposureEnabled: Boolean,
    exposed: Boolean,
    onToggleExposed: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (exposureEnabled) {
            Checkbox(
                state = if (exposed) ToggleableState.On else ToggleableState.Off,
                onClick = { onToggleExposed(!exposed) },
            )
        }
        PreferenceBlock(modifier = Modifier.weight(1f)) {
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
