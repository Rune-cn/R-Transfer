// SPDX-License-Identifier: GPL-3.0-only
package app.ai.rune.ziyong.transfer.util.timber

import android.content.Context
import app.ai.rune.ziyong.transfer.domain.settings.repository.AppSettingsRepository
import app.ai.rune.ziyong.transfer.domain.settings.repository.BooleanSetting
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import timber.log.Timber

class LogController(private val context: Context, private val appSettingsRepo: AppSettingsRepository) {
    private var fileLoggingTree: FileLoggingTree? = null

    // Use Main scope for collecting flow, file IO is handled internally by the Tree on IO dispatcher
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    init {
        // Shell mode: file logging is always available and controlled only by
        // the in-app "Save logs" toggle (default on). Unlike the upstream
        // installer, every build type may write logs so run logs are
        // inspectable on release installs too.
        scope.launch {
            appSettingsRepo.getBoolean(BooleanSetting.EnableFileLogging, true)
                .collectLatest { enabled ->
                    updateLoggingState(enabled)
                }
        }
    }

    private fun updateLoggingState(enabled: Boolean) {
        if (enabled) {
            if (fileLoggingTree == null) {
                val tree = FileLoggingTree(context)
                Timber.plant(tree)
                fileLoggingTree = tree
                // Self-verification: this entry flows through the freshly
                // planted tree, so the log file is guaranteed to exist with
                // at least one line.
                Timber.i("file logging enabled")
            }
        } else {
            fileLoggingTree?.let { tree ->
                Timber.i("file logging disabled")
                Timber.uproot(tree)
                tree.release()
                fileLoggingTree = null
            }
        }
    }
}