// SPDX-License-Identifier: GPL-3.0-only
package app.ai.rune.ziyong.transfer.core.exception

import androidx.annotation.StringRes

/**
 * The root exception to miuix-example
 * Every Custom Exception should extend this Exception
 */
abstract class AppException : RuntimeException {
    constructor() : super()
    constructor(message: String?) : super(message)
    constructor(message: String?, cause: Throwable?) : super(message, cause)
    constructor(cause: Throwable?) : super(cause)

    /**
     * Return the user-friendly error string resource id of the exception
     * @return string resource id
     */
    @StringRes
    abstract fun getStringResId(): Int
}
