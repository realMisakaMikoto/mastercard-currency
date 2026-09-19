package com.vibecoding.mcfx.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate

/**
 * Hidden panel (long-press the title five times). Lets a build be exercised
 * end-to-end without live network access by replaying a canned Mastercard
 * payload, which is how the success path is verified on the emulator.
 */
@Composable
fun DevPanel(state: UiState, vm: ConverterViewModel) {
    var useMock by remember { mutableStateOf(false) }
    var body by remember { mutableStateOf(sampleFor(state)) }

    AlertDialog(
        onDismissRequest = vm::closeDevPanel,
        title = { Text("开发者面板", fontSize = 16.sp) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("使用模拟响应（不联网）", fontSize = 13.sp, modifier = Modifier.weight(1f))
                    Switch(checked = useMock, onCheckedChange = { useMock = it })
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "粘贴一份万事达返回的 JSON，用于验证解析与展示逻辑。",
                    fontSize = 11.sp,
                    color = WiseInkFaint,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = body,
                    onValueChange = { body = it },
                    textStyle = TextStyle(fontSize = 11.sp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                )
                Spacer(Modifier.height(8.dp))
                Row {
                    TextButton(onClick = { body = sampleFor(vm.state.value) }) { Text("生成示例", fontSize = 12.sp) }                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                vm.setMockBody(body)
                vm.setUseMock(useMock)
                vm.closeDevPanel()
                vm.query()
            }) { Text("应用并查询") }
        },
        dismissButton = {
            TextButton(onClick = {
                vm.setUseMock(false)
                vm.closeDevPanel()
            }) { Text("关闭") }
        },
    )
}

private fun sampleFor(state: UiState): String {
    val amount = state.amountText.replace(",", "").ifBlank { "10000" }
    val today = LocalDate.now().toString()
    val rate = if (state.fromCode == "JPY") "0.04268" else "7.1234"
    val billed = runCatching { java.math.BigDecimal(amount).multiply(java.math.BigDecimal(rate)) }
        .getOrElse { java.math.BigDecimal("10000") }
        .setScale(2, java.math.RoundingMode.HALF_UP)
        .toPlainString()
    return """
{
  "name": "settlement-conversion-rate",
  "description": "Settlement conversion rate and billing amount",
  "date": "$today 12:00:00",
  "data": {
    "conversionRate": $rate,
    "crdhldBillAmt": $billed,
    "fxDate": "$today",
    "transCurr": "${state.fromCode}",
    "crdhldBillCurr": "${state.toCode}",
    "transAmt": $amount,
    "bankFee": ${state.bankFeeText.ifBlank { "0" }}
  }
}
    """.trimIndent()
}
