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
import androidx.compose.foundation.layout.Row
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
import top.yukonga.miuix.kmp.basic.Switch
import androidx.core.app.ActivityCompat
import app.ai.rune.ziyong.transfer.ui.page.miuix.settings.common.findActivity
import app.ai.rune.ziyong.transfer.ui.page.miuix.settings.common.SectionCard
import top.yukonga.miuix.kmp.basic.SmallTitle

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
    ) { granted ->
        if (granted) {
            viewModel.startGateway()
        } else {
            // 用户拒绝后如果还坚持启动，前台服务仍可运行（通知会被系统隐藏），
            // 但很多 ROM 会因此把服务判为不可见并很快回收，所以这里如实告知而不是静默失败。
            viewModel.notice("未授予通知权限：网关仍会尝试启动，但后台可能被系统回收；建议到「保活与权限」里开启")
            viewModel.startGateway()
        }
    }

    fun startWithPermission() {
        // API 33 以下没有 POST_NOTIFICATIONS 这个运行时权限，直接启动
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
        // 已经「拒绝且不再询问」时 launch 不会再弹窗、回调也不会触发，
        // 开关会看起来点了没反应。用 shouldShowRequestPermissionRationale 区分这两种情况：
        // false 且未授权 => 系统不会再问，只能引导去设置页手动开。
        val activity = context.findActivity()
        val canAskAgain = activity == null ||
            ActivityCompat.shouldShowRequestPermissionRationale(
                activity,
                Manifest.permission.POST_NOTIFICATIONS,
            )
        if (canAskAgain) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            viewModel.notice("通知权限已被永久拒绝，请到系统设置里手动开启；网关将先尝试启动")
            viewModel.startGateway()
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
                    modifier = Modifier.padding(bottom = 12.dp),
                )
            }

            // 总开关：紧跟状态卡，是进入本应用后第一个能操作的东西。
            // 用整行大开关而不是按钮——按钮只能表达动作，用户点完还得回头看卡片；
            // 开关把「当前状态」与「开关动作」合成一个控件。
            item {
                SectionCard(gap = 0.dp) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = "网关总开关",
                                style = MiuixTheme.textStyles.title4,
                                color = MiuixTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = if (uiState.running) {
                                    "已开启 · 正在监听 :${uiState.port}"
                                } else {
                                    "已关闭 · 客户端无法连接"
                                },
                                style = MiuixTheme.textStyles.footnote2,
                                color = if (uiState.running) {
                                    MiuixTheme.colorScheme.primary
                                } else {
                                    MiuixTheme.colorScheme.onSurfaceVariantSummary
                                },
                            )
                        }
                        Switch(
                            checked = uiState.running,
                            onCheckedChange = { on ->
                                if (on) startWithPermission() else viewModel.stopGateway()
                            },
                        )
                    }
                }
            }

            item { SmallTitle("接入信息") }
            item {
                SectionCard {
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
                        // 没有局域网地址时不响应点击：点一个「不可用」的条目没有意义
                        onClick = lanUrl?.let { url -> { copy(url) } },
                    )
                }
            }

            item { SmallTitle("今日用量") }
            item {
                SectionCard {
                    TelemetryRows(uiState)
                }
            }

            item { SmallTitle("快捷操作") }
            item {
                SectionCard {
                    BasicComponent(
                        title = "刷新全部额度",
                        summary = "向各上游重新拉取账号额度",
                        onClick = { viewModel.refreshAllCredits() },
                    )
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
    modifier: Modifier = Modifier,
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

    Column(modifier = modifier.fillMaxWidth()) {
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
