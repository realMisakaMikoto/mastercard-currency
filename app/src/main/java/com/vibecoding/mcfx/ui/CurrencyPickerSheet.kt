package com.vibecoding.mcfx.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vibecoding.mcfx.data.Currency
import com.vibecoding.mcfx.logic.CurrencySearch

/**
 * Searchable currency picker: 下拉式菜单 + 搜索，命中 三字母代码 / 货币名称 / 国家名称.
 * Flags are the same circular assets the main screen uses.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CurrencyPickerSheet(
    currencies: List<Currency>,
    selectedCode: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val results = remember(query, currencies) { CurrencySearch.search(currencies, query) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.White,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            Text(
                text = "选择货币",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = WiseInk,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "可搜索货币代码、货币名称或国家名称，例如 JPY / 日元 / 日本",
                fontSize = 12.sp,
                color = WiseInkFaint,
            )
            Spacer(Modifier.height(14.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.dp, WiseBorder, RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = null,
                    tint = WiseInkFaint,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 15.sp, color = WiseInk),
                    cursorBrush = SolidColor(WiseForest),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Search,
                    ),
                    modifier = Modifier.weight(1f),
                    decorationBox = { inner ->
                        Box {
                            if (query.isEmpty()) {
                                Text("搜索货币 / 国家 / 代码", fontSize = 15.sp, color = WiseInkFaint)
                            }
                            inner()
                        }
                    },
                )
                if (query.isNotEmpty()) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "清除",
                        tint = WiseInkFaint,
                        modifier = Modifier
                            .size(18.dp)
                            .clickable { query = "" },
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            if (results.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("没有匹配的货币", fontSize = 13.sp, color = WiseInkFaint)
                }
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 440.dp)) {
                    items(results, key = { it.code }) { currency ->
                        CurrencyRowItem(
                            currency = currency,
                            selected = currency.code == selectedCode,
                            onClick = { onSelect(currency.code) },
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun CurrencyRowItem(currency: Currency, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) WiseGreen.copy(alpha = 0.28f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        FlagIcon(cc = currency.cc, size = 32.dp)
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = currency.code,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = WiseInk,
                )
                Spacer(Modifier.width(8.dp))
                Text(text = currency.nameZh, fontSize = 13.sp, color = WiseInkSoft)
            }
            Text(
                text = "${currency.countryZh} · ${currency.nameEn}",
                fontSize = 11.sp,
                color = WiseInkFaint,
            )
        }
        if (selected) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = WiseForest,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
