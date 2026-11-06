// SPDX-License-Identifier: GPL-3.0-only
package app.ai.rune.ziyong.transfer.ui.navigation

import timber.log.Timber

import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.ai.rune.ziyong.transfer.domain.settings.model.preferences.PredictiveBackAnimation
import app.ai.rune.ziyong.transfer.domain.settings.model.preferences.ThemeState
import app.ai.rune.ziyong.transfer.ui.animation.predictiveback.appNavTransition
import app.ai.rune.ziyong.transfer.ui.page.main.settings.SettingsSharedViewModel
import app.ai.rune.ziyong.transfer.ui.page.miuix.settings.preferred.about.MiuixAboutPage
import app.ai.rune.ziyong.transfer.ui.page.miuix.settings.preferred.theme.MiuixThemeSettingsPage
import app.ai.rune.ziyong.transfer.ui.util.rememberDeviceCornerRadius
import org.koin.androidx.compose.koinViewModel
import top.yukonga.miuix.kmp.nav.core.NavCornerClipMode
import top.yukonga.miuix.kmp.nav.core.NavDisplay
import top.yukonga.miuix.kmp.nav.core.NavDisplayEffects
import top.yukonga.miuix.kmp.nav.core.rememberNavBackStack
import top.yukonga.miuix.kmp.nav.transition.NavSwipeDirection
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun AppNavContainer(uiState: ThemeState) {
    val sharedViewModel: SettingsSharedViewModel = koinViewModel(
        viewModelStoreOwner = LocalActivity.current as ComponentActivity,
    )

    val backStack = rememberNavBackStack<Route>(Route.Main)
    val navigator = remember(backStack) { Navigator(backStack) }
    val onBack = remember(navigator) { { navigator.pop() } }
    val useBlur = uiState.useBlur

    CompositionLocalProvider(
        LocalNavigator provides navigator,
    ) {
        val navCornerRadius = rememberDeviceCornerRadius(defaultRadius = 0.dp)
        val roundAllCorners = uiState.predictiveBackAnimation == PredictiveBackAnimation.AOSP ||
            uiState.predictiveBackAnimation == PredictiveBackAnimation.Scale ||
            uiState.predictiveBackAnimation == PredictiveBackAnimation.Classic
        val backdropColor = MiuixTheme.colorScheme.surface
        val effects = remember(navCornerRadius, roundAllCorners, backdropColor) {
            NavDisplayEffects(
                enableCornerClip = true,
                cornerClipRadius = if (roundAllCorners && navCornerRadius <= 0.dp) 32.dp else navCornerRadius,
                cornerClipMode = if (roundAllCorners) NavCornerClipMode.All else NavCornerClipMode.Leading,
                dimAmount = 0.5f,
                backdropColor = backdropColor,
                // 拖动过程中不要吞输入，否则手势到一半会“卡住”手感
                blockInputDuringTransition = false,
            )
        }
        val transition = remember(uiState.predictiveBackAnimation, uiState.predictiveBackExitDirection) {
            appNavTransition(
                animation = uiState.predictiveBackAnimation,
                exitDirection = uiState.predictiveBackExitDirection,
            )
        }
        val swipeBackDirection = when (LocalLayoutDirection.current) {
            LayoutDirection.Rtl -> NavSwipeDirection.RightToLeft
            LayoutDirection.Ltr -> NavSwipeDirection.LeftToRight
        }

        // 诊断日志：size<=1 时 NavDisplay 的预测式返回驱动是关闭的（系统接管退出动画）。
        Timber.d(
            "predictiveBack: animation=%s size=%d gestureDriver=%s",
            uiState.predictiveBackAnimation,
            backStack.size,
            backStack.size > 1,
        )

        // ⚠️ 这里不再注册任何 entry 级 NavigationBackHandler / BackHandler。
        // NavDisplay 内部的 PredictiveBackHandlerWithSessions(enabled = size > 1) 才是唯一的
        // 手势驱动者；entry 内任何 enabled 的返回处理器都会抢走手势（= 无预览）。
        NavDisplay(
            backStack = backStack,
            onBack = onBack,
            transition = transition,
            effects = effects,
        ) {
            entry<Route.Main> {
                MiuixMainPageWrapper(uiState, sharedViewModel)
            }
            entry<Route.About>(swipeDismiss = swipeBackDirection) {
                MiuixAboutPage(useBlur)
            }
            entry<Route.Theme>(swipeDismiss = swipeBackDirection) {
                MiuixThemeSettingsPage()
            }
            // Shell mode: 其它页面已移除（本工程为 miuix-example 空壳工程）。
        }
    }
}
