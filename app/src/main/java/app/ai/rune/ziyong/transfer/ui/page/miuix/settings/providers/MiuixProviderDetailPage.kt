// SPDX-License-Identifier: GPL-3.0-only
package app.ai.rune.ziyong.transfer.ui.page.miuix.settings.providers

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
import androidx.compose.runtime.LaunchedEffect
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
import dev.aigw.core.gateway.ProviderInfo
import dev.aigw.core.pool.AccountStatus
import dev.aigw.core.provider.AuthKind
import dev.aigw.core.provider.ProviderCapability
import kotlinx.coroutines.delay
import org.koin.androidx.compose.koinViewModel
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Edit
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import top.yukonga.miuix.kmp.window.WindowDialog

/**
 * 供应商详情：登录入口 + 账号管理。
 *
 * 排版对齐模板「关于」页：`SmallTitle` 分组 + `PreferenceCard`，
 * 登录入口按 [AuthKind] 渲染，账号行用 `SwitchRow` 管理启用状态。
 */
@Composable
fun MiuixProviderDetailPage(
    providerId: String,
    viewModel: AppViewModel = koinViewModel(),
    onBack: () -> Unit,
    outerPadding: PaddingValues,
    onEditCustom: ((String) -> Unit)? = null,
) {
    val uiState by viewModel.state.collectAsStateWithLifecycle()
    val provider = remember(uiState.providers, providerId) {
        uiState.providers.firstOrNull { it.id == providerId }
    }
    val accounts = uiState.accountsOf(providerId)
    val scrollBehavior = MiuixScrollBehavior()
    val layoutDirection = LocalLayoutDirection.current
    val backdrop = rememberMiuixBlurBackdrop(false)

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                modifier = Modifier.miuixBlurEffect(backdrop),
                color = backdrop.getMiuixAppBarColor(),
                title = provider?.displayName ?: providerId,
                subtitle = "${accounts.size} 个账号 · 可用 ${accounts.count { it.usable }} 个",
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = MiuixIcons.Regular.Back,
                            contentDescription = "返回",
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
                actions = {
                    if (provider?.custom == true && onEditCustom != null) {
                        IconButton(onClick = { onEditCustom(provider.id.removePrefix("custom:")) }) {
                            Icon(
                                imageVector = MiuixIcons.Regular.Edit,
                                contentDescription = "编辑供应商",
                                tint = MiuixTheme.colorScheme.primary,
                            )
                        }
                    }
                },
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

            if (provider == null) {
                item { SmallTitle("供应商") }
                item {
                    PreferenceCard {
                        PreferenceHint("供应商不存在，可能已被删除。", error = true)
                    }
                }
                item { Spacer(modifier = Modifier.navigationBarsPadding()) }
                return@LazyColumn
            }

            item { SmallTitle("登录 / 添加账号") }
            item {
                PreferenceCard {
                    when (provider.authKind) {
                        AuthKind.NONE -> CustomAccountForm(providerId, viewModel)
                        AuthKind.SMS_CODE -> SmsLoginForm(providerId, viewModel)
                        else -> NavigationRow(
                            title = if (provider.authKind == AuthKind.DEVICE_CODE) {
                                "在浏览器中完成设备授权"
                            } else {
                                "在浏览器中登录"
                            },
                            summary = "登录期间网关会监听本地回调端口，完成后自动添加账号",
                            onClick = { viewModel.beginBrowserLogin(provider.id, "") },
                        )
                    }
                }
            }

            item { SmallTitle("账号") }
            if (accounts.isEmpty()) {
                item {
                    PreferenceCard {
                        PreferenceHint("还没有账号，先用上方入口添加。")
                    }
                }
            } else {
                for (account in accounts) {
                    item(key = account.uid) {
                        PreferenceCard {
                            AccountBlock(
                                account = account,
                                provider = provider,
                                viewModel = viewModel,
                            )
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.navigationBarsPadding()) }
        }
    }
}

/** 短信验证码登录。 */
@Composable
private fun SmsLoginForm(providerId: String, viewModel: AppViewModel) {
    var ccode by remember { mutableStateOf("86") }
    var phone by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var countdown by remember { mutableStateOf(0) }

    // 上游有频率限制，连点只会被拒
    LaunchedEffect(countdown) {
        if (countdown > 0) {
            delay(1_000)
            countdown -= 1
        }
    }

    PreferenceBlock {
        TextField(
            value = ccode,
            onValueChange = { ccode = it.filter(Char::isDigit).take(4) },
            label = "国家码",
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        TextField(
            value = phone,
            onValueChange = { phone = it.filter(Char::isDigit).take(15) },
            label = "手机号",
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            modifier = Modifier.fillMaxWidth(),
        )
        TextField(
            value = code,
            onValueChange = { code = it.filter(Char::isDigit).take(8) },
            label = "验证码",
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
    }
    PreferenceButtonRow {
        TextButton(
            text = if (countdown > 0) "${countdown}s 后重发" else "发送验证码",
            enabled = phone.isNotBlank() && countdown == 0,
            modifier = Modifier.weight(1f),
            onClick = {
                countdown = 60
                viewModel.sendSmsCode(providerId, phone.trim(), ccode.trim()) { error ->
                    // 发送失败立刻恢复可点，否则用户要等满 60 秒
                    if (error.isNotEmpty()) countdown = 0
                }
            },
        )
        TextButton(
            text = "登录",
            enabled = phone.isNotBlank() && code.isNotBlank(),
            modifier = Modifier.weight(1f),
            onClick = { viewModel.smsLogin(providerId, phone.trim(), code.trim(), ccode.trim()) { } },
        )
    }
}

/** 自定义供应商：名称 + API Key + 模型列表。 */
@Composable
private fun CustomAccountForm(providerId: String, viewModel: AppViewModel) {
    var nickname by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }
    var models by remember { mutableStateOf("") }

    PreferenceBlock {
        PreferenceHint("接口地址在供应商设置里维护；这里只需填 Key 与模型。")
        TextField(
            value = nickname,
            onValueChange = { nickname = it },
            label = "名称（可选）",
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        TextField(
            value = apiKey,
            onValueChange = { apiKey = it },
            label = "API Key",
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        TextField(
            value = models,
            onValueChange = { models = it },
            label = "模型（一行一个，可留空稍后编辑）",
            minLines = 2,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    PreferenceActionRow(
        text = "添加账号",
        enabled = apiKey.isNotBlank(),
        onClick = {
            val list = models.lines().map { it.trim() }.filter { it.isNotEmpty() }
            viewModel.addCustomAccount(providerId, nickname.trim(), apiKey.trim(), list)
            nickname = ""
            apiKey = ""
            models = ""
        },
    )
}

/** 单个账号：启用开关 + 额度信息 + 刷新/删除。 */
@Composable
private fun AccountBlock(
    account: AccountStatus,
    provider: ProviderInfo,
    viewModel: AppViewModel,
) {
    var confirmDelete by remember { mutableStateOf(false) }

    SwitchRow(
        title = account.nickname.ifEmpty { account.uid },
        summary = buildString {
            append(if (account.disabled) "凭证失效" else if (!account.enabled) "已停用" else "可用")
            if (account.creditsKnown) {
                append(" · 额度 ")
                append(account.credits)
            }
            if (account.detail.isNotEmpty()) {
                append(" · ")
                append(account.detail)
            }
        },
        checked = account.enabled,
        onCheckedChange = { viewModel.setAccountEnabled(provider.id, account.uid, it) },
    )

    PreferenceButtonRow {
        if (ProviderCapability.CREDIT_REFRESH in provider.capabilities) {
            TextButton(
                text = "刷新额度",
                onClick = { viewModel.refreshCredits(provider.id, account.uid) },
                modifier = Modifier.weight(1f),
            )
        }
        TextButton(
            text = "删除",
            onClick = { confirmDelete = true },
            modifier = Modifier.weight(1f),
        )
    }

    if (confirmDelete) {
        WindowDialog(
            show = true,
            title = "删除这个账号？",
            onDismissRequest = { confirmDelete = false },
        ) {
            PreferenceHint("本地保存的凭证会被清除，之后要重新登录才能再用。此操作不可撤销。")
            PreferenceButtonRow {
                TextButton(
                    text = "取消",
                    onClick = { confirmDelete = false },
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    text = "删除",
                    modifier = Modifier.weight(1f),
                    onClick = {
                        // 先关弹窗再执行：删除会触发列表重建，弹窗留在组合里会引用已失效的账号
                        confirmDelete = false
                        viewModel.removeAccount(provider.id, account.uid)
                    },
                )
            }
        }
    }
}
