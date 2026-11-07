// SPDX-License-Identifier: GPL-3.0-only
package app.ai.rune.ziyong.transfer.ui.page.miuix.settings.security

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.ai.rune.ziyong.transfer.gateway.AppViewModel
import app.ai.rune.ziyong.transfer.ui.page.miuix.settings.common.NavigationRow
import app.ai.rune.ziyong.transfer.ui.page.miuix.settings.common.PreferenceActionRow
import app.ai.rune.ziyong.transfer.ui.page.miuix.settings.common.PreferenceBlock
import app.ai.rune.ziyong.transfer.ui.page.miuix.settings.common.PreferenceButtonRow
import app.ai.rune.ziyong.transfer.ui.page.miuix.settings.common.PreferenceCard
import app.ai.rune.ziyong.transfer.ui.page.miuix.settings.common.PreferenceHint
import app.ai.rune.ziyong.transfer.ui.page.miuix.settings.common.SwitchRow
import app.ai.rune.ziyong.transfer.ui.theme.getMiuixAppBarColor
import app.ai.rune.ziyong.transfer.ui.theme.miuixBlurEffect
import app.ai.rune.ziyong.transfer.ui.theme.rememberMiuixBlurBackdrop
import dev.aigw.core.security.OutboundEvidence
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.koin.androidx.compose.koinViewModel
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
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

/**
 * 安全防护：反审核脱敏 + 账号级限速。
 *
 * 排版对齐模板「关于」页：SmallTitle 分组 + PreferenceCard。
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
                start = innerPadding.calculateStartPadding(layoutDirection),
                top = innerPadding.calculateTopPadding(),
                end = innerPadding.calculateEndPadding(layoutDirection),
                bottom = outerPadding.calculateBottomPadding(),
            ),
            overscrollEffect = null,
        ) {
            item { Spacer(modifier = Modifier.size(12.dp)) }

            item { SmallTitle("反审核脱敏") }
            item {
                PreferenceCard {
                    SwitchRow(
                        title = "开启脱敏",
                        summary = "客户端的合规声明里常有 DoS / exploit 等词，会被上游误拦。" +
                            "开启后在这些词内部插零宽空格，只改 system 消息，不影响用户输入",
                        checked = security.sanitizeEnabled,
                        onCheckedChange = {
                            viewModel.updateSecuritySettings(security.copy(sanitizeEnabled = it))
                        },
                    )
                    NavigationRow(
                        title = "重新加载处理链",
                        summary = "词表更新后无需重启，点此让改动立即生效",
                        onClick = { viewModel.reloadSanitizerPipeline() },
                    )
                    NavigationRow(
                        title = "出网取证",
                        summary = "共 ${uiState.evidences.size} 条脱敏记录（只展示 system 改写）",
                        onClick = { showEvidence = true },
                    )
                }
            }

            item { SmallTitle("账号级限速") }
            item {
                PreferenceCard {
                    PreferenceHint(
                        "同一账号连续请求会被串成固定节拍，容易形成可识别特征。" +
                            "加间隔与抖动可打散节奏，代价是首字变慢——不需要规避风控时保持 0。",
                    )
                    PreferenceBlock {
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
                    }
                    PreferenceActionRow(
                        text = "保存限速参数",
                        onClick = {
                            // 解析失败回落原值：静默变 0 等于把限速关掉
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
                    )
                }
            }

            item { SmallTitle("说明") }
            item {
                PreferenceCard {
                    PreferenceHint(
                        "限速是在发往上游之前阻塞等待，对并发请求同样生效。" +
                            "若只希望降低单账号频率而不影响首字，建议只设最小间隔、把抖动留 0。",
                    )
                }
            }

            item { Spacer(modifier = Modifier.navigationBarsPadding()) }
        }
    }

    if (showEvidence) {
        EvidenceSheet(
            evidences = uiState.evidences,
            onClear = { viewModel.clearEvidences() },
            onDismiss = { showEvidence = false },
        )
    }
}

/**
 * 出网取证：展示脱敏前后的片段对照。
 *
 * 这是排查「明明开了脱敏还是被拦」的唯一依据——能看到哪些词被改写、改写成了什么。
 */
@Composable
private fun EvidenceSheet(
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
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item { Spacer(modifier = Modifier.size(12.dp)) }
            if (evidences.isEmpty()) {
                item {
                    PreferenceCard {
                        PreferenceHint(
                            "暂无记录。当客户端发送包含 DoS / exploit 等词的 system 提示词时，" +
                                "脱敏前后的快照会记录在这里。",
                        )
                    }
                }
            } else {
                item {
                    PreferenceCard {
                        NavigationRow(
                            title = "已知记录",
                            summary = "共 ${evidences.size} 条",
                            onClick = {},
                        )
                        PreferenceActionRow(text = "清空", onClick = onClear)
                    }
                }
                for (evidence in evidences) {
                    item(key = evidence.seq) {
                        PreferenceCard {
                            PreferenceBlock {
                                Text(
                                    text = "${timeFormat.format(Date(evidence.atMillis))} · ${evidence.model}",
                                    style = MiuixTheme.textStyles.footnote1,
                                    color = MiuixTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = "命中词汇：${evidence.matchedTerms.joinToString(", ")}",
                                    style = MiuixTheme.textStyles.footnote2,
                                    color = MiuixTheme.colorScheme.error,
                                )
                                Text(
                                    text = "脱敏前：${evidence.originalSnippet}",
                                    style = MiuixTheme.textStyles.footnote2,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                )
                                Text(
                                    text = "脱敏后：${evidence.sanitizedSnippet}",
                                    style = MiuixTheme.textStyles.footnote2,
                                    color = MiuixTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                }
            }
            item {
                PreferenceButtonRow {
                    TextButton(
                        text = "关闭",
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
