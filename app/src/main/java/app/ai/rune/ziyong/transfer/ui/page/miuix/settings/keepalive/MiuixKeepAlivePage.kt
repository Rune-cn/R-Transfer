// SPDX-License-Identifier: GPL-3.0-only
package app.ai.rune.ziyong.transfer.ui.page.miuix.settings.keepalive

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.ai.rune.ziyong.transfer.gateway.AppViewModel
import app.ai.rune.ziyong.transfer.gateway.KeepAlive
import app.ai.rune.ziyong.transfer.gateway.service.FloatingKeepAlive
import app.ai.rune.ziyong.transfer.ui.theme.getMiuixAppBarColor
import app.ai.rune.ziyong.transfer.ui.theme.miuixBlurEffect
import app.ai.rune.ziyong.transfer.ui.theme.rememberMiuixBlurBackdrop
import org.koin.androidx.compose.koinViewModel
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import app.ai.rune.ziyong.transfer.ui.page.miuix.settings.common.SectionCard

/**
 * 保活与权限：让网关在锁屏后仍能被客户端连上。
 *
 * 为什么这一页重要：前台服务本身不会因为锁屏而停，但系统若把应用放进
 * 电池优化名单、限制后台运行或开启流量节省，进程会被冻结、端口就调不通。
 * 用户看到的现象是「客户端突然连不上」，却很难联想到是系统策略——
 * 所以这里把每项条件单独检测并给出直达设置的入口。
 *
 * 从系统设置页返回时（ON_RESUME）会自动重检，用户不必手动刷新。
 */
@Composable
fun MiuixKeepAlivePage(
    viewModel: AppViewModel = koinViewModel(),
    onBack: () -> Unit,
    outerPadding: PaddingValues,
) {
    val context = LocalContext.current
    val uiState by viewModel.state.collectAsStateWithLifecycle()
    val status = uiState.keepAlive
    val scrollBehavior = MiuixScrollBehavior()
    val layoutDirection = LocalLayoutDirection.current
    val backdrop = rememberMiuixBlurBackdrop(false)

    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { viewModel.refresh() }

    // 从系统授权页返回时重检并补显示悬浮窗：用户授权后回到本页，开关无需重新点一遍
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.reapplyOverlayIfEnabled()
                viewModel.refresh()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                modifier = Modifier.miuixBlurEffect(backdrop),
                color = backdrop.getMiuixAppBarColor(),
                title = "保活与权限",
                subtitle = if (status?.allGood == true) "已就绪" else "有待处理项",
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = MiuixIcons.Regular.Back,
                            contentDescription = "返回",
                        )
                    }
                },
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
                top = innerPadding.calculateTopPadding() + 8.dp,
                end = innerPadding.calculateEndPadding(layoutDirection) + 12.dp,
                bottom = outerPadding.calculateBottomPadding(),
            ),
            overscrollEffect = null,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                SectionCard() {
                    BasicComponent(
                        title = if (status?.allGood == true) "后台条件已就绪" else "还有条件未满足",
                        summary = "通知权限 + 电池优化白名单 + 未被限制后台 + 未开启流量节省，" +
                            "全部满足后锁屏也能稳定调用。",
                    )
                    if (!uiState.running) {
                        BasicComponent(
                            title = "网关未启动",
                            summary = "先去首页启动网关，否则锁屏后没有进程监听端口。",
                        )
                    }
                }
            }

            item { SmallTitle("系统条件") }
            item {
                SectionCard() {
                    PermissionRow(
                        title = "通知权限",
                        detail = if (status?.notificationsGranted == true) {
                            "已允许，常驻通知可见"
                        } else {
                            "未允许，后台通知不会显示（前台服务仍会运行）"
                        },
                        ok = status?.notificationsGranted == true,
                        actionLabel = "去开启",
                        onAction = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                launch(context, KeepAlive.notificationSettingsIntent(context), "无法打开通知设置")
                            }
                        },
                    )
                    PermissionRow(
                        title = "电池优化",
                        detail = if (status?.batteryUnrestricted == true) {
                            "已加入白名单（不限制耗电）"
                        } else {
                            "受电池优化限制，锁屏后可能被冻结、端口调不通"
                        },
                        ok = status?.batteryUnrestricted == true,
                        actionLabel = "不限制",
                        onAction = {
                            // 部分 ROM 不支持直接请求，失败就退回到电池优化列表页
                            if (!launch(context, KeepAlive.batteryOptimizationIntent(context), null)) {
                                launch(context, KeepAlive.batterySettingsIntent(), "无法打开电池优化设置")
                            }
                        },
                    )
                    PermissionRow(
                        title = "后台运行限制",
                        detail = if (status?.backgroundRestricted == true) {
                            "系统已限制本应用后台运行"
                        } else {
                            "未被系统限制后台运行"
                        },
                        ok = status?.backgroundRestricted != true,
                        actionLabel = "去设置",
                        onAction = { launch(context, KeepAlive.appDetailsIntent(context), "无法打开应用详情") },
                    )
                    PermissionRow(
                        title = "流量节省",
                        detail = if (status?.dataSaverRestricted == true) {
                            "已开启流量节省，后台联网可能受限"
                        } else {
                            "未开启流量节省"
                        },
                        ok = status?.dataSaverRestricted != true,
                        actionLabel = "去设置",
                        onAction = { launch(context, KeepAlive.appDetailsIntent(context), "无法打开应用详情") },
                    )
                }
            }

            item { SmallTitle("保活方式") }
            item {
                SectionCard() {
                    BasicComponent(
                        title = "开机自动恢复",
                        summary = if (uiState.startOnBoot) {
                            "已开启：重启或应用更新后自动拉起网关"
                        } else {
                            "已关闭：重启后不会自动恢复"
                        },
                        onClick = { viewModel.setStartOnBoot(!uiState.startOnBoot) },
                    )
                    BasicComponent(
                        title = "悬浮窗保活",
                        summary = when {
                            !uiState.overlayKeepAlive -> "未开启：系统更容易在后台回收进程"
                            status?.overlayGranted != true -> "已开启，但缺少「显示在其他应用上层」权限"
                            else -> "已开启：屏幕上保留一个小标签，降低被回收的概率"
                        },
                        onClick = { viewModel.setOverlayKeepAlive(!uiState.overlayKeepAlive) },
                    )
                    if (status?.overlayGranted != true) {
                        TextButton(
                            text = "授予悬浮窗权限",
                            onClick = {
                                launch(
                                    context,
                                    FloatingKeepAlive.permissionIntent(context),
                                    "无法打开悬浮窗权限设置",
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            item { SmallTitle("厂商后台管理") }
            item {
                SectionCard() {
                    Text(
                        text = "国产系统默认会限制第三方应用后台。下面的入口会尝试打开厂商的后台管理页；" +
                            "打不开就按下方说明手动设置。",
                        style = MiuixTheme.textStyles.footnote2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    TextButton(
                        text = "打开厂商后台设置",
                        onClick = {
                            val opened = KeepAlive.autoStartIntents().any { launch(context, it, null) }
                            if (!opened) {
                                launch(context, KeepAlive.appDetailsIntent(context), "无法打开系统页面")
                            }
                            Toast.makeText(
                                context,
                                KeepAlive.manualHint(Build.MANUFACTURER),
                                Toast.LENGTH_LONG,
                            ).show()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = KeepAlive.manualHint(Build.MANUFACTURER),
                        style = MiuixTheme.textStyles.footnote2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            item {
                Text(
                    text = "说明：本应用用「常驻前台服务 + 通知」保持进程存活，并持有唤醒锁与 Wi-Fi 锁，" +
                        "锁屏后 CPU/Wi-Fi 不会被省电挂起。若系统仍把进程冻结，客户端会连接失败——" +
                        "按上面逐条处理即可。",
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }

            item { Spacer(modifier = Modifier.navigationBarsPadding()) }
        }
    }
}

/** 一项权限状态：标题 + 说明（不满足时变红）+ 直达设置的按钮。 */
@Composable
private fun PermissionRow(
    title: String,
    detail: String,
    ok: Boolean,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MiuixTheme.textStyles.body1,
                color = MiuixTheme.colorScheme.onSurface,
            )
            Text(
                text = detail,
                style = MiuixTheme.textStyles.footnote2,
                color = if (ok) {
                    MiuixTheme.colorScheme.onSurfaceVariantSummary
                } else {
                    MiuixTheme.colorScheme.error
                },
            )
        }
        TextButton(
            text = actionLabel,
            onClick = onAction,
            minWidth = 0.dp,
        )
    }
}

/**
 * 打开系统页面。
 *
 * 部分 ROM 不实现这些 Action，startActivity 会抛异常；
 * 返回 false 让调用方退回到其它入口，而不是静默失败。
 */
private fun launch(context: Context, intent: Intent, failureMessage: String?): Boolean = try {
    context.startActivity(intent)
    true
} catch (_: Exception) {
    if (failureMessage != null) {
        Toast.makeText(context, failureMessage, Toast.LENGTH_SHORT).show()
    }
    false
}
