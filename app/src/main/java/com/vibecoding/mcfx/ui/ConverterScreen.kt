package com.vibecoding.mcfx.ui

import android.content.Intent
import android.net.Uri
import android.webkit.WebView
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.vibecoding.mcfx.data.Currency
import com.vibecoding.mcfx.logic.MastercardEndpoints
import com.vibecoding.mcfx.logic.RateMath
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@Composable
fun ConverterScreen(vm: ConverterViewModel) {
    val state by vm.state.collectAsState()
    val loading = state.status as? QueryStatus.Loading
    // Session acquisition (cookie fetch) is a heavyweight, full-screen wait; a plain
    // rate lookup on a warm session only spins where the input fields are.
    val fullScreenLoading = loading?.acquiringSession == true

    // Drives the swap button's half-turn animation.
    var swapTurns by remember { mutableIntStateOf(0) }

    Box(modifier = Modifier.fillMaxSize().background(Color.White)) {

        // The browser surface that performs the lookup. It is a permanent,
        // full-size, attached child so the page gets a real window and viewport --
        // a detached or 1dp WebView is fingerprinted as a bot and Akamai answers 403.
        AndroidView(
            factory = { ctx -> WebView(ctx).also { vm.attachWebView(it) } },
            modifier = Modifier.fillMaxSize(),
        )

        if (fullScreenLoading) {
            SessionLoadingPanel()
        } else {
            ConverterContent(
                vm = vm,
                state = state,
                inlineLoading = loading != null,
                swapTurns = swapTurns,
                onSwap = {
                    swapTurns += 1
                    vm.swap()
                },
            )
        }
    }

    state.pickerTarget?.let { target ->
        CurrencyPickerSheet(
            currencies = vm.currencies,
            selectedCode = if (target == PickerTarget.FROM) state.fromCode else state.toCode,
            onSelect = vm::selectCurrency,
            onDismiss = vm::closePicker,
        )
    }

    if (state.showDatePicker) {
        val initial = state.dateText.takeIf { it.isNotBlank() }
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?: LocalDate.now()
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = vm::dismissDatePicker,
            confirmButton = {
                TextButton(onClick = {
                    val millis = pickerState.selectedDateMillis
                    if (millis != null) {
                        vm.onDateSelected(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                    } else {
                        vm.dismissDatePicker()
                    }
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = vm::dismissDatePicker) { Text("取消") } },
        ) {
            DatePicker(state = pickerState)
        }
    }

    if (state.diagnosticsVisible) {
        DiagnosticsSheet(state = state, onDismiss = vm::closeDiagnostics)
    }

    if (state.devPanelVisible) {
        DevPanel(state = state, vm = vm)
    }
}

/**
 * Full-screen wait shown only when the Mastercard session has to be established
 * (the page load that mints the edge cookies). Everything else uses the small
 * inline indicator inside the card.
 */
@Composable
private fun SessionLoadingPanel() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 40.dp),
        ) {
            MastercardLoadingMark(size = 72.dp)
            Spacer(Modifier.height(24.dp))
            Text(
                text = "正在建立万事达会话…",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = WiseInk,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "首次查询需要打开万事达汇率换算页以获取访问凭证，通常 5–10 秒；" +
                    "之后的查询会复用该会话，只需 1–2 秒。",
                fontSize = 12.sp,
                lineHeight = 18.sp,
                color = WiseInkFaint,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Inline wait: spins in the place of the amount / result fields, like Wise does. */
@Composable
private fun InlineRateLoading() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(150.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            MastercardLoadingMark(size = 44.dp)
            Spacer(Modifier.height(12.dp))
            Text(
                text = "正在获取汇率…",
                fontSize = 13.sp,
                color = WiseInkFaint,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConverterContent(
    vm: ConverterViewModel,
    state: UiState,
    inlineLoading: Boolean,
    swapTurns: Int,
    onSwap: () -> Unit,
) {
    val context = LocalContext.current
    val from = vm.currency(state.fromCode)
    val to = vm.currency(state.toCode)

    val success = (state.status as? QueryStatus.Success)?.result
    val error = state.status as? QueryStatus.Error

    // The rate returned by Mastercard does not depend on the amount, so the
    // converted amount is derived from whatever is in the amount box right now --
    // editing the amount re-converts instantly, the way Google's converter does.
    // Rounding and display follow the TARGET currency's ISO 4217 precision.
    val targetMinorUnits = to?.minorUnits ?: Currency.DEFAULT_MINOR_UNITS
    val amount = RateMath.parseDecimal(state.amountText)
    val liveAmount = amount?.takeIf { it.signum() > 0 }
    val amountUnchanged = liveAmount != null && liveAmount == success?.request?.amount
    val billed = success?.let { r ->
        when {
            // An untouched query shows exactly what Mastercard returned.
            amountUnchanged && r.quote.crdhldBillAmt.signum() >= 0 -> r.quote.crdhldBillAmt
            liveAmount != null -> RateMath.billedAmount(liveAmount, r.quote.conversionRate, targetMinorUnits)
            else -> null
        }
    }
    val baseRate = success?.let { RateMath.baseRate(it.quote.conversionRate, it.request.bankFeePercent) }
    val feeAmount = success?.let { r ->
        liveAmount?.let {
            RateMath.feeAmount(it, r.quote.conversionRate, r.request.bankFeePercent, targetMinorUnits)
        }
    }
    /** True when the amount box no longer matches the amount that was fetched. */
    val amountEditedLive = success != null && liveAmount != null && !amountUnchanged

    var showHelp by remember { mutableStateOf(false) }

    // Gentle entrance, the way Wise fades its converter card in.
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val cardAlpha by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = tween(WiseMotion.medium, easing = WiseMotion.easing),
        label = "cardAlpha",
    )
    val cardShift by animateFloatAsState(
        targetValue = if (entered) 0f else 24f,
        animationSpec = tween(WiseMotion.medium, easing = WiseMotion.easing),
        label = "cardShift",
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            // Must be opaque: the WebView sits behind this content and holds a real
            // Mastercard page, so any unpainted area would show the site through.
            .background(Color.White)
            .verticalScroll(rememberScrollState()),
    ) {
        // ------------------------------------------------------------- hero
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(WiseForest)
                .statusBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(top = 10.dp, bottom = 40.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MastercardMark(size = 30.dp)
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "万事达结算汇率",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = WiseGreen,
                        modifier = Modifier.clickable { vm.onTitleTapped() },
                    )
                    Text(
                        text = "数据来源 · 万事达汇率换算器",
                        fontSize = 11.sp,
                        color = WiseGreen.copy(alpha = 0.7f),
                    )
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.HelpOutline,
                    contentDescription = "说明",
                    tint = WiseGreen.copy(alpha = 0.9f),
                    modifier = Modifier
                        .size(22.dp)
                        .clickable { showHelp = true },
                )
            }

            Spacer(Modifier.height(22.dp))

            Text(
                text = "${from?.nameZh ?: state.fromCode}兑${to?.nameZh ?: state.toCode}",
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                color = WiseGreen,
                lineHeight = 40.sp,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "汇率取自万事达官方换算器，实时读取，含点差。",
                fontSize = 13.sp,
                lineHeight = 20.sp,
                color = WiseGreen.copy(alpha = 0.8f),
            )
        }

        // ------------------------------------------------------------- card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .offset(y = (-28).dp)
                .alpha(cardAlpha)
                .offset(y = cardShift.dp)
                .padding(horizontal = 12.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color.White)
                .padding(horizontal = 16.dp, vertical = 20.dp),
        ) {
            // rate headline
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "万事达结算汇率",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = WiseInk,
                    )
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.HelpOutline,
                        contentDescription = null,
                        tint = WiseInkFaint,
                        modifier = Modifier
                            .size(15.dp)
                            .clickable { showHelp = true },
                    )
                }

                Spacer(Modifier.height(8.dp))

                AnimatedContent(
                    targetState = success?.quote?.conversionRate,
                    transitionSpec = {
                        (fadeIn(tween(WiseMotion.medium)) + slideInVertically { it / 3 })
                            .togetherWith(fadeOut(tween(WiseMotion.fast)) + slideOutVertically { -it / 3 })
                    },
                    label = "rate",
                ) { rate ->
                    val label = if (rate == null) {
                        "—"
                    } else {
                        val code = success?.quote?.transCurr?.ifBlank { state.fromCode } ?: state.fromCode
                        val target = success?.quote?.crdhldBillCurr?.ifBlank { state.toCode } ?: state.toCode
                        val symbol = from?.symbol.orEmpty()
                        "$symbol" + "1 $code = ${RateMath.formatRate(rate)} $target"
                    }
                    Text(
                        text = label,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = WiseInk,
                        textAlign = TextAlign.Center,
                    )
                }

                Spacer(Modifier.height(4.dp))

                val subtitle = when {
                    success != null -> buildString {
                        append("汇率日期 ").append(success.effectiveDate)
                        if (success.dateFellBack) append("（所选日期未发布，已使用此前最近可用汇率）")
                        if (amountEditedLive) append(" · 按已获取汇率实时换算")
                    }
                    else -> "填写金额后点下方按钮查询"
                }
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = if (success?.dateFellBack == true) WiseNegative else WiseInkFaint,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )

                if (success != null && success.request.bankFeePercent.signum() > 0 && baseRate != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "不含手续费汇率 ${RateMath.formatRate(baseRate)}（手续费 ${RateMath.formatRate(success.request.bankFeePercent)}%）",
                        fontSize = 11.sp,
                        color = WiseInkFaint,
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            if (inlineLoading) {
                // A plain rate lookup on a warm session: spin in place of the fields.
                InlineRateLoading()
            } else {

            // amount
            SectionLabel("金额")
            Spacer(Modifier.height(8.dp))
            WiseField {
                BasicTextField(
                    value = state.amountText,
                    onValueChange = vm::onAmountChange,
                    singleLine = true,
                    textStyle = TextStyle(
                        fontSize = 28.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = WiseInk,
                    ),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    cursorBrush = SolidColor(WiseForest),
                    modifier = Modifier.weight(1f),
                    decorationBox = { inner ->
                        Box {
                            if (state.amountText.isEmpty()) {
                                Text("0", fontSize = 28.sp, color = WiseInkFaint, fontWeight = FontWeight.SemiBold)
                            }
                            inner()
                        }
                    },
                )
                CurrencyChip(
                    code = state.fromCode,
                    cc = from?.cc.orEmpty(),
                    onClick = { vm.openPicker(PickerTarget.FROM) },
                )
            }

            Spacer(Modifier.height(10.dp))

            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                SwapButton(
                    onClick = onSwap,
                    enabled = state.fromCode != state.toCode && !inlineLoading,
                    turns = swapTurns,
                )
            }

            Spacer(Modifier.height(10.dp))

            // result
            SectionLabel("换算为")
            Spacer(Modifier.height(8.dp))
            WiseField {
                // No animated swap here on purpose: this number now follows the
                // amount box keystroke by keystroke, and animating every digit
                // change would flicker.
                Text(
                    text = billed?.let { RateMath.formatMoney(it, targetMinorUnits) } ?: "—",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (billed != null) WiseInk else WiseInkFaint,
                    modifier = Modifier.weight(1f),
                )
                CurrencyChip(
                    code = state.toCode,
                    cc = to?.cc.orEmpty(),
                    onClick = { vm.openPicker(PickerTarget.TO) },
                )
            }

            }

            // breakdown
            if (success != null) {
                Spacer(Modifier.height(14.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(WiseFill)
                        .padding(14.dp),
                ) {
                    BreakdownRow(
                        label = "金额 × 汇率",
                        value = "${RateMath.formatAmountInput(liveAmount ?: success.request.amount)} × " +
                            RateMath.formatRate(success.quote.conversionRate),
                    )
                    if (success.request.bankFeePercent.signum() > 0 && feeAmount != null) {
                        BreakdownRow(
                            label = "银行手续费 ${RateMath.formatRate(success.request.bankFeePercent)}%",
                            value = RateMath.formatMoney(feeAmount, targetMinorUnits),
                        )
                    }
                    HairlineDivider(color = WiseBorder)
                    Spacer(Modifier.height(6.dp))
                    BreakdownRow(
                        label = "合计（${success.quote.crdhldBillCurr}）",
                        value = billed?.let { RateMath.formatMoney(it, targetMinorUnits) } ?: "-",
                        emphasised = true,
                    )
                }
            }

            // error
            if (error != null) {
                Spacer(Modifier.height(14.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(WiseNegativeBg)
                        .padding(14.dp),
                ) {
                    Text(
                        text = "未能获取汇率",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = WiseNegative,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(text = error.message, fontSize = 12.sp, lineHeight = 18.sp, color = WiseInkSoft)
                    if (error.code != null) {
                        Text(text = "错误码 ${error.code}", fontSize = 11.sp, color = WiseInkFaint)
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = vm::openDiagnostics) { Text("查看诊断", fontSize = 13.sp) }
                        TextButton(onClick = { vm.setUseManualRate(true) }) { Text("手动输入汇率", fontSize = 13.sp) }
                        TextButton(onClick = {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(MastercardEndpoints.CONVERTER_PAGE_CN)),
                            )
                        }) { Text("打开官网核对", fontSize = 13.sp) }
                    }
                }
            }

            // manual rate
            if (state.useManualRate) {
                Spacer(Modifier.height(14.dp))
                SectionLabel("手动汇率（1 ${state.fromCode} = ? ${state.toCode}）")
                Spacer(Modifier.height(8.dp))
                WiseField {
                    BasicTextField(
                        value = state.manualRateText,
                        onValueChange = vm::onManualRateChange,
                        singleLine = true,
                        textStyle = TextStyle(fontSize = 17.sp, color = WiseInk),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        cursorBrush = SolidColor(WiseForest),
                        modifier = Modifier.weight(1f),
                        decorationBox = { inner ->
                            Box {
                                if (state.manualRateText.isEmpty()) {
                                    Text("例如 0.0427", fontSize = 17.sp, color = WiseInkFaint)
                                }
                                inner()
                            }
                        },
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            InfoBanner(
                title = if (success != null) {
                    "本次换算按万事达结算汇率计算，不含发卡行额外加收的跨境费用。"
                } else {
                    "万事达结算汇率由万事达每日发布，通常与银行入账汇率一致。"
                },
                detail = "需要核对官方数据？可打开万事达官方换算页比对。",
                actionText = "了解详情",
                onAction = {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(MastercardEndpoints.CONVERTER_PAGE_CN)),
                    )
                },
            )
        }

        // -------------------------------------------------- date / fee / action
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 28.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    SectionLabel("汇率日期")
                    Spacer(Modifier.height(8.dp))
                    WiseField(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp)) {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { vm.openDatePicker() },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CalendarMonth,
                                contentDescription = null,
                                tint = WiseInkFaint,
                                modifier = Modifier.size(17.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = state.dateText.ifBlank { "默认当天" },
                                fontSize = 14.sp,
                                color = if (state.dateText.isBlank()) WiseInkFaint else WiseInk,
                            )
                        }
                        if (state.dateText.isNotBlank()) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "清除日期",
                                tint = WiseInkFaint,
                                modifier = Modifier
                                    .size(17.dp)
                                    .clickable { vm.clearDate() },
                            )
                        }
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    SectionLabel("银行手续费")
                    Spacer(Modifier.height(8.dp))
                    WiseField(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp)) {
                        BasicTextField(
                            value = state.bankFeeText,
                            onValueChange = vm::onBankFeeChange,
                            singleLine = true,
                            textStyle = TextStyle(fontSize = 14.sp, color = WiseInk),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            cursorBrush = SolidColor(WiseForest),
                            modifier = Modifier.weight(1f),
                            decorationBox = { inner ->
                                Box {
                                    if (state.bankFeeText.isEmpty()) {
                                        Text("默认 0", fontSize = 14.sp, color = WiseInkFaint)
                                    }
                                    inner()
                                }
                            },
                        )
                        Text("%", fontSize = 14.sp, color = WiseInkSoft)
                    }
                }
            }

            if (state.notice != null) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = state.notice.orEmpty(),
                    fontSize = 12.sp,
                    color = WiseNegative,
                    modifier = Modifier.clickable { vm.dismissNotice() },
                )
            }

            Spacer(Modifier.height(18.dp))

            PrimaryButton(
                text = "查询汇率",
                loading = inlineLoading,
                enabled = state.fromCode != state.toCode,
                onClick = vm::query,
            )

            Spacer(Modifier.height(4.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                Text(
                    text = "诊断信息",
                    fontSize = 12.sp,
                    color = WiseInkFaint,
                    modifier = Modifier
                        .clickable { vm.openDiagnostics() }
                        .padding(8.dp),
                )
            }
        }
    }

    if (showHelp) {
        AlertDialog(
            onDismissRequest = { showHelp = false },
            title = { Text("关于汇率数据") },
            text = {
                Text(
                    "本工具在查询时打开万事达官方汇率换算页，并在该页面内发起同源请求，\n" +
                        "读取万事达返回的结算汇率（conversionRate）。\n\n" +
                        "· 汇率日期留空表示取「最新已发布汇率」，与官网默认行为一致。\n" +
                        "· 银行手续费按百分比计入，与万事达换算器的口径一致。\n" +
                        "· 万事达结算汇率含点差，与「中间市场汇率」并不相同。\n\n" +
                        "数据仅供参考，最终以银行入账为准。",
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                )
            },
            confirmButton = { TextButton(onClick = { showHelp = false }) { Text("知道了") } },
        )
    }
}
