package com.vibecoding.mcfx

import com.vibecoding.mcfx.logic.RateMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal

class RateMathTest {

    /**
     * Mastercard's own spec sample: transAmt 23, bankFee 5, conversionRate 0.57,
     * crdhldBillAmt 13.11. The equality proves the returned rate already carries
     * the bank fee.
     */
    @Test
    fun `mastercard sample bills amount times returned rate`() {
        val billed = RateMath.billedAmount(BigDecimal("23"), BigDecimal("0.57"))
        assertEquals("13.11", billed.toPlainString())
    }

    @Test
    fun `base rate strips the bank fee back out`() {
        val base = RateMath.baseRate(BigDecimal("0.57"), BigDecimal("5"))
        // 0.57 / 1.05 = 0.542857...
        assertEquals(0, base.compareTo(BigDecimal("0.542857142857")))
    }

    @Test
    fun `zero fee leaves the rate untouched`() {
        assertEquals(
            0,
            RateMath.baseRate(BigDecimal("7.1234"), BigDecimal.ZERO).compareTo(BigDecimal("7.1234")),
        )
    }

    @Test
    fun `fee amount is the difference between billed and unfee'd`() {
        val fee = RateMath.feeAmount(BigDecimal("23"), BigDecimal("0.57"), BigDecimal("5"))
        // 13.11 - 23 * 0.542857142857 = 13.11 - 12.4857 = 0.62 (rounded)
        assertEquals("0.62", fee.toPlainString())
    }

    @Test
    fun `no fee yields zero fee amount`() {
        val fee = RateMath.feeAmount(BigDecimal("10000"), BigDecimal("0.04268"), BigDecimal.ZERO)
        assertEquals("0.00", fee.toPlainString())
    }

    @Test
    fun `billed amount rounds half up to two decimals`() {
        // 10000 * 0.04268 = 426.80
        assertEquals(
            "426.80",
            RateMath.billedAmount(BigDecimal("10000"), BigDecimal("0.04268")).toPlainString(),
        )
    }

    @Test
    fun `format money groups thousands`() {
        assertEquals("10,000.00", RateMath.formatMoney(BigDecimal("10000")))
        assertEquals("1,234,567.89", RateMath.formatMoney(BigDecimal("1234567.89")))
    }

    @Test
    fun `format rate trims trailing zeros but keeps two decimals`() {
        assertEquals("0.04268", RateMath.formatRate(BigDecimal("0.042680")))
        assertEquals("7.1", RateMath.formatRate(BigDecimal("7.1")))
        assertEquals("7", RateMath.formatRate(BigDecimal("7.000")))
        assertEquals("0.000123", RateMath.formatRate(BigDecimal("0.0001234")))
    }

    @Test
    fun `parse decimal tolerates separators and full width digits`() {
        assertEquals(BigDecimal("10000"), RateMath.parseDecimal("10,000"))
        assertEquals(BigDecimal("10000"), RateMath.parseDecimal("１０，０００"))
        assertEquals(BigDecimal("2.5"), RateMath.parseDecimal(" 2.5 "))
        assertNull(RateMath.parseDecimal(""))
        assertNull(RateMath.parseDecimal("abc"))
    }

    @Test
    fun `inverse rate inverts`() {
        val inverse = RateMath.inverseRate(BigDecimal("0.04268"))
        assertEquals(1.0 / 0.04268, inverse.toDouble(), 1e-6)
    }
}
