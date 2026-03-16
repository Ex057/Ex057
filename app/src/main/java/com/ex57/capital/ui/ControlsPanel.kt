package com.ex57.capital.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ex57.capital.model.ConfirmationMode
import com.ex57.capital.model.TradingSymbol

val SupportedSymbols = listOf(
    TradingSymbol("EURUSD", "Euro / US Dollar", "Forex", "frxEURUSD"),
    TradingSymbol("GBPUSD", "British Pound / US Dollar", "Forex", "frxGBPUSD"),
    TradingSymbol("USDJPY", "US Dollar / Japanese Yen", "Forex", "frxUSDJPY"),
    TradingSymbol("BTCUSD", "Bitcoin / US Dollar", "Crypto", "cryBTCUSD"),
    TradingSymbol("XAUUSD", "Gold / US Dollar", "Commodities", "frxXAUUSD"),
    TradingSymbol("VOL50", "Volatility 50 Index", "Synthetic", "R_50")
)

val SupportedTimeframes = listOf("1m", "5m", "15m", "1h", "4h", "1d")

@Composable
fun ControlsPanel(
    selectedSymbol: TradingSymbol,
    onSymbolChange: (TradingSymbol) -> Unit,
    selectedTimeframe: String,
    onTimeframeChange: (String) -> Unit,
    selectedMode: ConfirmationMode,
    onModeChange: (ConfirmationMode) -> Unit,
    onConnect: () -> Unit,
    onAnalyze: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
            contentColor = MaterialTheme.colorScheme.onSurface
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Trading Configuration", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                DropdownField(
                    label = "Symbol",
                    value = selectedSymbol.code,
                    options = SupportedSymbols.map { it.code },
                    onSelect = { code ->
                        onSymbolChange(SupportedSymbols.first { it.code == code })
                    },
                    modifier = Modifier.weight(1f)
                )
                DropdownField(
                    label = "Timeframe",
                    value = selectedTimeframe,
                    options = SupportedTimeframes,
                    onSelect = onTimeframeChange,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            DropdownField(
                label = "Confirmation Mode",
                value = selectedMode.label,
                options = ConfirmationMode.values().map { it.label },
                onSelect = { label ->
                    onModeChange(ConfirmationMode.values().first { it.label == label })
                },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = onConnect,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text("Connect Feed")
                }
                Button(
                    onClick = onAnalyze,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text("Analyze")
                }
            }
        }
    }
}

@Composable
fun DropdownField(
    label: String,
    value: String,
    options: List<String>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = modifier) {
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(6.dp))
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Text(value, modifier = Modifier.weight(1f))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    }
                )
            }
        }
    }
}
