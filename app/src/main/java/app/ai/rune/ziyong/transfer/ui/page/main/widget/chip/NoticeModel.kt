// SPDX-License-Identifier: GPL-3.0-only
package app.ai.rune.ziyong.transfer.ui.page.main.widget.chip

import androidx.compose.ui.graphics.Color

// Data model to hold both short tag and full description.
// Extracted from the (removed) MD3 Badge.kt; used by shared install mappers.
data class NoticeModel(
    val shortLabel: String, // Displayed on the chip
    val fullDescription: String, // Displayed in the dialog
    val color: Color, // Color of the chip
)
