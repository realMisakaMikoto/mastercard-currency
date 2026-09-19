package com.vibecoding.mcfx

import com.vibecoding.mcfx.logic.QueryGate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

/**
 * The re-entrancy rules that keep a single WebView from serving two lookups at once.
 */
class QueryGateTest {

    private fun check(
        loading: Boolean = false,
        amount: String? = "1000",
        from: String = "USD",
        to: String = "CNY",
    ) = QueryGate.check(loading, amount?.let(::BigDecimal), from, to)

    @Test
    fun `valid input while idle is allowed`() {
        val result = check()
        assertTrue(result is QueryGate.Result.Ready)
        assertEquals(BigDecimal("1000"), (result as QueryGate.Result.Ready).amount)
    }

    @Test
    fun `a lookup already running swallows the next request`() {
        val result = check(loading = true)
        assertEquals(QueryGate.Result.AlreadyRunning, result)
    }

    @Test
    fun `busy wins over invalid input so a second tap cannot raise an error`() {
        // Otherwise a stray tap during Loading would flash a validation notice.
        // An empty box parses to null (see RateMath.parseDecimal).
        assertEquals(QueryGate.Result.AlreadyRunning, check(loading = true, amount = null))
        assertEquals(QueryGate.Result.AlreadyRunning, check(loading = true, amount = "0"))
    }

    @Test
    fun `empty or zero amount is rejected`() {
        assertEquals(
            QueryGate.Result.Invalid(QueryGate.MESSAGE_BAD_AMOUNT),
            check(amount = null),
        )
        assertEquals(
            QueryGate.Result.Invalid(QueryGate.MESSAGE_BAD_AMOUNT),
            check(amount = "0"),
        )
        assertEquals(
            QueryGate.Result.Invalid(QueryGate.MESSAGE_BAD_AMOUNT),
            check(amount = "-5"),
        )
    }

    @Test
    fun `identical currencies are rejected`() {
        assertEquals(
            QueryGate.Result.Invalid(QueryGate.MESSAGE_SAME_CURRENCY),
            check(from = "CNY", to = "CNY"),
        )
    }

    @Test
    fun `fractional amounts are allowed`() {
        val result = check(amount = "0.01")
        assertEquals(BigDecimal("0.01"), (result as QueryGate.Result.Ready).amount)
    }
}
