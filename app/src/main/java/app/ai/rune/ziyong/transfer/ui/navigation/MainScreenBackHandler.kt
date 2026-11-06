// SPDX-License-Identifier: GPL-3.0-only

// Portions of this file are derived from weishu/KernelSU
// (https://github.com/tiann/KernelSU)
// Copyright (C) KernelSU contributors
// Licensed under GPL-3.0
package app.ai.rune.ziyong.transfer.ui.navigation

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.CancellationException
import top.yukonga.miuix.kmp.nav.gesture.PredictiveBackHandler

/**
 * 主页 tab 的返回：从 tab N 回到 tab 0。
 *
 * 必须用 [PredictiveBackHandler]（miuix nav 的，带手势进度）而不是
 * `NavigationBackHandler` / `androidx.activity.compose.BackHandler`：
 * 后两者只会在“抬手”时回调，拖动期间手势进度到不了 NavDisplay，
 * 结果就是“返回没有预览、瞬间跳页”。
 */
@Composable
fun MainScreenBackHandler(mainPagerState: MainPagerState, navController: Navigator) {
    val pagerState = mainPagerState.pagerState
    val density = LocalDensity.current

    val isPagerBackHandlerEnabled by remember(navController, pagerState) {
        derivedStateOf {
            navController.current() is Route.Main &&
                navController.backStackSize() == 1 &&
                pagerState.currentPage != 0
        }
    }

    PredictiveBackHandler(
        enabled = isPagerBackHandlerEnabled,
        onProgress = { events ->
            // 每一页的像素宽度（含 pageSpacing）；布局完成前为 0，需要跳过。
            val pageExtent = with(density) {
                (pagerState.layoutInfo.pageSize + pagerState.layoutInfo.pageSpacing).toFloat()
            }
            var applied = 0f
            try {
                events.collect { event ->
                    if (pageExtent > 0f) {
                        // progress: 0f（刚按下）→ 1f（手势完成）
                        val target = -event.progress * pageExtent
                        applied += pagerState.scrollBy(target - applied)
                    }
                }
                // 抬手且判定为“返回”：滚到首页
                pagerState.animateScrollToPage(0)
                mainPagerState.syncPage()
            } catch (cancel: CancellationException) {
                // 手势取消：回到最近整页
                pagerState.animateScrollToPage(pagerState.currentPage)
                throw cancel
            }
        },
        onCommit = {},
        onCancel = {},
    )
}
