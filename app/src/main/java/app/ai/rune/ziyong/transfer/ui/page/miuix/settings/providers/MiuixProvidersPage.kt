// SPDX-License-Identifier: GPL-3.0-only
package app.ai.rune.ziyong.transfer.ui.page.miuix.settings.providers

import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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

    // 二级页状态收在本 Tab 内部：返回仍停在「供应商」页
    var openProviderId by remember { mutableStateOf<String?>(null) }
    // 新建/编辑自定义供应商：null=未打开；""=新建；其它=编辑该 key
    var editingCustomKey by remember { mutableStateOf<String?>(null) }
    // 从详情进入编辑时记住来源，返回要回到详情
    var returnToDetail by remember { mutableStateOf<String?>(null) }

    BackHandler(enabled = openProviderId != null || editingCustomKey != null) {
        if (editingCustomKey != null) {
            editingCustomKey = null
            openProviderId = returnToDetail
            returnToDetail = null
        } else {
            openProviderId = null
        }
    }

    if (editingCustomKey != null) {
        MiuixCustomProviderPage(
            existingKey = editingCustomKey,
            viewModel = viewModel,
            onBack = {
                editingCustomKey = null
                openProviderId = returnToDetail
                returnToDetail = null
            },
            onSaved = { providerId ->
                editingCustomKey = null
                returnToDetail = null
                openProviderId = providerId
            },
            outerPadding = outerPadding,
        )
        return
    }

    openProviderId?.let { id ->
        MiuixProviderDetailPage(
            providerId = id,
            viewModel = viewModel,
            onBack = { openProviderId = null },
            outerPadding = outerPadding,
            onEditCustom = { key ->
                returnToDetail = id
                openProviderId = null
                editingCustomKey = key
            },
        )
        return
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                modifier = Modifier.miuixBlurEffect(backdrop),
                color = backdrop.getMiuixAppBarColor(),
                title = title,
                scrollBehavior = scrollBehavior,
                actions = {
                    IconButton(onClick = { editingCustomKey = "" }) {
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
                                onClick = { openProviderId = provider.id },
                            )
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.navigationBarsPadding()) }
        }
    }
}
