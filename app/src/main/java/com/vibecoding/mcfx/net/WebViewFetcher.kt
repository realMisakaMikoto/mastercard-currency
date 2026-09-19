package com.vibecoding.mcfx.net

import android.content.Context
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.vibecoding.mcfx.data.FetchAttempt
import com.vibecoding.mcfx.data.FetchLayer
import com.vibecoding.mcfx.data.FetchResponse
import com.vibecoding.mcfx.data.RateRequest
import com.vibecoding.mcfx.logic.MastercardEndpoints
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/**
 * Primary fetch layer: loads the Mastercard converter page in a real WebView and
 * issues the rate lookup from inside that page.
 *
 * Why it is written this way:
 *
 * 1. Mastercard's edge runs Akamai Bot Manager. Probing showed `favicon.ico` returns
 *    200 while every HTML document returns 403 for the same IP, so the block is
 *    client-fingerprint based, not network based. An Android WebView is trivially
 *    fingerprinted as "not a browser" unless the usual tells are removed: the
 *    default UA carries `; wv` and `Version/4.0`, third-party cookies must be
 *    accepted, and the WebView must be ATTACHED and laid out at a real size (a
 *    detached or 1dp WebView reports a zero-sized viewport). [attach] is therefore
 *    driven from the Compose tree with a full-size AndroidView.
 *
 * 2. The rate endpoint is bot-cookie protected: `.../currencies` answers 200 to a
 *    bare client, but `.../conversion-rates` answers 403 until the page's own edge
 *    cookies (`_abck`, `bm_sz`, `bm_sv`) exist. Hence: load the page first, then
 *    call the API from that page's origin.
 *
 * 3. Request building follows the site's own `_currency-converter.js` exactly --
 *    snake_case query parameters. See [MastercardEndpoints.rateUrl].
 */
class WebViewFetcher(private val context: Context) : RateFetcher {

    private var webView: WebView? = null
    private var pageDeferred: CompletableDeferred<Boolean>? = null
    private var resultDeferred: CompletableDeferred<String>? = null
    private var scriptDeferred: CompletableDeferred<String>? = null

    /**
     * Serialises everything that touches the WebView. `prewarm()` (fired at startup)
     * and `fetch()` (fired by the user) would otherwise drive the same WebView
     * concurrently, and a fast user can start a second lookup while the first is
     * still in flight -- both end up completing each other's Deferred and the app
     * shows one query's rate for another query's inputs.
     */
    private val operationMutex = Mutex()

    /**
     * Incremented for every JS round trip. The page echoes it back through the
     * bridge, so a callback that arrives after its timeout cannot complete a later
     * request's Deferred. Read from the JavaBridge thread, hence @Volatile.
     */
    @Volatile
    private var callbackGeneration = 0L

    /** URL of the navigation currently being awaited, used to reject late finishes. */
    @Volatile
    private var pendingPageUrl: String? = null

    private fun nextGeneration(): Long {
        callbackGeneration += 1
        return callbackGeneration
    }

    @Volatile
    private var mainFrameStatus: Int = 0

    @Volatile
    private var mainFrameError: String? = null

    /** The converter page is currently loaded and its edge cookies are valid. */
    @Volatile
    private var loadedPageUrl: String? = null

    @Volatile
    private var pageReady: Boolean = false

    /** API URLs the page itself requested; useful diagnostics. */
    private val observedApiUrls: MutableList<String> =
        java.util.Collections.synchronizedList(mutableListOf())

    /** Set once [attach] has configured a WebView. */
    val isAttached: Boolean get() = webView != null

    /**
     * True when the converter page is loaded and its cookies are valid, so the next
     * lookup can skip the page load. The UI uses this to decide whether a query
     * needs the heavyweight "establishing session" indicator or a small inline one.
     */
    val isSessionReady: Boolean get() = pageReady

    private val bridge = Bridge()

    inner class Bridge {
        @JavascriptInterface
        fun onResult(payload: String) {
            val generation = runCatching { JSONObject(payload).optLong(GEN_KEY, -1L) }.getOrDefault(-1L)
            if (generation != callbackGeneration) {
                android.util.Log.i(TAG, "bridge: dropped stale result (gen=$generation current=$callbackGeneration)")
                return
            }
            resultDeferred?.complete(payload)
        }

        @JavascriptInterface
        fun onValue(value: String) {
            val separator = value.indexOf(GEN_SEPARATOR)
            val generation = if (separator > 0) value.substring(0, separator).toLongOrNull() ?: -1L else -1L
            if (generation != callbackGeneration) {
                android.util.Log.i(TAG, "bridge: dropped stale value (gen=$generation current=$callbackGeneration)")
                return
            }
            scriptDeferred?.complete(if (separator > 0) value.substring(separator + 1) else value)
        }
    }

    /**
     * Wires a real, attached WebView. Must be called on the main thread from the
     * view hierarchy so the view has a window and non-zero viewport metrics.
     */
    fun attach(wv: WebView) {
        if (webView === wv) return
        // A different WebView instance means the page that was loaded lived in the
        // old one. Without this the fetcher would think a blank WebView still holds
        // a warm session, skip the load, fail, and then pay for a full reload --
        // which is exactly the "it fetched the cookies again" symptom.
        if (webView != null) {
            android.util.Log.i(TAG, "attach: WebView replaced (pageReady=$pageReady) -> session invalidated")
            invalidatePage()
        }
        webView = wv

        wv.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            // Strip the WebView markers so the UA matches Chrome for Android.
            userAgentString = cleanUserAgent(userAgentString)
        }

        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(wv, true)
        }

        wv.addJavascriptInterface(bridge, BRIDGE_NAME)
        wv.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                val expected = pendingPageUrl ?: return
                // A late finish from an earlier navigation must not satisfy the one
                // we are waiting for now.
                val expectedHost = expected.substringAfter("://").substringBefore('/')
                if (url != null && !url.contains(expectedHost)) {
                    android.util.Log.i(TAG, "onPageFinished: ignoring foreign url=$url")
                    return
                }
                pageDeferred?.complete(true)
            }

            override fun onReceivedHttpError(
                view: WebView?,
                request: WebResourceRequest?,
                errorResponse: WebResourceResponse?,
            ) {
                if (request?.isForMainFrame == true) {
                    mainFrameStatus = errorResponse?.statusCode ?: -1
                }
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?,
            ) {
                if (request?.isForMainFrame == true) {
                    mainFrameError = error?.description?.toString()
                }
            }

            /** Records the rate requests the page's own front-end makes. */
            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?,
            ): WebResourceResponse? {
                val url = request?.url?.toString().orEmpty()
                if (url.contains("mccom-services") && !observedApiUrls.contains(url)) {
                    observedApiUrls.add(url)
                }
                return null
            }

            // Deliberately no shouldOverrideUrlLoading: the page is allowed to
            // navigate wherever it needs to, exactly like a normal browser.
        }
    }

    override suspend fun fetch(request: RateRequest, today: LocalDate): FetchResponse =
        operationMutex.withLock {
            fetchLocked(request, today)
        }

    private suspend fun fetchLocked(request: RateRequest, today: LocalDate): FetchResponse =
        withContext(Dispatchers.Main.immediate) {
            val wv = webView
                ?: return@withContext FetchResponse(
                    layer = FetchLayer.WEBVIEW_IN_PAGE,
                    status = -1,
                    body = "WebView 尚未就绪",
                    attempts = emptyList(),
                )

            val attempts = mutableListOf<FetchAttempt>()

            for (host in MastercardEndpoints.HOSTS) {
                val converterUrl = MastercardEndpoints.pageUrlFor(host)

                var pageState = navigate(wv, converterUrl)
                if (!pageState.ok) {
                    // One warm-up pass: collect the locale root (and its cookies),
                    // then retry. Akamai often admits the second request of a session.
                    navigate(wv, MastercardEndpoints.warmUpUrlFor(host))
                    pageState = navigate(wv, converterUrl, forceReload = true)
                }

                if (!pageState.ok) {
                    attempts += FetchAttempt(
                        url = converterUrl,
                        status = pageState.status,
                        bodySnippet = "换算页未能正常打开：${pageState.describe()}",
                        layer = FetchLayer.WEBVIEW_IN_PAGE,
                    )
                    continue
                }

                // Consent + settle only cost time on a real page load; a reused page
                // already has its cookies and running scripts.
                if (!pageState.reused) {
                    val consent = acceptConsent(wv)
                    delay(PAGE_SETTLE_MS)
                    attempts += FetchAttempt(
                        url = converterUrl,
                        status = 200,
                        bodySnippet = pageDiagnostics(wv, consent),
                        layer = FetchLayer.WEBVIEW_IN_PAGE,
                    )
                }

                val urls = MastercardEndpoints.candidateUrls(host, request, today)

                var payload = runInPage(wv, urls)
                if (!isUsable(payload)) {
                    // The edge sensor can take a moment longer; one retry is cheaper
                    // than making the user press the button again.
                    delay(FETCH_RETRY_DELAY_MS)
                    val retry = runInPage(wv, urls)
                    if (isUsable(retry)) payload = retry
                }

                // Only a genuine session problem (403 / Akamai deny page) justifies
                // re-acquiring cookies. Any other unusable payload must NOT trigger a
                // page reload -- that is what made ordinary cache misses look like the
                // app was "fetching cookies again".
                if (!isUsable(payload) && pageState.reused && looksLikeSessionProblem(payload)) {
                    android.util.Log.i(TAG, "navigate: reused session rejected (403) -> refreshing cookies")
                    invalidatePage()
                    val reloaded = navigate(wv, converterUrl, forceReload = true)
                    if (reloaded.ok) {
                        acceptConsent(wv)
                        delay(PAGE_SETTLE_MS)
                        payload = runInPage(wv, urls)
                    }
                }

                if (payload == null) {
                    attempts += FetchAttempt(
                        url = host,
                        status = -1,
                        bodySnippet = "页面内请求超时（${IN_PAGE_TIMEOUT_MS / 1000}s）",
                        layer = FetchLayer.WEBVIEW_IN_PAGE,
                    )
                    continue
                }

                val json = runCatching { JSONObject(payload) }.getOrNull()
                if (json == null) {
                    attempts += FetchAttempt(
                        url = host,
                        status = -1,
                        bodySnippet = "页面返回无法解析：${payload.take(200)}",
                        layer = FetchLayer.WEBVIEW_IN_PAGE,
                    )
                    continue
                }

                val ok = json.optBoolean("ok", false)
                attempts += readAttempts(json.optJSONArray("attempts"))

                if (ok) {
                    return@withContext FetchResponse(
                        layer = FetchLayer.WEBVIEW_IN_PAGE,
                        status = json.optInt("status", 200),
                        body = json.optString("body", ""),
                        attempts = attempts,
                    )
                }
            }

            val last = attempts.lastOrNull()
            FetchResponse(
                layer = FetchLayer.WEBVIEW_IN_PAGE,
                status = last?.status ?: -1,
                body = last?.bodySnippet ?: "",
                attempts = attempts,
            )
        }

    override fun release() {
        runCatching {
            webView?.let {
                it.stopLoading()
                it.destroy()
            }
        }
        webView = null
    }

    // ------------------------------------------------------------------ page

    private class PageState(
        val status: Int,
        val blocked: Boolean,
        val error: String?,
        /** True when an already-loaded page was reused instead of reloaded. */
        val reused: Boolean = false,
    ) {
        val ok: Boolean get() = status in 200..299 && !blocked && error == null

        fun describe(): String = when {
            error != null -> error
            blocked -> "HTTP $status（Akamai Access Denied 拦截页）"
            status > 0 -> "HTTP $status"
            else -> "未知错误"
        }
    }

    /**
     * Loads [url], unless the same page is already loaded and its cookies are still
     * considered good -- in which case nothing is requested at all and the caller
     * can go straight to the rate call.
     */
    private suspend fun navigate(wv: WebView, url: String, forceReload: Boolean = false): PageState {
        if (!forceReload && pageReady && loadedPageUrl == url) {
            android.util.Log.i(TAG, "navigate: REUSE already-loaded page (no request)")
            return PageState(status = 200, blocked = false, error = null, reused = true)
        }
        android.util.Log.i(
            TAG,
            "navigate: LOAD page (forceReload=$forceReload pageReady=$pageReady sameUrl=${loadedPageUrl == url})",
        )

        invalidatePage()
        mainFrameStatus = 0
        mainFrameError = null

        val deferred = CompletableDeferred<Boolean>()
        pageDeferred = deferred
        pendingPageUrl = url
        wv.loadUrl(url)

        val finished = try {
            withTimeoutOrNull(PAGE_TIMEOUT_MS) { deferred.await() } ?: false
        } finally {
            // Drop both so a late onPageFinished cannot satisfy a later navigation.
            if (pageDeferred === deferred) pageDeferred = null
            if (pendingPageUrl == url) pendingPageUrl = null
        }
        if (!finished) return PageState(-1, false, "页面加载超时（${PAGE_TIMEOUT_MS / 1000}s）")

        val status = mainFrameStatus
        val error = mainFrameError
        val blocked = isBlockedDocument(wv)

        val state = PageState(status = if (status == 0) 200 else status, blocked = blocked, error = error)
        if (state.ok) {
            loadedPageUrl = url
            pageReady = true
        }
        return state
    }

    private fun invalidatePage() {
        pageReady = false
        loadedPageUrl = null
    }

    /**
     * Loads the converter page ahead of the first query so its Akamai cookies exist
     * before the user presses anything. Failure is silent: the next lookup simply
     * loads the page itself.
     */
    suspend fun prewarm() = operationMutex.withLock { prewarmLocked() }

    private suspend fun prewarmLocked() = withContext(Dispatchers.Main.immediate) {
        val wv = webView ?: return@withContext
        if (pageReady) return@withContext
        val url = MastercardEndpoints.pageUrlFor(MastercardEndpoints.HOST_COM)
        val state = runCatching { navigate(wv, url, forceReload = true) }.getOrNull()
        if (state?.ok == true) {
            acceptConsent(wv)
            delay(PAGE_SETTLE_MS)
        }
    }

    /** True when the document is Akamai's deny page rather than the converter. */
    private suspend fun isBlockedDocument(wv: WebView): Boolean {
        val probe = evaluate(
            wv,
            "document.title + '\\u0001' + (document.body ? document.body.innerText.slice(0, 200) : '')",
        ) ?: return false
        val text = probe.lowercase()
        return text.contains("access denied") ||
            text.contains("don't have permission to access") ||
            text.contains("errors.edgesuite.net")
    }

    /** Human-readable page/cookie state, surfaced on the diagnostics screen. */
    private suspend fun pageDiagnostics(wv: WebView, consent: String): String {
        val title = evaluate(wv, "document.title") ?: "?"
        val cookieNames = evaluate(
            wv,
            "document.cookie.split(';').map(function(c){return c.trim().split('=')[0]}).filter(Boolean).join(',')",
        ) ?: ""
        val abck = if (cookieNames.contains("_abck")) "已下发" else "未下发"
        return "页面就绪｜标题=$title｜Cookie 同意=${consent.ifBlank { "无弹窗" }}｜" +
            "反爬 Cookie(_abck)=$abck" +
            "｜页面自身接口调用=[${observedApiUrls.joinToString(" ;; ").take(300)}]"
    }

    /** Clicks the cookie-consent accept button if the page is showing one. */
    private suspend fun acceptConsent(wv: WebView): String {
        return evaluate(
            wv,
            """
(function () {
  var ids = ['onetrust-accept-btn-handler', 'accept-recommended-btn-handler'];
  for (var i = 0; i < ids.length; i++) {
    var b = document.getElementById(ids[i]);
    if (b) { b.click(); return 'id:' + ids[i]; }
  }
  var nodes = document.querySelectorAll('button, a, input[type=button]');
  for (var j = 0; j < nodes.length; j++) {
    var t = (nodes[j].textContent || nodes[j].value || '').trim();
    if (/^(接受|全部接受|同意|接受全部|Accept all|Accept|I agree)/i.test(t)) {
      nodes[j].click();
      return 'text:' + t;
    }
  }
  return '';
})()
            """.trimIndent(),
        ) ?: ""
    }

    private suspend fun evaluate(wv: WebView, script: String, timeoutMs: Long = EVAL_TIMEOUT_MS): String? {
        val generation = nextGeneration()
        val deferred = CompletableDeferred<String>()
        scriptDeferred = deferred
        try {
            jsonWrap(wv, script, generation)
            return withTimeoutOrNull(timeoutMs) { deferred.await() }
        } finally {
            if (scriptDeferred === deferred) scriptDeferred = null
        }
    }

    private fun jsonWrap(wv: WebView, script: String, generation: Long) {
        // Route the value back through the JS bridge so the result is a plain
        // string. Promise results are awaited; the generation prefix lets the
        // bridge discard callbacks belonging to an earlier request.
        val js = """
(function () {
  function send(v) { try { MCFX.onValue('$generation' + '\u0001' + String(v)); } catch (e) {} }
  function fail(e) { try { MCFX.onValue('$generation' + '\u0001' + 'ERR ' + (e && e.message ? e.message : e)); } catch (x) {} }
  try {
    var v = ($script);
    if (v && typeof v.then === 'function') { v.then(send, fail); } else { send(v); }
  } catch (e) { fail(e); }
})();
        """.trimIndent()
        wv.evaluateJavascript(js, null)
    }

    private suspend fun runInPage(wv: WebView, urls: List<String>): String? {
        val generation = nextGeneration()
        val deferred = CompletableDeferred<String>()
        resultDeferred = deferred
        try {
            wv.evaluateJavascript(buildScript(urls, generation), null)
            return withTimeoutOrNull(IN_PAGE_TIMEOUT_MS) { deferred.await() }
        } finally {
            if (resultDeferred === deferred) resultDeferred = null
        }
    }

    /** True when a payload is a real answer (a usable quote, or JSON we can parse). */
    private fun isUsable(payload: String?): Boolean {
        if (payload.isNullOrBlank()) return false
        val json = runCatching { JSONObject(payload) }.getOrNull() ?: return false
        if (json.optBoolean("ok", false)) return true
        val list = json.optJSONArray("attempts") ?: return false
        if (list.length() == 0) return false
        val first = list.optJSONObject(0) ?: return false
        // A refused request carries an HTML deny page; that is not usable.
        return !first.optString("body").contains("Access Denied", ignoreCase = true)
    }

    /**
     * True when the page-internal request was refused by the edge (HTTP 403 or an
     * Akamai deny page) rather than merely failing validation. Only then is the
     * session genuinely stale and worth re-establishing.
     */
    private fun looksLikeSessionProblem(payload: String?): Boolean {
        if (payload.isNullOrBlank()) return true
        val json = runCatching { JSONObject(payload) }.getOrNull() ?: return true
        val list = json.optJSONArray("attempts") ?: return true
        for (i in 0 until list.length()) {
            val attempt = list.optJSONObject(i) ?: continue
            val status = attempt.optInt("status", -1)
            val body = attempt.optString("body")
            if (status == 403 || body.contains("Access Denied", ignoreCase = true)) return true
        }
        return false
    }

    private fun readAttempts(array: JSONArray?): List<FetchAttempt> {
        if (array == null) return emptyList()
        val out = ArrayList<FetchAttempt>(array.length())
        for (i in 0 until array.length()) {
            val o = array.optJSONObject(i) ?: continue
            out += FetchAttempt(
                url = o.optString("url"),
                status = o.optInt("status", -1),
                bodySnippet = o.optString("body").take(400),
                layer = FetchLayer.WEBVIEW_IN_PAGE,
            )
        }
        return out
    }

    private fun buildScript(urls: List<String>, generation: Long): String {
        val urlsJson = JSONArray(urls as Collection<*>).toString()
        return """
(function () {
  var urls = $urlsJson;
  var results = [];
  function done(ok) {
    MCFX.onResult(JSON.stringify({
      $GEN_KEY: $generation,
      ok: ok,
      status: results.length ? results[results.length - 1].status : -1,
      body: results.length ? results[results.length - 1].body : '',
      attempts: results
    }));
  }
  function step(i) {
    if (i >= urls.length) { done(false); return; }
    var u = urls[i];
    fetch(u, { credentials: 'include', headers: { 'Accept': 'application/json, text/plain, */*' } })
      .then(function (r) {
        return r.text().then(function (t) {
          var good = false;
          try {
            var j = JSON.parse(t);
            good = !!(j && j.data && j.data.conversionRate !== undefined && !j.type);
          } catch (e) { good = false; }
          results.push({ url: u, status: r.status, body: t.slice(0, 4000) });
          if (good) { done(true); } else { step(i + 1); }
        });
      })
      .catch(function (e) {
        results.push({ url: u, status: -1, body: String(e) });
        step(i + 1);
      });
  }
  step(0);
})();
        """.trimIndent()
    }

    companion object {
        private const val BRIDGE_NAME = "MCFX"

        /** JSON field carrying the callback generation back from the page. */
        private const val GEN_KEY = "gen"

        /** Separator in the `onValue` payload: "<generation>\u0001<value>". */
        private const val GEN_SEPARATOR = '\u0001'

        /** logcat tag: `adb logcat -s MCFX`. */
        private const val TAG = "MCFX"
        private const val PAGE_TIMEOUT_MS = 25_000L
        private const val IN_PAGE_TIMEOUT_MS = 30_000L
        private const val EVAL_TIMEOUT_MS = 5_000L
        private const val PAGE_SETTLE_MS = 2_500L
        private const val FETCH_RETRY_DELAY_MS = 3_000L

        /** Removes the tokens that mark a request as coming from an embedded WebView. */
        fun cleanUserAgent(defaultUa: String?): String {
            val ua = defaultUa.orEmpty()
            if (ua.isBlank()) return CHROME_ANDROID_UA
            return ua
                .replace("; wv)", ")")
                .replace("; wv;", ";")
                .replace("Version/4.0 ", "")
                .replace(" Version/4.0", "")
                .trim()
        }

        private const val CHROME_ANDROID_UA =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/131.0.0.0 Mobile Safari/537.36"
    }
}
