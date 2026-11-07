// SPDX-License-Identifier: GPL-3.0-only
package app.ai.rune.ziyong.transfer.ui.page.miuix.settings.endpoints

import android.content.ClipData
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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.ai.rune.ziyong.transfer.gateway.AppViewModel
import app.ai.rune.ziyong.transfer.ui.page.miuix.settings.common.NavigationRow
import app.ai.rune.ziyong.transfer.ui.page.miuix.settings.common.PreferenceActionRow
import app.ai.rune.ziyong.transfer.ui.page.miuix.settings.common.PreferenceBlock
import app.ai.rune.ziyong.transfer.ui.page.miuix.settings.common.PreferenceCard
import app.ai.rune.ziyong.transfer.ui.page.miuix.settings.common.PreferenceHint
import app.ai.rune.ziyong.transfer.ui.page.miuix.settings.common.SwitchRow
import app.ai.rune.ziyong.transfer.ui.theme.getMiuixAppBarColor
import app.ai.rune.ziyong.transfer.ui.theme.miuixBlurEffect
import app.ai.rune.ziyong.transfer.ui.theme.rememberMiuixBlurBackdrop
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/**
 * 接入设置：客户端要用的地址、端口与 API Key。
 *
 * 排版对齐模板「关于」页：SmallTitle 分组 + PreferenceCard。
 * 地址条用 NavigationRow（点击复制），可点击性一眼可见。
 */
@Composable
fun MiuixEndpointsPage(
    viewModel: AppViewModel = koinViewModel(),
    onBack: () -> Unit,
    outerPadding: PaddingValues,
) {
    val uiState by viewModel.state.collectAsStateWithLifecycle()
    val settings = uiState.settings
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val scrollBehavior = MiuixScrollBehavior()
    val layoutDirection = LocalLayoutDirection.current
    val backdrop = rememberMiuixBlurBackdrop(false)

    var port by remember(settings.port) { mutableStateOf(settings.port.toString()) }
    var apiKey by remember(settings.apiKey) { mutableStateOf(settings.apiKey) }

    fun copy(text: String) {
        scope.launch {
            runCatching { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("text", text))) }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                modifier = Modifier.miuixBlurEffect(backdrop),
                color = backdrop.getMiuixAppBarColor(),
                title = "接入设置",
                subtitle = if (uiState.running) "网关运行中" else "网关已停止",
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

            item { SmallTitle("客户端填这些") }
            item {
                PreferenceCard {
                    val localUrl = uiState.localUrl.ifEmpty { "http://127.0.0.1:${uiState.port}/v1" }
                    NavigationRow(
                        title = "接口地址（本机）",
                        summary = localUrl,
                        onClick = { copy(localUrl) },
                    )
                    val lanUrl = uiState.lanUrls.firstOrNull()
                    NavigationRow(
                        title = "接口地址（局域网）",
                        summary = lanUrl ?: "未连接 Wi-Fi 或未开启局域网监听",
                        onClick = { lanUrl?.let { copy(it) } },
                    )
                    NavigationRow(
                        title = "API Key",
                        summary = when {
                            settings.apiKey.isNotBlank() -> settings.apiKey
                            settings.allowNoKey -> "当前不校验（可留空）"
                            else -> "已关闭无 Key 调用，但尚未设置 Key"
                        },
                        onClick = { settings.apiKey.takeIf { it.isNotBlank() }?.let { copy(it) } },
                    )
                    NavigationRow(
                        title = "模型列表地址",
                        summary = "${localUrl.removeSuffix("/v1")}/v1/models",
                        onClick = { copy("${localUrl.removeSuffix("/v1")}/v1/models") },
                    )
                }
            }

            item { SmallTitle("端口与网络") }
            item {
                PreferenceCard {
                    PreferenceBlock {
                        TextField(
                            value = port,
                            onValueChange = { port = it.filter(Char::isDigit).take(5) },
                            label = "监听端口（1024~65535）",
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        // 端口与运行中不一致时提示：出现/消失带动画
                        AnimatedVisibility(
                            visible = uiState.running && port != uiState.port.toString(),
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically(),
                        ) {
                            PreferenceHint("端口改动需重启网关后生效", error = true)
                        }
                    }
                    SwitchRow(
                        title = "同时监听局域网",
                        summary = "关闭时仅 127.0.0.1 可访问；开启会把端口暴露给同网段设备",
                        checked = settings.exposeLan,
                        onCheckedChange = { viewModel.updateSettings(settings.copy(exposeLan = it)) },
                    )
                }
            }

            item { SmallTitle("鉴权") }
            item {
                PreferenceCard {
                    PreferenceBlock {
                        TextField(
                            value = apiKey,
                            onValueChange = { apiKey = it },
                            label = "API Key（留空表示不校验）",
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    PreferenceActionRow(
                        text = "生成随机 Key",
                        onClick = { apiKey = randomApiKey() },
                    )
                    SwitchRow(
                        title = "允许无 Key 调用",
                        summary = "关闭后客户端必须携带上面的 Key 才能调用",
                        checked = settings.allowNoKey,
                        onCheckedChange = { viewModel.updateSettings(settings.copy(allowNoKey = it)) },
                    )
                }
            }

            item {
                PreferenceCard {
                    PreferenceActionRow(
                        text = "保存",
                        onClick = {
                            val parsed = port.toIntOrNull()
                            if (parsed == null || parsed !in 1024..65535) {
                                viewModel.notice("端口需要在 1024~65535 之间")
                                return@PreferenceActionRow
                            }
                            viewModel.updateSettings(settings.copy(port = parsed, apiKey = apiKey.trim()))
                            viewModel.notice(
                                if (uiState.running && parsed != uiState.port) {
                                    "已保存；端口改动需重启网关生效"
                                } else {
                                    "已保存"
                                },
                            )
                        },
                    )
                }
            }

            item { Spacer(modifier = Modifier.navigationBarsPadding()) }
        }
    }
}

/** 生成 32 位十六进制随机 Key：足够长、无需用户自己编。 */
private fun randomApiKey(): String {
    val chars = "0123456789abcdef"
    return (1..32).map { chars.random() }.joinToString("")
}
