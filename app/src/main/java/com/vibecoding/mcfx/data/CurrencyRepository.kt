package com.vibecoding.mcfx.data

import android.content.Context
import org.json.JSONArray

/** Loads and caches the bundled currency dataset. */
class CurrencyRepository(private val context: Context) {

    @Volatile
    private var cache: List<Currency>? = null

    fun all(): List<Currency> = cache ?: synchronized(this) {
        cache ?: load().also { cache = it }
    }

    fun byCode(code: String): Currency? = all().firstOrNull { it.code == code.uppercase() }

    private fun load(): List<Currency> {
        val text = context.assets.open(ASSET_NAME).bufferedReader().use { it.readText() }
        val array = JSONArray(text)
        val out = ArrayList<Currency>(array.length())
        for (i in 0 until array.length()) {
            val o = array.getJSONObject(i)
            val aliases = o.optJSONArray("aliases")?.let { a ->
                (0 until a.length()).map { a.getString(it) }
            } ?: emptyList()
            out += Currency(
                code = o.getString("code"),
                cc = o.optString("cc", ""),
                nameZh = o.optString("nameZh", ""),
                nameEn = o.optString("nameEn", ""),
                countryZh = o.optString("countryZh", ""),
                countryEn = o.optString("countryEn", ""),
                symbol = o.optString("symbol", ""),
                aliases = aliases,
                minorUnits = o.optInt("minorUnits", Currency.DEFAULT_MINOR_UNITS),
            )
        }
        return out
    }

    companion object {
        const val ASSET_NAME = "currencies.json"
    }
}
