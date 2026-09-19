package com.vibecoding.mcfx.net

import com.vibecoding.mcfx.data.FetchResponse
import com.vibecoding.mcfx.data.RateRequest
import java.time.LocalDate

/**
 * One way of obtaining a raw Mastercard payload. Implementations are layered:
 * the WebView in-page fetcher runs first because it presents a genuine browser
 * context (the same thing that lets the user's own browser load the page), and
 * the native HTTP fetcher is the fallback when no WebView is usable.
 */
interface RateFetcher {
    suspend fun fetch(request: RateRequest, today: LocalDate): FetchResponse
    fun release() {}
}
