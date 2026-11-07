// SPDX-License-Identifier: GPL-3.0-only
package app.ai.rune.ziyong.transfer.ui.page.miuix.settings.home

import android.Manifest
import android.content.ClipData
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.ai.rune.ziyong.transfer.gateway.AppViewModel
import app.ai.rune.ziyong.transfer.gateway.formatCount
import app.ai.rune.ziyong.transfer.gateway.formatTokens
import app.ai.rune.ziyong.transfer.ui.icons.AppIcons
import app.ai.rune.ziyong.transfer.ui.page.miuix.settings.common.NavigationRow
import app.ai.rune.ziyong.transfer.ui.page.miuix.settings.common.PreferenceCard
import app.ai.rune.ziyong.transfer.ui.page.miuix.settings.common.PreferenceHint
import app.ai.rune.ziyong.transfer.ui.page.miuix.settings.common.SwitchRow
import app.ai.rune.ziyong.transfer.ui.page.miuix.settings.common.findActivity
import app.ai.rune.ziyong.transfer.ui.theme.AppTheme
import app.ai.rune.ziyong.transfer.ui.theme.getMiuixAppBarColor
import app.ai.rune.ziyong.transfer.ui.theme.miuixBlurEffect
import app.ai.rune.ziyong.transfer.ui.theme.miuixHomeStatusCardColorAbnormal
import app.ai.rune.ziyong.transfer.ui.theme.miuixHomeStatusCardColorRunning
import app.ai.rune.ziyong.transfer.ui.theme.rememberMiuixBlurBackdrop
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import timber.log.Timber
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.MiuixTheme.isDynamicColor
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/**
 * 首页：网关控制台。
 *
 * 排版严格对齐模板的「个性化」「关于」页：
 * `Spacer(12dp)` → `SmallTitle(分组)` → `PreferenceCard { 各种 Preference 行 }`。
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
    val backdrop = rememberMiuixBlurBackdrop(enableBlur)

    // 通知权限：被永久拒绝时 launch 不再弹窗，回调也不触发，开关会看起来没反应
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (!granted) {
            viewModel.notice("未授予通知权限：网关仍会尝试启动，但后台可能被系统回收")
        }
        viewModel.startGateway()
    }

    fun startWithPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            viewModel.startGateway()
            return
        }
        val granted = context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) {
            viewModel.startGateway()
            return
        }
        val activity = context.findActivity()
        val canAskAgain = activity == null ||
            ActivityCompat.shouldShowRequestPermissionRationale(
                activity,
                Manifest.permission.POST_NOTIFICATIONS,
            )
        if (canAskAgain) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            viewModel.notice("通知权限已被永久拒绝，请到系统设置里手动开启")
            viewModel.startGateway()
        }
    }

    fun copy(text: String) {
        scope.launch {
            runCatching { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("text", text))) }
        }
    }

    // 启动失败原因常驻显示：Snackbar 会消失，而「为什么打不开」需要留着看
    var lastStartError by remember { mutableStateOf("") }
    LaunchedEffect(uiState.notice) {
        val message = uiState.notice
        if (message.startsWith("启动失败：")) {
            lastStartError = message.removePrefix("启动失败：")
        }
    }
    LaunchedEffect(uiState.running) {
        if (uiState.running) lastStartError = ""
    }

    LaunchedEffect(Unit) {
        Timber.i("Home page shown")
    }

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
                start = innerPadding.calculateStartPadding(layoutDirection),
                top = innerPadding.calculateTopPadding(),
                end = innerPadding.calculateEndPadding(layoutDirection),
                bottom = outerPadding.calculateBottomPadding(),
            ),
            overscrollEffect = null,
        ) {
            item { Spacer(modifier = Modifier.size(12.dp)) }
            item {
                MiuixStatusCard(
                    running = uiState.running,
                    port = uiState.port,
                    usable = uiState.pool.usable,
                    total = uiState.pool.total,
                )
            }

            // 总开关：模板没有对应控件，但语义上属于「设置行 + 开关」，
            // 因此用 SwitchPreference 而非自绘 Row，保持与其余开关行一致。
            item { SmallTitle("网关") }
            item {
                PreferenceCard {
                    SwitchRow(
                        title = "网关总开关",
                        summary = if (uiState.running) {
                            "已开启 · 正在监听 :${uiState.port}"
                        } else {
                            "已关闭 · 客户端无法连接"
                        },
                        checked = uiState.running,
                        onCheckedChange = { on ->
                            if (on) startWithPermission() else viewModel.stopGateway()
                        },
                    )
                    AnimatedVisibility(
                        visible = lastStartError.isNotEmpty(),
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically(),
                    ) {
                        PreferenceHint("启动失败：$lastStartError", error = true)
                    }
                    AnimatedVisibility(
                        visible = uiState.busy.isNotEmpty(),
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically(),
                    ) {
                        PreferenceHint(uiState.busy)
                    }
                    NavigationRow(
                        title = "刷新全部额度",
                        summary = "向各上游重新拉取账号额度",
                        onClick = { viewModel.refreshAllCredits() },
                    )
                }
            }

            item { SmallTitle("接入信息") }
            item {
                PreferenceCard {
                    val localUrl = uiState.localUrl.ifEmpty { "http://127.0.0.1:${uiState.port}/v1" }
                    NavigationRow(
                        title = "本机接入地址",
                        summary = localUrl,
                        onClick = { copy(localUrl) },
                    )
                    val lanUrl = uiState.lanUrls.firstOrNull()
                    NavigationRow(
                        title = "局域网接入地址",
                        summary = lanUrl ?: "未连接 Wi-Fi（局域网不可用）",
                        // 没有地址时不响应点击：点一个「不可用」的条目没有意义
                        onClick = { lanUrl?.let { copy(it) } },
                    )
                }
            }

            item { SmallTitle("今日用量") }
            item {
                PreferenceCard {
                    NavigationRow(
                        title = "今日请求",
                        summary = "${formatCount(uiState.today.requests)} 次 · 失败 ${formatCount(uiState.today.failed)} 次",
                        onClick = {},
                    )
                    NavigationRow(
                        title = "今日 Tokens",
                        summary = "合计 ${formatTokens(uiState.today.totalTokens)}" +
                            "（输入 ${formatTokens(uiState.today.promptTokens)} · " +
                            "输出 ${formatTokens(uiState.today.completionTokens)}）",
                        onClick = {},
                    )
                    NavigationRow(
                        title = "聚合模型",
                        summary = when {
                            uiState.modelsError.isNotEmpty() -> "拉取失败：${uiState.modelsError}"
                            uiState.models.isEmpty() -> "尚未拉取模型目录"
                            else -> "共 ${uiState.models.size} 个"
                        },
                        onClick = {},
                    )
                    NavigationRow(
                        title = "已知额度",
                        summary = if (uiState.pool.creditsKnown > 0) {
                            "${formatCount(uiState.pool.totalCredits)}（已同步 ${uiState.pool.creditsKnown} 个账号）"
                        } else {
                            "尚未同步"
                        },
                        onClick = {},
                    )
                }
            }

            item { Spacer(modifier = Modifier.navigationBarsPadding()) }
        }
    }
}

/**
 * 网关状态卡片（沿用模板的视觉：大图标 + 容器色）。
 *
 * 与模板的差异只在数据来源：模板的 `isHealthy` 恒为 true（演示用），
 * 这里由真实的 `state.running` 驱动。
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
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
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
        Spacer(modifier = Modifier.size(12.dp))
    }
}
