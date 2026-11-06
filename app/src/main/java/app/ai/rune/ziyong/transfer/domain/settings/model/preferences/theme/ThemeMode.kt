// SPDX-License-Identifier: GPL-3.0-only
package app.ai.rune.ziyong.transfer.domain.settings.model.preferences.theme

enum class ThemeMode {
    LIGHT,
    DARK,
    SYSTEM,
    ;

    companion object {
        fun fromValueOrDefault(value: String) = entries.find { it.name == value } ?: SYSTEM
    }
}
