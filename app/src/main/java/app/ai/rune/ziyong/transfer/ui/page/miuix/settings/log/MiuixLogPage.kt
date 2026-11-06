// SPDX-License-Identifier: GPL-3.0-only
package app.ai.rune.ziyong.transfer.ui.page.miuix.settings.log

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.ai.rune.ziyong.transfer.R
import app.ai.rune.ziyong.transfer.ui.page.main.settings.log.LogViewModel
import app.ai.rune.ziyong.transfer.ui.theme.getMiuixAppBarColor
import app.ai.rune.ziyong.transfer.ui.theme.miuixBlurEffect
import app.ai.rune.ziyong.transfer.ui.theme.rememberMiuixBlurBackdrop
import app.ai.rune.ziyong.transfer.util.toast
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.koin.androidx.compose.koinViewModel
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

@Composable
fun MiuixLogPage(
    enableBlur: Boolean,
    viewModel: LogViewModel = koinViewModel(),
    title: String,
    outerPadding: PaddingValues,
) {
    val context = LocalContext.current
    val lines by viewModel.lines.collectAsStateWithLifecycle()
    val crashes by viewModel.crashes.collectAsStateWithLifecycle()
    val crashLogsTitle = stringResource(R.string.crash_logs)
    val runLogsTitle = stringResource(R.string.run_logs)
    val scrollBehavior = MiuixScrollBehavior()
    val layoutDirection = LocalLayoutDirection.current

    val backdrop = rememberMiuixBlurBackdrop(enableBlur)

    // Auto-refresh: re-read the log file while the page is composed.
    LaunchedEffect(viewModel) {
        while (isActive) {
            viewModel.refresh()
            delay(AUTO_REFRESH_MS)
        }
    }

    fun copyLog() {
        val text = buildString {
            if (crashes.isNotEmpty()) {
                append(crashLogsTitle).append('\n')
                crashes.forEach { append(it).append('\n') }
            }
            if (lines.isNotEmpty()) {
                append(runLogsTitle).append('\n')
                lines.forEach { append(it).append('\n') }
            }
        }.trim()
        if (text.isBlank()) {
            context.toast(R.string.run_logs_empty)
            return
        }
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("run_logs", text))
        context.toast(R.string.run_logs_copied)
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                modifier = Modifier.miuixBlurEffect(backdrop),
                color = backdrop.getMiuixAppBarColor(),
                title = title,
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .then(backdrop?.let { Modifier.layerBackdrop(it) } ?: Modifier)
                .padding(
                    start = innerPadding.calculateStartPadding(layoutDirection),
                    top = innerPadding.calculateTopPadding(),
                    end = innerPadding.calculateEndPadding(layoutDirection),
                    bottom = outerPadding.calculateBottomPadding(),
                ),
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .scrollEndHaptic()
                    .overScrollVertical()
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                overscrollEffect = null,
            ) {
                // Action row scrolls with the content instead of being pinned.
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(
                            text = stringResource(R.string.run_logs_clear),
                            onClick = {
                                viewModel.clear()
                                context.toast(R.string.run_logs_cleared)
                            },
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        TextButton(
                            text = stringResource(R.string.run_logs_copy),
                            onClick = { copyLog() },
                        )
                    }
                }

                if (crashes.isNotEmpty()) {
                    item { SmallTitle(stringResource(R.string.crash_logs)) }
                    items(crashes) { line ->
                        LogLineText(line)
                    }
                }
                if (lines.isNotEmpty()) {
                    item { SmallTitle(stringResource(R.string.run_logs)) }
                    items(lines.asReversed()) { line ->
                        LogLineText(line)
                    }
                }
                if (crashes.isEmpty() && lines.isEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.run_logs_empty),
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LogLineText(line: String) {
    Text(
        text = line.trimEnd('\n'),
        fontSize = 11.sp,
        fontFamily = FontFamily.Monospace,
        lineHeight = 14.sp,
        color = MiuixTheme.colorScheme.onSurface,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp),
    )
}

private const val AUTO_REFRESH_MS = 5_000L