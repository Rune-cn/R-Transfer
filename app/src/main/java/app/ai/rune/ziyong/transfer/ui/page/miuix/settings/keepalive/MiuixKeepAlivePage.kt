// SPDX-License-Identifier: GPL-3.0-only
package app.ai.rune.ziyong.transfer.ui.page.miuix.settings.keepalive

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
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
import app.ai.rune.ziyong.transfer.ui.page.miuix.settings.common.NavigationRow
import app.ai.rune.ziyong.transfer.ui.page.miuix.settings.common.PreferenceActionRow
import app.ai.rune.ziyong.transfer.ui.page.miuix.settings.common.PreferenceCard
import app.ai.rune.ziyong.transfer.ui.page.miuix.settings.common.PreferenceHint
import app.ai.rune.ziyong.transfer.ui.page.miuix.settings.common.SwitchRow
import app.ai.rune.ziyong.transfer.ui.theme.getMiuixAppBarColor
import app.ai.rune.ziyong.transfer.ui.theme.miuixBlurEffect
import app.ai.rune.ziyong.transfer.ui.theme.rememberMiuixBlurBackdrop
import org.koin.androidx.compose.koinViewModel
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/**
 * 保活与权限：让网关在锁屏后仍能被客户端连上。
 *
 * 排版对齐模板「关于」页：SmallTitle 分组 + PreferenceCard。
 * 四项系统条件各用一行 NavigationRow（不满足时 summary 里说明原因，右侧箭头即「去设置」）。
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

    // 从系统授权页返回时重检并补显示悬浮窗，用户不必手动刷新
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
                start = innerPadding.calculateStartPadding(layoutDirection),
                top = innerPadding.calculateTopPadding(),
                end = innerPadding.calculateEndPadding(layoutDirection),
                bottom = outerPadding.calculateBottomPadding(),
            ),
            overscrollEffect = null,
        ) {
            item { Spacer(modifier = Modifier.size(12.dp)) }

            item { SmallTitle("概览") }
            item {
                PreferenceCard {
                    PreferenceHint(
                        "通知权限 + 电池优化白名单 + 未被限制后台 + 未开启流量节省，" +
                            "全部满足后锁屏也能稳定调用。",
                    )
                    // 网关未启动时给出醒目标注：出现/消失带动画
                    AnimatedVisibility(
                        visible = !uiState.running,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically(),
                    ) {
                        PreferenceHint("网关未启动：先去首页启动，否则锁屏后没有进程监听端口。", error = true)
                    }
                }
            }

            item { SmallTitle("系统条件") }
            item {
                PreferenceCard {
                    NavigationRow(
                        title = "通知权限",
                        summary = if (status?.notificationsGranted == true) {
                            "已允许，常驻通知可见"
                        } else {
                            "未允许，后台通知不会显示（前台服务仍会运行）"
                        },
                        onClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                launch(context, KeepAlive.notificationSettingsIntent(context), "无法打开通知设置")
                            }
                        },
                    )
                    NavigationRow(
                        title = "电池优化",
                        summary = if (status?.batteryUnrestricted == true) {
                            "已加入白名单（不限制耗电）"
                        } else {
                            "受电池优化限制，锁屏后可能被冻结、端口调不通"
                        },
                        onClick = {
                            // 部分 ROM 不支持直接请求，失败就退回到电池优化列表页
                            if (!launch(context, KeepAlive.batteryOptimizationIntent(context), null)) {
                                launch(context, KeepAlive.batterySettingsIntent(), "无法打开电池优化设置")
                            }
                        },
                    )
                    NavigationRow(
                        title = "后台运行限制",
                        summary = if (status?.backgroundRestricted == true) {
                            "系统已限制本应用后台运行"
                        } else {
                            "未被系统限制后台运行"
                        },
                        onClick = { launch(context, KeepAlive.appDetailsIntent(context), "无法打开应用详情") },
                    )
                    NavigationRow(
                        title = "流量节省",
                        summary = if (status?.dataSaverRestricted == true) {
                            "已开启流量节省，后台联网可能受限"
                        } else {
                            "未开启流量节省"
                        },
                        onClick = { launch(context, KeepAlive.appDetailsIntent(context), "无法打开应用详情") },
                    )
                }
            }

            item { SmallTitle("保活方式") }
            item {
                PreferenceCard {
                    SwitchRow(
                        title = "开机自动恢复",
                        summary = if (uiState.startOnBoot) {
                            "已开启：重启或应用更新后自动拉起网关"
                        } else {
                            "已关闭：重启后不会自动恢复"
                        },
                        checked = uiState.startOnBoot,
                        onCheckedChange = { viewModel.setStartOnBoot(it) },
                    )
                    SwitchRow(
                        title = "悬浮窗保活",
                        summary = when {
                            !uiState.overlayKeepAlive -> "未开启：系统更容易在后台回收进程"
                            status?.overlayGranted != true -> "已开启，但缺少「显示在其他应用上层」权限"
                            else -> "已开启：屏幕上保留一个小标签，降低被回收的概率"
                        },
                        checked = uiState.overlayKeepAlive,
                        onCheckedChange = { viewModel.setOverlayKeepAlive(it) },
                    )
                    // 缺悬浮窗权限时才出现授权入口：出现/消失带动画
                    AnimatedVisibility(
                        visible = status?.overlayGranted != true,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically(),
                    ) {
                        PreferenceActionRow(
                            text = "授予悬浮窗权限",
                            onClick = {
                                launch(
                                    context,
                                    FloatingKeepAlive.permissionIntent(context),
                                    "无法打开悬浮窗权限设置",
                                )
                            },
                        )
                    }
                }
            }

            item { SmallTitle("厂商后台管理") }
            item {
                PreferenceCard {
                    NavigationRow(
                        title = "打开厂商后台设置",
                        summary = "国产系统默认会限制第三方应用后台；打不开就按下方说明手动设置",
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
                    )
                    PreferenceHint(KeepAlive.manualHint(Build.MANUFACTURER))
                }
            }

            item { SmallTitle("说明") }
            item {
                PreferenceCard {
                    PreferenceHint(
                        "本应用用「常驻前台服务 + 通知」保持进程存活，并持有唤醒锁与 Wi-Fi 锁，" +
                            "锁屏后 CPU/Wi-Fi 不会被省电挂起。若系统仍把进程冻结，客户端会连接失败——" +
                            "按上面逐条处理即可。",
                    )
                }
            }

            item { Spacer(modifier = Modifier.navigationBarsPadding()) }
        }
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
