// SPDX-License-Identifier: GPL-3.0-only
package app.ai.rune.ziyong.transfer.ui.page.miuix.settings.transfer

import android.content.ClipData
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.ai.rune.ziyong.transfer.gateway.AppViewModel
import app.ai.rune.ziyong.transfer.ui.theme.getMiuixAppBarColor
import app.ai.rune.ziyong.transfer.ui.theme.miuixBlurEffect
import app.ai.rune.ziyong.transfer.ui.theme.rememberMiuixBlurBackdrop
import dev.aigw.core.pool.AccountImportResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.androidx.compose.koinViewModel
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Checkbox
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
import app.ai.rune.ziyong.transfer.ui.page.miuix.settings.common.PreferenceCard

/** 勾选树里的账号：导出（AccountStatus）与导入（ProviderAccount）共用。 */
private data class SelectableAccount(val providerId: String, val uid: String, val nickname: String) {
    val key: String get() = "$providerId/$uid"
}

/**
 * 账号导入导出：换机与备份。
 *
 * ⚠ 导出内容含各账号的登录凭证（token），界面上必须把这一点说清楚：
 * 全程只经剪贴板或用户自选的文件，不联网、不上传。
 *
 * 粒度是「供应商 → 账号」两级：默认全部，也可以只导出某个供应商或其中一两个账号。
 * 导入同样先预览再勾选，避免一次误导入覆盖掉可用账号。
 */
@Composable
fun MiuixAccountTransferPage(
    viewModel: AppViewModel = koinViewModel(),
    onBack: () -> Unit,
    outerPadding: PaddingValues,
) {
    val context = LocalContext.current
    val uiState by viewModel.state.collectAsStateWithLifecycle()
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val scrollBehavior = MiuixScrollBehavior()
    val layoutDirection = LocalLayoutDirection.current
    val backdrop = rememberMiuixBlurBackdrop(false)

    // 导出范围
    var exportAll by remember { mutableStateOf(true) }
    var exportSelection by remember { mutableStateOf(emptySet<String>()) }
    var exportedChars by remember { mutableStateOf(0) }

    // 导入
    var importText by remember { mutableStateOf("") }
    var parsed by remember { mutableStateOf<List<SelectableAccount>?>(null) }
    var importSelection by remember { mutableStateOf(emptySet<String>()) }
    var overwrite by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<AccountImportResult?>(null) }
    var importError by remember { mutableStateOf("") }

    val exportGroups = uiState.providers
        .map { info ->
            info.id to uiState.accountsOf(info.id).map { SelectableAccount(it.providerId, it.uid, it.nickname) }
        }
        .filter { it.second.isNotEmpty() }
    val exportCount = if (exportAll) exportGroups.sumOf { it.second.size } else exportSelection.size

    fun currentExportJson(): String =
        viewModel.exportAccountsJson(if (exportAll) emptySet() else exportSelection)

    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            // 导出要逐条解密凭证，放到 IO 线程做：几十个账号在主线程跑会明显卡顿
            val json = withContext(Dispatchers.IO) { currentExportJson() }
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) } != null
                }.getOrDefault(false)
            }
            viewModel.notice(if (ok) "已导出 $exportCount 个账号" else "写入文件失败")
        }
    }

    val openLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
                }.getOrNull()
            }
            if (text.isNullOrBlank()) {
                importError = "读取文件失败或文件为空"
                return@launch
            }
            importText = text
            parsed = null
            importError = ""
            result = null
        }
    }

    // 粘贴即识别：防抖 250ms，输入过程中不抖解析错误
    LaunchedEffect(importText) {
        val text = importText.trim()
        if (text.isEmpty()) {
            parsed = null
            importError = ""
            return@LaunchedEffect
        }
        delay(250)
        runCatching { viewModel.parseAccountTransfer(text) }
            .onSuccess { accounts ->
                val mapped = accounts.map { SelectableAccount(it.providerId, it.uid, it.nickname) }
                parsed = mapped
                val keys = mapped.map { it.key }.toSet()
                // 换一份文件后不能沿用上一份的选择
                importSelection = if (importSelection.isEmpty() || !keys.containsAll(importSelection)) {
                    keys
                } else {
                    importSelection.intersect(keys)
                }
                importError = ""
            }
            .onFailure { e ->
                parsed = null
                importError = e.message ?: "解析失败"
            }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                modifier = Modifier.miuixBlurEffect(backdrop),
                color = backdrop.getMiuixAppBarColor(),
                title = "账号导入导出",
                subtitle = "备份与迁移",
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
            item { SmallTitle("导出") }
            item {
                PreferenceCard() {
                    Text(
                        text = "导出内容含各账号的登录凭证（token），请勿外传。全程只经剪贴板或你选定的文件，不联网。",
                        style = MiuixTheme.textStyles.footnote2,
                        color = MiuixTheme.colorScheme.error,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = "导出全部账号",
                                style = MiuixTheme.textStyles.subtitle,
                                color = MiuixTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = if (exportAll) {
                                    "共 $exportCount 个账号"
                                } else {
                                    "已勾选 $exportCount 个"
                                },
                                style = MiuixTheme.textStyles.footnote2,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                        Switch(checked = exportAll, onCheckedChange = { exportAll = it })
                    }

                    if (!exportAll) {
                        AccountTree(
                            groups = exportGroups,
                            displayName = { id -> uiState.providerOf(id)?.displayName ?: id },
                            selected = exportSelection,
                            onSelectionChange = { exportSelection = it },
                        )
                    }

                    TextButton(
                        text = "复制到剪贴板",
                        onClick = {
                            if (exportCount == 0) {
                                viewModel.notice("没有选中任何账号")
                                return@TextButton
                            }
                            val json = currentExportJson()
                            exportedChars = json.length
                            scope.launch {
                                runCatching {
                                    clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("accounts", json)))
                                }
                                viewModel.notice("已复制 $exportCount 个账号")
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    TextButton(
                        text = "导出为 JSON 文件",
                        onClick = {
                            if (exportCount == 0) {
                                viewModel.notice("没有选中任何账号")
                                return@TextButton
                            }
                            saveLauncher.launch("r-transfer-accounts.json")
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (exportedChars > 0) {
                        Text(
                            text = "最近一次导出：$exportedChars 字符",
                            style = MiuixTheme.textStyles.footnote2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            item { SmallTitle("导入") }
            item {
                PreferenceCard() {
                    Text(
                        text = "粘贴导出内容，或选择一个 .json 文件。内容贴进来会自动识别，列出账号后可勾选要导入哪些。",
                        style = MiuixTheme.textStyles.footnote2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    TextField(
                        value = importText,
                        onValueChange = {
                            importText = it
                            result = null
                        },
                        label = "粘贴导出内容",
                        // 长 JSON 铺满整屏会把下方按钮顶出视口，限高 + 内部滚动
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp),
                        minLines = 3,
                    )
                    TextButton(
                        text = "从文件选择 .json",
                        onClick = { openLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
                        modifier = Modifier.fillMaxWidth(),
                    )

                    when {
                        importError.isNotEmpty() -> Text(
                            text = importError,
                            style = MiuixTheme.textStyles.footnote2,
                            color = MiuixTheme.colorScheme.error,
                            modifier = Modifier.fillMaxWidth(),
                        )

                        importText.isNotBlank() && parsed == null -> Text(
                            text = "正在识别…",
                            style = MiuixTheme.textStyles.footnote2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            parsed?.let { accounts ->
                item {
                    PreferenceCard() {
                        val grouped = uiState.providers
                            .map { info -> info.id to accounts.filter { it.providerId == info.id } }
                            .filter { it.second.isNotEmpty() }
                        val unknown = accounts.filter { uiState.providerOf(it.providerId) == null }

                        BasicComponent(
                            title = "文件里有 ${accounts.size} 个账号",
                            summary = "已选 ${importSelection.size} 个",
                        )
                        if (unknown.isNotEmpty()) {
                            Text(
                                text = "本机没有这些供应商，将被跳过：" +
                                    unknown.map { it.providerId }.distinct().joinToString("、"),
                                style = MiuixTheme.textStyles.footnote2,
                                color = MiuixTheme.colorScheme.error,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = "覆盖同名账号",
                                    style = MiuixTheme.textStyles.subtitle,
                                    color = MiuixTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = "关闭时，已存在的账号保持原样（跳过）",
                                    style = MiuixTheme.textStyles.footnote2,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                )
                            }
                            Switch(checked = overwrite, onCheckedChange = { overwrite = it })
                        }

                        // 主操作放在列表之前：账号多时不用滚到底才能找到导入按钮
                        TextButton(
                            text = "导入选中的 ${importSelection.size} 个账号",
                            onClick = {
                                if (importSelection.isEmpty()) {
                                    importError = "没有选中任何账号"
                                    return@TextButton
                                }
                                result = viewModel.importAccountsJson(importText, importSelection, overwrite)
                                importError = ""
                                viewModel.refresh()
                            },
                            enabled = importSelection.isNotEmpty(),
                            modifier = Modifier.fillMaxWidth(),
                        )

                        AccountTree(
                            groups = grouped,
                            displayName = { id -> uiState.providerOf(id)?.displayName ?: id },
                            selected = importSelection,
                            onSelectionChange = { importSelection = it },
                        )
                    }
                }
            }

            result?.let { outcome ->
                item {
                    PreferenceCard() {
                        BasicComponent(
                            title = "导入完成",
                            summary = "新增 ${outcome.added.size} · 覆盖 ${outcome.updated.size} · " +
                                "跳过 ${outcome.skipped.size} · 未知供应商 ${outcome.unknownProviders.size}",
                        )
                        if (outcome.skipped.isNotEmpty()) {
                            Text(
                                text = "跳过的账号：" + outcome.skipped.take(8).joinToString("、") +
                                    if (outcome.skipped.size > 8) " 等 ${outcome.skipped.size} 个" else "",
                                style = MiuixTheme.textStyles.footnote2,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        if (outcome.unknownProviders.isNotEmpty()) {
                            Text(
                                text = "未知供应商：" + outcome.unknownProviders.distinct().joinToString("、"),
                                style = MiuixTheme.textStyles.footnote2,
                                color = MiuixTheme.colorScheme.error,
                                modifier = Modifier.fillMaxWidth(),
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
 * 供应商 → 账号两级勾选。
 *
 * 供应商行是整组开关：部分勾选时显示为半选（三态 Checkbox），并标注已选数量。
 */
@Composable
private fun AccountTree(
    groups: List<Pair<String, List<SelectableAccount>>>,
    displayName: (String) -> String,
    selected: Set<String>,
    onSelectionChange: (Set<String>) -> Unit,
) {
    if (groups.isEmpty()) {
        Text(
            text = "没有可选的账号",
            style = MiuixTheme.textStyles.footnote2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.fillMaxWidth(),
        )
        return
    }
    for ((providerId, accounts) in groups) {
        val keys = accounts.map { it.key }
        val selectedCount = keys.count { it in selected }
        val groupState = when {
            selectedCount == 0 -> ToggleableState.Off
            selectedCount == keys.size -> ToggleableState.On
            else -> ToggleableState.Indeterminate
        }
        Column(Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Checkbox(
                    state = groupState,
                    onClick = {
                        onSelectionChange(
                            if (groupState == ToggleableState.On) selected - keys.toSet() else selected + keys,
                        )
                    },
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        text = displayName(providerId),
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "已选 $selectedCount / ${accounts.size}",
                        style = MiuixTheme.textStyles.footnote2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            }
            for (account in accounts) {
                val key = account.key
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Checkbox(
                        state = if (key in selected) ToggleableState.On else ToggleableState.Off,
                        onClick = {
                            onSelectionChange(if (key in selected) selected - key else selected + key)
                        },
                    )
                    Text(
                        text = account.nickname.ifEmpty { account.uid },
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}
