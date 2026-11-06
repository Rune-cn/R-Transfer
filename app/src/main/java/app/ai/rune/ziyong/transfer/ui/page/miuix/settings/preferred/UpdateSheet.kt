// SPDX-License-Identifier: GPL-3.0-only
package app.ai.rune.ziyong.transfer.ui.page.miuix.settings.preferred

import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import app.ai.rune.ziyong.transfer.R
import app.ai.rune.ziyong.transfer.core.env.AppConfig
import app.ai.rune.ziyong.transfer.domain.updater.model.UpdateInfo
import app.ai.rune.ziyong.transfer.ui.icons.AppMiuixIcons
import app.ai.rune.ziyong.transfer.ui.page.miuix.widgets.MiuixBackButton
import app.ai.rune.ziyong.transfer.ui.page.miuix.widgets.MiuixMarkdownText
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.LocalDismissState
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowBottomSheet

/**
 * ============================================================
 * UpdateSheet — "check update" result sheet
 * ============================================================
 *
 * Target shape follows legado-with-MD3's UpdateSheet:
 *   app/src/main/java/io/legado/app/ui/about/AboutSheets.kt:64
 *
 * Implemented:
 *   - bottom sheet + title + [installed/latest/ABI/channel] 4 rows + notes + buttons
 *   - data: consumes UpdateInfo directly (from PreferredViewState.updateInfo)
 *   - release notes rendered with MiuixMarkdownText (minimal, dependency-free)
 *   - "download" / "open release" open the remote URL in the system browser
 *
 * TODO(legado-update-sheet) access points still open:
 *   1. done — Markdown release notes (MiuixMarkdownText)
 *   2. done — UpdateInfo.updateLog (model + GithubRelease.body mapping)
 *   3. done — channel label localization (call sites pass localized label)
 *   4. partial — download landing opens the URL in the system browser;
 *      full DownloadManager + notification progress not implemented yet
 *   5. open — download progress in-sheet
 *   6. open — VIEW_LOG mode (installed version changelog)
 *
 * Replace by searching `legado-update-sheet`; keep the signature stable.
 */
@Composable
fun MiuixUpdateSheet(
    show: Boolean,
    updateInfo: UpdateInfo?,
    channelLabel: String,
    releaseNotes: String? = null,
    isDownloading: Boolean = false,
    downloadProgress: Float? = null,
    onDismissRequest: () -> Unit,
    onDownload: (UpdateInfo) -> Unit,
    onOpenReleasePage: (String) -> Unit = {},
) {
    WindowBottomSheet(
        show = show,
        onDismissRequest = onDismissRequest,
        title = stringResource(R.string.update_sheet_title),
        startAction = {
            val dismissState = LocalDismissState.current
            MiuixBackButton(
                icon = AppMiuixIcons.Close,
                onClick = { dismissState?.invoke() ?: onDismissRequest() },
            )
        },
        insideMargin = DpSize(16.dp, 0.dp),
        backgroundColor = MiuixTheme.colorScheme.surfaceContainerHigh,
    ) {
        val info = updateInfo
        if (info == null) return@WindowBottomSheet

        Column(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 560.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                UpdateSheetInfoRow(
                    label = stringResource(R.string.update_sheet_installed_version),
                    value = AppConfig.VERSION_NAME,
                )
                UpdateSheetInfoRow(
                    label = stringResource(R.string.update_sheet_latest_version),
                    value = info.remoteVersion,
                    valueColor = MiuixTheme.colorScheme.primary,
                )
                UpdateSheetInfoRow(
                    label = stringResource(R.string.update_sheet_abi),
                    value = Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown",
                )
                UpdateSheetInfoRow(
                    label = stringResource(R.string.update_sheet_channel),
                    value = channelLabel,
                    modifier = Modifier.padding(bottom = 8.dp),
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                Text(
                    text = stringResource(R.string.update_sheet_release_notes),
                    style = MiuixTheme.textStyles.body1,
                    color = MiuixTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Markdown release notes (minimal renderer, dependency-free).
                if (releaseNotes.isNullOrBlank()) {
                    Text(
                        text = stringResource(R.string.update_sheet_no_notes),
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                } else {
                    MiuixMarkdownText(markdown = releaseNotes)
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (info.releaseUrl.isNotBlank()) {
                TextButton(
                    text = stringResource(R.string.update_sheet_open_release),
                    onClick = { onOpenReleasePage(info.releaseUrl) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // TODO(legado-update-sheet)【接入点 4/5】download landing & progress.
            // Landing: callers open the download/release URL in the system browser;
            // in-sheet progress (DownloadManager + notification) is not implemented yet.
            TextButton(
                text = stringResource(R.string.update_sheet_download),
                onClick = { onDownload(info) },
                enabled = info.downloadUrl.isNotBlank() && !isDownloading,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
            )
        }
    }
}

@Composable
private fun UpdateSheetInfoRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MiuixTheme.textStyles.body1,
            color = MiuixTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = value,
            style = MiuixTheme.textStyles.body1,
            color = valueColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun PreviewMiuixUpdateSheet() {
    MiuixTheme {
        MiuixUpdateSheet(
            show = true,
            updateInfo = UpdateInfo(
                hasUpdate = true,
                remoteVersion = "1.0.1",
                releaseUrl = "https://github.com/Rune-cn/miuix-example",
                downloadUrl = "https://github.com/Rune-cn/miuix-example/releases",
            ),
            channelLabel = "Stable",
            onDismissRequest = {},
            onDownload = {},
        )
    }
}
