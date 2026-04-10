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
import androidx.compose.material3.OutlinedTextField
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
    TradingSymbol("EURUSD", "Euro / US Dollar", "Forex", "frxEURUSD", spec = forexSpec(typicalSpread = 0.00010)),
    TradingSymbol("AUDUSD", "Australian Dollar / US Dollar", "Forex", "frxAUDUSD", spec = forexSpec(typicalSpread = 0.00012)),
    TradingSymbol("EURGBP", "Euro / British Pound", "Forex", "frxEURGBP", spec = forexSpec(typicalSpread = 0.00014)),
    TradingSymbol("GBPUSD", "British Pound / US Dollar", "Forex", "frxGBPUSD", spec = forexSpec(typicalSpread = 0.00015)),
    TradingSymbol("NZDUSD", "New Zealand Dollar / US Dollar", "Forex", "frxNZDUSD", spec = forexSpec(typicalSpread = 0.00016)),
    TradingSymbol("USDCAD", "US Dollar / Canadian Dollar", "Forex", "frxUSDCAD", spec = forexSpec(typicalSpread = 0.00018)),
    TradingSymbol("USDCHF", "US Dollar / Swiss Franc", "Forex", "frxUSDCHF", spec = forexSpec(typicalSpread = 0.00016)),
    TradingSymbol("USDJPY", "US Dollar / Japanese Yen", "Forex", "frxUSDJPY", spec = forexSpec(typicalSpread = 0.015, pricePrecision = 3, tickSize = 0.001)),
    TradingSymbol(
        "BTCUSD",
        "Bitcoin / US Dollar",
        "Crypto",
        "cryBTCUSD",
        spec = symbolSpec(
            contractSize = 1.0,
            minLot = 0.01,
            maxLot = 5.0,
            lotStep = 0.01,
            typicalSpread = 30.0,
            effectiveLeverage = 50.0,
            tickSize = 0.01,
            pricePrecision = 2
        )
    ),
    TradingSymbol(
        "ETHUSD",
        "Ethereum / US Dollar",
        "Crypto",
        "cryETHUSD",
        spec = symbolSpec(
            contractSize = 1.0,
            minLot = 0.01,
            maxLot = 10.0,
            lotStep = 0.01,
            typicalSpread = 3.0,
            effectiveLeverage = 50.0,
            tickSize = 0.01,
            pricePrecision = 2
        )
    ),
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
    ),
    TradingSymbol("VOL10", "Volatility 10 Index", "Synthetic", "R_10", spec = syntheticSpec(minLot = 0.20, lotStep = 0.10, typicalSpread = 0.80)),
    TradingSymbol("VOL25", "Volatility 25 Index", "Synthetic", "R_25", spec = syntheticSpec(minLot = 0.20, lotStep = 0.10, typicalSpread = 1.20)),
    TradingSymbol("VOL50", "Volatility 50 Index", "Synthetic", "R_50", spec = syntheticSpec(minLot = 0.20, lotStep = 0.10, typicalSpread = 1.80)),
    TradingSymbol("VOL75", "Volatility 75 Index", "Synthetic", "R_75", spec = syntheticSpec(minLot = 0.20, lotStep = 0.10, typicalSpread = 3.20)),
    TradingSymbol("VOL100", "Volatility 100 Index", "Synthetic", "R_100", spec = syntheticSpec(minLot = 0.20, lotStep = 0.10, typicalSpread = 4.20)),
    TradingSymbol("VOL10_1S", "Volatility 10 (1s) Index", "Synthetic", "1HZ10V", spec = syntheticSpec(minLot = 0.005, lotStep = 0.001, typicalSpread = 0.08, effectiveLeverage = 300.0)),
    TradingSymbol("VOL25_1S", "Volatility 25 (1s) Index", "Synthetic", "1HZ25V", spec = syntheticSpec(minLot = 0.005, lotStep = 0.001, typicalSpread = 0.12, effectiveLeverage = 300.0)),
    TradingSymbol("VOL50_1S", "Volatility 50 (1s) Index", "Synthetic", "1HZ50V", spec = syntheticSpec(minLot = 0.005, lotStep = 0.001, typicalSpread = 0.18, effectiveLeverage = 300.0)),
    TradingSymbol("VOL75_1S", "Volatility 75 (1s) Index", "Synthetic", "1HZ75V", spec = syntheticSpec(minLot = 0.005, lotStep = 0.001, typicalSpread = 0.28, effectiveLeverage = 300.0)),
    TradingSymbol("VOL100_1S", "Volatility 100 (1s) Index", "Synthetic", "1HZ100V", spec = syntheticSpec(minLot = 0.005, lotStep = 0.001, typicalSpread = 0.38, effectiveLeverage = 300.0)),
    TradingSymbol("BOOM300", "Boom 300 Index", "Crash/Boom", "BOOM300N", spec = syntheticSpec(minLot = 0.20, lotStep = 0.10, typicalSpread = 0.575, effectiveLeverage = 200.0)),
    TradingSymbol("BOOM500", "Boom 500 Index", "Crash/Boom", "BOOM500", spec = syntheticSpec(minLot = 0.20, lotStep = 0.10, typicalSpread = 0.392, effectiveLeverage = 200.0)),
    TradingSymbol("BOOM600", "Boom 600 Index", "Crash/Boom", "BOOM600", spec = syntheticSpec(minLot = 0.10, lotStep = 0.10, typicalSpread = 0.418, effectiveLeverage = 200.0)),
    TradingSymbol("BOOM900", "Boom 900 Index", "Crash/Boom", "BOOM900", spec = syntheticSpec(minLot = 0.10, lotStep = 0.10, typicalSpread = 0.499, effectiveLeverage = 200.0)),
    TradingSymbol("BOOM1000", "Boom 1000 Index", "Crash/Boom", "BOOM1000", spec = syntheticSpec(minLot = 0.20, lotStep = 0.10, typicalSpread = 1.5719, effectiveLeverage = 200.0)),
    TradingSymbol("CRASH300", "Crash 300 Index", "Crash/Boom", "CRASH300N", spec = syntheticSpec(minLot = 0.20, lotStep = 0.10, typicalSpread = 0.575, effectiveLeverage = 200.0)),
    TradingSymbol("CRASH500", "Crash 500 Index", "Crash/Boom", "CRASH500", spec = syntheticSpec(minLot = 0.20, lotStep = 0.10, typicalSpread = 0.392, effectiveLeverage = 200.0)),
    TradingSymbol("CRASH600", "Crash 600 Index", "Crash/Boom", "CRASH600", spec = syntheticSpec(minLot = 0.10, lotStep = 0.10, typicalSpread = 0.418, effectiveLeverage = 200.0)),
    TradingSymbol("CRASH900", "Crash 900 Index", "Crash/Boom", "CRASH900", spec = syntheticSpec(minLot = 0.10, lotStep = 0.10, typicalSpread = 0.499, effectiveLeverage = 200.0)),
    TradingSymbol("CRASH1000", "Crash 1000 Index", "Crash/Boom", "CRASH1000", spec = syntheticSpec(minLot = 0.20, lotStep = 0.10, typicalSpread = 1.5719, effectiveLeverage = 200.0))
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
    onConnect: () -> Unit,
    onAnalyze: () -> Unit,
    lotSizeInput: String,
    onLotSizeInputChange: (String) -> Unit
) {
    val selectedCategory = selectedSymbol.category
    val symbolsInCategory = remember(selectedCategory) {
        SupportedSymbols.filter { it.category == selectedCategory }
    }

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
                    label = "Category",
                    value = selectedCategory,
                    options = SupportedCategories,
                    onSelect = { category ->
                        val firstSymbol = SupportedSymbols.first { it.category == category }
                        onSymbolChange(firstSymbol)
                    },
                    modifier = Modifier.weight(1f)
                )
                DropdownField(
                    label = "Symbol",
                    value = selectedSymbol.code,
                    options = symbolsInCategory.map { it.code },
                    onSelect = { code ->
                        onSymbolChange(symbolsInCategory.first { it.code == code })
                    },
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
                value = lotSizeInput,
                onValueChange = { updated ->
                    if (updated.isEmpty() || updated.matches(Regex("^\\d{0,3}(\\.\\d{0,3})?$"))) {
                        onLotSizeInputChange(updated)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Lot Size") },
                supportingText = {
                    Text(
                        "Broker-style sizing for ${selectedSymbol.code}: min ${formatLot(selectedSymbol.spec.minLot)}, step ${formatLot(selectedSymbol.spec.lotStep)}, max ${formatLot(selectedSymbol.spec.maxLot)}."
                    )
                },
                singleLine = true
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
