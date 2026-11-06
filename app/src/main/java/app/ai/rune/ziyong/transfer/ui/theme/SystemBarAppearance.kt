// SPDX-License-Identifier: GPL-3.0-only
package app.ai.rune.ziyong.transfer.ui.theme

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import timber.log.Timber

/**
 * 窗口级系统栏外观（不依赖 Compose 首帧）。
 *
 * 背景：App 的深色是**应用内设置**（ThemeMode），不是系统夜间模式。而
 *  - XML 主题 `Theme.MiuixExample` 在夜间资源里仍是 `Theme.Material.Light`（白 windowBackground +
 *    windowLightStatusBar=true）；
 *  - `enableEdgeToEdge()` 无参时用 `SystemBarStyle.auto(…)`，其 detectDarkMode 读的是
 *    `Configuration.uiMode`（系统夜间模式）。
 * 于是「App 强制深色 + 系统浅色」时，窗口一被创建就是「白底 + 深色图标」，直到 Compose 的
 * SideEffect 跑起来才修正。`recreate()`（切换语言）会把这个窗口期完整重放一遍，用户看到的就是
 * 「切完语言状态栏变白」。
 *
 * 修法：把 resolve 结果缓存下来，窗口创建时同步应用一次。
 */
object SystemBarAppearance {

    private const val PREFS_NAME = "window_appearance"
    private const val KEY_DARK = "is_dark"
    private const val KEY_SURFACE = "surface_argb"

    // 内存副本：recreate 时最准（进程级，不走磁盘）
    private var memoryDark: Boolean? = null
    private var memorySurfaceArgb: Int? = null

    /** Compose 侧在主题解析完成后调用，把结果钉住。 */
    fun remember(context: Context, isDark: Boolean, surfaceArgb: Int) {
        if (memoryDark == isDark && memorySurfaceArgb == surfaceArgb) return
        memoryDark = isDark
        memorySurfaceArgb = surfaceArgb
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_DARK, isDark)
            .putInt(KEY_SURFACE, surfaceArgb)
            .apply()
        Timber.d("windowAppearance: cache isDark=%s surface=%08X", isDark, surfaceArgb)
    }

    /** 进程内启动/重建早期可以同步读。 */
    fun isDark(context: Context): Boolean =
        memoryDark ?: prefs(context).getBoolean(KEY_DARK, systemIsDark(context))

    fun surfaceArgb(context: Context): Int =
        memorySurfaceArgb ?: prefs(context).getInt(
            KEY_SURFACE,
            if (isDark(context)) DARK_SURFACE_FALLBACK else LIGHT_SURFACE_FALLBACK,
        )

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun systemIsDark(context: Context): Boolean =
        (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
}

// `const val` 不允许调用 toInt()，这里退化成 val（编译期常量语义在此并不必要）。
private val DARK_SURFACE_FALLBACK: Int = 0xFF141218.toInt()
private val LIGHT_SURFACE_FALLBACK: Int = 0xFFFEF7FF.toInt()

/**
 * 一次性把「edge-to-edge + 窗口背景 + 系统栏图标明暗」全部定到 App 主题，而不是系统主题。
 *
 * 必须在 `super.onCreate()` 之后、`setContent { }` 之前调用（`enableEdgeToEdge` 的约束），
 * 且在主题变化时再次调用。
 */
fun ComponentActivity.applyWindowAppearance(isDark: Boolean, surfaceArgb: Int) {
    // 1) 用自己的深色判断喂给 enableEdgeToEdge（默认实现只看系统 uiMode）
    val barStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { isDark }
    enableEdgeToEdge(
        statusBarStyle = barStyle,
        navigationBarStyle = barStyle,
    )

    // 2) 窗口背景 = App 的 surface，inset 区域（状态栏/导航栏后面）永远不会露出
    //    Theme.Material.Light 的白色 windowBackground
    window.setBackgroundDrawable(ColorDrawable(surfaceArgb))

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        window.isStatusBarContrastEnforced = false
        window.isNavigationBarContrastEnforced = false
    }

    // 3) 图标明暗：立刻生效，不等 Compose
    val controller = WindowCompat.getInsetsController(window, window.decorView)
    controller.isAppearanceLightStatusBars = !isDark
    controller.isAppearanceLightNavigationBars = !isDark
    Timber.d(
        "windowAppearance: apply isDark=%s surface=%08X lightBars=%s",
        isDark,
        surfaceArgb,
        !isDark,
    )
}
