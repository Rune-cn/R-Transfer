// SPDX-License-Identifier: GPL-3.0-only
package app.ai.rune.ziyong.transfer.ui.page.miuix.settings.providers

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
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
import app.ai.rune.ziyong.transfer.ui.theme.getMiuixAppBarColor
import app.ai.rune.ziyong.transfer.ui.theme.miuixBlurEffect
import app.ai.rune.ziyong.transfer.ui.theme.rememberMiuixBlurBackdrop
import dev.aigw.core.gateway.CustomProviderConfig
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
import top.yukonga.miuix.kmp.window.WindowDialog
import app.ai.rune.ziyong.transfer.ui.page.miuix.settings.common.PreferenceCard
import androidx.compose.foundation.layout.size

/**
 * 自定义供应商的新建 / 编辑。
 *
 * 这里只维护「供应商身份」：名称、短名、接口地址。
 * API Key 与模型列表属于账号级信息，在供应商详情页维护——把它们混在一起会让
 * 一个供应商只能配一组 Key，与实际（一个站点可以挂多个 Key 轮流用）不符。
 *
 * 短名建好后不可改：它已经被写进模型路由标识（`custom:<短名>/模型`），
 * 允许修改会让所有已配置的客户端同时失效。
 */
@Composable
fun MiuixCustomProviderPage(
    existingKey: String?,
    viewModel: AppViewModel = koinViewModel(),
    onBack: () -> Unit,
    onSaved: (providerId: String) -> Unit,
    outerPadding: PaddingValues,
) {
    val uiState by viewModel.state.collectAsStateWithLifecycle()
    val existing = remember(uiState.customProviders, existingKey) {
        existingKey?.let { key -> uiState.customProviders.firstOrNull { it.key == key } }
    }

    var name by remember(existing) { mutableStateOf(existing?.name.orEmpty()) }
    var key by remember(existing) { mutableStateOf(existing?.key.orEmpty()) }
    var baseUrl by remember(existing) { mutableStateOf(existing?.baseUrl.orEmpty()) }
    var enabled by remember(existing) { mutableStateOf(existing?.enabled ?: true) }
    var confirmDelete by remember { mutableStateOf(false) }

    val scrollBehavior = MiuixScrollBehavior()
    val layoutDirection = LocalLayoutDirection.current
    val backdrop = rememberMiuixBlurBackdrop(false)

    // 短名只允许小写字母、数字与连字符：它要出现在模型名前缀里，
    // 带空格或中文会让客户端难以输入，也会让 URL 拼接出歧义。
    val keyValid = key.isNotBlank() && key.all { it.isLetterOrDigit() && it.code < 128 } && !key.contains(' ')

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                modifier = Modifier.miuixBlurEffect(backdrop),
                color = backdrop.getMiuixAppBarColor(),
                title = if (existing == null) "新建供应商" else "编辑供应商",
                subtitle = "OpenAI 兼容接口",
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
            item { SmallTitle("基本信息") }
            item {
                PreferenceCard {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextField(
                            value = name,
                            onValueChange = { name = it },
                            label = "显示名称",
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        TextField(
                            value = key,
                            onValueChange = { key = it.trim() },
                            label = "短名（用于模型前缀 custom:<短名>）",
                            singleLine = true,
                            // 已存在的供应商不允许改短名，见文件头说明
                            enabled = existing == null,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (!keyValid && key.isNotEmpty()) {
                            Text(
                                text = "短名只能包含英文字母、数字与连字符",
                                style = MiuixTheme.textStyles.footnote2,
                                color = MiuixTheme.colorScheme.error,
                            )
                        }
                        TextField(
                            value = baseUrl,
                            onValueChange = { baseUrl = it.trim() },
                            label = "接口地址（到 /v1，或直接写 /v1/chat/completions）",
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    SwitchRow(
                        title = "启用",
                        summary = "关闭后该供应商的模型不再对外暴露",
                        checked = enabled,
                        onCheckedChange = { enabled = it },
                    )
                }
            }

            item { SmallTitle("怎么填") }
            item {
                PreferenceCard {
                    PreferenceHint(
                        "接口地址：多数中转站填到 /v1 即可，例如 https://api.example.com/v1；" +
                            "若服务商要求完整路径，也可直接写 /v1/chat/completions",
                    )
                    PreferenceHint("下一步：保存后进入该供应商，用「添加账号」填入 API Key 与模型名。")
                }
            }

            item {
                TextButton(
                    text = "保存",
                    onClick = {
                        val trimmedKey = key.trim()
                        val config = CustomProviderConfig(
                            key = trimmedKey,
                            name = name.trim().ifEmpty { trimmedKey },
                            baseUrl = baseUrl.trim(),
                            // 账号级信息沿用原值，不在本页改动
                            apiKeys = existing?.apiKeys.orEmpty(),
                            models = existing?.models.orEmpty(),
                            enabled = enabled,
                        )
                        viewModel.saveCustomProvider(config)
                        onSaved(config.providerId)
                    },
                    enabled = keyValid && baseUrl.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (existing != null) {
                item {
                    TextButton(
                        text = "删除该供应商",
                        onClick = { confirmDelete = true },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            item { Spacer(modifier = Modifier.navigationBarsPadding()) }
        }
    }

    if (confirmDelete && existing != null) {
        WindowDialog(
            show = true,
            title = "删除这个供应商？",
            onDismissRequest = { confirmDelete = false },
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "「${existing.name.ifEmpty { existing.key }}」及其全部账号、模型配置都会被移除，" +
                        "调用过它的客户端将无法再使用。此操作不可撤销。",
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        text = "取消",
                        onClick = { confirmDelete = false },
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        text = "删除",
                        modifier = Modifier.weight(1f),
                        onClick = {
                            confirmDelete = false
                            viewModel.removeCustomProvider(existing.key)
                            onBack()
                        },
                    )
                }
            }
        }
    }
}
