package com.vibecoding.mcfx.data

/**
 * One selectable currency, backed by app/src/main/assets/currencies.json.
 *
 * Mastercard publishes the supported settlement currencies as `{alphaCd, currNam}`
 * pairs; this dataset enriches each alpha code with Chinese/English currency and
 * country names so the picker can be searched by 三字母代码 / 货币名称 / 国家名称.
 *
 * Flags are derived from the ISO-3166 alpha-2 code at runtime as Unicode regional
 * indicators, which is how the Mastercard converter renders its round flags too --
 * so no bitmap assets are needed and nothing can be mis-encoded in the JSON.
 */
data class Currency(
    val code: String,
    val cc: String,
    val nameZh: String,
    val nameEn: String,
    val countryZh: String,
    val countryEn: String,
    val symbol: String = "",
    val aliases: List<String> = emptyList(),
    /**
     * ISO 4217 minor units (decimal places): 0 for JPY/KRW, 3 for KWD/BHD, 2 for
     * most. Converted amounts must be rounded and displayed with the TARGET
     * currency's precision, not a fixed two digits.
     */
    val minorUnits: Int = DEFAULT_MINOR_UNITS,
) {
    val flag: String by lazy { flagOf(cc) }

    val searchBlob: String by lazy {
        buildString {
            append(code.lowercase())
            append(' ').append(nameZh)
            append(' ').append(nameEn.lowercase())
            append(' ').append(countryZh)
            append(' ').append(countryEn.lowercase())
            append(' ').append(symbol)
            for (a in aliases) append(' ').append(a.lowercase())
        }
    }

    /** e.g. "日元 · 日本" for the picker's secondary line. */
    val subtitle: String get() = "$nameZh · $countryZh"

    companion object {
        const val DEFAULT_MINOR_UNITS = 2

        fun flagOf(cc: String): String {
            val c = cc.trim().uppercase()
            if (c.length != 2 || c.any { it !in 'A'..'Z' }) return "\uD83C\uDFF3"
            return buildString {
                for (ch in c) appendCodePoint(0x1F1E6 + (ch - 'A'))
            }
        }
    }
}
