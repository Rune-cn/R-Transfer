// SPDX-License-Identifier: GPL-3.0-only
package app.ai.rune.ziyong.transfer.ui.page.main.settings.home

class HomePageViewState(
    val deviceName: String = "",
    /**
     * Runtime health of this demo shell (NOT installer activation).
     *
     * The shell always runs normally by default; the field and the "abnormal"
     * branch are kept so a real health check can drive the status card later.
     */
    val isRunningNormally: Boolean = true,
) {
    val isHealthy: Boolean get() = isRunningNormally
}