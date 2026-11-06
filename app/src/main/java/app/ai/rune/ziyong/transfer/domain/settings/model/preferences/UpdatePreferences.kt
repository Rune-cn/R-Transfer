// SPDX-License-Identifier: GPL-3.0-only
package app.ai.rune.ziyong.transfer.domain.settings.model.preferences

/**
 * Define GitHub Update Channels used by miuix-example
 */
enum class GithubUpdateChannel {
    OFFICIAL,
    PROXY_7ED,
    CUSTOM,
    ;

    companion object {
        fun fromValueOrDefault(value: String) = entries.find { it.name == value } ?: OFFICIAL
    }
}
