package com.vibecoding.mcfx

import com.vibecoding.mcfx.logic.RateMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
        // 13.11 - 23 * (0.57 / 1.05), with the un-fee'd rate at 12 decimals.
        // Exact now: no rounding to a currency precision.
        val expected = BigDecimal("13.11")
            .subtract(BigDecimal("23").multiply(RateMath.baseRate(BigDecimal("0.57"), BigDecimal("5"))))
        assertEquals(0, fee.compareTo(expected))
        assertTrue("fee must be positive", fee.signum() > 0)
    }

    @Test
    fun `no fee yields zero fee amount`() {
        val fee = RateMath.feeAmount(BigDecimal("10000"), BigDecimal("0.04268"), BigDecimal.ZERO)
        assertEquals(0, fee.signum())
    }

    @Test
    fun `billed amount is the exact product, not rounded`() {
        // 10000 * 0.04268 = 426.80 exactly; the full product is kept.
        assertEquals(
            "426.80000",
            RateMath.billedAmount(BigDecimal("10000"), BigDecimal("0.04268")).toPlainString(),
        )
        // A product that does not land on a cent keeps every digit.
        val odd = RateMath.billedAmount(BigDecimal("12345.67"), BigDecimal("6.6991"))
        assertEquals("82704.877897", odd.toPlainString())
    }

    @Test
    fun `format money groups thousands`() {
        assertEquals("10,000.00", RateMath.formatMoney(BigDecimal("10000")))
        assertEquals("1,234,567.89", RateMath.formatMoney(BigDecimal("1234567.89")))
    }

    @Test
    fun `format exact keeps every decimal and trims only trailing zeros`() {
        // Trailing zeros carry no information, so they are dropped -- nothing else is.
        assertEquals("426.8", RateMath.formatExact(BigDecimal("426.80000")))
        assertEquals("82,704.877897", RateMath.formatExact(BigDecimal("82704.877897")))
        assertEquals("66,991", RateMath.formatExact(BigDecimal("66991.0000000")))
        assertEquals("0.624285714289", RateMath.formatExact(BigDecimal("0.624285714289")))
        assertEquals("0.0001234", RateMath.formatExact(BigDecimal("0.00012340")))
    }

    @Test
    fun `format rate keeps the full returned precision`() {
        assertEquals("0.04268", RateMath.formatRate(BigDecimal("0.042680")))
        assertEquals("7.1", RateMath.formatRate(BigDecimal("7.1")))
        assertEquals("7", RateMath.formatRate(BigDecimal("7.000")))
        // Used to be capped at 6 decimals, which made a hand calculation disagree
        // with the displayed total. Mastercard returns 7.
        assertEquals("0.0001234", RateMath.formatRate(BigDecimal("0.0001234")))
        assertEquals("6.6991", RateMath.formatRate(BigDecimal("6.6991000")))
    }

    @Test
    fun `displayed rate multiplied by amount reproduces the billed total`() {
        // The whole point of the change: no hidden truncation between the two rows.
        val rate = BigDecimal("6.6991000")
        val amount = BigDecimal("12345.67")
        val billed = RateMath.billedAmount(amount, rate)
        val shownRate = BigDecimal(RateMath.formatRate(rate).replace(",", ""))
        assertEquals(
            0,
            billed.compareTo(amount.multiply(shownRate)),
        )
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
