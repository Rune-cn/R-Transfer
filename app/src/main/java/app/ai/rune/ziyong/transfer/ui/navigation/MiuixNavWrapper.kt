// SPDX-License-Identifier: GPL-3.0-only
package app.ai.rune.ziyong.transfer.ui.navigation

import android.os.Build
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.ai.rune.ziyong.transfer.R
import app.ai.rune.ziyong.transfer.domain.settings.model.preferences.ThemeState
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Home
import top.yukonga.miuix.kmp.icon.extended.ListView
import top.yukonga.miuix.kmp.icon.extended.NotesFill
import top.yukonga.miuix.kmp.icon.extended.Store
import top.yukonga.miuix.kmp.icon.extended.Tune
import app.ai.rune.ziyong.transfer.ui.library.FloatingBottomBarMode
import app.ai.rune.ziyong.transfer.ui.page.main.settings.SettingsSharedViewModel
import app.ai.rune.ziyong.transfer.ui.page.miuix.settings.SettingsCompactLayout
import app.ai.rune.ziyong.transfer.ui.page.miuix.settings.SettingsWideScreenLayout
import app.ai.rune.ziyong.transfer.ui.theme.LocalWindowLayoutInfo
import app.ai.rune.ziyong.transfer.ui.theme.WindowLayoutType
import app.ai.rune.ziyong.transfer.ui.theme.rememberMiuixBlurBackdrop
import org.koin.compose.koinInject
import top.yukonga.miuix.kmp.basic.NavigationItem
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop

// Centralized complex layout logic to keep provider clean and readable
@Composable
fun MiuixMainPageWrapper(uiState: ThemeState, sharedViewModel: SettingsSharedViewModel) {
    val layoutInfo = LocalWindowLayoutInfo.current
    val sharedState by sharedViewModel.state.collectAsStateWithLifecycle()
    val useBlur = uiState.useBlur
    val useFloatingBottomBar = uiState.useAppleFloatingBar
    val floatingBottomBarMode = when {
        useBlur && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> FloatingBottomBarMode.LiquidGlass
        useBlur -> FloatingBottomBarMode.Blur
        else -> FloatingBottomBarMode.None
    }

    val configCount = 0 // 网关页面暂不需要角标
    val homeLabel = stringResource(R.string.home)
    val configLabel = "供应商"
    val modelsLabel = "模型"
    val logLabel = stringResource(R.string.run_logs)
    val preferredLabel = stringResource(R.string.preferred)

    // 图标统一取 miuix 官方图标集：与 NavigationBar 的选中态缩放、视觉重量同源；
    // 混用 Material Icons 会让选中图标看起来忽粗忽细。
    //
    // 逐项选择依据（按「用户去这一页想干什么」挑，而不是按字面）：
    //   首页   Home      —— 回到总览
    //   供应商 Store     —— 账号来源是「商店/货架」语义，比 Layers（图层）更贴近
    //   日志   NotesFill —— 实心稿在大底栏更压得住；Notes 留给「文档」语义
    //   设置   Tune      —— 调节旋钮，与「偏好设置」一致；Settings 齿轮留给深层的系统设置
    val navigationItems = remember(homeLabel, configLabel, modelsLabel, logLabel, preferredLabel) {
        listOf(
            NavigationItem(
                label = homeLabel,
                icon = MiuixIcons.Regular.Home,
            ),
            NavigationItem(
                label = configLabel,
                icon = MiuixIcons.Regular.Store,
            ),
            NavigationItem(
                label = modelsLabel,
                icon = MiuixIcons.Regular.ListView,
            ),
            NavigationItem(
                label = logLabel,
                icon = MiuixIcons.Regular.NotesFill,
            ),
            NavigationItem(
                label = preferredLabel,
                icon = MiuixIcons.Regular.Tune,
            ),
        )
    }

    val pagerState = rememberPagerState(
        initialPage = sharedState.lastMainPageIndex,
        pageCount = { navigationItems.size },
    )
    val mainPagerState = rememberMainPagerState(pagerState)
    val currentPage = mainPagerState.pagerState.currentPage
    val settledPage = mainPagerState.pagerState.settledPage
    LaunchedEffect(currentPage) {
        mainPagerState.syncPage()
    }
    LaunchedEffect(settledPage) {
        if (sharedState.lastMainPageIndex != settledPage) {
            sharedViewModel.updateLastMainPageIndex(settledPage)
        }
    }
    MainScreenBackHandler(
        mainPagerState = mainPagerState,
        navController = LocalNavigator.current,
    )

    val snackbarHostState = remember { SnackbarHostState() }

    // Create separated backdrops for different blurred components
    val floatingBackdrop = rememberLayerBackdrop()
    val miuixBackdrop = rememberMiuixBlurBackdrop(useBlur)

    // Branch statically without layout delay traps
    if (layoutInfo.type == WindowLayoutType.EXPANDED || layoutInfo.showNavigationRail) {
        SettingsWideScreenLayout(
            configCount = configCount,
            mainPagerState = mainPagerState,
            navigationItems = navigationItems,
            snackbarHostState = snackbarHostState,
            useFloatingBottomBar = useFloatingBottomBar,
            floatingBottomBarMode = floatingBottomBarMode,
            floatingBackdrop = floatingBackdrop,
            miuixBackdrop = miuixBackdrop,
        )
    } else {
        SettingsCompactLayout(
            configCount = configCount,
            mainPagerState = mainPagerState,
            navigationItems = navigationItems,
            snackbarHostState = snackbarHostState,
            useFloatingBottomBar = useFloatingBottomBar,
            floatingBottomBarMode = floatingBottomBarMode,
            floatingBackdrop = floatingBackdrop,
            miuixBackdrop = miuixBackdrop,
        )
    }
}
