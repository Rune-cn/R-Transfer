// SPDX-License-Identifier: GPL-3.0-only
package app.ai.rune.ziyong.transfer.ui.page.miuix.settings.providers

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LayoutDirection
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.ai.rune.ziyong.transfer.gateway.AppUiState
import app.ai.rune.ziyong.transfer.gateway.AppViewModel
import app.ai.rune.ziyong.transfer.ui.page.miuix.settings.common.NavigationRow
import app.ai.rune.ziyong.transfer.ui.page.miuix.settings.common.PreferenceCard
import app.ai.rune.ziyong.transfer.ui.page.miuix.settings.common.PreferenceHint
import app.ai.rune.ziyong.transfer.ui.theme.getMiuixAppBarColor
import app.ai.rune.ziyong.transfer.ui.theme.miuixBlurEffect
import app.ai.rune.ziyong.transfer.ui.theme.rememberMiuixBlurBackdrop
import org.koin.androidx.compose.koinViewModel
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/**
 * 供应商列表：每个供应商一张卡片，点进去管理账号。
 *
 * 排版对齐模板「个性化」页：`SmallTitle` + `PreferenceCard { ArrowPreference }`。
 */
/**
 * 供应商页内部的目标页。
 *
 * 用 sealed 目标 + [AnimatedContent] 代替「两个状态 + return」硬切换：
 * 列表 → 详情、详情 → 编辑都是向右推入；返回反向滑出。返回路径沿层级回溯。
 */
private sealed interface ProvidersTarget {
    /** 某个供应商的详情页。 */
    data class Detail(val providerId: String) : ProvidersTarget

    /** 自定义供应商的新建/编辑；`key` 为空串表示新建。 */
    data class Edit(val key: String, val returnToDetail: String?) : ProvidersTarget
}

@Composable
fun MiuixProvidersPage(
    enableBlur: Boolean,
    viewModel: AppViewModel = koinViewModel(),
    title: String,
    outerPadding: PaddingValues,
) {
    val uiState by viewModel.state.collectAsStateWithLifecycle()
    val scrollBehavior = MiuixScrollBehavior()
    val layoutDirection = LocalLayoutDirection.current
    val backdrop = rememberMiuixBlurBackdrop(enableBlur)

    // 当前目标（null = 列表）；返回仍停在「供应商」页，沿层级回退
    var target by remember { mutableStateOf<ProvidersTarget?>(null) }

    BackHandler(enabled = target != null) {
        target = when (val current = target) {
            is ProvidersTarget.Edit -> current.returnToDetail?.let { ProvidersTarget.Detail(it) }
            else -> null
        }
    }

    // 二级页过渡：进入向右推入、返回反向滑出，与设置页二级页一致
    AnimatedContent(
        targetState = target,
        transitionSpec = {
            if (targetState == null) {
                (slideInHorizontally(
                    animationSpec = tween(320, easing = FastOutSlowInEasing),
                    initialOffsetX = { -it / 4 },
                ) + fadeIn(animationSpec = tween(320))) togetherWith
                    (slideOutHorizontally(
                        animationSpec = tween(320, easing = FastOutSlowInEasing),
                        targetOffsetX = { it },
                    ) + fadeOut(animationSpec = tween(240)))
            } else {
                (slideInHorizontally(
                    animationSpec = tween(320, easing = FastOutSlowInEasing),
                    initialOffsetX = { it },
                ) + fadeIn(animationSpec = tween(320))) togetherWith
                    (slideOutHorizontally(
                        animationSpec = tween(320, easing = FastOutSlowInEasing),
                        targetOffsetX = { -it / 4 },
                    ) + fadeOut(animationSpec = tween(240)))
            }
        },
        label = "providersPage",
    ) { current ->
        when (current) {
            is ProvidersTarget.Detail -> MiuixProviderDetailPage(
                providerId = current.providerId,
                viewModel = viewModel,
                onBack = { target = null },
                outerPadding = outerPadding,
                onEditCustom = { key ->
                    target = ProvidersTarget.Edit(key, returnToDetail = current.providerId)
                },
            )

            is ProvidersTarget.Edit -> MiuixCustomProviderPage(
                existingKey = current.key,
                viewModel = viewModel,
                onBack = {
                    target = current.returnToDetail?.let { ProvidersTarget.Detail(it) } ?: null
                },
                onSaved = { providerId ->
                    target = ProvidersTarget.Detail(providerId)
                },
                outerPadding = outerPadding,
            )

            null -> ProvidersListContent(
                enableBlur = enableBlur,
                title = title,
                outerPadding = outerPadding,
                uiState = uiState,
                scrollBehavior = scrollBehavior,
                layoutDirection = layoutDirection,
                backdrop = backdrop,
                onOpenDetail = { target = ProvidersTarget.Detail(it) },
                onOpenNewCustom = { target = ProvidersTarget.Edit("", returnToDetail = null) },
            )
        }
    }
}

/** 供应商列表（target == null 时的内容）。 */
@Composable
private fun ProvidersListContent(
    enableBlur: Boolean,
    title: String,
    outerPadding: PaddingValues,
    uiState: AppUiState,
    scrollBehavior: MiuixScrollBehavior,
    layoutDirection: LayoutDirection,
    backdrop: LayerBackdrop?,
    onOpenDetail: (String) -> Unit,
    onOpenNewCustom: () -> Unit,
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                modifier = Modifier.miuixBlurEffect(backdrop),
                color = backdrop.getMiuixAppBarColor(),
                title = title,
                scrollBehavior = scrollBehavior,
                actions = {
                    IconButton(onClick = onOpenNewCustom) {
                        Icon(
                            imageVector = MiuixIcons.Regular.Add,
                            contentDescription = "新建自定义供应商",
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

            if (uiState.providers.isEmpty()) {
                item { SmallTitle("供应商") }
                item {
                    PreferenceCard {
                        PreferenceHint("还没有供应商。内置供应商会在引擎启动时自动登记。")
                    }
                }
            } else {
                for (provider in uiState.providers) {
                    val accounts = uiState.accountsOf(provider.id)
                    item(key = "title-${provider.id}") { SmallTitle(provider.displayName) }
                    item(key = "card-${provider.id}") {
                        PreferenceCard {
                            NavigationRow(
                                title = "账号",
                                summary = "${provider.accountCount} 个 · 可用 ${provider.usableCount} 个",
                                onClick = { onOpenDetail(provider.id) },
                            )
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.navigationBarsPadding()) }
        }
    }
}
