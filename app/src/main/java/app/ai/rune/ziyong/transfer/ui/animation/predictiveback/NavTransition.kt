// SPDX-License-Identifier: GPL-3.0-only
package app.ai.rune.ziyong.transfer.ui.animation.predictiveback

import app.ai.rune.ziyong.transfer.domain.settings.model.preferences.PredictiveBackAnimation
import app.ai.rune.ziyong.transfer.domain.settings.model.preferences.PredictiveBackExitDirection
import top.yukonga.miuix.kmp.nav.transition.NavTransition
import top.yukonga.miuix.kmp.nav.transition.NavTransitions

/**
 * 把用户选的「预测式返回动画」映射成 miuix-nav 的 [NavTransition]。
 *
 * ⚠️ `None` 的语义（A4）：
 *
 * 之前 `AppNavContainer` 在 `None` 模式下注册了 entry 级
 * `NavigationBackHandler(isBackEnabled = true)`，它会在手指按下时就抢走手势，
 * 于是 [NoPredictiveBackTransition] 精心写的「grab anchor」逻辑成了**死代码**。
 * 拦截器拆掉之后，`None` 由库的 `predictivePop` 分支（`isGestureDriven(scope)`）
 * 真正驱动，恢复到它本来的语义：
 *
 * * 拖动期间：`noPredictiveVisualProgress()` 让视觉停在「抓取锚点」上——**不跟手**
 *   （这就是「无预测式预览动画」的字面语义）；
 * * 抬起（Commit）：从锚点起播 `NoPredictivePop`，不再是一次莫名其妙的 programmatic pop；
 * * 取消（Cancel）：从锚点回弹，不会把「进行中的 push」打断成半截。
 *
 * 如果产品上要求「`None` 也要能拉一半看到预览」，只需把下面这行改成
 * `PredictiveBackAnimation.None -> NavTransitions.MiuixDefault`（跟手位移 + 边缘阴影），
 * 代价是 `NoPredictiveBackTransition` 变成不可达代码。
 */
fun appNavTransition(animation: PredictiveBackAnimation, exitDirection: PredictiveBackExitDirection): NavTransition = when (animation) {
    // 保留 NoPredictiveBackTransition：尊重“无预测式预览动画”的选项语义（不跟手，仅松手后播放）。
    // 「None 也要跟手预览」的开关：把本行替换为 `NavTransitions.MiuixDefault`。
    PredictiveBackAnimation.None -> NoPredictiveBackTransition
    PredictiveBackAnimation.MIUIX -> NavTransitions.MiuixDefault
    PredictiveBackAnimation.AOSP -> AospNavTransition
    PredictiveBackAnimation.Scale -> scaleNavTransition(exitDirection)
    PredictiveBackAnimation.Classic -> ClassicNavTransition
}
