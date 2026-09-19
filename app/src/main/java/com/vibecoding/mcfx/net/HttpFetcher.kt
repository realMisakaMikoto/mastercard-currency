package com.vibecoding.mcfx.net

import com.vibecoding.mcfx.data.FetchAttempt
import com.vibecoding.mcfx.data.FetchLayer
import com.vibecoding.mcfx.data.FetchResponse
import com.vibecoding.mcfx.data.RateRequest
import com.vibecoding.mcfx.logic.MastercardEndpoints
import com.vibecoding.mcfx.logic.RateParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate

/**
 * Fallback fetch layer: a direct HTTPS GET against the same endpoints the page uses.
 *
 * Request headers mirror what the converter page's own XHR sends. This matters a
 * lot: the identical `currencies` URL returns 403 with a bare client and 200 once
 * the full browser header set is present, so the headers are part of the contract,
 * not decoration.
 */
class HttpFetcher : RateFetcher {

    override val layer: FetchLayer = FetchLayer.NATIVE_HTTP

    override suspend fun fetch(request: RateRequest, today: LocalDate): FetchResponse =
        withContext(Dispatchers.IO) {
            val attempts = mutableListOf<FetchAttempt>()

            for (host in MastercardEndpoints.HOSTS) {
                val urls = MastercardEndpoints.candidateUrls(host, request, today)
                for (url in urls.take(MAX_URLS_PER_HOST)) {
                    val result = try {
                        get(url, host)
                    } catch (error: Throwable) {
                        attempts += FetchAttempt(url, -1, "请求异常：${error.message}", FetchLayer.NATIVE_HTTP)
                        continue
                    }
                    attempts += FetchAttempt(url, result.status, result.body.take(400), FetchLayer.NATIVE_HTTP)

                    if (result.status == 200 &&
                        RateParser.parse(result.body) is RateParser.Outcome.Quote
                    ) {
                        return@withContext FetchResponse(
                            layer = FetchLayer.NATIVE_HTTP,
                            status = 200,
                            body = result.body,
                            attempts = attempts,
                        )
                    }
                }
            }

            val last = attempts.lastOrNull()
            FetchResponse(
                layer = FetchLayer.NATIVE_HTTP,
                status = last?.status ?: -1,
                body = last?.bodySnippet ?: "",
                attempts = attempts,
            )
        }

    private class HttpResult(val status: Int, val body: String)

    private fun get(url: String, host: String): HttpResult {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            instanceFollowRedirects = true
            applyBrowserHeaders(this, host)
        }
        return try {
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            HttpResult(status, body)
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private const val CONNECT_TIMEOUT_MS = 10_000
        private const val READ_TIMEOUT_MS = 15_000
        private const val MAX_URLS_PER_HOST = 13

        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/131.0.0.0 Mobile Safari/537.36"

        /** Header set that the page's own XHR carries; stripped of it Akamai answers 403. */
        fun applyBrowserHeaders(connection: HttpURLConnection, host: String) {
            set(connection, "User-Agent", USER_AGENT)
            set(connection, "Accept", "application/json, text/plain, */*")
            set(connection, "Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
            set(connection, "Referer", MastercardEndpoints.pageUrlFor(host))
            set(connection, "Origin", host)
            set(connection, "sec-ch-ua", "\"Google Chrome\";v=\"131\", \"Chromium\";v=\"131\", \"Not_A Brand\";v=\"24\"")
            set(connection, "sec-ch-ua-mobile", "?1")
            set(connection, "sec-ch-ua-platform", "\"Android\"")
            set(connection, "Sec-Fetch-Dest", "empty")
            set(connection, "Sec-Fetch-Mode", "cors")
            set(connection, "Sec-Fetch-Site", "same-origin")
            set(connection, "Cache-Control", "no-cache")
        }

        /** Origin/Referer are restricted in some JDKs; never let that break the request. */
        private fun set(connection: HttpURLConnection, name: String, value: String) {
            runCatching { connection.setRequestProperty(name, value) }
        }
    }
}
