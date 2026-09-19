package com.vibecoding.mcfx.logic

import com.vibecoding.mcfx.data.RateQuote
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal

/**
 * Parses a Mastercard currency-conversion payload.
 *
 * The service answers with the same envelope the published spec documents:
 *   {"data":{"conversionRate":0.57,"crdhldBillAmt":13.11,"fxDate":"2019-09-30",
 *            "transCurr":"ALL","crdhldBillCurr":"DZD","transAmt":23,"bankFee":5}}
 *   {"type":"error","data":{"errorCode":"104","errorMessage":"..."}}
 *
 * Parsing is deliberately tolerant because the public marketing endpoint is not
 * documented: it may nest the payload, return it at the root, wrap it in an array,
 * or use a different spelling for the converted amount.
 */
object RateParser {

    /** 104 == "no rate published for the requested date" -> worth retrying another date. */
    const val CODE_RATE_UNAVAILABLE = "104"

    sealed interface Outcome {
        data class Quote(val quote: RateQuote) : Outcome
        data class ApiError(val code: String?, val message: String) : Outcome
        data class Malformed(val message: String) : Outcome
    }

    fun parse(body: String): Outcome {
        val text = body.trim()
        if (text.isEmpty()) return Outcome.Malformed("响应为空")
        if (!text.startsWith("{")) {
            return Outcome.Malformed("响应不是 JSON（${text.take(80)}）")
        }
        val root = runCatching { JSONObject(text) }.getOrElse {
            return Outcome.Malformed("JSON 解析失败：${it.message}")
        }

        val data = root.opt("data")
        val container: JSONObject = when {
            data is JSONObject -> data
            data is JSONArray -> data.optJSONObject(0) ?: root
            else -> root
        }

        val errorCode = container.stringOrNull("errorCode") ?: root.stringOrNull("errorCode")
        val errorMessage = container.optString("errorMessage", "")
            .ifBlank { root.optString("errorMessage", "") }
        val type = root.optString("type", "")
        if (errorCode != null || type.equals("error", ignoreCase = true)) {
            return Outcome.ApiError(
                errorCode ?: errorCodeFrom(type),
                errorMessage.ifBlank { "万事达未返回该组合的汇率" },
            )
        }

        val rate = container.decimalOrNull("conversionRate")
            ?: container.decimalOrNull("rate")
            ?: root.decimalOrNull("conversionRate")
            ?: return Outcome.ApiError(null, "响应中没有 conversionRate（${text.take(160)}）")
        if (rate.signum() <= 0) return Outcome.ApiError(null, "汇率无效：$rate")

        val bill = container.decimalOrNull("crdhldBillAmt")
            ?: container.decimalOrNull("billAmt")
            ?: container.decimalOrNull("convertedAmount")
            ?: BigDecimal.ZERO
        val fee = container.decimalOrNull("bankFee") ?: BigDecimal.ZERO

        return Outcome.Quote(
            RateQuote(
                conversionRate = rate,
                crdhldBillAmt = bill,
                fxDate = container.optString("fxDate", "").ifBlank { root.optString("fxDate", "") },
                transCurr = container.optString("transCurr", ""),
                crdhldBillCurr = container.optString("crdhldBillCurr", ""),
                bankFee = fee,
            ),
        )
    }

    /** The public endpoint reports "104" inside the message when a date has no rate. */
    private fun errorCodeFrom(message: String): String? {
        val digits = Regex("\\b(\\d{3})\\b").find(message)?.groupValues?.get(1)
        return digits
    }

    private fun JSONObject.stringOrNull(key: String): String? {
        if (!has(key) || isNull(key)) return null
        val value = opt(key)?.toString()?.trim()
        return value?.takeIf { it.isNotEmpty() && !it.equals("null", true) }
    }

    /** Tolerates both JSON numbers and numeric strings. */
    private fun JSONObject.decimalOrNull(key: String): BigDecimal? {
        if (!has(key) || isNull(key)) return null
        val raw = opt(key)
        return when {
            raw is Number -> BigDecimal(raw.toString())
            raw is String -> RateMath.parseDecimal(raw)
            else -> null
        }
    }
}
