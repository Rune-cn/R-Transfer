// SPDX-License-Identifier: GPL-3.0-only
package app.ai.rune.ziyong.transfer.ui.page.miuix.settings.log

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.ai.rune.ziyong.transfer.gateway.AppViewModel
import app.ai.rune.ziyong.transfer.gateway.formatDuration
import app.ai.rune.ziyong.transfer.gateway.formatTokens
import app.ai.rune.ziyong.transfer.ui.page.miuix.settings.common.NavigationRow
import app.ai.rune.ziyong.transfer.ui.page.miuix.settings.common.PreferenceButtonRow
import app.ai.rune.ziyong.transfer.ui.page.miuix.settings.common.PreferenceCard
import app.ai.rune.ziyong.transfer.ui.page.miuix.settings.common.PreferenceHint
import app.ai.rune.ziyong.transfer.ui.theme.getMiuixAppBarColor
import app.ai.rune.ziyong.transfer.ui.theme.miuixBlurEffect
import app.ai.rune.ziyong.transfer.ui.theme.rememberMiuixBlurBackdrop
import dev.aigw.core.usage.CallRecord
import dev.aigw.core.usage.CallStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.koin.androidx.compose.koinViewModel
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import top.yukonga.miuix.kmp.window.WindowDialog
import androidx.compose.foundation.layout.fillMaxWidth

/**
 * 调用记录：网关处理过的每一次请求。
 *
 * 排版对齐模板「关于」页：SmallTitle 分组 + PreferenceCard，
 * 每条记录是一行 NavigationRow（点开看详情）。
 */
@Composable
fun MiuixCallLogPage(
    enableBlur: Boolean,
    viewModel: AppViewModel = koinViewModel(),
    title: String,
    outerPadding: PaddingValues,
) {
    val uiState by viewModel.state.collectAsStateWithLifecycle()
    val scrollBehavior = MiuixScrollBehavior()
    val layoutDirection = LocalLayoutDirection.current
    val backdrop = rememberMiuixBlurBackdrop(enableBlur)
    var detail by remember { mutableStateOf<CallRecord?>(null) }

    // 停留在本页时定时刷新；离开页面随组合销毁而停止
    LaunchedEffect(Unit) {
        while (isActive) {
            viewModel.refreshRecords()
            delay(AUTO_REFRESH_MS)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                modifier = Modifier.miuixBlurEffect(backdrop),
                color = backdrop.getMiuixAppBarColor(),
                title = title,
                subtitle = "共 ${uiState.calls.size} 条 · 存储 ${uiState.storedCalls} 条",
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

            item { SmallTitle("今日") }
            item {
                PreferenceCard {
                    NavigationRow(
                        title = "请求",
                        summary = "共 ${uiState.today.requests} 次 · 成功 ${uiState.today.success} · " +
                            "失败 ${uiState.today.failed} · 中断 ${uiState.today.aborted}",
                        onClick = {},
                    )
                    NavigationRow(
                        title = "Tokens",
                        summary = "输入 ${formatTokens(uiState.today.promptTokens)} · " +
                            "输出 ${formatTokens(uiState.today.completionTokens)} · " +
                            "合计 ${formatTokens(uiState.today.totalTokens)}",
                        onClick = {},
                    )
                }
            }

            item { SmallTitle("调用记录") }
            if (uiState.calls.isEmpty()) {
                item {
                    PreferenceCard {
                        PreferenceHint("暂无调用记录。用任一 API Key 调用本地网关后，记录会出现在这里。")
                    }
                }
            } else {
                for (record in uiState.calls) {
                    item(key = record.id) {
                        PreferenceCard {
                            NavigationRow(
                                title = record.model.ifBlank { "未指定模型" },
                                summary = buildString {
                                    append(TIME_FORMAT.format(Date(record.startedAtMillis)))
                                    append(" · ")
                                    append(statusLabel(record.status))
                                    append(" · ")
                                    append(formatDuration(record.durationMillis))
                                    if (record.totalTokens > 0) {
                                        append(" · ")
                                        append(formatTokens(record.totalTokens))
                                        append(" tokens")
                                    }
                                    if (record.accountNickname.isNotEmpty()) {
                                        append(" · ")
                                        append(record.accountNickname)
                                    }
                                    if (record.error.isNotEmpty()) {
                                        append("\n")
                                        append(record.error)
                                    }
                                },
                                onClick = { detail = record },
                            )
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.navigationBarsPadding()) }
        }
    }

    detail?.let { record ->
        CallDetailDialog(record = record, onDismiss = { detail = null })
    }
}

/**
 * 详情弹窗：把一条记录里排障需要的字段摊开。
 *
 * 只读关键字段而不显示完整请求/响应原文——原文动辄几十 KB，
 * 放进 Dialog 会让首帧明显卡顿。
 */
@Composable
private fun CallDetailDialog(record: CallRecord, onDismiss: () -> Unit) {
    WindowDialog(
        show = true,
        title = "调用详情",
        onDismissRequest = onDismiss,
    ) {
        PreferenceCard {
            NavigationRow(
                title = "模型",
                summary = record.model.ifEmpty { "未指定模型" },
                onClick = {},
            )
            NavigationRow(
                title = "时间",
                summary = "${TIME_FORMAT.format(Date(record.startedAtMillis))} · " +
                    "${statusLabel(record.status)} · HTTP ${record.httpStatus}",
                onClick = {},
            )
            NavigationRow(title = "供应商", summary = record.providerId, onClick = {})
            NavigationRow(
                title = "账号",
                summary = record.accountNickname.ifEmpty { record.accountUid },
                onClick = {},
            )
            NavigationRow(
                title = "流式",
                summary = if (record.streaming) "是" else "否",
                onClick = {},
            )
            NavigationRow(
                title = "耗时",
                summary = buildString {
                    append(formatDuration(record.durationMillis))
                    if (record.firstByteMillis >= 0) {
                        append(" · 首字延迟 ")
                        append(formatDuration(record.firstByteMillis))
                    }
                },
                onClick = {},
            )
            NavigationRow(
                title = "Tokens",
                summary = "输入 ${formatTokens(record.promptTokens)} · " +
                    "输出 ${formatTokens(record.completionTokens)} · " +
                    "合计 ${formatTokens(record.totalTokens)}",
                onClick = {},
            )
            if (record.cachedTokens > 0) {
                NavigationRow(
                    title = "缓存命中",
                    summary = formatTokens(record.cachedTokens),
                    onClick = {},
                )
            }
            if (record.pointsConsumed > 0) {
                NavigationRow(
                    title = "积分消耗",
                    summary = record.pointsConsumed.toString(),
                    onClick = {},
                )
            }
            if (record.error.isNotEmpty()) {
                PreferenceHint(record.error, error = true)
            }
        }
        PreferenceButtonRow {
            TextButton(
                text = "关闭",
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private fun statusLabel(status: CallStatus): String = when (status) {
    CallStatus.SUCCESS -> "成功"
    CallStatus.FAILED -> "失败"
    CallStatus.ABORTED -> "中断"
}

private const val AUTO_REFRESH_MS = 5_000L

private val TIME_FORMAT = SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault())
