package com.vibecoding.mcfx

import com.vibecoding.mcfx.data.RateRequest
import com.vibecoding.mcfx.logic.MastercardEndpoints
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class MastercardEndpointsTest {

    private val request = RateRequest(
        fromCode = "JPY",
        toCode = "CNY",
        amount = BigDecimal("10000"),
        requestedDate = "2026-09-18",
        bankFeePercent = BigDecimal("0"),
    )

    private val today = LocalDate.of(2026, 9, 19)

    /**
     * The contract is taken verbatim from the page's own `_currency-converter.js`:
     *   new URLSearchParams({exchange_date, transaction_currency,
     *                        cardholder_billing_currency, bank_fee, transaction_amount})
     */
    @Test
    fun `rate url uses the snake_case parameters the site itself sends`() {
        val url = MastercardEndpoints.rateUrl(MastercardEndpoints.HOST_COM, request, "2026-09-18")
        assertEquals(
            "https://www.mastercard.com/marketingservices/public/mccom-services/currency-conversions/conversion-rates" +
                "?exchange_date=2026-09-18" +
                "&transaction_currency=JPY" +
                "&cardholder_billing_currency=CNY" +
                "&bank_fee=0" +
                "&transaction_amount=10000",
            url,
        )
    }

    @Test
    fun `an empty date means latest published rate, exactly like the official page`() {
        val noDate = request.copy(requestedDate = null)
        val urls = MastercardEndpoints.candidateUrls(MastercardEndpoints.HOST_COM, noDate, today)
        assertTrue(urls.first().contains("exchange_date=${MastercardEndpoints.LATEST_DATE_SENTINEL}"))
    }

    @Test
    fun `an explicit date is honoured first and only then backed off`() {
        val urls = MastercardEndpoints.candidateUrls(MastercardEndpoints.HOST_COM, request, today)
        assertTrue(urls.first().contains("exchange_date=2026-09-18"))
        assertTrue(urls[1].contains("exchange_date=2026-09-17"))
        // The full date semantics (7-day window, no latest-sentinel for explicit
        // dates) live in DateFallbackTest.
    }

    @Test
    fun `primary candidate is the live marketing endpoint, not the legacy settlement path`() {
        val urls = MastercardEndpoints.candidateUrls(MastercardEndpoints.HOST_COM, request, today)
        assertTrue(urls.first().contains(MastercardEndpoints.PATH_CONVERSION_RATES))
        assertTrue(!urls.first().contains("/settlement/"))
    }

    @Test
    fun `legacy settlement paths are kept out of the lookup path`() {
        val urls = MastercardEndpoints.candidateUrls(MastercardEndpoints.HOST_COM, request, today)
        assertTrue(
            "the public converter does not use these paths and Akamai 403s them",
            urls.none { it.contains("/settlement/") },
        )
        // They remain reachable for diagnostics only.
        val legacy = MastercardEndpoints.diagnosticLegacyUrls(
            MastercardEndpoints.HOST_COM,
            request,
            today,
        )
        assertTrue(legacy.isNotEmpty())
    }

    @Test
    fun `candidate urls are unique`() {
        val urls = MastercardEndpoints.candidateUrls(MastercardEndpoints.HOST_COM, request, today)
        assertEquals(urls.size, urls.distinct().size)
    }

    @Test
    fun `amount and fee are sent without grouping separators`() {
        val padded = request.copy(
            amount = BigDecimal("1234.50"),
            bankFeePercent = BigDecimal("2.5"),
        )
        val url = MastercardEndpoints.rateUrl(MastercardEndpoints.HOST_COM, padded, "2026-09-18")
        assertTrue(url.contains("&bank_fee=2.5"))
        assertTrue(url.contains("&transaction_amount=1234.5"))
    }

    @Test
    fun `currencies probe targets the published currency list`() {
        assertEquals(
            "https://www.mastercard.com/marketingservices/public/mccom-services/currency-conversions/currencies",
            MastercardEndpoints.currenciesUrl(),
        )
    }

    @Test
    fun `page url follows the host`() {
        assertEquals(
            MastercardEndpoints.CONVERTER_PAGE_CN,
            MastercardEndpoints.pageUrlFor(MastercardEndpoints.HOST_COM),
        )
        assertEquals(
            MastercardEndpoints.CONVERTER_PAGE_US,
            MastercardEndpoints.pageUrlFor(MastercardEndpoints.HOST_US),
        )
    }

    @Test
    fun `webview user agent loses the embedded-webview markers`() {
        val default =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8 Build/UP1A; wv) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Version/4.0 Chrome/131.0.0.0 Mobile Safari/537.36"
        val cleaned = com.vibecoding.mcfx.net.WebViewFetcher.cleanUserAgent(default)
        assertTrue(cleaned.contains("Chrome/131.0.0.0"))
        assertTrue(!cleaned.contains("; wv"))
        assertTrue(!cleaned.contains("Version/4.0"))
    }
}
