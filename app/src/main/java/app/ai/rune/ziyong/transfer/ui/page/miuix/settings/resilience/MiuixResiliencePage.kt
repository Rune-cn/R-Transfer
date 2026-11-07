// SPDX-License-Identifier: GPL-3.0-only
package app.ai.rune.ziyong.transfer.ui.page.miuix.settings.resilience

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
import dev.aigw.core.failover.CircuitBreakerConfig
import dev.aigw.core.failover.ConcurrencyConfig
import dev.aigw.core.failover.ModelCooldownConfig
import dev.aigw.core.failover.RetryConfig
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
import top.yukonga.miuix.kmp.window.WindowDialog
import app.ai.rune.ziyong.transfer.ui.page.miuix.settings.common.SectionCard
import app.ai.rune.ziyong.transfer.ui.page.miuix.settings.common.CompactButton

/**
 * 并发与容错：四组参数，分别对应上游异常时的四种处理方式。
 *
 * - **并发闸门**：限制同时在途的请求数。单账号默认 1（串行）——
 *   上游按账号限流并发流，同一账号被多条流同时打时，除第一条外会被挂住只回个空 chunk，
 *   客户端遂超时。这不是保守，而是忠实于官方客户端的行为。
 * - **失败转移**：单供应商内换号重试 + 跨供应商切换。
 * - **熔断器**：某供应商连续失败到阈值后静默一段时间，避免持续撞墙。
 * - **模型级冷却**：只针对「该模型不可用」类错误，不换账号、不计熔断。
 *
 * 四组参数的编辑弹窗共用同一个骨架，只换字段与提示——避免四处细节各写一遍后逐渐漂移。
 */
@Composable
fun MiuixResiliencePage(
    viewModel: AppViewModel = koinViewModel(),
    onBack: () -> Unit,
    outerPadding: PaddingValues,
) {
    val uiState by viewModel.state.collectAsStateWithLifecycle()
    val failover = uiState.failover
    val scrollBehavior = MiuixScrollBehavior()
    val layoutDirection = LocalLayoutDirection.current
    val backdrop = rememberMiuixBlurBackdrop(false)

    var editTarget by remember { mutableStateOf<EditTarget?>(null) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                modifier = Modifier.miuixBlurEffect(backdrop),
                color = backdrop.getMiuixAppBarColor(),
                title = "并发与容错",
                subtitle = "闸门 · 重试 · 熔断 · 冷却",
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
            item { SmallTitle("并发闸门") }
            item {
                SectionCard() {
                    ParamRow(
                        "单账号在途上限",
                        if (failover.concurrency.maxPerAccount == 0) {
                            "不限"
                        } else {
                            "${failover.concurrency.maxPerAccount} 条"
                        },
                    )
                    ParamRow(
                        "全局在途上限",
                        if (failover.concurrency.maxGlobal == 0) "不限" else "${failover.concurrency.maxGlobal} 条",
                    )
                    ParamRow("排队超时", "${failover.concurrency.queueTimeoutMs} 毫秒")
                    Text(
                        text = "单账号默认 1：上游按账号限流并发流，同时打多条会除首条外被挂住，客户端超时。",
                        style = MiuixTheme.textStyles.footnote2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    TextButton(
                        text = "修改并发参数",
                        onClick = { editTarget = EditTarget.Concurrency },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            item { SmallTitle("失败转移") }
            item {
                SectionCard() {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = "启用失败转移",
                                style = MiuixTheme.textStyles.subtitle,
                                color = MiuixTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = "关闭后单请求只在本供应商内换号：不退避重试、也不切到其它供应商",
                                style = MiuixTheme.textStyles.footnote2,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                        Switch(
                            checked = failover.enabled,
                            onCheckedChange = {
                                viewModel.updateFailoverSettings(failover.copy(enabled = it))
                            },
                        )
                    }
                    ParamRow("最大尝试次数", "${failover.retry.maxAttempts} 次（含首次）")
                    ParamRow("初始退避", "${failover.retry.initialBackoffMs} 毫秒")
                    ParamRow("退避上限", "${failover.retry.maxBackoffMs} 毫秒")
                    ParamRow("退避倍率", "×${failover.retry.backoffMultiplier}")
                    ParamRow("随机抖动", "±${(failover.retry.jitterRatio * 100).toInt()}%")
                    TextButton(
                        text = "修改重试参数",
                        onClick = { editTarget = EditTarget.Retry },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            item { SmallTitle("熔断器（按供应商）") }
            item {
                SectionCard() {
                    ParamRow("连续失败阈值", "${failover.circuitBreaker.failureThreshold} 次")
                    ParamRow("熔断静默时长", "${failover.circuitBreaker.openDurationMs / 1000} 秒")
                    ParamRow("半开恢复成功数", "${failover.circuitBreaker.halfOpenSuccessCount} 次")
                    TextButton(
                        text = "修改熔断参数",
                        onClick = { editTarget = EditTarget.CircuitBreaker },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            item { SmallTitle("模型级冷却") }
            item {
                SectionCard() {
                    ParamRow("冷却时长", "${failover.modelCooldown.cooldownMs / 1000} 秒")
                    ParamRow("冷却上限", "${failover.modelCooldown.maxCooldownMs / 1000} 秒")
                    Text(
                        text = "上游报「该模型已超限」时只冷却该模型：不换账号、不计熔断，冷却期内直接跳过。",
                        style = MiuixTheme.textStyles.footnote2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    TextButton(
                        text = "修改模型级冷却",
                        onClick = { editTarget = EditTarget.ModelCooldown },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            item {
                Text(
                    text = "全部参数保存后立即生效，无需重启网关。并发「在途」指已向上游发起、尚未返回的请求；" +
                        "排队超时后该次尝试会换下一个账号或供应商。",
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }

            item { Spacer(modifier = Modifier.navigationBarsPadding()) }
        }
    }

    editTarget?.let { target ->
        when (target) {
            EditTarget.Concurrency -> ConcurrencyDialog(
                current = failover.concurrency,
                onDismiss = { editTarget = null },
                onConfirm = {
                    viewModel.updateFailoverSettings(failover.copy(concurrency = it))
                    editTarget = null
                },
            )

            EditTarget.Retry -> RetryDialog(
                current = failover.retry,
                onDismiss = { editTarget = null },
                onConfirm = {
                    viewModel.updateFailoverSettings(failover.copy(retry = it))
                    editTarget = null
                },
            )

            EditTarget.CircuitBreaker -> CircuitBreakerDialog(
                current = failover.circuitBreaker,
                onDismiss = { editTarget = null },
                onConfirm = {
                    viewModel.updateFailoverSettings(failover.copy(circuitBreaker = it))
                    editTarget = null
                },
            )

            EditTarget.ModelCooldown -> ModelCooldownDialog(
                current = failover.modelCooldown,
                onDismiss = { editTarget = null },
                onConfirm = {
                    viewModel.updateFailoverSettings(failover.copy(modelCooldown = it))
                    editTarget = null
                },
            )
        }
    }
}

private enum class EditTarget { Concurrency, Retry, CircuitBreaker, ModelCooldown }

/** 参数展示行：左标题右值。 */
@Composable
private fun ParamRow(title: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = title,
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurface,
        )
        Text(
            text = value,
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.primary,
        )
    }
}

// ============================== 编辑弹窗 ==============================

@Composable
private fun ConcurrencyDialog(
    current: ConcurrencyConfig,
    onDismiss: () -> Unit,
    onConfirm: (ConcurrencyConfig) -> Unit,
) {
    var perAccount by remember { mutableStateOf(current.maxPerAccount.toString()) }
    var global by remember { mutableStateOf(current.maxGlobal.toString()) }
    var timeout by remember { mutableStateOf(current.queueTimeoutMs.toString()) }

    EditDialog(
        title = "修改并发参数",
        hint = "风控敏感的账号建议设 1；上游稳定时可放宽。全局上限建议不小于 单账号上限 × 账号数。",
        onDismiss = onDismiss,
        onConfirm = {
            // 解析失败回落原值：静默变成 0 会让风控直接失效（0 = 不限）
            onConfirm(
                ConcurrencyConfig(
                    maxPerAccount = ConcurrencyConfig.clampMaxPerAccount(
                        perAccount.toIntOrNull() ?: current.maxPerAccount,
                    ),
                    maxGlobal = ConcurrencyConfig.clampMaxGlobal(global.toIntOrNull() ?: current.maxGlobal),
                    queueTimeoutMs = ConcurrencyConfig.clampQueueTimeoutMs(
                        timeout.toLongOrNull() ?: current.queueTimeoutMs,
                    ),
                ),
            )
        },
    ) {
        NumberField("单账号在途上限（0-20，0=不限）", perAccount, 2) { perAccount = it }
        NumberField("全局在途上限（0-100，0=不限）", global, 3) { global = it }
        NumberField("排队超时（毫秒，最大 120000）", timeout, 6) { timeout = it }
    }
}

@Composable
private fun RetryDialog(
    current: RetryConfig,
    onDismiss: () -> Unit,
    onConfirm: (RetryConfig) -> Unit,
) {
    var attempts by remember { mutableStateOf(current.maxAttempts.toString()) }
    var initial by remember { mutableStateOf(current.initialBackoffMs.toString()) }
    var maxBackoff by remember { mutableStateOf(current.maxBackoffMs.toString()) }
    var multiplier by remember { mutableStateOf(current.backoffMultiplier.toString()) }
    var jitter by remember { mutableStateOf((current.jitterRatio * 100).toInt().toString()) }

    EditDialog(
        title = "修改重试参数",
        hint = "退避按指数增长：第 n 次重试等待 = 初始退避 × 倍率^n，并叠加随机抖动。",
        onDismiss = onDismiss,
        onConfirm = {
            onConfirm(
                RetryConfig(
                    maxAttempts = (attempts.toIntOrNull() ?: current.maxAttempts).coerceIn(1, 10),
                    initialBackoffMs = (initial.toLongOrNull() ?: current.initialBackoffMs)
                        .coerceIn(0L, 10_000L),
                    maxBackoffMs = (maxBackoff.toLongOrNull() ?: current.maxBackoffMs)
                        .coerceIn(0L, 60_000L),
                    backoffMultiplier = (multiplier.toDoubleOrNull() ?: current.backoffMultiplier)
                        .coerceIn(1.0, 5.0),
                    jitterRatio = (
                        (jitter.toIntOrNull() ?: (current.jitterRatio * 100).toInt())
                            .coerceIn(0, 100) / 100.0
                        ),
                ),
            )
        },
    ) {
        NumberField("最大尝试次数（1-10）", attempts, 2) { attempts = it }
        NumberField("初始退避（毫秒）", initial, 5) { initial = it }
        NumberField("退避上限（毫秒）", maxBackoff, 6) { maxBackoff = it }
        NumberField("退避倍率（1.0-5.0）", multiplier, 4, allowDecimal = true) { multiplier = it }
        NumberField("随机抖动（0-100%）", jitter, 3) { jitter = it }
    }
}

@Composable
private fun CircuitBreakerDialog(
    current: CircuitBreakerConfig,
    onDismiss: () -> Unit,
    onConfirm: (CircuitBreakerConfig) -> Unit,
) {
    var threshold by remember { mutableStateOf(current.failureThreshold.toString()) }
    var openSeconds by remember { mutableStateOf((current.openDurationMs / 1000).toString()) }
    var halfOpen by remember { mutableStateOf(current.halfOpenSuccessCount.toString()) }

    EditDialog(
        title = "修改熔断参数",
        hint = "客户端 400 类错误不计入失败；熔断只统计上游侧错误。",
        onDismiss = onDismiss,
        onConfirm = {
            onConfirm(
                CircuitBreakerConfig(
                    failureThreshold = (threshold.toIntOrNull() ?: current.failureThreshold)
                        .coerceIn(1, 100),
                    openDurationMs = (
                        (openSeconds.toLongOrNull() ?: (current.openDurationMs / 1000))
                            .coerceIn(1L, 3600L) * 1000
                        ),
                    halfOpenSuccessCount = (halfOpen.toIntOrNull() ?: current.halfOpenSuccessCount)
                        .coerceIn(1, 10),
                ),
            )
        },
    ) {
        NumberField("连续失败阈值（1-100）", threshold, 3) { threshold = it }
        NumberField("熔断静默时长（秒，1-3600）", openSeconds, 4) { openSeconds = it }
        NumberField("半开恢复成功数（1-10）", halfOpen, 2) { halfOpen = it }
    }
}

@Composable
private fun ModelCooldownDialog(
    current: ModelCooldownConfig,
    onDismiss: () -> Unit,
    onConfirm: (ModelCooldownConfig) -> Unit,
) {
    var cooldownSeconds by remember { mutableStateOf((current.cooldownMs / 1000).toString()) }
    var maxSeconds by remember { mutableStateOf((current.maxCooldownMs / 1000).toString()) }

    EditDialog(
        title = "修改模型级冷却",
        hint = "冷却上限用于避免误判出超长冷却；到上限后即使仍报错也只在下次请求时重试。",
        onDismiss = onDismiss,
        onConfirm = {
            onConfirm(
                ModelCooldownConfig(
                    cooldownMs = (cooldownSeconds.toLongOrNull() ?: (current.cooldownMs / 1000))
                        .coerceIn(1L, 7200L) * 1000,
                    maxCooldownMs = (maxSeconds.toLongOrNull() ?: (current.maxCooldownMs / 1000))
                        .coerceIn(1L, 7200L) * 1000,
                ),
            )
        },
    ) {
        NumberField("冷却时长（秒）", cooldownSeconds, 5) { cooldownSeconds = it }
        NumberField("冷却上限（秒）", maxSeconds, 5) { maxSeconds = it }
    }
}

/**
 * 编辑弹窗的公共骨架。
 *
 * 四个弹窗的差异只有字段与解析规则；统一到这层后，
 * 按钮位置、取消语义、提示文案位置不会再各自漂移。
 */
@Composable
private fun EditDialog(
    title: String,
    hint: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    fields: @Composable () -> Unit,
) {
    WindowDialog(
        show = true,
        title = title,
        onDismissRequest = onDismiss,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            fields()
            Text(
                text = hint,
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CompactButton(text = "取消", onClick = onDismiss)
                CompactButton(text = "保存", primary = true, onClick = onConfirm)
            }
        }
    }
}

@Composable
private fun NumberField(
    label: String,
    value: String,
    maxLen: Int,
    allowDecimal: Boolean = false,
    onChange: (String) -> Unit,
) {
    TextField(
        value = value,
        onValueChange = { input ->
            // 整数字段不放行小数点：避免「1.5」被 toIntOrNull 静默丢弃后落回默认值
            val filtered = input.filter { it.isDigit() || (allowDecimal && it == '.') }
            onChange(filtered.take(maxLen))
        },
        label = label,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
}
