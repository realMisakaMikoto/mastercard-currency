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

    const val MONEY_SCALE = 2
    private const val RATE_SCALE = 12

    /** Amount the cardholder is billed: amount x returned rate. */
    fun billedAmount(amount: BigDecimal, conversionRate: BigDecimal): BigDecimal =
        amount.multiply(conversionRate).setScale(MONEY_SCALE, RoundingMode.HALF_UP)

    /** Strips the bank fee back out of the returned rate. */
    fun baseRate(conversionRate: BigDecimal, bankFeePercent: BigDecimal): BigDecimal {
        if (bankFeePercent.signum() <= 0) return conversionRate
        val divisor = BigDecimal.ONE.add(
            bankFeePercent.divide(BigDecimal.valueOf(100), RATE_SCALE, RoundingMode.HALF_UP),
        )
        return conversionRate.divide(divisor, RATE_SCALE, RoundingMode.HALF_UP)
    }

    /** Fee portion of the bill, in the target currency. */
    fun feeAmount(amount: BigDecimal, conversionRate: BigDecimal, bankFeePercent: BigDecimal): BigDecimal {
        if (bankFeePercent.signum() <= 0) return BigDecimal.ZERO.setScale(MONEY_SCALE)
        val billed = billedAmount(amount, conversionRate)
        val unFee = amount.multiply(baseRate(conversionRate, bankFeePercent))
        return billed.subtract(unFee).setScale(MONEY_SCALE, RoundingMode.HALF_UP)
    }

    private fun moneyFormat(): DecimalFormat =
        DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.US))

    /** "10,000.00" */
    fun formatMoney(value: BigDecimal): String = moneyFormat().format(value)

    /** Thousands-grouped source amount, trimming a redundant ".00". */
    fun formatAmountInput(value: BigDecimal): String {
        val stripped = value.stripTrailingZeros()
        val pattern = if (stripped.scale() <= 0) "#,##0" else "#,##0." + "#".repeat(stripped.scale())
        return DecimalFormat(pattern, DecimalFormatSymbols.getInstance(Locale.US)).format(value)
    }

    /** Rate display: up to 6 decimals, trailing zeros trimmed. */
    fun formatRate(value: BigDecimal): String {
        val rounded = value.setScale(6, RoundingMode.HALF_UP).stripTrailingZeros()
        val decimals = maxOf(rounded.scale(), 0)
        val pattern = if (decimals == 0) "#,##0" else "#,##0." + "#".repeat(decimals)
        return DecimalFormat(pattern, DecimalFormatSymbols.getInstance(Locale.US)).format(rounded)
    }

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
