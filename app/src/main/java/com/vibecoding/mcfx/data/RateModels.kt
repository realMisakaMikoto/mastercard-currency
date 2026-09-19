package com.vibecoding.mcfx.data

import java.math.BigDecimal

/** Inputs for one conversion lookup. */
data class RateRequest(
    val fromCode: String,
    val toCode: String,
    val amount: BigDecimal,
    /** null = 未填写，默认当天 */
    val requestedDate: String?,
    /** 百分比；0 表示未填写 */
    val bankFeePercent: BigDecimal,
) {
    /**
     * Amount as Mastercard expects it: plain decimal, no grouping separators.
     * 10000 -> "10000", 10000.50 -> "10000.5"
     */
    val amountParam: String
        get() = amount.stripTrailingZeros().toPlainString()

    val bankFeeParam: String
        get() = bankFeePercent.stripTrailingZeros().toPlainString()
}

/** Which layer produced a response. Surfaced in the diagnostics screen. */
enum class FetchLayer(val label: String) {
    WEBVIEW_IN_PAGE("页面内同源请求"),
    NATIVE_HTTP("原生 HTTPS 请求"),
    PAGE_DOM("页面文案抓取"),
    MOCK("模拟响应(调试)"),
}

/** One raw HTTP attempt, kept for diagnostics. */
data class FetchAttempt(
    val url: String,
    val status: Int,
    val bodySnippet: String,
    val layer: FetchLayer,
)

/** Raw payload returned by a fetch layer, before parsing. */
data class FetchResponse(
    val layer: FetchLayer,
    val status: Int,
    val body: String,
    val attempts: List<FetchAttempt> = emptyList(),
)

/** Parsed Mastercard payload. */
data class RateQuote(
    val conversionRate: BigDecimal,
    val crdhldBillAmt: BigDecimal,
    val fxDate: String,
    val transCurr: String,
    val crdhldBillCurr: String,
    val bankFee: BigDecimal,
)

sealed interface RateResult {
    data class Success(
        val quote: RateQuote,
        val request: RateRequest,
        /** The date actually used, which may differ from the requested one after fallback. */
        val effectiveDate: String,
        val dateFellBack: Boolean,
        val layer: FetchLayer,
        val rawJson: String,
        val attempts: List<FetchAttempt>,
    ) : RateResult

    data class Failure(
        val request: RateRequest,
        val message: String,
        val errorCode: String?,
        val attempts: List<FetchAttempt>,
    ) : RateResult
}
