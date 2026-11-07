// SPDX-License-Identifier: GPL-3.0-only
package app.ai.rune.ziyong.transfer.ui.page.miuix.settings.providers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
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
import app.ai.rune.ziyong.transfer.ui.theme.getMiuixAppBarColor
import app.ai.rune.ziyong.transfer.ui.theme.miuixBlurEffect
import app.ai.rune.ziyong.transfer.ui.theme.rememberMiuixBlurBackdrop
import org.koin.androidx.compose.koinViewModel
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.MiuixTheme.isDynamicColor
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/**
 * 供应商页：按供应商分组列出账号与可用状态。
 *
 * 只做「看得见的状态」，不做登录与账号管理——那些是需要多步交互的深流程，
 * 放进这里会把主列表挤成操作台。列表点击留给后续的供应商详情页。
 */
@Composable
fun MiuixProvidersPage(
    enableBlur: Boolean,
    viewModel: AppViewModel = koinViewModel(),
    title: String,
    outerPadding: PaddingValues,
) {
    val uiState by viewModel.state.collectAsStateWithLifecycle()

    // 二级页状态放在本 Tab 内部：进入详情后返回仍在「供应商」页，
    // 而不是回到首页——这是用户对「点进去再退出来」的默认预期。
    var openProviderId by remember { mutableStateOf<String?>(null) }

    BackHandler(enabled = openProviderId != null) { openProviderId = null }

    openProviderId?.let { id ->
        MiuixProviderDetailPage(
            providerId = id,
            viewModel = viewModel,
            onBack = { openProviderId = null },
            outerPadding = outerPadding,
        )
        return
    }

    val scrollBehavior = MiuixScrollBehavior()
    val layoutDirection = LocalLayoutDirection.current
    val backdrop = rememberMiuixBlurBackdrop(enableBlur)

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                modifier = Modifier.miuixBlurEffect(backdrop),
                color = backdrop.getMiuixAppBarColor(),
                title = title,
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
            if (uiState.providers.isEmpty()) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        BasicComponent(
                            title = "还没有供应商",
                            summary = "内置供应商会在引擎启动时自动登记，稍等片刻或重启应用。",
                        )
                    }
                }
            }

            for (provider in uiState.providers) {
                val accounts = uiState.accountsOf(provider.id)
                item {
                    SmallTitle(provider.displayName)
                }
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        BasicComponent(
                            title = "账号",
                            summary = "${provider.accountCount} 个 · 可用 ${provider.usableCount} 个 · 点此管理",
                            onClick = { openProviderId = provider.id },
                        )
                        if (accounts.isEmpty()) {
                            BasicComponent(
                                title = "尚未添加账号",
                                summary = "该供应商暂无可用于调用的账号",
                            )
                        } else {
                            for (account in accounts) {
                                BasicComponent(
                                    title = account.nickname.ifEmpty { account.uid },
                                    summary = buildString {
                                        append(if (account.usable) "可用" else "不可用")
                                        if (account.creditsKnown) {
                                            append(" · 额度 ")
                                            append(account.credits)
                                        }
                                        if (account.detail.isNotEmpty()) {
                                            append(" · ")
                                            append(account.detail)
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.navigationBarsPadding()) }
        }
    }
}
