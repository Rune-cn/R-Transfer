// SPDX-License-Identifier: GPL-3.0-only
package app.ai.rune.ziyong.transfer.ui.page.main.settings.log

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.ai.rune.ziyong.transfer.util.timber.FileLoggingTree
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Reads the latest on-disk run log (written by [FileLoggingTree]) and the
 * dedicated crash dumps (written by CrashHandler), and exposes them for the
 * run-log page. Also supports clearing both stores.
 */
class LogViewModel(private val context: Context) : ViewModel() {

    private val _lines = MutableStateFlow<List<String>>(emptyList())
    /** Run-log lines in chronological order (oldest first). */
    val lines: StateFlow<List<String>> = _lines.asStateFlow()

    private val _crashes = MutableStateFlow<List<String>>(emptyList())
    /** Crash dumps, newest first. Each entry starts with a `>>> file` header. */
    val crashes: StateFlow<List<String>> = _crashes.asStateFlow()

    private val _latestLogFile = MutableStateFlow<File?>(null)
    /** Latest run-log file. */
    val latestLogFile: StateFlow<File?> = _latestLogFile.asStateFlow()

    init {
        Timber.i("Run-log page opened (log dir: %s)", crashLogDir().parentFile)
        refresh()
    }

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            val latest = latestRunLogFile()
            _latestLogFile.value = latest
            val loaded = if (latest == null) {
                emptyList()
            } else {
                runCatching { latest.readLines() }
                    .getOrDefault(emptyList())
                    .takeLast(MAX_LINES)
            }
            _lines.value = loaded
            _crashes.value = loadCrashes()
            Timber.i("Run-log refreshed: %d lines from %s", loaded.size, latest?.name ?: "(none)")
        }
    }

    /** Deletes all run-log files and crash dumps, then reloads. */
    fun clear() {
        viewModelScope.launch(Dispatchers.IO) {
            runLogDir().listFiles()?.forEach { f -> runCatching { f.delete() } }
            crashLogDir().listFiles()?.forEach { f -> runCatching { f.delete() } }
            Timber.i("Run logs and crash dumps cleared")
            refresh()
        }
    }

    private fun runLogDir(): File = File(context.cacheDir, FileLoggingTree.LOG_DIR_NAME)

    private fun crashLogDir(): File = File(context.cacheDir, CRASH_DIR)

    private fun latestRunLogFile(): File? {
        val dir = runLogDir()
        if (!dir.exists()) return null
        return dir.listFiles { f -> f.isFile && f.name.endsWith(FileLoggingTree.LOG_SUFFIX) }
            ?.maxByOrNull { it.lastModified() }
    }

    private fun loadCrashes(): List<String> {
        val dir = crashLogDir()
        if (!dir.exists()) return emptyList()
        val files = dir.listFiles { f -> f.isFile && f.name.endsWith(".txt") }
            ?.sortedByDescending { it.lastModified() }
            ?: return emptyList()
        return buildList {
            files.forEach { file ->
                add(">>> ${file.name}")
                runCatching { File(file.absolutePath).readLines() }
                    .getOrDefault(listOf("<unreadable crash file>"))
                    .forEach { add(it) }
            }
        }.takeLast(MAX_CRASH_LINES)
    }

    private companion object {
        private const val MAX_LINES = 800
        private const val MAX_CRASH_LINES = 600
        private const val CRASH_DIR = "crashes"
    }
}