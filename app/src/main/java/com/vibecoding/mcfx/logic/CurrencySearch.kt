package com.vibecoding.mcfx.logic

import com.vibecoding.mcfx.data.Currency

/**
 * Search over 三字母代码 / 货币名称(中英) / 国家名称(中英) / 符号 / 别名.
 * Pure Kotlin so the ranking is unit-tested without an emulator.
 */
object CurrencySearch {

    /** Shown first when the query is empty. */
    val POPULAR_CODES = listOf(
        "CNY", "USD", "JPY", "EUR", "GBP", "HKD", "TWD", "KRW", "SGD", "AUD",
        "CAD", "THB", "MYR", "CHF", "NZD", "AED", "INR", "PHP", "VND", "IDR",
    )

    private fun normalise(raw: String): String =
        raw.trim()
            .lowercase()
            .filterNot { it.isWhitespace() || it == '·' }

    /**
     * Ranks [currencies] against [query]. An empty query returns the popular
     * codes first, then everything else in dataset order.
     */
    fun search(currencies: List<Currency>, query: String, limit: Int = 60): List<Currency> {
        val q = normalise(query)
        if (q.isEmpty()) {
            val byCode = currencies.associateBy { it.code }
            val popular = POPULAR_CODES.mapNotNull { byCode[it] }
            val rest = currencies.filterNot { it.code in POPULAR_CODES }
            return (popular + rest).take(limit)
        }

        return currencies
            .mapNotNull { c -> score(c, q)?.let { c to it } }
            .sortedWith(
                compareByDescending<Pair<Currency, Int>> { it.second }
                    .thenBy { it.first.code },
            )
            .map { it.first }
            .take(limit)
    }

    /** Higher is better; null means "no match". */
    fun score(currency: Currency, rawQuery: String): Int? {
        val q = normalise(rawQuery)
        if (q.isEmpty()) return 0

        val code = currency.code.lowercase()
        val nameZh = normalise(currency.nameZh)
        val nameEn = normalise(currency.nameEn)
        val countryZh = normalise(currency.countryZh)
        val countryEn = normalise(currency.countryEn)
        val aliases = currency.aliases.map(::normalise)

        if (code == q) return 1000
        if (aliases.any { it == q }) return 950
        if (code.startsWith(q)) return 800
        if (nameZh == q) return 780
        if (nameZh.startsWith(q)) return 700
        if (nameEn == q) return 690
        if (nameEn.startsWith(q)) return 650
        if (aliases.any { it.startsWith(q) }) return 640
        if (countryZh.startsWith(q)) return 600
        if (countryEn.startsWith(q)) return 560

        if (nameEn.contains(" $q")) return 520
        if (countryEn.contains(" $q")) return 500

        if (code.contains(q)) return 400
        if (nameZh.contains(q)) return 350
        if (nameEn.contains(q)) return 300
        if (aliases.any { it.contains(q) }) return 280
        if (countryZh.contains(q)) return 250
        if (countryEn.contains(q)) return 200
        if (currency.searchBlob.contains(q)) return 100
        return null
    }
}
