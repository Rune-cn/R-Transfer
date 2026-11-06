// SPDX-License-Identifier: GPL-3.0-only
package app.ai.rune.ziyong.transfer.ui.page.miuix.widgets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.ai.rune.ziyong.transfer.R
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.theme.LocalDismissState
import top.yukonga.miuix.kmp.window.WindowBottomSheet

/**
 * A bottom-sheet that displays an error reason and offers retry/dismiss actions.
 *
 * @param showState A MutableState controlling the visibility of the sheet.
 * @param exception The Throwable whose message/stack is displayed.
 * @param onDismissRequest Request to close the sheet.
 * @param onDismissFinished Optional callback invoked after the dismiss animation.
 * @param onRetry Optional retry action; when absent a single close button is shown.
 * @param title The sheet title.
 */
@Composable
fun ErrorDisplaySheet(
    showState: MutableState<Boolean>,
    exception: Throwable,
    onDismissRequest: () -> Unit,
    onDismissFinished: (() -> Unit)? = null,
    onRetry: (() -> Unit)? = null,
    title: String,
) {
    WindowBottomSheet(
        show = showState.value,
        onDismissRequest = onDismissRequest,
        onDismissFinished = onDismissFinished,
        title = title,
        startAction = {
            val dismissState = LocalDismissState.current
            MiuixBackButton(
                icon = MiuixIcons.Regular.Close,
                onClick = { dismissState?.invoke() ?: onDismissRequest() },
            )
        },
    ) {
        val dismissState = LocalDismissState.current
        val dismissSheet = { dismissState?.invoke() ?: onDismissRequest() }

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            MiuixErrorTextBlock(
                error = exception,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false),
            )
            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 40.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (onRetry != null) {
                    TextButton(
                        text = stringResource(R.string.cancel),
                        onClick = dismissSheet,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        text = stringResource(R.string.retry),
                        onClick = onRetry,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    TextButton(
                        text = stringResource(R.string.close),
                        onClick = dismissSheet,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}