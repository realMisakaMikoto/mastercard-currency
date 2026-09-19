package com.vibecoding.mcfx.net

import com.vibecoding.mcfx.data.FetchAttempt
import com.vibecoding.mcfx.data.FetchLayer
import com.vibecoding.mcfx.data.FetchResponse
import com.vibecoding.mcfx.data.RateQuote
import com.vibecoding.mcfx.data.RateRequest
import com.vibecoding.mcfx.data.RateResult
import com.vibecoding.mcfx.logic.RateParser
import java.time.LocalDate

/** Test/dev double: replays a canned Mastercard payload without touching the network. */
class MockFetcher : RateFetcher {
    @Volatile
    var mockBody: String? = null

    override suspend fun fetch(request: RateRequest, today: LocalDate): FetchResponse {
        val body = mockBody ?: ""
        return FetchResponse(
            layer = FetchLayer.MOCK,
            status = 200,
            body = body,
            attempts = listOf(FetchAttempt("mock://rate", 200, body.take(200), FetchLayer.MOCK)),
        )
    }
}

/**
 * Runs the fetch layers in order and parses the first usable payload.
 *
 * There is deliberately NO local rate cache: every query performs a live request,
 * so the number on screen is always the current Mastercard figure and can never be
 * a stale copy. (A cache used to exist and never expired, which meant a rate could
 * be replayed days later.) All diagnostics from every layer are preserved on the
 * result so the diagnostics screen can show exactly what Mastercard answered.
 */
class RateEngine(
    private val mockFetcher: MockFetcher,
    private val webViewFetcher: RateFetcher,
    private val httpFetcher: RateFetcher,
) {

    /** When true (dev panel), every lookup replays [MockFetcher.mockBody]. */
    @Volatile
    var useMock: Boolean = false

    suspend fun lookup(
        request: RateRequest,
        today: LocalDate,
    ): RateResult {
        val requestedDate = request.requestedDate ?: today.toString()

        val allAttempts = mutableListOf<FetchAttempt>()
        var bestError: String? = null
        var bestCode: String? = null

        val layers: List<RateFetcher> = if (useMock) {
            listOf(mockFetcher)
        } else {
            listOf(webViewFetcher, httpFetcher)
        }

        for (fetcher in layers) {
            val response = try {
                fetcher.fetch(request, today)
            } catch (error: Throwable) {
                allAttempts += FetchAttempt(
                    url = fetcher::class.java.simpleName,
                    status = -1,
                    bodySnippet = "取数异常：${error.message}",
                    layer = FetchLayer.WEBVIEW_IN_PAGE,
                )
                continue
            }
            allAttempts += response.attempts

            when (val outcome = RateParser.parse(response.body)) {
                is RateParser.Outcome.Quote -> {
                    val quote = outcome.quote
                    val effective = quote.fxDate.ifBlank { requestedDate }
                    logAttempts(allAttempts, "成功")
                    return RateResult.Success(
                        quote = quote,
                        request = request,
                        effectiveDate = effective,
                        dateFellBack = request.requestedDate != null && effective != request.requestedDate,
                        layer = response.layer,
                        rawJson = response.body.take(4000),
                        attempts = allAttempts,
                    )
                }

                is RateParser.Outcome.ApiError -> {
                    bestError = outcome.message
                    bestCode = outcome.code
                }

                is RateParser.Outcome.Malformed -> {
                    if (bestError == null) {
                        bestError = if (response.status != 200 && response.status != -1) {
                            describeStatus(response.status)
                        } else {
                            outcome.message
                        }
                    }
                }
            }
        }

        val statuses = allAttempts.map { it.status }
        val firstHttpError = statuses.firstOrNull { it in 400..599 }
        val pageRefused = allAttempts.any { it.bodySnippet.contains(PAGE_REFUSED_MARKER) }
        // The service's own validation message is the most actionable signal there is.
        val apiValidation = allAttempts
            .firstOrNull { it.bodySnippet.contains("errorMessage") }
            ?.let { extractErrorMessage(it.bodySnippet) }

        val message = when {
            bestError != null && bestCode != null -> bestError
            apiValidation != null -> "万事达接口拒绝了请求：$apiValidation"
            pageRefused -> PAGE_BLOCKED_HINT
            statuses.contains(403) -> BLOCKED_HINT
            firstHttpError != null -> describeStatus(firstHttpError)
            bestError != null -> bestError
            else -> "未能获取汇率"
        }

        logAttempts(allAttempts, "失败：$message")

        return RateResult.Failure(
            request = request,
            message = message,
            errorCode = bestCode,
            attempts = allAttempts,
        )
    }

    private fun describeStatus(status: Int): String = when (status) {
        403 -> BLOCKED_HINT
        -1 -> "网络请求失败或超时"
        else -> "万事达返回 HTTP $status"
    }

    /** Pulls `errorMessage` out of a body snippet without needing a full parse. */
    private fun extractErrorMessage(snippet: String): String? {
        val match = Regex("\"errorMessage\"\\s*:\\s*\"([^\"]+)\"").find(snippet) ?: return null
        return match.groupValues[1].takeIf { it.isNotBlank() }
    }

    /**
     * Every probe result goes to logcat. The public endpoint is undocumented, so
     * `adb logcat -s MCFX` is how the parameter probing is read back.
     */
    private fun logAttempts(attempts: List<FetchAttempt>, outcome: String) {
        android.util.Log.i(TAG, "=== lookup $outcome, ${attempts.size} attempts ===")
        attempts.forEachIndexed { index, attempt ->
            val url = attempt.url.substringAfter("conversion-rates", attempt.url).take(220)
            val body = attempt.bodySnippet.replace("\n", " ").take(220)
            android.util.Log.i(TAG, "#${index + 1} [${attempt.status}] $url | $body")
        }
    }
    companion object {
        /** logcat tag: `adb logcat -s MCFX`. */
        const val TAG = "MCFX"

        /** Written by WebViewFetcher when the converter page itself was refused. */
        const val PAGE_REFUSED_MARKER = "换算页未能正常打开"

        const val BLOCKED_HINT =
            "万事达边缘返回 HTTP 403（Akamai 安全策略拦截）。请求已送达万事达，但当前会话被判定为风险来源。" +
                "请切换网络后重试，或使用手动汇率。"

        const val PAGE_BLOCKED_HINT =
            "万事达拒绝对本机浏览器会话返回换算页（Akamai Access Denied）。" +
                "该拦截基于客户端指纹而非网络，请先点「查看诊断」确认，或改用「手动输入汇率」。"
    }
}
