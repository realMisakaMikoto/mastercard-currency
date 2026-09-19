package com.vibecoding.mcfx

import com.vibecoding.mcfx.logic.RateParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RateParserTest {

    /** Verbatim success sample from Mastercard's published spec. */
    private val officialSample = """
        {
           "name":"settlement-conversion-rate",
           "description":"Settlement conversion rate and billing amount",
           "date":"2017-11-03 03:59:50",
           "data":{
              "conversionRate":0.57,
              "crdhldBillAmt":13.11,
              "fxDate":"2019-09-30",
              "transCurr":"ALL",
              "crdhldBillCurr":"DZD",
              "transAmt":23,
              "bankFee":5
           }
        }
    """.trimIndent()

    /** Verbatim error sample from the same spec. */
    private val officialError = """
        {
            "name":"settlement-conversion-rate",
            "description":"An error occurred during the request",
            "date":"2019-08-08 16:34:34",
            "type":"error",
            "data":{
                "errorCode":"104",
                "errorMessage":"Not Found , The calculated cross rates for the selected date is not available."
            }
        }
    """.trimIndent()

    @Test
    fun `parses the official success sample`() {
        val outcome = RateParser.parse(officialSample)
        assertTrue(outcome is RateParser.Outcome.Quote)
        val quote = (outcome as RateParser.Outcome.Quote).quote
        assertEquals("0.57", quote.conversionRate.toPlainString())
        assertEquals("13.11", quote.crdhldBillAmt.toPlainString())
        assertEquals("2019-09-30", quote.fxDate)
        assertEquals("ALL", quote.transCurr)
        assertEquals("DZD", quote.crdhldBillCurr)
        assertEquals("5", quote.bankFee.toPlainString())
    }

    @Test
    fun `surfaces the error code from the official error sample`() {
        val outcome = RateParser.parse(officialError)
        assertTrue(outcome is RateParser.Outcome.ApiError)
        val error = outcome as RateParser.Outcome.ApiError
        assertEquals("104", error.code)
        assertTrue(error.message.contains("cross rates"))
    }

    @Test
    fun `accepts numeric strings the way the public endpoint sometimes returns them`() {
        val body = """{"data":{"conversionRate":"0.04268","crdhldBillAmt":"426.80","fxDate":"2026-09-18"}}"""
        val outcome = RateParser.parse(body)
        assertTrue(outcome is RateParser.Outcome.Quote)
        val quote = (outcome as RateParser.Outcome.Quote).quote
        assertEquals("0.04268", quote.conversionRate.toPlainString())
        assertEquals("426.80", quote.crdhldBillAmt.toPlainString())
    }

    @Test
    fun `missing conversionRate is reported as an api error`() {
        val outcome = RateParser.parse("""{"data":{"fxDate":"2026-09-18"}}""")
        assertTrue(outcome is RateParser.Outcome.ApiError)
    }

    @Test
    fun `non positive rate is rejected`() {
        val outcome = RateParser.parse("""{"data":{"conversionRate":0}}""")
        assertTrue(outcome is RateParser.Outcome.ApiError)
    }

    @Test
    fun `html error page is reported as malformed`() {
        val outcome = RateParser.parse("<html><body>Access Denied</body></html>")
        assertTrue(outcome is RateParser.Outcome.Malformed)
    }

    @Test
    fun `empty body is reported as malformed`() {
        assertTrue(RateParser.parse("   ") is RateParser.Outcome.Malformed)
    }

    @Test
    fun `missing conversionRate is reported with the body so the cause is visible`() {
        val outcome = RateParser.parse("""{"name":"x"}""")
        assertTrue(outcome is RateParser.Outcome.ApiError)
        assertTrue((outcome as RateParser.Outcome.ApiError).message.contains("conversionRate"))
    }

    @Test
    fun `payload nested in a data array is still parsed`() {
        val body = """{"data":[{"conversionRate":0.04268,"crdhldBillAmt":426.8,"fxDate":"2026-09-18"}]}"""
        val outcome = RateParser.parse(body)
        assertTrue(outcome is RateParser.Outcome.Quote)
        assertEquals("0.04268", (outcome as RateParser.Outcome.Quote).quote.conversionRate.toPlainString())
    }

    @Test
    fun `rate at the document root is accepted`() {
        val body = """{"conversionRate":7.1234,"crdhldBillAmt":71234,"fxDate":"2026-09-18"}"""
        val outcome = RateParser.parse(body)
        assertTrue(outcome is RateParser.Outcome.Quote)
        assertEquals("7.1234", (outcome as RateParser.Outcome.Quote).quote.conversionRate.toPlainString())
    }
}
