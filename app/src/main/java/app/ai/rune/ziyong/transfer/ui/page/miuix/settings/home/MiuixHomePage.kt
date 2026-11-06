// SPDX-License-Identifier: GPL-3.0-only
package app.ai.rune.ziyong.transfer.ui.page.miuix.settings.home

import android.Manifest
import android.content.ClipData
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.ai.rune.ziyong.transfer.R
import app.ai.rune.ziyong.transfer.gateway.AppViewModel
import app.ai.rune.ziyong.transfer.gateway.AppUiState
import app.ai.rune.ziyong.transfer.gateway.formatCount
import app.ai.rune.ziyong.transfer.gateway.formatTokens
import app.ai.rune.ziyong.transfer.ui.icons.AppIcons
import app.ai.rune.ziyong.transfer.ui.theme.AppTheme
import app.ai.rune.ziyong.transfer.ui.theme.getMiuixAppBarColor
import app.ai.rune.ziyong.transfer.ui.theme.miuixBlurEffect
import app.ai.rune.ziyong.transfer.ui.theme.miuixHomeStatusCardColorAbnormal
import app.ai.rune.ziyong.transfer.ui.theme.miuixHomeStatusCardColorRunning
import app.ai.rune.ziyong.transfer.ui.theme.rememberMiuixBlurBackdrop
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import timber.log.Timber
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.MiuixTheme.isDynamicColor
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/**
 * 首页：网关控制台。
 *
 * 视觉骨架沿用 miuix-example 的状态卡片（大图标 + 容器色），
 * 但语义从「演示运行正常」换成真实的网关状态：
 * 运行中 = 端口正在监听、客户端可以调用；已停止 = 端口关闭。
 *
 * 卡片下方依次是：启停操作、接入地址、今日遥测。
 */
@Composable
fun MiuixHomePage(
    enableBlur: Boolean,
    viewModel: AppViewModel = koinViewModel(),
    title: String,
    outerPadding: PaddingValues,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val uiState by viewModel.state.collectAsStateWithLifecycle()
    val scrollBehavior = MiuixScrollBehavior()
    val layoutDirection = LocalLayoutDirection.current

    // Android 13+ 通知权限被拒时前台服务照常跑，但常驻通知会被系统静默隐藏，
    // 用户看起来就是「服务没起来」；启动前先补上授权请求。
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { viewModel.startGateway() }

    fun startWithPermission() {
        val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) {
            viewModel.startGateway()
        } else {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    fun copy(text: String) {
        scope.launch {
            runCatching { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("text", text))) }
        }
    }

    LaunchedEffect(Unit) {
        Timber.i("Home page shown")
    }

    val backdrop = rememberMiuixBlurBackdrop(enableBlur)

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                modifier = Modifier.miuixBlurEffect(backdrop),
                color = backdrop.getMiuixAppBarColor(),
                title = title,
                scrollBehavior = scrollBehavior,
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
                start = innerPadding.calculateStartPadding(layoutDirection) + 12.dp,
                top = innerPadding.calculateTopPadding() + 12.dp,
                end = innerPadding.calculateEndPadding(layoutDirection) + 12.dp,
                bottom = outerPadding.calculateBottomPadding(),
            ),
            overscrollEffect = null,
        ) {
            item {
                MiuixStatusCard(
                    running = uiState.running,
                    port = uiState.port,
                    usable = uiState.pool.usable,
                    total = uiState.pool.total,
                )
            }

            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                ) {
                    BasicComponent(
                        title = if (uiState.running) "停止网关" else "启动网关",
                        summary = if (uiState.running) {
                            "关闭本地端口，客户端将无法连接"
                        } else {
                            "开始监听端口，供客户端以 OpenAI 兼容接口调用"
                        },
                        onClick = {
                            if (uiState.running) viewModel.stopGateway() else startWithPermission()
                        },
                    )
                    BasicComponent(
                        title = "刷新全部额度",
                        summary = "向各上游重新拉取账号额度",
                        onClick = { viewModel.refreshAllCredits() },
                    )
                }
            }

            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                ) {
                    val localUrl = uiState.localUrl.ifEmpty { "http://127.0.0.1:${uiState.port}/v1" }
                    BasicComponent(
                        title = "本机接入地址",
                        summary = localUrl,
                        onClick = { copy(localUrl) },
                    )
                    val lanUrl = uiState.lanUrls.firstOrNull()
                    BasicComponent(
                        title = "局域网接入地址",
                        summary = lanUrl ?: "未连接 Wi-Fi（局域网不可用）",
                        // 没有局域网地址时不响应点击：点一个「不可用」的条目没有任何意义
                        onClick = lanUrl?.let { url -> { copy(url) } },
                    )
                }
            }

            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                ) {
                    TelemetryRows(uiState)
                }
            }

            item { Spacer(modifier = Modifier.navigationBarsPadding()) }
        }
    }
}

/** 今日遥测：把最常看的四个数字压进一张卡，避免首页堆满小卡片。 */
@Composable
private fun TelemetryRows(state: AppUiState) {
    BasicComponent(title = "今日请求", summary = "${formatCount(state.today.requests)} 次 · 失败 ${formatCount(state.today.failed)} 次")
    BasicComponent(
        title = "今日 Tokens",
        summary = "合计 ${formatTokens(state.today.totalTokens)}（输入 ${formatTokens(state.today.promptTokens)} · 输出 ${formatTokens(state.today.completionTokens)}）",
    )
    BasicComponent(
        title = "聚合模型",
        summary = when {
            state.modelsError.isNotEmpty() -> "拉取失败：${state.modelsError}"
            state.models.isEmpty() -> "尚未拉取模型目录"
            else -> "共 ${state.models.size} 个"
        },
    )
    BasicComponent(
        title = "已知额度",
        summary = if (state.pool.creditsKnown > 0) {
            "${formatCount(state.pool.totalCredits)}（已同步 ${state.pool.creditsKnown} 个账号）"
        } else {
            "尚未同步"
        },
    )
}

/**
 * 网关状态卡片（沿用模板视觉）。
 *
 * 与模板的差异只在数据来源：模板的 `isHealthy` 恒为 true（演示用），
 * 这里由真实的 `state.running` 驱动——运行中绿色、已停止红色。
 * 大图标的配色与容器色在动态取色开启时跟随主题，否则走固定的 MIUI 语义色。
 */
@Composable
private fun MiuixStatusCard(
    running: Boolean,
    port: Int,
    usable: Int,
    total: Int,
) {
    val containerColor = if (running) {
        when {
            isDynamicColor -> MiuixTheme.colorScheme.secondaryContainer
            AppTheme.isDark -> Color(0xFF1A3825)
            else -> Color(0xFFDFFAE4)
        }
    } else {
        when {
            isDynamicColor -> MiuixTheme.colorScheme.errorContainer
            AppTheme.isDark -> Color(0xFF381A1A)
            else -> Color(0xFFFAEEEE)
        }
    }

    val textContentColor = if (running) {
        if (isDynamicColor) MiuixTheme.colorScheme.onSecondaryContainer else MiuixTheme.colorScheme.onSurface
    } else {
        if (isDynamicColor) MiuixTheme.colorScheme.onErrorContainer else MiuixTheme.colorScheme.onSurface
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.defaultColors(color = containerColor),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 128.dp),
            ) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .offset(42.dp, 22.dp),
                    contentAlignment = Alignment.BottomEnd,
                ) {
                    Icon(
                        modifier = Modifier.size(124.dp),
                        imageVector = if (running) AppIcons.Active else Icons.Rounded.ErrorOutline,
                        tint = if (running) {
                            if (isDynamicColor) {
                                MiuixTheme.colorScheme.primary.copy(alpha = 0.8f)
                            } else {
                                miuixHomeStatusCardColorRunning
                            }
                        } else {
                            if (isDynamicColor) {
                                MiuixTheme.colorScheme.error.copy(alpha = 0.8f)
                            } else {
                                miuixHomeStatusCardColorAbnormal
                            }
                        },
                        contentDescription = null,
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 22.dp, vertical = 18.dp),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        modifier = Modifier.fillMaxWidth(),
                        text = if (running) "网关运行中" else "网关已停止",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = textContentColor,
                    )
                    Text(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        text = if (running) {
                            "监听 :$port · $usable/$total 个账号可用"
                        } else {
                            "端口未监听，客户端无法连接"
                        },
                        fontSize = 13.sp,
                        color = textContentColor.copy(alpha = 0.72f),
                    )
                }
            }
        }
    }
}
