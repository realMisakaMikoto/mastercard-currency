package com.vibecoding.mcfx

import com.vibecoding.mcfx.data.Currency
import com.vibecoding.mcfx.logic.RateMath
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.math.BigDecimal

/**
 * Amounts must follow the TARGET currency's ISO 4217 precision. A fixed two-decimal
 * scale misstates JPY (no minor unit), KRW, and the three-decimal Gulf currencies.
 */
class CurrencyMinorUnitsTest {

    private fun currency(code: String, minorUnits: Int) =
        Currency(code, "ZZ", code, code, code, code, minorUnits = minorUnits)

    private val usd = currency("USD", 2)
    private val cny = currency("CNY", 2)
    private val jpy = currency("JPY", 0)
    private val krw = currency("KRW", 0)
    private val kwd = currency("KWD", 3)
    private val bhd = currency("BHD", 3)

    @Test
    fun `model carries the declared precision`() {
        assertEquals(2, usd.minorUnits)
        assertEquals(2, cny.minorUnits)
        assertEquals(0, jpy.minorUnits)
        assertEquals(0, krw.minorUnits)
        assertEquals(3, kwd.minorUnits)
        assertEquals(3, bhd.minorUnits)
    }

    @Test
    fun `unknown currency falls back to two decimals`() {
        assertEquals(2, Currency.DEFAULT_MINOR_UNITS)
    }

    // ------------------------------------------------------- real dataset

    /** The shipped dataset is the source of truth; verify it against ISO 4217. */
    @Test
    fun `dataset declares ISO 4217 precision for every currency`() {
        val json = JSONArray(assetsFile().readText())
        val byCode = HashMap<String, Int>()
        for (i in 0 until json.length()) {
            val o = json.getJSONObject(i)
            assertTrue(
                "entry ${o.optString("code")} is missing minorUnits",
                o.has("minorUnits"),
            )
            byCode[o.getString("code")] = o.getInt("minorUnits")
        }

        val zeroDecimal = listOf(
            "BIF", "CLP", "DJF", "GNF", "ISK", "JPY", "KMF", "KRW",
            "PYG", "RWF", "UGX", "VND", "VUV", "XAF", "XOF", "XPF",
        )
        val threeDecimal = listOf("BHD", "IQD", "JOD", "KWD", "LYD", "OMR", "TND")

        for (code in zeroDecimal) {
            assertEquals("$code must have 0 minor units", 0, byCode[code])
        }
        for (code in threeDecimal) {
            assertEquals("$code must have 3 minor units", 3, byCode[code])
        }
        for (code in listOf("USD", "CNY", "EUR", "GBP", "HKD", "TWD", "SGD", "AUD")) {
            assertEquals("$code must have 2 minor units", 2, byCode[code])
        }
        assertTrue("dataset looks too small: ${byCode.size}", byCode.size >= 150)
    }

    private fun assetsFile(): File {
        val candidates = listOf(
            File("src/main/assets/currencies.json"),
            File("app/src/main/assets/currencies.json"),
        )
        return candidates.firstOrNull { it.isFile }
            ?: error("currencies.json not found from ${File(".").absolutePath}")
    }

    // ------------------------------------------------------- exact results

    // The converted amount is deliberately NOT rounded to the currency's minor
    // units any more: the exact product is shown instead, so multiplying the
    // displayed rate by the displayed amount reproduces the displayed total.

    @Test
    fun `conversion keeps the full product regardless of currency precision`() {
        assertEquals(
            "123.5",
            RateMath.billedAmount(BigDecimal("123.5"), BigDecimal.ONE).toPlainString(),
        )
        assertEquals(
            "123.4567",
            RateMath.billedAmount(BigDecimal("123.4567"), BigDecimal.ONE).toPlainString(),
        )
        assertEquals(
            "426.80000",
            RateMath.billedAmount(BigDecimal("10000"), BigDecimal("0.04268")).toPlainString(),
        )
    }

    @Test
    fun `exact display keeps every digit and trims only trailing zeros`() {
        assertEquals("1,234", RateMath.formatExact(BigDecimal("1234")))
        assertEquals("1,234.567", RateMath.formatExact(BigDecimal("1234.5670")))
        assertEquals("1,493.451", RateMath.formatExact(BigDecimal("1493.4510")))
        assertEquals("1,581,100", RateMath.formatExact(BigDecimal("1581100.0000000")))
    }

    // ----------------------------------------------------------- formatting

    @Test
    fun `money formatting respects minor units`() {
        assertEquals("1,234", RateMath.formatMoney(BigDecimal("1234"), 0))
        assertEquals("1,234.56", RateMath.formatMoney(BigDecimal("1234.56"), 2))
        assertEquals("1,234.567", RateMath.formatMoney(BigDecimal("1234.567"), 3))
    }

    @Test
    fun `zero decimals does not print a trailing point`() {
        assertEquals("0", RateMath.formatMoney(BigDecimal.ZERO, 0))
        assertEquals("0.00", RateMath.formatMoney(BigDecimal.ZERO, 2))
        assertEquals("0.000", RateMath.formatMoney(BigDecimal.ZERO, 3))
    }

    @Test
    fun `target precision is not trimmed away`() {
        // A whole number in a 2-decimal currency must still show .00
        assertEquals("10,000.00", RateMath.formatMoney(BigDecimal("10000"), 2))
        assertEquals("10,000.000", RateMath.formatMoney(BigDecimal("10000"), 3))
    }

    // ------------------------------------------------------------------ fee

    @Test
    fun `zero fee yields an exact zero`() {
        assertEquals(
            0,
            RateMath.feeAmount(BigDecimal("10000"), BigDecimal("0.1493451"), BigDecimal.ZERO)
                .signum(),
        )
    }

    @Test
    fun `fee with a surcharge is not rounded`() {
        val fee = RateMath.feeAmount(BigDecimal("10000"), BigDecimal("0.0438524"), BigDecimal("2"))
        assertTrue("fee must be positive", fee.signum() > 0)
        // Exact difference between billed and the same amount at the un-fee'd rate.
        val expected = BigDecimal("10000").multiply(BigDecimal("0.0438524"))
            .subtract(
                BigDecimal("10000")
                    .multiply(RateMath.baseRate(BigDecimal("0.0438524"), BigDecimal("2"))),
            )
        assertEquals(0, fee.compareTo(expected))
    }
}
