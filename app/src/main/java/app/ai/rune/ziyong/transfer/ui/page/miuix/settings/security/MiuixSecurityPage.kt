// SPDX-License-Identifier: GPL-3.0-only
package app.ai.rune.ziyong.transfer.ui.page.miuix.settings.security

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.ai.rune.ziyong.transfer.gateway.AppViewModel
import app.ai.rune.ziyong.transfer.ui.theme.getMiuixAppBarColor
import app.ai.rune.ziyong.transfer.ui.theme.miuixBlurEffect
import app.ai.rune.ziyong.transfer.ui.theme.rememberMiuixBlurBackdrop
import dev.aigw.core.security.OutboundEvidence
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.koin.androidx.compose.koinViewModel
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import top.yukonga.miuix.kmp.window.WindowBottomSheet
import app.ai.rune.ziyong.transfer.ui.page.miuix.settings.common.SectionCard

/**
 * 安全防护：反审核脱敏 + 账号级限速。
 *
 * 两项能力面向不同问题，界面上也分开放：
 * - **脱敏**解决「上游误拦」：客户端合规声明里的 DoS / exploit 等词会被上游过滤，
 *   开启后在这些词内部插入零宽空格，只改 system/developer 消息，不碰用户输入。
 * - **限速**解决「被风控打标」：同一账号连续请求按固定节拍会形成可识别特征，
 *   加间隔与抖动把节奏打散。代价是首字变慢，所以默认关闭，页面上写明取舍。
 */
@Composable
fun MiuixSecurityPage(
    viewModel: AppViewModel = koinViewModel(),
    onBack: () -> Unit,
    outerPadding: PaddingValues,
) {
    val uiState by viewModel.state.collectAsStateWithLifecycle()
    val security = uiState.security
    val scrollBehavior = MiuixScrollBehavior()
    val layoutDirection = LocalLayoutDirection.current
    val backdrop = rememberMiuixBlurBackdrop(false)

    var minInterval by remember(security.minIntervalMillis) {
        mutableStateOf(security.minIntervalMillis.toString())
    }
    var jitter by remember(security.jitterMillis) {
        mutableStateOf(security.jitterMillis.toString())
    }
    var showEvidence by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                modifier = Modifier.miuixBlurEffect(backdrop),
                color = backdrop.getMiuixAppBarColor(),
                title = "安全防护",
                subtitle = "脱敏与账号限速",
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
            item { SmallTitle("反审核脱敏") }
            item {
                SectionCard() {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = "开启脱敏",
                                style = MiuixTheme.textStyles.subtitle,
                                color = MiuixTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = "客户端的合规声明里常有 DoS / exploit 等词，会被上游误拦。" +
                                    "开启后在这些词内部插零宽空格，只改 system 消息，不影响用户输入",
                                style = MiuixTheme.textStyles.footnote2,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                        Switch(
                            checked = security.sanitizeEnabled,
                            onCheckedChange = {
                                viewModel.updateSecuritySettings(security.copy(sanitizeEnabled = it))
                            },
                        )
                    }
                    TextButton(
                        text = "重新加载处理链",
                        onClick = { viewModel.reloadSanitizerPipeline() },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    BasicComponent(
                        title = "出网取证",
                        summary = "共 ${uiState.evidences.size} 条脱敏记录（只展示 system 改写）",
                        onClick = { showEvidence = true },
                    )
                }
            }

            item { SmallTitle("账号级限速") }
            item {
                SectionCard() {
                    Text(
                        text = "同一账号连续请求会被串成固定节拍，容易形成可识别特征。" +
                            "加间隔与抖动可打散节奏，代价是首字变慢——不需要规避风控时保持 0。",
                        style = MiuixTheme.textStyles.footnote2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextField(
                            value = minInterval,
                            onValueChange = { minInterval = it.filter(Char::isDigit).take(5) },
                            label = "最小间隔（毫秒，建议 1000~3000）",
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        TextField(
                            value = jitter,
                            onValueChange = { jitter = it.filter(Char::isDigit).take(5) },
                            label = "抖动幅度（毫秒，避免固定节拍）",
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        TextButton(
                            text = "保存限速参数",
                            onClick = {
                                // 解析失败回落原值，不能静默变成 0（那等于关掉限速）
                                val min = (minInterval.toLongOrNull() ?: security.minIntervalMillis)
                                    .coerceIn(0L, 60_000L)
                                val jit = (jitter.toLongOrNull() ?: security.jitterMillis)
                                    .coerceIn(0L, 10_000L)
                                viewModel.updateSecuritySettings(
                                    security.copy(minIntervalMillis = min, jitterMillis = jit),
                                )
                                minInterval = min.toString()
                                jitter = jit.toString()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.textButtonColorsPrimary(),
                        )
                    }
                }
            }

            item {
                Text(
                    text = "说明：限速是在发往上游之前阻塞等待，对并发请求同样生效。" +
                        "若只希望降低单账号频率而不影响首字，建议只设最小间隔、把抖动留 0。",
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }

            item { Spacer(modifier = Modifier.navigationBarsPadding()) }
        }
    }

    if (showEvidence) {
        EvidenceDialog(
            evidences = uiState.evidences,
            onClear = { viewModel.clearEvidences() },
            onDismiss = { showEvidence = false },
        )
    }
}

/**
 * 出网取证：展示脱敏前后的片段对照。
 *
 * 这是排查「明明开了脱敏还是被拦」的唯一依据——
 * 能看到哪些词被改写、改写成了什么。
 */
@Composable
private fun EvidenceDialog(
    evidences: List<OutboundEvidence>,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    WindowBottomSheet(
        show = true,
        title = "出网取证",
        onDismissRequest = onDismiss,
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (evidences.isEmpty()) {
                        "暂无记录"
                    } else {
                        "共 ${evidences.size} 条"
                    },
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.weight(1f),
                )
                if (evidences.isNotEmpty()) {
                    TextButton(
                        text = "清空",
                        onClick = onClear,
                        minWidth = 0.dp,
                        colors = ButtonDefaults.textButtonColors(textColor = MiuixTheme.colorScheme.error),
                    )
                }
            }

            if (evidences.isEmpty()) {
                Text(
                    text = "当客户端发送包含 DoS / exploit 等词的 system 提示词时，" +
                        "脱敏前后的快照会记录在这里。",
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(evidences.size, key = { evidences[it].seq }) { index ->
                        val item = evidences[index]
                        SectionCard() {
                            Text(
                                text = "${timeFormat.format(Date(item.atMillis))} · ${item.model}",
                                style = MiuixTheme.textStyles.footnote1,
                                color = MiuixTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = "命中词汇：${item.matchedTerms.joinToString(", ")}",
                                style = MiuixTheme.textStyles.footnote2,
                                color = MiuixTheme.colorScheme.error,
                            )
                            Text(
                                text = "脱敏前：${item.originalSnippet}",
                                style = MiuixTheme.textStyles.footnote2,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                            Text(
                                text = "脱敏后：${item.sanitizedSnippet}",
                                style = MiuixTheme.textStyles.footnote2,
                                color = MiuixTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }

            TextButton(
                text = "关闭",
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.textButtonColorsPrimary(),
            )
        }
    }
}
