package com.vibecoding.mcfx

import com.vibecoding.mcfx.data.Currency
import com.vibecoding.mcfx.logic.CurrencySearch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CurrencySearchTest {

    private val currencies = listOf(
        Currency("CNY", "CN", "人民币", "Chinese Yuan", "中国", "China", "¥", listOf("rmb", "yuan")),
        Currency("JPY", "JP", "日元", "Japanese Yen", "日本", "Japan", "¥", listOf("yen")),
        Currency("USD", "US", "美元", "US Dollar", "美国", "United States", "$", listOf("美金")),
        Currency("EUR", "EU", "欧元", "Euro", "欧元区", "Eurozone", "€"),
        Currency("HKD", "HK", "港元", "Hong Kong Dollar", "中国香港", "Hong Kong", "HK$", listOf("港币")),
        Currency("TWD", "TW", "新台币", "New Taiwan Dollar", "中国台湾", "Taiwan", "NT$", listOf("台币")),
        Currency("KRW", "KR", "韩元", "South Korean Won", "韩国", "South Korea", "₩"),
    )

    private fun firstCode(query: String): String? =
        CurrencySearch.search(currencies, query).firstOrNull()?.code

    @Test
    fun `three letter code ranks first`() {
        assertEquals("JPY", firstCode("jpy"))
        assertEquals("JPY", firstCode("JPY"))
        assertEquals("USD", firstCode("usd"))
    }

    @Test
    fun `chinese currency name matches`() {
        assertEquals("JPY", firstCode("日元"))
        assertEquals("CNY", firstCode("人民币"))
        assertEquals("USD", firstCode("美元"))
    }

    @Test
    fun `country name matches`() {
        assertEquals("JPY", firstCode("日本"))
        assertEquals("USD", firstCode("美国"))
        assertEquals("KRW", firstCode("韩国"))
    }

    @Test
    fun `english names match`() {
        assertEquals("JPY", firstCode("japan"))
        assertEquals("EUR", firstCode("euro"))
        assertEquals("CNY", firstCode("china"))
    }

    @Test
    fun `aliases and colloquial names match`() {
        assertEquals("HKD", firstCode("港币"))
        assertEquals("TWD", firstCode("台币"))
        assertEquals("CNY", firstCode("rmb"))
        assertEquals("USD", firstCode("美金"))
    }

    @Test
    fun `partial input still narrows to the right currency`() {
        assertEquals("JPY", firstCode("jp"))
        assertEquals("HKD", firstCode("hon"))
    }

    @Test
    fun `empty query lists popular codes first`() {
        val results = CurrencySearch.search(currencies, "")
        assertEquals("CNY", results.first().code)
        assertTrue(results.map { it.code }.containsAll(listOf("CNY", "USD", "JPY")))
    }

    @Test
    fun `unmatched query returns nothing`() {
        assertTrue(CurrencySearch.search(currencies, "zzzzzz").isEmpty())
    }

    @Test
    fun `ranking prefers exact code over substring match`() {
        // "us" is a substring of nothing else here, but USD must win outright.
        assertEquals("USD", firstCode("us"))
    }

    @Test
    fun `flag is derived from the country code`() {
        assertEquals("\uD83C\uDDEF\uD83C\uDDF5", currencies.first { it.code == "JPY" }.flag)
        assertEquals("\uD83C\uDDE8\uD83C\uDDF3", currencies.first { it.code == "CNY" }.flag)
        assertEquals("\uD83C\uDDEA\uD83C\uDDFA", currencies.first { it.code == "EUR" }.flag)
    }
}
