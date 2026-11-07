// SPDX-License-Identifier: GPL-3.0-only
package app.ai.rune.ziyong.transfer.ui.page.miuix.settings.log

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
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
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import top.yukonga.miuix.kmp.window.WindowDialog
import app.ai.rune.ziyong.transfer.ui.page.miuix.settings.common.SectionCard

/**
 * 调用记录页：网关处理过的每一次请求。
 *
 * 与模板原先的「运行日志」页的分工：
 * - 本页回答「谁调了什么模型、用了多久、耗了多少 token、成没成功」——面向使用者；
 * - 应用自身的调试日志（Timber 落盘）不再占用一级入口，需要时从设置页进入。
 *
 * 数据源是内核的 CallRecord，不做二次聚合：每条记录都对应一次真实请求，
 * 聚合会丢掉「这一次为什么失败」这类排障所需的信息。
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

    // 停留在本页时定时刷新：调用记录更新频繁，退到后台即停止（LaunchedEffect 随组合销毁）
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
                start = innerPadding.calculateStartPadding(layoutDirection) + 12.dp,
                top = innerPadding.calculateTopPadding() + 8.dp,
                end = innerPadding.calculateEndPadding(layoutDirection) + 12.dp,
                bottom = outerPadding.calculateBottomPadding(),
            ),
            overscrollEffect = null,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { SmallTitle("今日") }
            item {
                SectionCard() {
                    StatusRow(uiState.today.requests, uiState.today.success, uiState.today.failed, uiState.today.aborted)
                    BasicComponent(
                        title = "Tokens",
                        summary = "输入 ${formatTokens(uiState.today.promptTokens)} · " +
                            "输出 ${formatTokens(uiState.today.completionTokens)} · " +
                            "合计 ${formatTokens(uiState.today.totalTokens)}",
                    )
                }
            }

            item { SmallTitle("调用记录") }

            if (uiState.calls.isEmpty()) {
                item {
                    SectionCard() {
                        BasicComponent(
                            title = "暂无调用记录",
                            summary = "用任一 API Key 调用本地网关后，记录会出现在这里。",
                        )
                    }
                }
            } else {
                items(uiState.calls.size, key = { uiState.calls[it].id }) { index ->
                    val record = uiState.calls[index]
                    SectionCard() {
                        CallRow(record, onClick = { detail = record })
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

/** 今日请求构成：一行四个数字，避免为每个指标单开一行。 */
@Composable
private fun StatusRow(requests: Long, success: Long, failed: Long, aborted: Long) {
    BasicComponent(
        title = "请求",
        summary = "共 $requests 次 · 成功 $success · 失败 $failed · 中断 $aborted",
    )
}

/** 单条调用记录：模型 + 状态 + 时间，展开看用量与错误。 */
@Composable
private fun CallRow(record: CallRecord, onClick: () -> Unit) {
    BasicComponent(
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
        onClick = onClick,
    )
}

/**
 * 详情弹窗：把一条记录里排障需要的字段摊开。
 *
 * 只读关键字段而不显示完整请求/响应原文——原文动辄几十 KB，
 * 放进 Dialog 会让首帧明显卡顿；需要原文时后续可加「查看原文」二级页。
 */
@Composable
private fun CallDetailDialog(record: CallRecord, onDismiss: () -> Unit) {
    WindowDialog(
        show = true,
        title = "调用详情",
        onDismissRequest = onDismiss,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = record.model.ifEmpty { "未指定模型" },
                style = MiuixTheme.textStyles.body1,
                color = MiuixTheme.colorScheme.onSurface,
            )
            Text(
                text = "${TIME_FORMAT.format(Date(record.startedAtMillis))} · ${statusLabel(record.status)} · HTTP ${record.httpStatus}",
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )

            DetailLine("供应商", record.providerId)
            DetailLine("账号", record.accountNickname.ifEmpty { record.accountUid })
            DetailLine("流式", if (record.streaming) "是" else "否")
            DetailLine("耗时", formatDuration(record.durationMillis))
            if (record.firstByteMillis >= 0) {
                DetailLine("首字延迟", formatDuration(record.firstByteMillis))
            }
            DetailLine(
                "Tokens",
                "输入 ${formatTokens(record.promptTokens)} · 输出 ${formatTokens(record.completionTokens)} · 合计 ${formatTokens(record.totalTokens)}",
            )
            if (record.cachedTokens > 0) {
                DetailLine("缓存命中", formatTokens(record.cachedTokens))
            }
            if (record.pointsConsumed > 0) {
                DetailLine("积分消耗", record.pointsConsumed.toString())
            }
            if (record.error.isNotEmpty()) {
                Text(
                    text = record.error,
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.error,
                )
            }

            Box(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                top.yukonga.miuix.kmp.basic.TextButton(
                    text = "关闭",
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = label,
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        Text(
            text = value,
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f, fill = false),
        )
    }
}

private fun statusLabel(status: CallStatus): String = when (status) {
    CallStatus.SUCCESS -> "成功"
    CallStatus.FAILED -> "失败"
    CallStatus.ABORTED -> "中断"
}

private const val AUTO_REFRESH_MS = 5_000L

private val TIME_FORMAT = SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault())
