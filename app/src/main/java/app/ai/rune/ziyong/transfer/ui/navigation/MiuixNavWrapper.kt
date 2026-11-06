// SPDX-License-Identifier: GPL-3.0-only
package app.ai.rune.ziyong.transfer.ui.navigation

import android.os.Build
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.RoomPreferences
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.ai.rune.ziyong.transfer.R
import app.ai.rune.ziyong.transfer.domain.settings.model.preferences.ThemeState
import app.ai.rune.ziyong.transfer.ui.icons.AppIcons
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

    val configCount = 0 // Shell: example tabs carry no installer config count.
    val homeLabel = stringResource(R.string.home)
    val homeIcon = ImageVector.vectorResource(R.drawable.ic_tool)
    val configLabel = "供应商"
    val logLabel = stringResource(R.string.run_logs)
    val preferredLabel = stringResource(R.string.preferred)

    val navigationItems = remember(homeLabel, configLabel, logLabel, preferredLabel) {
        listOf(
            NavigationItem(
                label = homeLabel,
                icon = homeIcon,
            ),
            NavigationItem(
                label = configLabel,
                icon = Icons.Rounded.Storage,
            ),
            NavigationItem(
                label = logLabel,
                icon = AppIcons.Terminal,
            ),
            NavigationItem(
                label = preferredLabel,
                icon = Icons.Rounded.Settings,
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
