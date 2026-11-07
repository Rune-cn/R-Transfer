// SPDX-License-Identifier: GPL-3.0-only
package app.ai.rune.ziyong.transfer.ui.page.miuix.settings.credits

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.ai.rune.ziyong.transfer.gateway.AppViewModel
import app.ai.rune.ziyong.transfer.gateway.AppUiState
import app.ai.rune.ziyong.transfer.ui.theme.getMiuixAppBarColor
import app.ai.rune.ziyong.transfer.ui.theme.miuixBlurEffect
import app.ai.rune.ziyong.transfer.ui.theme.rememberMiuixBlurBackdrop
import dev.aigw.core.provider.PointsBill
import dev.aigw.core.provider.ProviderCapability
import dev.aigw.core.provider.QuotaPack
import java.text.DateFormat
import java.util.Date
import org.koin.androidx.compose.koinViewModel
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import app.ai.rune.ziyong.transfer.ui.page.miuix.settings.common.PreferenceCard
import androidx.compose.ui.text.style.TextOverflow
import app.ai.rune.ziyong.transfer.ui.page.miuix.settings.common.NavigationRow
import app.ai.rune.ziyong.transfer.ui.page.miuix.settings.common.PreferenceHint
import app.ai.rune.ziyong.transfer.ui.page.miuix.settings.common.PreferenceActionRow

/**
 * 额度中心：按供应商查看各账号的额度包明细。
 *
 * 两层结构：
 * - 第一层列出有账号的供应商，附可用账号占比条（纯数字看不出「这个供应商健不健康」）；
 * - 第二层按账号平铺额度包，并区分「全账号共享」与「仅某模型可用」的配额。
 *
 * 最后一点很关键：模型专属配额（如某个模型的周额度）用完时，
 * 若不加区分就会被误当成「整个账号已耗尽」，从而白换账号。
 */
private sealed interface CreditsTarget {
    /** 单个供应商的额度明细页。 */
    data class ProviderDetail(val providerId: String) : CreditsTarget
}

@Composable
fun MiuixCreditsPage(
    viewModel: AppViewModel = koinViewModel(),
    onBack: () -> Unit,
    outerPadding: PaddingValues,
) {
    val uiState by viewModel.state.collectAsStateWithLifecycle()

    // 二级页目标（null = 总额度列表）；打开单个供应商明细时向右推入，返回反向滑出
    var target by remember { mutableStateOf<CreditsTarget?>(null) }
    BackHandler(enabled = target != null) { target = null }

    AnimatedContent(
        targetState = target,
        transitionSpec = {
            if (targetState == null) {
                (slideInHorizontally(
                    animationSpec = tween(320, easing = FastOutSlowInEasing),
                    initialOffsetX = { -it / 4 },
                ) + fadeIn(animationSpec = tween(320))) togetherWith
                    (slideOutHorizontally(
                        animationSpec = tween(320, easing = FastOutSlowInEasing),
                        targetOffsetX = { it },
                    ) + fadeOut(animationSpec = tween(240)))
            } else {
                (slideInHorizontally(
                    animationSpec = tween(320, easing = FastOutSlowInEasing),
                    initialOffsetX = { it },
                ) + fadeIn(animationSpec = tween(320))) togetherWith
                    (slideOutHorizontally(
                        animationSpec = tween(320, easing = FastOutSlowInEasing),
                        targetOffsetX = { -it / 4 },
                    ) + fadeOut(animationSpec = tween(240)))
            }
        },
        label = "creditsPage",
    ) { current ->
        when (current) {
            is CreditsTarget.ProviderDetail -> MiuixProviderCreditsPage(
                providerId = current.providerId,
                state = uiState,
                viewModel = viewModel,
                onBack = { target = null },
                outerPadding = outerPadding,
            )

            null -> CreditsListContent(
                onBack = onBack,
                outerPadding = outerPadding,
                uiState = uiState,
                viewModel = viewModel,
                onOpenProvider = { target = CreditsTarget.ProviderDetail(it) },
            )
        }
    }
}

/** 额度中心列表（target == null 时的内容）。 */
@Composable
private fun CreditsListContent(
    onBack: () -> Unit,
    outerPadding: PaddingValues,
    uiState: AppUiState,
    viewModel: AppViewModel,
    onOpenProvider: (String) -> Unit,
) {
    val scrollBehavior = MiuixScrollBehavior()
    val layoutDirection = LocalLayoutDirection.current
    val backdrop = rememberMiuixBlurBackdrop(false)

    val providersWithAccounts = remember(uiState.providers, uiState.accounts) {
        uiState.providers.filter { uiState.accountsOf(it.id).isNotEmpty() }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                modifier = Modifier.miuixBlurEffect(backdrop),
                color = backdrop.getMiuixAppBarColor(),
                title = "额度中心",
                subtitle = "按供应商查看额度包",
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
            // 列表页首统一 12dp 顶距（与模板所有页面一致）
            if (providersWithAccounts.isEmpty()) {
                item { Spacer(modifier = Modifier.size(12.dp)) }
                item {
                    PreferenceCard {
                        PreferenceHint("还没有账号。先去「供应商」页添加账号，额度会显示在这里。")
                    }
                }
                item { Spacer(modifier = Modifier.navigationBarsPadding()) }
                return@LazyColumn
            }

            item { Spacer(modifier = Modifier.size(12.dp)) }
            item { SmallTitle("额度概览") }
            item {
                PreferenceCard {
                    val known = uiState.accounts.filter { it.creditsKnown }
                    NavigationRow(
                        title = "额度合计",
                        summary = if (known.isEmpty()) {
                            "尚未同步额度"
                        } else {
                            "${known.sumOf { it.credits }}（已同步 ${known.size} 个账号）"
                        },
                        onClick = {},
                    )
                    NavigationRow(
                        title = "刷新全部",
                        summary = "向所有上游重新拉取额度，可能需要几十秒",
                        onClick = { viewModel.refreshAllCredits() },
                    )
                }
            }

            item { SmallTitle("按供应商查看") }
            for (info in providersWithAccounts) {
                item(key = info.id) {
                    val accounts = uiState.accountsOf(info.id)
                    val known = accounts.filter { it.creditsKnown }
                    PreferenceCard(onClick = { onOpenProvider(info.id) }) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = info.displayName,
                                    style = MiuixTheme.textStyles.subtitle,
                                    color = MiuixTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = "${accounts.size} 个账号 · 额度合计 " +
                                        if (known.isEmpty()) "—" else known.sumOf { it.credits }.toString(),
                                    style = MiuixTheme.textStyles.footnote2,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                )
                                // 可用账号占比：一眼看出这个供应商是否健康
                                val fraction = accounts.count { it.usable }.toFloat() / accounts.size
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(5.dp)
                                        .clip(CircleShape)
                                        .background(MiuixTheme.colorScheme.surfaceContainerHighest),
                                ) {
                                    Box(
                                        Modifier
                                            .fillMaxWidth(fraction.coerceIn(0f, 1f))
                                            .height(5.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (accounts.any { !it.usable }) {
                                                    MiuixTheme.colorScheme.error
                                                } else {
                                                    MiuixTheme.colorScheme.primary
                                                },
                                            ),
                                    )
                                }
                            }
                            Text(
                                text = "查看",
                                style = MiuixTheme.textStyles.footnote1,
                                color = MiuixTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.navigationBarsPadding()) }
        }
    }
}

/**
 * 某个供应商各账号的额度明细。
 *
 * 进页面拉一次（账号集合变化时重拉）；对「有账本」的供应商（如小浣熊）
 * 额外拉积分流水——这类供应商没有额度包概念，额度就是积分余额，
 * 只列额度包会让它看起来「没有明细」。
 */
@Composable
private fun MiuixProviderCreditsPage(
    providerId: String,
    state: AppUiState,
    viewModel: AppViewModel,
    onBack: () -> Unit,
    outerPadding: PaddingValues,
) {
    val accounts = state.accountsOf(providerId)
    val provider = state.providerOf(providerId)
    val hasLedger = provider?.capabilities?.contains(ProviderCapability.POINTS_BILLS) == true
    val scrollBehavior = MiuixScrollBehavior()
    val layoutDirection = LocalLayoutDirection.current
    val backdrop = rememberMiuixBlurBackdrop(false)

    LaunchedEffect(providerId, accounts.map { it.uid }) {
        accounts.forEach {
            viewModel.loadCreditPacks(providerId, it.uid)
            if (hasLedger) viewModel.loadPointsBills(providerId, it.uid)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                modifier = Modifier.miuixBlurEffect(backdrop),
                color = backdrop.getMiuixAppBarColor(),
                title = provider?.displayName ?: providerId,
                subtitle = "额度包明细",
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
            if (accounts.isEmpty()) {
                item { Spacer(modifier = Modifier.size(12.dp)) }
                item {
                    PreferenceCard {
                        PreferenceHint("还没有账号。先去该供应商页添加账号。")
                    }
                }
                item { Spacer(modifier = Modifier.navigationBarsPadding()) }
                return@LazyColumn
            }

            item { Spacer(modifier = Modifier.size(12.dp)) }
            item { SmallTitle("账号额度") }
            items(accounts.size, key = { accounts[it].uid }) { index ->
                val account = accounts[index]
                val packs = state.creditPacks["$providerId/${account.uid}"].orEmpty()
                val bills = state.pointsBills["$providerId/${account.uid}"]

                PreferenceCard {
                    NavigationRow(
                        title = account.nickname.ifEmpty { account.uid },
                        summary = buildString {
                            append("余额 ")
                            append(if (account.creditsKnown) account.credits.toString() else "—")
                            append(" · ")
                            append(if (account.disabled) "凭证失效" else if (!account.enabled) "已停用" else "可用")
                        },
                        onClick = {},
                    )

                    if (packs.isEmpty()) {
                        PreferenceHint(
                            text = when {
                                // 账本型供应商没有额度包，额度就是积分余额
                                hasLedger -> "该供应商没有额度包，积分流水见下"
                                !account.creditsKnown && account.detail.isNotEmpty() -> account.detail
                                else -> "没有额度包明细"
                            },
                        )
                    } else {
                        for (pack in packs) {
                            QuotaPackRow(pack)
                        }
                    }

                    if (hasLedger) {
                        Text(
                            text = "积分明细",
                            style = MiuixTheme.textStyles.subtitle,
                            color = MiuixTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(start = 16.dp, top = 8.dp, end = 16.dp),
                        )
                        when {
                            bills == null -> PreferenceHint("正在加载积分明细…")
                            bills.error.isNotEmpty() -> PreferenceHint("加载失败：${bills.error}", error = true)
                            bills.bills.isEmpty() -> PreferenceHint("暂无积分明细")
                            else -> for (bill in bills.bills) PointsBillRow(bill)
                        }
                    }

                    PreferenceActionRow(
                        text = "刷新",
                        onClick = {
                            viewModel.refreshCredits(providerId, account.uid)
                            viewModel.loadCreditPacks(providerId, account.uid)
                            if (hasLedger) viewModel.loadPointsBills(providerId, account.uid)
                        },
                    )
                }
            }

            item { Spacer(modifier = Modifier.navigationBarsPadding()) }
        }
    }
}

/** 单个额度包：名称（分组）+ 作用范围 + 进度条 + 剩余/总量 + 过期时间。 */
@Composable
private fun QuotaPackRow(pack: QuotaPack) {
    // limit 为 0 表示上游没给总量：此时「占比」无从谈起，按未知处理而不是画成 0%
    val fraction = if (pack.limit > 0) {
        (pack.remain.toFloat() / pack.limit).coerceIn(0f, 1f)
    } else {
        null
    }
    val barColor = when {
        fraction == null -> MiuixTheme.colorScheme.primary
        fraction > 0.5f -> MiuixTheme.colorScheme.primary
        fraction > 0.2f -> MiuixTheme.colorScheme.error.copy(alpha = 0.6f)
        else -> MiuixTheme.colorScheme.error
    }
    val expireText = remember(pack.expireAt) {
        if (pack.expireAt > 0) {
            DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                .format(Date(pack.expireAt * 1000))
        } else {
            ""
        }
    }

    Column(
        // 与表格行一致的水平内边距，避免进度条贴卡片边缘
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // 名称与过期时间上下排：额度包名往往很长（含分组），与时间挤在一行会互相挤压，
        // 窄屏上时间被压成两行、名称被截断，读起来都很别扭。
        Text(
            text = if (pack.group.isEmpty()) pack.name else "${pack.name}（${pack.group}）",
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        // 模型专属配额：不标出来会被误当成账号总额度
        if (pack.scope.isNotEmpty()) {
            Text(
                text = pack.scope,
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.primary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (expireText.isNotEmpty()) {
            Text(
                text = "有效期至 $expireText",
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                maxLines = 1,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(5.dp)
                    .clip(CircleShape)
                    .background(MiuixTheme.colorScheme.surfaceContainerHighest),
            ) {
                if (fraction != null && fraction > 0f) {
                    Box(
                        Modifier
                            .fillMaxWidth(fraction)
                            .height(5.dp)
                            .clip(CircleShape)
                            .background(barColor),
                    )
                }
            }
            Text(
                text = if (fraction != null) {
                    "${(fraction * 100).toInt()}%"
                } else {
                    "剩余 ${pack.remain}"
                },
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                maxLines = 1,
                softWrap = false,
            )
        }
        if (fraction != null) {
            Text(
                text = "剩余 ${pack.remain} / ${pack.limit}",
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
    }
}

/** 一笔积分流水。 */
@Composable
private fun PointsBillRow(bill: PointsBill) {
    val time = remember(bill.createdAtMillis) {
        if (bill.createdAtMillis > 0) {
            DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                .format(Date(bill.createdAtMillis))
        } else {
            ""
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = bill.title,
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurface,
            )
            if (time.isNotEmpty()) {
                Text(
                    text = time,
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }
        Text(
            text = if (bill.points > 0) "+${bill.points}" else bill.points.toString(),
            style = MiuixTheme.textStyles.footnote1,
            color = when {
                bill.points > 0 -> MiuixTheme.colorScheme.primary
                bill.points < 0 -> MiuixTheme.colorScheme.error
                else -> MiuixTheme.colorScheme.onSurfaceVariantSummary
            },
        )
    }
}
