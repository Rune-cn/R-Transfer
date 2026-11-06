// SPDX-License-Identifier: GPL-3.0-only
package app.ai.rune.ziyong.transfer.core.i18n

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.LocaleList
import timber.log.Timber
import java.util.Locale

/**
 * Minimal per-app locale implementation (no appcompat dependency).
 * SharedPreferences is used because [wrap] runs inside attachBaseContext,
 * where only synchronous reads are safe.
 */
object LocaleManager {

    const val FOLLOW_SYSTEM = "auto"
    const val SIMPLIFIED_CHINESE = "zh"
    const val ENGLISH = "en"

    val supportedTags: List<String> =
        listOf(FOLLOW_SYSTEM, SIMPLIFIED_CHINESE, ENGLISH)

    private const val PREF_NAME = "app_locale"
    private const val KEY_LANGUAGE = "app_language"

    fun currentTag(context: Context): String {
        val tag = prefs(context).getString(KEY_LANGUAGE, FOLLOW_SYSTEM) ?: FOLLOW_SYSTEM
        return tag.takeIf { it in supportedTags } ?: FOLLOW_SYSTEM
    }

    fun indexOf(context: Context): Int =
        supportedTags.indexOf(currentTag(context)).coerceAtLeast(0)

    fun setTag(context: Context, tag: String) {
        prefs(context).edit().putString(KEY_LANGUAGE, tag).apply()
    }

    /** Call inside Application/Activity attachBaseContext. */
    fun wrap(base: Context): Context {
        val tag = currentTag(base)
        Timber.d("LocaleManager.wrap: tag=%s", tag)
        if (tag == FOLLOW_SYSTEM) return base
        val locale = localeOf(tag)
        Locale.setDefault(locale)
        val configuration = Configuration(base.resources.configuration)
        configuration.setLocales(LocaleList(locale))
        // base.resources.configuration 可能是进程启动时的快照；夜间模式跟随“当前系统值”，
        // 否则 isSystemInDarkTheme() 可能拿到过期 uiMode（表现为整屏配色错乱）。
        val nightMask = Configuration.UI_MODE_NIGHT_MASK
        configuration.uiMode = (configuration.uiMode and nightMask.inv()) or
            (Resources.getSystem().configuration.uiMode and nightMask)
        return base.createConfigurationContext(configuration)
    }

    private fun localeOf(tag: String): Locale = when (tag) {
        SIMPLIFIED_CHINESE -> Locale.SIMPLIFIED_CHINESE
        ENGLISH -> Locale.ENGLISH
        else -> Locale.getDefault()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
}
