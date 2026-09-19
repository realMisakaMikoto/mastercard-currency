package com.vibecoding.mcfx.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vibecoding.mcfx.logic.MastercardEndpoints
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Shows exactly what Mastercard answered. This is the compensation for not being
 * able to verify the live request from the build machine.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsSheet(state: UiState, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val success = (state.status as? QueryStatus.Success)?.result
    val error = state.status as? QueryStatus.Error
    val attempts = success?.attempts ?: error?.attempts ?: emptyList()
    val raw = success?.rawJson ?: ""
    val timeFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.White,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text("诊断信息", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = WiseInk)
            Spacer(Modifier.height(4.dp))
            Text(
                "换算页：${MastercardEndpoints.CONVERTER_PAGE_CN}",
                fontSize = 11.sp,
                color = WiseInkFaint,
            )
            Spacer(Modifier.height(12.dp))

            InfoBlock(
                "本次结果",
                buildString {
                    append("源货币：").append(state.fromCode).append('\n')
                    append("目标货币：").append(state.toCode).append('\n')
                    append("金额：").append(state.amountText).append('\n')
                    append("手续费：").append(state.bankFeeText.ifBlank { "0" }).append("%\n")
                    append("汇率日期：").append(state.dateText.ifBlank { "默认当天" }).append('\n')
                    if (success != null) {
                        append("命中层级：").append(success.layer.label).append('\n')
                        append("生效日期：").append(success.effectiveDate)
                        if (success.dateFellBack) append("（已回退）")
                        append('\n')                    } else if (error != null) {
                        append("失败原因：").append(error.message).append('\n')
                        append("错误码：").append(error.code ?: "-")
                    } else {
                        append("尚未查询")
                    }
                },
            )

            if (attempts.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text("请求尝试（共 ${attempts.size} 次）", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = WiseInk)
                Spacer(Modifier.height(6.dp))
                attempts.forEachIndexed { index, attempt ->
                    InfoBlock(
                        "#${index + 1} · ${attempt.layer.label} · HTTP ${attempt.status}",
                        "${attempt.url}\n${attempt.bodySnippet}",
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }

            if (raw.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text("原始响应", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = WiseInk)
                Spacer(Modifier.height(6.dp))
                InfoBlock(timeFormat.format(Date()), raw)
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun InfoBlock(title: String, body: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(WiseFill)
            .padding(12.dp),
    ) {
        Text(title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = WiseInk)
        Spacer(Modifier.height(4.dp))
        Text(body, fontSize = 11.sp, lineHeight = 17.sp, color = WiseInkSoft)
    }
}
