// SPDX-License-Identifier: GPL-3.0-only
package app.ai.rune.ziyong.transfer.ui.activity

import app.ai.rune.ziyong.transfer.core.i18n.LocaleManager

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import app.ai.rune.ziyong.transfer.R
import app.ai.rune.ziyong.transfer.domain.settings.model.preferences.ThemeState
import app.ai.rune.ziyong.transfer.domain.settings.provider.ThemeStateProvider
import app.ai.rune.ziyong.transfer.domain.settings.repository.AppSettingsRepository
import app.ai.rune.ziyong.transfer.domain.updater.repository.UpdateRepository
import kotlinx.coroutines.flow.first
import app.ai.rune.ziyong.transfer.ui.navigation.AppNavContainer
import app.ai.rune.ziyong.transfer.ui.theme.AppTheme
import app.ai.rune.ziyong.transfer.ui.theme.LocalWindowLayoutInfo
import app.ai.rune.ziyong.transfer.ui.theme.SystemBarAppearance
import app.ai.rune.ziyong.transfer.ui.theme.applyWindowAppearance
import app.ai.rune.ziyong.transfer.ui.theme.rememberWindowLayoutInfo
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import timber.log.Timber
import top.yukonga.miuix.kmp.theme.MiuixTheme

class SettingsActivity :
    ComponentActivity(),
    KoinComponent {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleManager.wrap(newBase))
    }

    private val themeStateProvider by inject<ThemeStateProvider>()

    private val updateRepo by inject<UpdateRepository>()
    private val appSettingsRepo by inject<AppSettingsRepository>()

    // legado-style: the startup update check runs at most once per process.
    private var startupUpdateChecked = false

    override fun onCreate(savedInstanceState: Bundle?) {
        // 1) 只有冷启动才装 splash；recreate（切语言）不重装，
        //    避免又用 windowSplashScreenBackground（浅色）遮一遍。
        //    （installSplashScreen 必须在 super.onCreate 之前）
        val splashScreen = if (savedInstanceState == null) installSplashScreen() else null

        var isThemeLoaded = savedInstanceState != null
        splashScreen?.setKeepOnScreenCondition { !isThemeLoaded }

        super.onCreate(savedInstanceState)

        Timber.i("SettingsActivity created (cold start=%s)", savedInstanceState == null)

        // 2) 用“上次解析好的”外观把窗口定住：这两步都在 Compose 之前，
        //    recreate() 之后状态栏不会再变白/变浅。
        //    （applyWindowAppearance 内部会调用 enableEdgeToEdge，所以不能再有无参 enableEdgeToEdge()）
        applyWindowAppearance(
            isDark = SystemBarAppearance.isDark(this),
            surfaceArgb = SystemBarAppearance.surfaceArgb(this),
        )

        autoCheckUpdateOnStart()

        // 首帧前就能拿到的兜底底色（同步读，不依赖 DataStore）
        val cachedSurface = Color(SystemBarAppearance.surfaceArgb(this))

        setContent {
            val uiState by themeStateProvider.themeStateFlow.collectAsStateWithLifecycle(initialValue = ThemeState())
            isThemeLoaded = uiState.isLoaded

            if (!uiState.isLoaded) {
                // 状态还没到：绝不能让窗口露出 Theme.Material.Light 的白底
                Box(Modifier.fillMaxSize().background(cachedSurface))
                return@setContent
            }

            val layoutInfo = rememberWindowLayoutInfo()

            CompositionLocalProvider(
                LocalWindowLayoutInfo provides layoutInfo,
            ) {
                AppTheme(
                    useMiuix = uiState.useMiuix,
                    themeMode = uiState.themeMode,
                    paletteStyle = uiState.paletteStyle,
                    colorSpec = uiState.colorSpec,
                    useDynamicColor = uiState.useDynamicColor,
                    useMiuixMonet = uiState.useMiuixMonet,
                    seedColor = androidx.compose.ui.graphics.Color(uiState.seedColor),
                ) {
                    val backgroundColor =
                        MiuixTheme.colorScheme.surface
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(backgroundColor),
                    ) {
                        AppNavContainer(uiState)
                    }
                }
            }
        }
    }

    /**
     * legado-style: check for updates only once per process start,
     * and only when the setting is enabled.
     */
    private fun autoCheckUpdateOnStart() {
        if (startupUpdateChecked) return
        startupUpdateChecked = true
        lifecycleScope.launch {
            val enabled = appSettingsRepo.preferencesFlow.first().autoCheckUpdateOnStart
            Timber.i("Startup update check: autoCheckUpdateOnStart=%s", enabled)
            if (enabled) {
                updateRepo.checkUpdate()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Timber.i("SettingsActivity resumed")
    }
}
