package com.vibecoding.mcfx.logic

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * Pure conversion + formatting maths.
 *
 * Mastercard's own worked example (spec sample): transAmt 23, bankFee 5,
 * conversionRate 0.57, crdhldBillAmt 13.11 -- and 23 x 0.57 = 13.11 exactly.
 * Therefore the returned `conversionRate` ALREADY includes the bank fee.
 * The un-fee'd rate is therefore rate / (1 + fee/100).
 */
object RateMath {

    private const val RATE_SCALE = 12

    /** ISO 4217 default when a currency's precision is unknown. */
    const val DEFAULT_MINOR_UNITS = 2

    /**
     * Amount the cardholder is billed: the EXACT product `amount x rate`, with no
     * rounding at all.
     *
     * Rounding to a currency's minor units was removed on request: the figure on
     * screen is now the exact product, so multiplying the displayed rate by the
     * displayed amount always reproduces the displayed total.
     */
    fun billedAmount(amount: BigDecimal, conversionRate: BigDecimal): BigDecimal =
        amount.multiply(conversionRate)

    /** Strips the bank fee back out of the returned rate. */
    fun baseRate(conversionRate: BigDecimal, bankFeePercent: BigDecimal): BigDecimal {
        if (bankFeePercent.signum() <= 0) return conversionRate
        val divisor = BigDecimal.ONE.add(
            bankFeePercent.divide(BigDecimal.valueOf(100), RATE_SCALE, RoundingMode.HALF_UP),
        )
        return conversionRate.divide(divisor, RATE_SCALE, RoundingMode.HALF_UP)
    }

    /**
     * Fee portion of the bill, in the target currency: the exact difference between
     * the billed total and the same amount at the un-fee'd rate. Not rounded, so the
     * breakdown still adds up against the total.
     */
    fun feeAmount(
        amount: BigDecimal,
        conversionRate: BigDecimal,
        bankFeePercent: BigDecimal,
    ): BigDecimal {
        if (bankFeePercent.signum() <= 0) return BigDecimal.ZERO
        val billed = billedAmount(amount, conversionRate)
        val unFee = amount.multiply(baseRate(conversionRate, bankFeePercent))
        return billed.subtract(unFee)
    }

    /**
     * Fixed-precision display, e.g. "10,000.00" for a 2-decimal currency, "10,000"
     * for JPY, "10,000.000" for KWD.
     *
     * Kept because the currency dataset carries real ISO 4217 precision. The
     * converted amount no longer uses it -- see [formatExact].
     */
    fun formatMoney(value: BigDecimal, minorUnits: Int = DEFAULT_MINOR_UNITS): String {
        val scale = minorUnits.coerceIn(0, 6)
        val pattern = if (scale == 0) "#,##0" else "#,##0." + "0".repeat(scale)
        return DecimalFormat(pattern, DecimalFormatSymbols.getInstance(Locale.US)).format(value)
    }

    /** Thousands-grouped source amount, trimming a redundant ".00". */
    fun formatAmountInput(value: BigDecimal): String {
        val stripped = value.stripTrailingZeros()
        val pattern = if (stripped.scale() <= 0) "#,##0" else "#,##0." + "#".repeat(stripped.scale())
        return DecimalFormat(pattern, DecimalFormatSymbols.getInstance(Locale.US)).format(value)
    }

    /**
     * Shows every decimal digit the value carries, trimming only TRAILING ZEROS.
     *
     * Nothing is rounded away, so 10000 x 0.04268 renders as "426.80000" rather than
     * "426.80", and a 7-decimal Mastercard rate stays a 7-decimal multiplier.
     */
    fun formatExact(value: BigDecimal): String {
        val stripped = value.stripTrailingZeros()
        val decimals = maxOf(stripped.scale(), 0)
        val pattern = if (decimals == 0) "#,##0" else "#,##0." + "#".repeat(decimals)
        return DecimalFormat(pattern, DecimalFormatSymbols.getInstance(Locale.US)).format(stripped)
    }

    /**
     * Rate display: every decimal the service returned, trailing zeros trimmed.
     *
     * There is deliberately no fixed cap. Truncating the rate (it used to be cut to
     * 6 decimals while Mastercard returns 7) made a hand calculation with the
     * on-screen rate disagree with the on-screen total.
     */
    fun formatRate(value: BigDecimal): String = formatExact(value)

    /** Inverse rate for the "1 TARGET = x SOURCE" helper line. */
    fun inverseRate(conversionRate: BigDecimal): BigDecimal =
        if (conversionRate.signum() == 0) BigDecimal.ZERO
        else BigDecimal.ONE.divide(conversionRate, RATE_SCALE, RoundingMode.HALF_UP)

    /** Parses user input, tolerating thousands separators and full-width digits. */
    fun parseDecimal(input: String): BigDecimal? {
        val normalised = input
            .trim()
            .map { if (it.code in 0xFF10..0xFF19) (it.code - 0xFF10 + '0'.code).toChar() else it }
            .joinToString("")
            .replace(",", "")
            .replace(" ", "")
            .replace("，", "")
        if (normalised.isEmpty()) return null
        return runCatching { BigDecimal(normalised) }.getOrNull()
    }
}
