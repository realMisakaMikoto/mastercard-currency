package com.vibecoding.mcfx.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vibecoding.mcfx.data.Currency
import com.vibecoding.mcfx.data.CurrencyRepository
import com.vibecoding.mcfx.data.FetchAttempt
import com.vibecoding.mcfx.data.RateRequest
import com.vibecoding.mcfx.data.RateResult
import com.vibecoding.mcfx.logic.QueryGate
import com.vibecoding.mcfx.logic.RateMath
import com.vibecoding.mcfx.logic.RateParser
import com.vibecoding.mcfx.net.HttpFetcher
import com.vibecoding.mcfx.net.MockFetcher
import com.vibecoding.mcfx.net.RateEngine
import com.vibecoding.mcfx.net.WebViewFetcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.LocalDate

enum class PickerTarget { FROM, TO }

sealed interface QueryStatus {
    data object Idle : QueryStatus

    /**
     * @param acquiringSession true when the Mastercard page has to be loaded first
     *   (cookie acquisition). That deserves a full-screen indicator; a plain rate
     *   fetch on an existing session only needs an inline one at the input fields.
     */
    data class Loading(val acquiringSession: Boolean) : QueryStatus

    data class Success(val result: RateResult.Success) : QueryStatus
    data class Error(
        val message: String,
        val code: String?,
        val attempts: List<FetchAttempt>,
    ) : QueryStatus
}

data class UiState(
    val amountText: String = "10,000",
    val bankFeeText: String = "",
    val dateText: String = "",
    val fromCode: String = "USD",
    val toCode: String = "CNY",
    val status: QueryStatus = QueryStatus.Idle,
    val pickerTarget: PickerTarget? = null,
    val showDatePicker: Boolean = false,
    val diagnosticsVisible: Boolean = false,
    val devPanelVisible: Boolean = false,
    val manualRateText: String = "",
    val useManualRate: Boolean = false,
    val notice: String? = null,
)

class ConverterViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = CurrencyRepository(app)
    private val mockFetcher = MockFetcher()
    private val webViewFetcher = WebViewFetcher(app)
    private val httpFetcher = HttpFetcher()
    private val engine = RateEngine(mockFetcher, webViewFetcher, httpFetcher)
    val currencies: List<Currency> = repository.all()

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var titleTapCount = 0

    /** Guards the one-shot background page warm-up. */
    private var prewarmStarted = false

    fun currency(code: String): Currency? = repository.byCode(code)

    /**
     * Hands the live, attached WebView to the fetch layer. Called from the Compose
     * tree so the browser surface has a window and a real viewport size.
     *
     * Attaching is also the earliest safe moment to warm the page up in the
     * background, so the Mastercard session cookies exist before the first query.
     */
    fun attachWebView(webView: android.webkit.WebView) {
        webViewFetcher.attach(webView)
        if (!prewarmStarted) {
            prewarmStarted = true
            viewModelScope.launch { webViewFetcher.prewarm() }
        }
    }

    // ---------------------------------------------------------------- input

    fun onAmountChange(raw: String) {
        val parsed = RateMath.parseDecimal(raw)
        // Keep the field usable while typing: allow an empty or partial value.
        if (raw.isNotBlank() && parsed == null) return
        _state.update { it.copy(amountText = raw, notice = null) }
    }

    fun onBankFeeChange(raw: String) {
        if (raw.isBlank()) {
            _state.update { it.copy(bankFeeText = "", notice = null, status = QueryStatus.Idle) }
            return
        }
        val parsed = RateMath.parseDecimal(raw) ?: return
        if (parsed.signum() < 0 || parsed > BigDecimal.valueOf(100)) {
            _state.update { it.copy(notice = "手续费需为 0–100 之间的百分数") }
            return
        }
        // The fee changes the rate Mastercard returns, so any previous result is
        // no longer valid for this input and is cleared.
        _state.update {
            it.copy(bankFeeText = raw, notice = null, status = QueryStatus.Idle)
        }
    }

    fun onManualRateChange(raw: String) {
        if (raw.isNotBlank() && RateMath.parseDecimal(raw) == null) return
        _state.update { it.copy(manualRateText = raw) }
    }

    fun openPicker(target: PickerTarget) = _state.update { it.copy(pickerTarget = target) }

    fun closePicker() = _state.update { it.copy(pickerTarget = null) }

    /**
     * Picking a currency invalidates whatever was on screen: the converted amount
     * belongs to the previous pair, so it is cleared immediately -- before, and
     * regardless of, any new query.
     */
    fun selectCurrency(code: String) {
        _state.update { current ->
            val changed = when (current.pickerTarget) {
                PickerTarget.FROM -> current.fromCode != code
                PickerTarget.TO -> current.toCode != code
                null -> false
            }
            if (!changed) {
                current.copy(pickerTarget = null)
            } else {
                when (current.pickerTarget) {
                    PickerTarget.FROM -> current.copy(
                        fromCode = code,
                        pickerTarget = null,
                        status = QueryStatus.Idle,
                    )
                    PickerTarget.TO -> current.copy(
                        toCode = code,
                        pickerTarget = null,
                        status = QueryStatus.Idle,
                    )
                    null -> current
                }
            }
        }
    }

    /**
     * Swapping while a lookup is in flight would start a second request for the new
     * pair and race the first one, so during Loading the pair is swapped visually
     * but no query is issued -- the user presses 查询 when ready.
     */
    fun swap() {
        val loading = _state.value.status is QueryStatus.Loading
        _state.update { it.copy(fromCode = it.toCode, toCode = it.fromCode, status = QueryStatus.Idle) }
        if (!loading) query()
    }

    fun openDatePicker() = _state.update { it.copy(showDatePicker = true) }

    fun dismissDatePicker() = _state.update { it.copy(showDatePicker = false) }

    fun onDateSelected(date: LocalDate) = _state.update {
        it.copy(dateText = date.toString(), showDatePicker = false, status = QueryStatus.Idle)
    }

    fun clearDate() = _state.update { it.copy(dateText = "", status = QueryStatus.Idle) }

    fun dismissNotice() = _state.update { it.copy(notice = null) }

    // ------------------------------------------------------------ navigation

    fun openDiagnostics() = _state.update { it.copy(diagnosticsVisible = true) }

    fun closeDiagnostics() = _state.update { it.copy(diagnosticsVisible = false) }

    fun closeDevPanel() = _state.update { it.copy(devPanelVisible = false) }

    fun onTitleTapped() {
        titleTapCount += 1
        if (titleTapCount >= 5) {
            titleTapCount = 0
            _state.update { it.copy(devPanelVisible = true) }
        }
    }

    fun setUseMock(enabled: Boolean) {
        engine.useMock = enabled
        _state.update { it.copy(useManualRate = false) }
    }

    fun setMockBody(body: String) {
        mockFetcher.mockBody = body
    }

    fun setUseManualRate(enabled: Boolean) = _state.update {
        it.copy(useManualRate = enabled, status = QueryStatus.Idle)
    }
    // --------------------------------------------------------------- lookup

    fun query() {
        val current = _state.value

        // One lookup at a time. Rapid taps (or a tap landing while prewarm runs)
        // would otherwise queue several requests whose answers can arrive out of
        // order and be shown for the wrong inputs.
        val amount = when (
            val gate = QueryGate.check(
                loading = current.status is QueryStatus.Loading,
                rawAmount = RateMath.parseDecimal(current.amountText),
                fromCode = current.fromCode,
                toCode = current.toCode,
            )
        ) {
            QueryGate.Result.AlreadyRunning -> return
            is QueryGate.Result.Invalid -> {
                _state.update { it.copy(notice = gate.message) }
                return
            }
            is QueryGate.Result.Ready -> gate.amount
        }
        val fee = current.bankFeeText.takeIf { it.isNotBlank() }
            ?.let(RateMath::parseDecimal) ?: BigDecimal.ZERO

        val request = RateRequest(
            fromCode = current.fromCode,
            toCode = current.toCode,
            amount = amount,
            requestedDate = current.dateText.takeIf { it.isNotBlank() },
            bankFeePercent = fee,
        )

        if (current.useManualRate) {
            applyManualRate(request)
            return
        }

        // Decide upfront which kind of wait this is: loading the converter page to
        // obtain cookies, or just asking for a rate on an already-warm session.
        val acquiringSession = !webViewFetcher.isSessionReady

        _state.update { it.copy(status = QueryStatus.Loading(acquiringSession), notice = null) }
        viewModelScope.launch {
            val result = engine.lookup(request, LocalDate.now())
            _state.update {
                when (result) {
                    is RateResult.Success -> it.copy(status = QueryStatus.Success(result))
                    is RateResult.Failure -> it.copy(
                        status = QueryStatus.Error(result.message, result.errorCode, result.attempts),
                    )
                }
            }
        }
    }

    private fun applyManualRate(request: RateRequest) {
        val rate = RateMath.parseDecimal(_state.value.manualRateText)
        if (rate == null || rate.signum() <= 0) {
            _state.update { it.copy(notice = "请输入有效的手动汇率") }
            return
        }
        val quote = com.vibecoding.mcfx.data.RateQuote(
            conversionRate = rate.multiply(BigDecimal.ONE.add(request.bankFeePercent.movePointLeft(2))),
            crdhldBillAmt = request.amount.multiply(
                rate.multiply(BigDecimal.ONE.add(request.bankFeePercent.movePointLeft(2))),
            ),
            fxDate = request.requestedDate ?: LocalDate.now().toString(),
            transCurr = request.fromCode,
            crdhldBillCurr = request.toCode,
            bankFee = request.bankFeePercent,
        )
        _state.update {
            it.copy(
                status = QueryStatus.Success(
                    RateResult.Success(
                        quote = quote,
                        request = request,
                        effectiveDate = quote.fxDate,
                        dateFellBack = false,
                        layer = com.vibecoding.mcfx.data.FetchLayer.MOCK,
                        rawJson = "（手动输入的汇率）",                        attempts = emptyList(),
                    ),
                ),
                notice = null,
            )
        }
    }

    /**
     * Diagnostics only: what does a plain HTTPS client get? Normally 403, which is
     * exactly why the native layer is no longer part of a production lookup.
     */
    fun probeNativeHttp() {
        val current = _state.value
        if (current.status is QueryStatus.Loading) return
        val request = RateRequest(
            fromCode = current.fromCode,
            toCode = current.toCode,
            amount = RateMath.parseDecimal(current.amountText) ?: BigDecimal.ONE,
            requestedDate = current.dateText.takeIf { it.isNotBlank() },
            bankFeePercent = BigDecimal.ZERO,
        )
        _state.update { it.copy(status = QueryStatus.Loading(acquiringSession = false), notice = null) }
        viewModelScope.launch {
            val summary = runCatching { httpFetcher.probe(request, LocalDate.now()) }
                .map { response ->
                    when (val outcome = RateParser.parse(response.body)) {
                        is RateParser.Outcome.Quote -> "原生 HTTP 探测：成功（${outcome.quote.conversionRate}）"
                        is RateParser.Outcome.ApiError ->
                            "原生 HTTP 探测：HTTP ${response.status}，${outcome.message.take(60)}"
                        is RateParser.Outcome.Malformed ->
                            "原生 HTTP 探测：HTTP ${response.status}（${outcome.message.take(60)}）"
                    }
                }
                .getOrElse { "原生 HTTP 探测失败：${it.message}" }
            android.util.Log.i("MCFX", "native probe: $summary")
            _state.update { it.copy(status = QueryStatus.Idle, notice = summary) }
        }
    }

    override fun onCleared() {
        super.onCleared()
        webViewFetcher.release()
    }
}
