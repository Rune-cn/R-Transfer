// SPDX-License-Identifier: GPL-3.0-only
package app.ai.rune.ziyong.transfer.util

import android.content.Context
import android.content.pm.PackageManager
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.ai.rune.ziyong.transfer.R
import app.ai.rune.ziyong.transfer.core.exception.AppException
import java.util.zip.ZipException

/**
 * A private helper that maps a [Throwable] to its corresponding string
 * resource ID.
 *
 * @return The string resource ID defined in `R.string`.
 */
@StringRes
private fun Throwable.getStringRes() = when (this) {
    is AppException -> this.getStringResId()
    is ZipException -> R.string.exception_zip_exception
    is PackageManager.NameNotFoundException -> R.string.exception_package_manager_name_not_found
    else -> R.string.exception_unknown
}

/**
 * Returns a user-friendly error message for this [Throwable]
 * in a Jetpack Compose environment.
 *
 * This is the Compose-specific API and should be used inside
 * composable functions.
 *
 * @receiver The [Throwable] to be converted into a readable message.
 * @return A localized error message string.
 */
@Composable
fun Throwable.help() = stringResource(this.getStringRes())

/**
 * Returns a user-friendly error message for this [Throwable]
 * in a non-Compose environment.
 *
 * @receiver The [Throwable] to be converted into a readable message.
 * @param context The [Context] used to resolve the string resource.
 * @return A localized error message string.
 */
fun Throwable.getErrorMessage(context: Context) = context.getString(this.getStringRes())