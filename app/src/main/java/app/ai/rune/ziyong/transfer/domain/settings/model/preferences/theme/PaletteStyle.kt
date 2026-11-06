// SPDX-License-Identifier: GPL-3.0-only
package app.ai.rune.ziyong.transfer.domain.settings.model.preferences.theme

enum class PaletteStyle(val displayName: String, val desc: String = "") {
    TonalSpot("Tonal Spot"),
    Neutral("Neutral"),
    Vibrant("Vibrant"),
    Expressive("Expressive"),
    Rainbow("Rainbow"),
    FruitSalad("FruitSalad"),
    Monochrome("Monochrome"),
    Fidelity("Fidelity"),
    Content("Content"),
    ;

    val supportedSpecs: List<ThemeColorSpec>
        get() = when (this) {
            TonalSpot, Neutral, Vibrant, Expressive -> listOf(ThemeColorSpec.SPEC_2021, ThemeColorSpec.SPEC_2025)
            else -> listOf(ThemeColorSpec.SPEC_2021)
        }

    /** Resolve the active spec without overwriting the user's preferred spec. */
    fun effectiveSpec(preferredSpec: ThemeColorSpec): ThemeColorSpec = preferredSpec.takeIf { it in supportedSpecs } ?: supportedSpecs.last()

    companion object {
        fun fromValueOrDefault(value: String) = entries.find { it.name == value } ?: TonalSpot
    }
}
