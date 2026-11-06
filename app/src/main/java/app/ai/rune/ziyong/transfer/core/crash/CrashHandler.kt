// SPDX-License-Identifier: GPL-3.0-only
package app.ai.rune.ziyong.transfer.core.crash

import android.content.Context
import android.os.Process
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.system.exitProcess
import timber.log.Timber

/**
 * Default uncaught exception handler that records every crash both to Timber
 * (which pipes it into the run-log file) and to a dedicated crash dump under
 * `cacheDir/crashes/`.
 */
object CrashHandler : Thread.UncaughtExceptionHandler {
    private const val TAG = "CRASH"
    private const val CRASH_DIR = "crashes"

    private var defaultHandler: Thread.UncaughtExceptionHandler? = null
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
        defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler(this)
    }

    override fun uncaughtException(t: Thread, e: Throwable) {
        val stack = android.util.Log.getStackTraceString(e)
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())

        // 1) Log to Timber (flows into the run-log file as E/CRASH)
        Timber.Forest.tag(TAG).e(e, "Uncaught exception on thread %s", t.name)

        // 2) Write a dedicated crash dump file for the crash-log view
        writeCrashDump(time, t, e, stack)

        // Give the file logger a moment to drain the buffer to disk.
        try {
            Thread.sleep(500)
        } catch (_: InterruptedException) {
        }

        defaultHandler?.uncaughtException(t, e) ?: run {
            Process.killProcess(Process.myPid())
            exitProcess(1)
        }
    }

    private fun writeCrashDump(time: String, t: Thread, e: Throwable, stack: String) {
        val context = appContext ?: return
        val dir = File(context.cacheDir, CRASH_DIR)
        runCatching {
            dir.mkdirs()
            val name = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss_SSS", Locale.US).format(Date()) + ".txt"
            val dump = buildString {
                append(time)
                append('\n')
                append("Thread: ").append(t.name)
                append('\n')
                append(stack)
                append('\n')
            }
            File(dir, name).writeText(dump)
        }.onFailure { err ->
            Timber.e(err, "Failed to write crash dump")
        }
    }
}