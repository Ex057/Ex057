package com.ex57.capital.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.CircularProgressIndicator
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
import com.ex57.capital.model.SymbolTradingSpec
import com.ex57.capital.model.TradingSymbol

private fun symbolSpec(
    contractSize: Double,
    minLot: Double,
    maxLot: Double,
    lotStep: Double,
    typicalSpread: Double,
    effectiveLeverage: Double,
    tickSize: Double,
    pricePrecision: Int
) = SymbolTradingSpec(
    contractSize = contractSize,
    minLot = minLot,
    maxLot = maxLot,
    lotStep = lotStep,
    typicalSpread = typicalSpread,
    effectiveLeverage = effectiveLeverage,
    tickSize = tickSize,
    pricePrecision = pricePrecision
)

private fun forexSpec(
    typicalSpread: Double,
    pricePrecision: Int = 5,
    tickSize: Double = 0.00001,
    effectiveLeverage: Double = 1000.0
) = symbolSpec(
    contractSize = 100_000.0,
    minLot = 0.01,
    maxLot = 40.0,
    lotStep = 0.01,
    typicalSpread = typicalSpread,
    effectiveLeverage = effectiveLeverage,
    tickSize = tickSize,
    pricePrecision = pricePrecision
)

private fun syntheticSpec(
    minLot: Double,
    lotStep: Double,
    typicalSpread: Double,
    effectiveLeverage: Double = 500.0
) = symbolSpec(
    contractSize = 1.0,
    minLot = minLot,
    maxLot = 100.0,
    lotStep = lotStep,
    typicalSpread = typicalSpread,
    effectiveLeverage = effectiveLeverage,
    tickSize = 0.01,
    pricePrecision = 2
)

val SupportedSymbols = listOf(
    TradingSymbol(
        "XAUUSD",
        "Gold / US Dollar",
        "Commodities",
        "frxXAUUSD",
        spec = symbolSpec(
            contractSize = 100.0,
            minLot = 0.01,
            maxLot = 20.0,
            lotStep = 0.01,
            typicalSpread = 0.45,
            effectiveLeverage = 500.0,
            tickSize = 0.01,
            pricePrecision = 2
        )
    )
)

val SupportedTimeframes = listOf("1m", "5m", "15m", "30m", "1h", "2h", "4h", "8h", "1d")
val SupportedCategories = SupportedSymbols.map { it.category }.distinct()

@Composable
fun ControlsPanel(
    selectedSymbol: TradingSymbol,
    onSymbolChange: (TradingSymbol) -> Unit,
    selectedTimeframe: String,
    onTimeframeChange: (String) -> Unit,
    selectedMode: ConfirmationMode,
    onModeChange: (ConfirmationMode) -> Unit,
    onAnalyzeMarket: () -> Unit,
    isAnalyzingMarket: Boolean,
    riskPercentInput: String,
    onRiskPercentInputChange: (String) -> Unit
) {
    val selectedCategory = selectedSymbol.category

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
                OutlinedTextField(
                    value = selectedCategory,
                    onValueChange = {},
                    enabled = false,
                    label = { Text("Market") },
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = selectedSymbol.code,
                    onValueChange = {},
                    enabled = false,
                    label = { Text("Symbol") },
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
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
            OutlinedTextField(
                value = riskPercentInput,
                onValueChange = { updated ->
                    if (updated.isEmpty() || updated.matches(Regex("^\\d{0,2}(\\.\\d{0,2})?$"))) {
                        onRiskPercentInputChange(updated)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Risk % per signal") },
                supportingText = {
                    Text(
                        "Sizing uses equity, stop distance, and ${selectedSymbol.code} contract rules: min ${formatLot(selectedSymbol.spec.minLot)}, step ${formatLot(selectedSymbol.spec.lotStep)}."
                    )
                },
                singleLine = true
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onAnalyzeMarket,
                enabled = !isAnalyzingMarket,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                if (isAnalyzingMarket) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(if (isAnalyzingMarket) "Analysing Market" else "Analyse Market")
            }
        }
    }
}

fun formatLot(value: Double): String {
    return when {
        value >= 1.0 -> "%.2f".format(value)
        value >= 0.01 -> "%.3f".format(value).trimEnd('0').trimEnd('.')
        else -> "%.4f".format(value).trimEnd('0').trimEnd('.')
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
