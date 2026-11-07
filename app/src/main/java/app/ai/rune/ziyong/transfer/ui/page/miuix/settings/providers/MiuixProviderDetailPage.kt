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
                        AuthKind.COOKIE_PASTE -> CookiePasteForm(providerId, viewModel)
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

/**
 * 粘贴 Cookie 登录（腾讯元宝等 Cookie 会话类供应商）。
 *
 * 元宝没有 OAuth 回调：用户在浏览器（电脑/手机均可）登录后，
 * 从开发者工具复制请求里的 Cookie 串，粘贴到这里完成导入。
 * 导入成功会立即出现在下方账号列表。
 */
@Composable
private fun CookiePasteForm(providerId: String, viewModel: AppViewModel) {
    var cookie by remember { mutableStateOf("") }

    PreferenceBlock {
        PreferenceHint(
            "在浏览器打开 yuanbao.tencent.com 并登录，然后 F12 → Network → 刷新一次页面，" +
                "复制任意请求的 Cookie 请求头粘贴到这里。",
        )
        TextField(
            value = cookie,
            onValueChange = { cookie = it },
            label = "完整 Cookie 串（含 hy_token / hy_user）",
            minLines = 3,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    PreferenceActionRow(
        text = "导入账号",
        enabled = cookie.isNotBlank(),
        onClick = {
            viewModel.importCredentials(providerId, cookie.trim())
            cookie = ""
        },
    )
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
    var showUskey by remember { mutableStateOf(false) }

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

    // 元宝：对话需要 qimei USKey，配置入口放在账号级（支持铸造服务 URL / 静态签名）
    if (provider.authKind == AuthKind.COOKIE_PASTE) {
        val uskey = remember(account.uid) { viewModel.yuanbaoUskeyOf(provider.id, account.uid) }
        NavigationRow(
            title = "USKey 签名（对话必需）",
            summary = when {
                uskey.serviceUrl.isNotEmpty() -> "铸造服务：${uskey.serviceUrl}"
                uskey.hasStatic() -> "静态签名已配置（${if (uskey.timestamp > 0) "约 ${TimeFormat.format(uskey.timestamp)}" else "未记时间"}）"
                else -> "未配置：对话会被拒绝（避免白扣额度），点此配置"
            },
            onClick = { showUskey = true },
        )
    }

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

    if (showUskey) {
        YuanbaoUskeyDialog(
            account = account,
            initial = viewModel.yuanbaoUskeyOf(provider.id, account.uid),
            viewModel = viewModel,
            onDismiss = { showUskey = false },
            onSave = { config ->
                viewModel.saveYuanbaoUskey(provider.id, account.uid, config)
                showUskey = false
            },
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

/**
 * 元宝 USKey 配置对话框。
 *
 * 两种来源二选一（同时填时铸造服务优先）：
 * 1. **铸造服务 URL**：`uskey_browser.py --serve <port>` 的地址（如
 *    `http://127.0.0.1:8787/uskey`），每次对话前自动取新鲜签名——推荐，长期有效；
 * 2. **静态签名**：浏览器抓包 `/api/chat` 请求的 X-Uskey / X-HY92(h38) /
 *    X-Timestamp / 参数串，短暂有效（约 5 分钟），仅用于验证跑通。
 *
 * 「清除配置」= 恢复未配置：对话会被拒绝（避免被上游拒绝时白扣额度）。
 */
@Composable
private fun YuanbaoUskeyDialog(
    account: AccountStatus,
    initial: dev.aigw.core.provider.yuanbao.UskeyConfig,
    viewModel: AppViewModel,
    onDismiss: () -> Unit,
    onSave: (dev.aigw.core.provider.yuanbao.UskeyConfig) -> Unit,
) {
    var serviceUrl by remember(initial.serviceUrl) { mutableStateOf(initial.serviceUrl) }
    var uskey by remember(initial.uskey) { mutableStateOf(initial.uskey) }
    var h38 by remember(initial.h38) { mutableStateOf(initial.h38) }
    var timestamp by remember(initial.timestamp) {
        mutableStateOf(if (initial.timestamp > 0) initial.timestamp.toString() else "")
    }
    var params by remember(initial.params) { mutableStateOf(initial.params) }
    var builtinState by remember { mutableStateOf("") } // "" 空闲 / "starting" 启动中 / "ok" 成功 / "error"
    var builtinError by remember { mutableStateOf("") }

    WindowDialog(
        show = true,
        title = "配置 USKey 签名（${account.nickname.ifEmpty { account.uid }}）",
        onDismissRequest = onDismiss,
    ) {
        PreferenceCard {
            PreferenceHint(
                "元宝对话需要 qimei USKey（浏览器环境铸造，短暂有效）。" +
                    "可一键使用「内置铸造器」（本机 WebView 铸造，无需电脑），" +
                    "或手动填铸造服务地址 / 静态签名。",
            )
            // 一键内置铸造器
            PreferenceButtonRow {
                TextButton(
                    text = "使用内置铸造器",
                    enabled = builtinState != "starting",
                    onClick = {
                        builtinState = "starting"
                        builtinError = ""
                        viewModel.useBuiltinUskeyMinter()?.let { url ->
                            serviceUrl = url
                            builtinState = "ok"
                        } ?: run {
                            builtinState = "error"
                            builtinError = viewModel.builtinUskeyError()
                        }
                    },
                    modifier = Modifier.weight(1f),
                )
            }
            when (builtinState) {
                "ok" -> PreferenceHint("内置铸造器已就绪：$serviceUrl（点击下方「保存」生效）")
                "starting" -> PreferenceHint("正在启动内置铸造器（首次加载页面需几秒）…")
                "error" -> PreferenceHint("内置铸造器启动失败：$builtinError", error = true)
            }
            PreferenceBlock {
                TextField(
                    value = serviceUrl,
                    onValueChange = { serviceUrl = it.trim() },
                    label = "USKey 铸造服务地址（如 http://127.0.0.1:8787/uskey）",
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            PreferenceHint("用浏览器抓包一次 /api/chat，把 X-Uskey 与 h38 填到下面（两分钟有效，仅验证用）：")
            PreferenceBlock {
                TextField(
                    value = uskey,
                    onValueChange = { uskey = it.trim() },
                    label = "X-Uskey（URL 编码后的值）",
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                TextField(
                    value = h38,
                    onValueChange = { h38 = it.trim() },
                    label = "h38（X-HY92，38 位设备标识）",
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                TextField(
                    value = timestamp,
                    onValueChange = { timestamp = it.filter(Char::isDigit).take(13) },
                    label = "X-Timestamp（毫秒，可留空自动用当前时间）",
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                TextField(
                    value = params,
                    onValueChange = { params = it.trim() },
                    label = "参数串（h38=..&timestamp=..&platform=web，可留空自动拼）",
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            PreferenceButtonRow {
                TextButton(
                    text = "清除配置",
                    onClick = {
                        onSave(dev.aigw.core.provider.yuanbao.UskeyConfig())
                    },
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    text = "保存",
                    onClick = {
                        val ts = timestamp.toLongOrNull() ?: 0L
                        onSave(
                            dev.aigw.core.provider.yuanbao.UskeyConfig(
                                serviceUrl = serviceUrl,
                                uskey = uskey,
                                h38 = h38,
                                timestamp = ts,
                                params = params,
                            ),
                        )
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** 静态签名时间戳的展示格式。 */
private object TimeFormat {
    fun format(epochMillis: Long): String {
        val sdf = java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault())
        return sdf.format(java.util.Date(epochMillis))
    }
}
