package com.vibecoding.mcfx.logic

import com.vibecoding.mcfx.data.RateRequest
import java.time.LocalDate

/**
 * URL construction for Mastercard's public currency-conversion service.
 *
 * The converter page publishes its own back-end URLs as hidden inputs:
 *
 *   <input id="currencyListUrl"       data-cmp-url="/marketingservices/public/mccom-services/currency-conversions/currencies"/>
 *   <input id="currencyConversionUrl" data-cmp-url="/marketingservices/public/mccom-services/currency-conversions/conversion-rates"/>
 *
 * and its clientlib (`_currency-converter.js`) builds the request exactly like this:
 *
 *   const requestData = new URLSearchParams({
 *     exchange_date: transactionDateValue,
 *     transaction_currency: state.fromCurrency,
 *     cardholder_billing_currency: state.toCurrency,
 *     bank_fee: state.bankFee,
 *     transaction_amount: parseFloat(state.amount),
 *   });
 *   const convertedData = await fetchData(`${config.conversionUrl}?${requestData}`);
 *
 * The names are snake_case. Anything else (including the documented developer-API
 * names transCurr/crdhldBillCurr/...) is rejected by the service with a 400 whose
 * message names one arbitrary missing parameter.
 *
 * `/settlement/currencyrate/conversion-rate` -- the credentialed developer API --
 * is NOT the public endpoint: on www.mastercard.com it is answered with an Akamai
 * "Access Denied" 403 for every network and client. It is kept only as a last-resort
 * fallback.
 */
object MastercardEndpoints {

    const val CONVERTER_PAGE_CN =
        "https://www.mastercard.com/cn/zh/personal/get-support/currency-exchange-rate-converter.html"

    const val CONVERTER_PAGE_US =
        "https://www.mastercard.us/en-us/personal/get-support/convert-currency.html"

    const val HOST_COM = "https://www.mastercard.com"
    const val HOST_US = "https://www.mastercard.us"

    /** Only the .com host publishes the current conversion service. */
    val HOSTS = listOf(HOST_COM)

    // ------------------------------------------------------------ current paths

    const val MS_PREFIX = "/marketingservices/public/mccom-services/currency-conversions"
    const val PATH_CONVERSION_RATES = "$MS_PREFIX/conversion-rates"
    const val PATH_CURRENCIES = "$MS_PREFIX/currencies"

    // ------------------------------------------------------------- legacy paths

    const val RATE_PATH_LEGACY = "/settlement/currencyrate/conversion-rate"
    const val RATE_PATH_LEGACY_ALT = "/settlement/currencyrate/settlement-conversion-rate"

    /**
     * What the page sends when no date is chosen. The service reads it as
     * "most recent published rate", which is exactly the "默认当天" behaviour.
     */
    const val LATEST_DATE_SENTINEL = "0000-00-00"

    /** Calendar days to walk back when an explicitly chosen date has no rate. */
    const val FALLBACK_DAYS = 2

    fun pageUrlFor(host: String): String =
        if (host.contains("mastercard.us")) CONVERTER_PAGE_US else CONVERTER_PAGE_CN

    /** Locale root, used as a cookie-establishing warm-up before the converter page. */
    const val LOCALE_ROOT_CN = "https://www.mastercard.com/cn/zh.html"
    const val LOCALE_ROOT_US = "https://www.mastercard.us/en-us.html"

    fun warmUpUrlFor(host: String): String =
        if (host.contains("mastercard.us")) LOCALE_ROOT_US else LOCALE_ROOT_CN

    // ------------------------------------------------------------------ builder

    /** The exact request the site's own converter issues. */
    fun rateUrl(host: String, request: RateRequest, exchangeDate: String): String = buildString {
        append(host).append(PATH_CONVERSION_RATES)
        append("?exchange_date=").append(exchangeDate)
        append("&transaction_currency=").append(request.fromCode.uppercase())
        append("&cardholder_billing_currency=").append(request.toCode.uppercase())
        append("&bank_fee=").append(request.bankFeeParam)
        append("&transaction_amount=").append(request.amountParam)
    }

    /** Legacy URL shape, kept only so a fallback attempt can still be made. */
    fun legacyUrl(host: String, path: String, request: RateRequest, fxDate: String): String =
        buildString {
            append(host).append(path)
            append("?fxDate=").append(fxDate)
            append("&transCurr=").append(request.fromCode.uppercase())
            append("&crdhldBillCurr=").append(request.toCode.uppercase())
            append("&bankFee=").append(request.bankFeeParam)
            append("&transAmt=").append(request.amountParam)
        }

    fun candidateDates(requested: LocalDate, fallbackDays: Int = FALLBACK_DAYS): List<String> =
        buildList {
            add(requested.toString())
            for (i in 1..fallbackDays) add(requested.minusDays(i.toLong()).toString())
            add(LATEST_DATE_SENTINEL)
        }

    /**
     * Ordered candidates. An empty date field means "most recent published rate",
     * matching the official page; an explicit date is honoured first and only then
     * backed off.
     */
    fun candidateUrls(host: String, request: RateRequest, today: LocalDate): List<String> {
        val requested = request.requestedDate?.let(LocalDate::parse)
        val urls = mutableListOf<String>()

        if (requested == null) {
            urls += rateUrl(host, request, LATEST_DATE_SENTINEL)
            urls += rateUrl(host, request, today.toString())
        } else {
            for (date in candidateDates(requested)) urls += rateUrl(host, request, date)
        }

        // Last resort: the credentialed developer path, in case it is ever opened up.
        urls += legacyUrl(host, RATE_PATH_LEGACY, request, requested?.toString() ?: today.toString())
        urls += legacyUrl(host, RATE_PATH_LEGACY, request, LATEST_DATE_SENTINEL)
        urls += legacyUrl(host, RATE_PATH_LEGACY_ALT, request, requested?.toString() ?: today.toString())

        return urls.distinct()
    }

    /** Currency list endpoint, the same one the page fetches to build its dropdowns. */
    fun currenciesUrl(host: String = HOST_COM): String = "$host$PATH_CURRENCIES"
}
