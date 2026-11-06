// SPDX-License-Identifier: GPL-3.0-only
package app.ai.rune.ziyong.transfer.domain.settings.model.preferences

/**
 * Define Predictive Back Animation types
 */
enum class PredictiveBackAnimation(val value: String) {
    None("none"),
    AOSP("aosp"),
    MIUIX("miuix"),
    Scale("scale"),
    Classic("ksu_classic"),
    ;

    companion object {
        fun fromValueOrDefault(value: String) = entries.find { it.value == value } ?: MIUIX
    }
}
