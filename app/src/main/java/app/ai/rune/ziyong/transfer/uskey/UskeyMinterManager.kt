// SPDX-License-Identifier: GPL-3.0-only
package app.ai.rune.ziyong.transfer.uskey

import android.content.Context

/**
 * 内置 USKey 铸造器的门面（Application 级单例，由 Koin 注入）。
 *
 * 职责：
 * - 组合 [WebViewUskeyMinter]（铸造）与 [LocalUskeyService]（本机回环服务）；
 * - 懒启动：首次调用 [ensureStarted] 时才创建 WebView 与本地服务；
 * - 向外部暴露当前服务地址与服务状态，供账号配置与 UI 展示。
 *
 * 生命周期：随进程常驻，不显式销毁（App 杀死即回收）。
 * 若未来需要彻底释放（如用户删除全部元宝账号），可调用 [shutdown]。
 */
class UskeyMinterManager(
    context: Context,
) {
    private val appContext = context.applicationContext

    /** 铸造核心：WebView 懒创建。挂到当前 Activity 窗口上，保证 JS 常驻可执行。 */
    val minter: WebViewUskeyMinter by lazy {
        WebViewUskeyMinter(
            context = appContext,
            activityProvider = {
                (appContext as? app.ai.rune.ziyong.transfer.App)?.currentActivity
            },
        )
    }

    /** 本机回环服务：按需启动；铸造需要账号 Cookie 时从注册的提供者取。 */
    private val service by lazy { LocalUskeyService(minter) }

    @Volatile
    private var started = false

    /**
     * 启动内置铸造服务（幂等）。
     *
     * [cookieProvider] 提供元宝账号 Cookie：本地服务在每次铸造时询问当前应使用的 Cookie
     * （多账号场景取「当前配置了内置服务且启用的账号」的 Cookie，由调用方决定口径）。
     */
    fun ensureStarted(cookieProvider: () -> String): Boolean {
        if (started && service.isRunning) return true
        synchronized(this) {
            if (started && service.isRunning) return true
            service.start(cookieProvider)
            started = service.isRunning
        }
        return started
    }

    /** 服务是否在运行。 */
    val isRunning: Boolean get() = service.isRunning

    /** 内置服务地址（形如 http://127.0.0.1:PORT/uskey）；未启动返回 null。 */
    val serviceUrl: String? get() = if (service.isRunning) service.serviceUrl() else null

    /** 最近一次铸造的错误（供 UI 展示「为什么失败」）。 */
    fun lastError(): String = minter.lastError

    /** 停止服务并释放 WebView（删除全部元宝账号/退出时调用）。 */
    fun shutdown() {
        synchronized(this) {
            service.stop()
            minter.destroy()
            started = false
        }
    }

    /** 接管内置浏览器登录用过的 WebView（成为铸造器，无需重新加载页面）。 */
    fun adoptWebView(view: android.webkit.WebView, cookie: String) {
        minter.adopt(view, cookie)
    }
}