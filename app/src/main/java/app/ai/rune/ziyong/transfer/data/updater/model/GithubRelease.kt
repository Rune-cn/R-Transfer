// SPDX-License-Identifier: GPL-3.0-only
package app.ai.rune.ziyong.transfer.data.updater.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GithubRelease(
    @SerialName("tag_name")
    val tagName: String,
    @SerialName("prerelease")
    val isPrerelease: Boolean = false,
    @SerialName("html_url")
    val htmlUrl: String? = null,
    /** Markdown release notes body, mapped to [UpdateInfo.updateLog]. */
    val body: String? = null,
    val assets: List<GithubAsset> = emptyList(),
)
