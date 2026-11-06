// SPDX-License-Identifier: GPL-3.0-only
package app.ai.rune.ziyong.transfer.util

import android.content.Context
import android.widget.Toast
import androidx.annotation.StringRes

fun Context.toast(text: CharSequence, duration: Int = Toast.LENGTH_SHORT) {
    Toast.makeText(this, text, duration).show()
}

fun Context.toast(@StringRes resId: Int, duration: Int = Toast.LENGTH_SHORT) {
    Toast.makeText(this, resId, duration).show()
}

fun Context.toast(@StringRes resId: Int, vararg formatArgs: Any, duration: Int = Toast.LENGTH_SHORT) {
    val text = this.getString(resId, *formatArgs)
    Toast.makeText(this, text, duration).show()
}
