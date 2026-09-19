package com.vibecoding.mcfx

import com.vibecoding.mcfx.data.RateRequest
import com.vibecoding.mcfx.logic.MastercardEndpoints
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Date semantics of the candidate list.
 *
 * The rule that matters: an explicitly chosen historical date must never be
 * answered with "the latest published rate". Falling back to the sentinel would
 * silently replace a 2024 question with today's number.
 */
class DateFallbackTest {

    private val today = LocalDate.of(2026, 9, 19)

    private fun request(date: String?) = RateRequest(
        fromCode = "USD",
        toCode = "CNY",
        amount = BigDecimal("1000"),
        requestedDate = date,
        bankFeePercent = BigDecimal.ZERO,
    )

    private fun urls(date: String?) =
        MastercardEndpoints.candidateUrls(MastercardEndpoints.HOST_COM, request(date), today)

    private fun datesIn(urls: List<String>): List<String> =
        urls.map { it.substringAfter("exchange_date=").substringBefore('&') }

    @Test
    fun `empty date starts with the latest sentinel`() {
        val dates = datesIn(urls(null))
        assertEquals(MastercardEndpoints.LATEST_DATE_SENTINEL, dates.first())
        assertTrue("today should still be tried as a fallback", dates.contains(today.toString()))
    }

    @Test
    fun `explicit date never uses the latest sentinel`() {
        val dates = datesIn(urls("2024-01-01"))
        assertFalse(
            "an explicit date must not fall back to the latest published rate",
            dates.contains(MastercardEndpoints.LATEST_DATE_SENTINEL),
        )
        assertFalse(urls("2024-01-01").any { it.contains(MastercardEndpoints.LATEST_DATE_SENTINEL) })
    }

    @Test
    fun `explicit date walks back a full week`() {
        val dates = datesIn(urls("2024-01-08"))
        assertEquals(8, dates.size)
        assertEquals("2024-01-08", dates.first())
        for (back in 1..MastercardEndpoints.FALLBACK_DAYS) {
            assertTrue(
                "expected a candidate $back day(s) earlier",
                dates.contains(LocalDate.of(2024, 1, 8).minusDays(back.toLong()).toString()),
            )
        }
    }

    @Test
    fun `fallback window is seven days`() {
        assertEquals(7, MastercardEndpoints.FALLBACK_DAYS)
    }

    @Test
    fun `explicit date crossing a month boundary is handled`() {
        val dates = datesIn(urls("2024-03-03"))
        assertEquals("2024-03-03", dates.first())
        assertTrue(dates.contains("2024-02-29")) // leap year
        assertTrue(dates.contains("2024-02-25"))
    }

    @Test
    fun `candidates are unique and stay on the public converter endpoint`() {
        val list = urls("2024-01-01")
        assertEquals(list.size, list.distinct().size)
        assertTrue(list.all { it.contains(MastercardEndpoints.PATH_CONVERSION_RATES) })
    }

    @Test
    fun `legacy settlement endpoints are not part of a normal lookup`() {
        for (date in listOf(null, "2024-01-01", "2026-09-19")) {
            val list = urls(date)
            assertFalse(
                "legacy credentialed paths must not appear in candidateUrls (date=$date)",
                list.any { it.contains("/settlement/") },
            )
        }
    }

    @Test
    fun `legacy urls remain available for diagnostics only`() {
        val legacy = MastercardEndpoints.diagnosticLegacyUrls(
            MastercardEndpoints.HOST_COM,
            request("2024-01-01"),
            today,
        )
        assertTrue(legacy.all { it.contains("/settlement/") })
        assertTrue(legacy.all { it.contains("fxDate=2024-01-01") })
    }

    @Test
    fun `query parameters stay snake_case`() {
        val url = urls("2024-01-01").first()
        assertTrue(url.contains("exchange_date="))
        assertTrue(url.contains("transaction_currency="))
        assertTrue(url.contains("cardholder_billing_currency="))
        assertTrue(url.contains("bank_fee="))
        assertTrue(url.contains("transaction_amount="))
    }
}
