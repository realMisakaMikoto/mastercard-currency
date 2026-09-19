package com.vibecoding.mcfx.logic

import java.math.BigDecimal

/**
 * Decides whether a lookup may start. Pure Kotlin so the re-entrancy rules are
 * unit-testable without an emulator or a WebView.
 *
 * Background: `WebViewFetcher` drives a single WebView, and a second lookup while
 * the first is still running would produce two requests whose answers can arrive
 * out of order -- the UI would then show one query's rate for another query's
 * inputs. The gate is the single place that says "no" to that.
 */
object QueryGate {

    sealed interface Result {
        /** Inputs are valid and nothing else is running. */
        data class Ready(val amount: BigDecimal) : Result

        /** A lookup is already in flight; the new request is dropped silently. */
        data object AlreadyRunning : Result

        /** Inputs are unusable and the user needs telling. */
        data class Invalid(val message: String) : Result
    }

    const val MESSAGE_BAD_AMOUNT = "请输入大于 0 的金额"
    const val MESSAGE_SAME_CURRENCY = "源货币与目标货币相同，请重新选择"

    fun check(
        loading: Boolean,
        rawAmount: BigDecimal?,
        fromCode: String,
        toCode: String,
    ): Result = when {
        loading -> Result.AlreadyRunning
        rawAmount == null || rawAmount.signum() <= 0 -> Result.Invalid(MESSAGE_BAD_AMOUNT)
        fromCode == toCode -> Result.Invalid(MESSAGE_SAME_CURRENCY)
        else -> Result.Ready(rawAmount)
    }
}
