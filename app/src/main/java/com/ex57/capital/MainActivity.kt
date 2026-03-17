package com.ex57.capital

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ex57.capital.analysis.AnalysisStub
import com.ex57.capital.data.PriceViewModel
import com.ex57.capital.model.AnalysisResult
import com.ex57.capital.model.ClosedTradeRecord
import com.ex57.capital.model.ConfirmationMode
import com.ex57.capital.model.DataQuality
import com.ex57.capital.model.MarketCandle
import com.ex57.capital.model.PositionRecommendation
import com.ex57.capital.model.PositionSide
import com.ex57.capital.model.TradePosition
import com.ex57.capital.model.TradeBias
import com.ex57.capital.model.TradeDecision
import com.ex57.capital.model.TradingSymbol
import com.ex57.capital.ui.ControlsPanel
import com.ex57.capital.ui.DropdownField
import com.ex57.capital.ui.SupportedSymbols
import com.ex57.capital.ui.SupportedTimeframes
import com.ex57.capital.ui.theme.EX57Theme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                Color.Transparent.toArgb(),
                Color.Transparent.toArgb()
            ),
            navigationBarStyle = SystemBarStyle.auto(
                Color.Transparent.toArgb(),
                Color.Transparent.toArgb()
            )
        )
        setContent {
            EX57Theme {
                EX57App()
            }
        }
    }
}

private enum class AppTab(
    val label: String,
    val icon: ImageVector,
    val title: String,
    val subtitle: String
) {
    HOME(
        label = "Home",
        icon = Icons.Outlined.Home,
        title = "Signal Home",
        subtitle = "Live setup scoring tuned for fast directional reads."
    ),
    MARKETS(
        label = "Markets",
        icon = Icons.Outlined.Search,
        title = "Market Scanner",
        subtitle = "Track which instruments are currently worth attention."
    ),
    ALERTS(
        label = "Alerts",
        icon = Icons.Outlined.Notifications,
        title = "Signal Alerts",
        subtitle = "Review trigger quality before you act on fast conditions."
    ),
    BACKTEST(
        label = "Backtest",
        icon = Icons.AutoMirrored.Outlined.ShowChart,
        title = "Evidence Lab",
        subtitle = "Inspect expectancy, sample size, and robustness before trusting a setup."
    ),
    SETTINGS(
        label = "Settings",
        icon = Icons.Outlined.Settings,
        title = "Workspace",
        subtitle = "Adjust filtering, execution discipline, and display behavior."
    )
}

private data class SignalAlertEntry(
    val timestampLabel: String,
    val headline: String,
    val body: String,
    val approved: Boolean,
    val trigger: String
)

private data class PriceCandle(
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double
)

private data class ChartOverlayState(
    val fastTrend: List<Double>,
    val slowTrend: List<Double>,
    val biasLine: List<Double>
)

private data class AlertMonitorState(
    val enabled: Boolean = false,
    val symbol: TradingSymbol? = null,
    val timeframe: String = "15m",
    val intervalMinutes: Int = 15,
    val lastCheckLabel: String = "Not started"
)

private val AlertMonitorIntervals = listOf(5, 15, 30, 60)

@Composable
fun EX57App() {
    val priceViewModel: PriceViewModel = viewModel()
    val livePrice by priceViewModel.price.collectAsState()
    val recentPrices by priceViewModel.priceHistory.collectAsState()
    val candleHistory by priceViewModel.candleHistory.collectAsState()
    val candleStack by priceViewModel.candleStack.collectAsState()
    val dataQuality by priceViewModel.dataQuality.collectAsState()
    val connectedFeed by priceViewModel.symbol.collectAsState()
    val openPositions by priceViewModel.openPositions.collectAsState()
    val closedTrades by priceViewModel.closedTrades.collectAsState()
    val darkTheme = androidx.compose.foundation.isSystemInDarkTheme()
    var selectedTab by remember { mutableStateOf(AppTab.HOME) }
    var selectedSymbol by remember { mutableStateOf(SupportedSymbols.first()) }
    var selectedTimeframe by remember { mutableStateOf(SupportedTimeframes[2]) }
    var selectedMode by remember { mutableStateOf(ConfirmationMode.MODERATE) }
    var analysisResult by remember { mutableStateOf<AnalysisResult?>(null) }
    var alertHistory by remember { mutableStateOf<List<SignalAlertEntry>>(emptyList()) }
    var alertMonitor by remember { mutableStateOf(AlertMonitorState()) }
    var autoAnalyzeLiveFeed by remember { mutableStateOf(false) }
    var keepAlertHistory by remember { mutableStateOf(true) }
    val colors = MaterialTheme.colorScheme

    val pageBackground = if (darkTheme) {
        Brush.verticalGradient(
            listOf(
                colors.background,
                colors.surface.copy(alpha = 0.98f),
                colors.primaryContainer.copy(alpha = 0.40f)
            )
        )
    } else {
        Brush.verticalGradient(
            listOf(
                colors.background,
                colors.surfaceVariant.copy(alpha = 0.72f),
                colors.primaryContainer.copy(alpha = 0.34f)
            )
        )
    }
    val scrim = Brush.verticalGradient(
        listOf(
            colors.background.copy(alpha = if (darkTheme) 0.44f else 0.14f),
            Color.Transparent
        )
    )

    fun pushAlert(symbol: TradingSymbol, result: AnalysisResult, source: String) {
        if (!keepAlertHistory || result.bias == TradeBias.NEUTRAL) return
        val entry = SignalAlertEntry(
            timestampLabel = currentTimeLabel(),
            headline = "$source • ${symbol.code} • ${result.bias.label} • ${result.confidence}%",
            body = result.summary,
            approved = result.approved,
            trigger = result.nextTrigger
        )
        if (alertHistory.firstOrNull() == entry) return
        alertHistory = listOf(entry) + alertHistory.take(11)
    }

    fun runAnalysis(targetSymbol: TradingSymbol = selectedSymbol, recordAlert: Boolean, source: String) {
        val openPosition = priceViewModel.findOpenPosition(
            symbolCode = targetSymbol.code,
            timeframe = selectedTimeframe
        )
        val result = AnalysisStub.analyze(
            symbol = targetSymbol,
            timeframe = selectedTimeframe,
            mode = selectedMode,
            candles = candleHistory,
            candleStack = candleStack,
            recentPrices = recentPrices,
            openPosition = openPosition
        )
        analysisResult = result
        if (recordAlert) {
            pushAlert(targetSymbol, result, source)
        }
    }

    LaunchedEffect(autoAnalyzeLiveFeed, recentPrices, selectedSymbol, selectedTimeframe, selectedMode, connectedFeed, openPositions) {
        if (
            autoAnalyzeLiveFeed &&
            recentPrices.size >= 12 &&
            connectedFeed == selectedSymbol.derivSymbol
        ) {
            runAnalysis(recordAlert = false, source = "Auto")
        }
    }

    LaunchedEffect(candleHistory, selectedSymbol, selectedTimeframe, connectedFeed, openPositions) {
        if (
            analysisResult != null &&
            candleHistory.isNotEmpty() &&
            connectedFeed == selectedSymbol.derivSymbol
        ) {
            runAnalysis(recordAlert = false, source = "OHLC")
        }
    }

    LaunchedEffect(alertMonitor.enabled, alertMonitor.symbol, alertMonitor.timeframe, alertMonitor.intervalMinutes) {
        val monitorSymbol = alertMonitor.symbol ?: return@LaunchedEffect
        if (!alertMonitor.enabled) return@LaunchedEffect

        while (alertMonitor.enabled) {
            selectedSymbol = monitorSymbol
            selectedTimeframe = alertMonitor.timeframe
            analysisResult = null
            priceViewModel.connect(monitorSymbol.derivSymbol, alertMonitor.timeframe)
            delay(1_500)
            priceViewModel.refreshCandles(monitorSymbol.derivSymbol, alertMonitor.timeframe)
            delay(1_500)
            runAnalysis(targetSymbol = monitorSymbol, recordAlert = true, source = "Monitor")
            alertMonitor = alertMonitor.copy(lastCheckLabel = currentTimeLabel())
            delay(alertMonitor.intervalMinutes * 60_000L)
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            FloatingBottomNav(
                selectedTab = selectedTab,
                onTabSelected = { selectedTab = it },
                darkTheme = darkTheme
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(pageBackground)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(128.dp)
                    .background(scrim)
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(padding)
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 12.dp)
            ) {
                FloatingHeader(tab = selectedTab)
                Spacer(modifier = Modifier.height(16.dp))
                when (selectedTab) {
                    AppTab.HOME -> HomeDashboard(
                        priceViewModel = priceViewModel,
                        livePrice = livePrice,
                        recentPrices = recentPrices,
                        selectedSymbol = selectedSymbol,
                        onSymbolChange = { selectedSymbol = it },
                        selectedTimeframe = selectedTimeframe,
                        onTimeframeChange = { selectedTimeframe = it },
                        selectedMode = selectedMode,
                        onModeChange = { selectedMode = it },
                        analysisResult = analysisResult,
                        openPosition = openPositions.firstOrNull {
                            it.symbolCode == selectedSymbol.code && it.timeframe == selectedTimeframe
                        },
                        onOpenTrade = {
                            analysisResult?.let { result ->
                                priceViewModel.openTrade(
                                    symbol = selectedSymbol,
                                    timeframe = selectedTimeframe,
                                    analysisResult = result,
                                    executionPrice = livePrice
                                )
                                runAnalysis(recordAlert = false, source = "Position")
                            }
                        },
                        onCloseTrade = {
                            priceViewModel.closeTrade(
                                symbolCode = selectedSymbol.code,
                                timeframe = selectedTimeframe,
                                exitPrice = livePrice
                            )
                            runAnalysis(recordAlert = false, source = "Close")
                        },
                        onConnect = {
                            analysisResult = null
                            priceViewModel.connect(selectedSymbol.derivSymbol, selectedTimeframe)
                        },
                        onAnalyze = {
                            priceViewModel.refreshCandles(selectedSymbol.derivSymbol, selectedTimeframe)
                            runAnalysis(recordAlert = true, source = "Manual")
                        },
                        candles = candleHistory,
                        dataQuality = dataQuality
                    )
                    AppTab.MARKETS -> MarketsPanel(
                        selectedSymbol = selectedSymbol,
                        onTrackSymbol = { symbol ->
                            selectedSymbol = symbol
                            selectedTab = AppTab.HOME
                        },
                        selectedTimeframe = selectedTimeframe,
                        selectedMode = selectedMode,
                        onConnectSymbol = { symbol ->
                            selectedSymbol = symbol
                            analysisResult = null
                            priceViewModel.connect(symbol.derivSymbol, selectedTimeframe)
                        },
                        onAnalyzeSymbol = { symbol ->
                            selectedSymbol = symbol
                            selectedTab = AppTab.HOME
                            priceViewModel.refreshCandles(symbol.derivSymbol, selectedTimeframe)
                            runAnalysis(targetSymbol = symbol, recordAlert = true, source = "Scanner")
                        },
                        connectedFeed = connectedFeed
                    )
                    AppTab.ALERTS -> AlertsPanel(
                        analysisResult = analysisResult,
                        alertHistory = alertHistory,
                        selectedSymbol = selectedSymbol,
                        selectedTimeframe = selectedTimeframe,
                        monitorState = alertMonitor,
                        onMonitorIntervalChange = { minutes ->
                            alertMonitor = alertMonitor.copy(intervalMinutes = minutes)
                        },
                        onStartMonitoring = {
                            alertMonitor = AlertMonitorState(
                                enabled = true,
                                symbol = selectedSymbol,
                                timeframe = selectedTimeframe,
                                intervalMinutes = alertMonitor.intervalMinutes,
                                lastCheckLabel = "Starting"
                            )
                        },
                        onStopMonitoring = {
                            alertMonitor = alertMonitor.copy(enabled = false)
                        },
                        onClearAlerts = { alertHistory = emptyList() },
                        onPinCurrentSignal = {
                            analysisResult?.let { result -> pushAlert(selectedSymbol, result, "Pinned") }
                        }
                    )
                    AppTab.BACKTEST -> BacktestPanel(
                        analysisResult = analysisResult,
                        dataQuality = dataQuality,
                        selectedSymbol = selectedSymbol,
                        closedTrades = closedTrades
                    )
                    AppTab.SETTINGS -> SettingsPanel(
                        selectedMode = selectedMode,
                        onModeChange = { selectedMode = it },
                        autoAnalyzeLiveFeed = autoAnalyzeLiveFeed,
                        onAutoAnalyzeChange = { autoAnalyzeLiveFeed = it },
                        keepAlertHistory = keepAlertHistory,
                        onKeepAlertHistoryChange = { keepAlertHistory = it },
                        onDisconnectFeed = {
                            analysisResult = null
                            priceViewModel.disconnect()
                        },
                        connectedFeed = connectedFeed
                    )
                }
            }
        }
    }
}

@Composable
private fun FloatingHeader(tab: AppTab) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(top = 8.dp)
    ) {
        Text(
            text = tab.title,
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = tab.subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun HomeDashboard(
    priceViewModel: PriceViewModel,
    livePrice: Double?,
    recentPrices: List<Double>,
    candles: List<MarketCandle>,
    dataQuality: DataQuality,
    selectedSymbol: TradingSymbol,
    onSymbolChange: (TradingSymbol) -> Unit,
    selectedTimeframe: String,
    onTimeframeChange: (String) -> Unit,
    selectedMode: ConfirmationMode,
    onModeChange: (ConfirmationMode) -> Unit,
    analysisResult: AnalysisResult?,
    openPosition: TradePosition?,
    onOpenTrade: () -> Unit,
    onCloseTrade: () -> Unit,
    onConnect: () -> Unit,
    onAnalyze: () -> Unit
) {
    HeroCard(selectedMode)
    Spacer(modifier = Modifier.height(16.dp))
    ControlsPanel(
        selectedSymbol = selectedSymbol,
        onSymbolChange = onSymbolChange,
        selectedTimeframe = selectedTimeframe,
        onTimeframeChange = onTimeframeChange,
        selectedMode = selectedMode,
        onModeChange = onModeChange,
        onConnect = onConnect,
        onAnalyze = onAnalyze
    )
    Spacer(modifier = Modifier.height(16.dp))
    SignalCard(
        priceViewModel = priceViewModel,
        recentPrices = recentPrices,
        candles = candles,
        dataQuality = dataQuality,
        selectedSymbol = selectedSymbol,
        selectedTimeframe = selectedTimeframe,
        selectedMode = selectedMode,
        analysisResult = analysisResult,
        openPosition = openPosition,
        onOpenTrade = onOpenTrade,
        onCloseTrade = onCloseTrade
    )
    openPosition?.let {
        Spacer(modifier = Modifier.height(12.dp))
        ActivePositionCard(
            position = it,
            livePrice = livePrice,
            analysisResult = analysisResult,
            onCloseTrade = onCloseTrade
        )
    }
}

@Composable
private fun HeroCard(selectedMode: ConfirmationMode) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
            contentColor = MaterialTheme.colorScheme.onSurface
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Market Bias Engine", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Live tick-driven directional scoring for cleaner entry timing. Use it as a probability filter, not certainty.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Mode:", fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.width(8.dp))
                ModeChip(selectedMode.label)
            }
        }
    }
}

@Composable
private fun ModeChip(label: String) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.96f)
        )
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

@Composable
private fun SignalCard(
    priceViewModel: PriceViewModel,
    recentPrices: List<Double>,
    candles: List<MarketCandle>,
    dataQuality: DataQuality,
    selectedSymbol: TradingSymbol,
    selectedTimeframe: String,
    selectedMode: ConfirmationMode,
    analysisResult: AnalysisResult?,
    openPosition: TradePosition? = null,
    onOpenTrade: () -> Unit = {},
    onCloseTrade: () -> Unit = {}
) {
    val price by priceViewModel.price.collectAsState()
    val status by priceViewModel.status.collectAsState()
    val symbol by priceViewModel.symbol.collectAsState()
    val biasColor = when (analysisResult?.bias) {
        TradeBias.BULLISH -> MaterialTheme.colorScheme.primary
        TradeBias.BEARISH -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurface
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
            contentColor = MaterialTheme.colorScheme.onSurface
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Signal Console", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Text("Status: $status")
            Spacer(modifier = Modifier.height(6.dp))
            Text("Feed: ${symbol ?: "-"}")
            Spacer(modifier = Modifier.height(6.dp))
            Text("Configured: ${selectedSymbol.code} • $selectedTimeframe • ${selectedMode.label}")
            Spacer(modifier = Modifier.height(8.dp))
            Text("Live Price: ${price?.let { "%.5f".format(it) } ?: "-"}")
            Spacer(modifier = Modifier.height(6.dp))
            Text("Samples: ${recentPrices.size}/240")
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                "Data: ${dataQuality.label} ${if (dataQuality == DataQuality.EXCHANGE_OHLC) "OK" else "WARN"}",
                color = if (dataQuality == DataQuality.EXCHANGE_OHLC) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
            )
            Spacer(modifier = Modifier.height(12.dp))

            if (analysisResult == null) {
                Text(
                    "Flow: pick a pair, connect the live feed, run Analyze, then review the chart and the trade plan before acting.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    "Bias: ${analysisResult.bias.label}",
                    fontWeight = FontWeight.Bold,
                    color = biasColor
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text("Setup: ${analysisResult.setupType.label}")
                Spacer(modifier = Modifier.height(6.dp))
                Text("Confidence: ${analysisResult.confidence}%")
                Spacer(modifier = Modifier.height(6.dp))
                Text("Decision: ${analysisResult.decision.label}")
                Spacer(modifier = Modifier.height(8.dp))
                Text(analysisResult.summary)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    analysisResult.executionPlan,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    analysisResult.riskNote,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                analysisResult.mtfaStatus?.let { mtfa ->
                    MtfaStatusCard(mtfaStatus = mtfa)
                    Spacer(modifier = Modifier.height(12.dp))
                }
                ProcessFlowCard(
                    analysisResult = analysisResult,
                    selectedSymbol = selectedSymbol
                )
                Spacer(modifier = Modifier.height(12.dp))
                ChartCard(
                    prices = recentPrices,
                    candles = candles,
                    dataQuality = dataQuality,
                    timeframe = selectedTimeframe,
                    analysisResult = analysisResult
                )
                Spacer(modifier = Modifier.height(12.dp))
                TradePlanCard(
                    analysisResult = analysisResult,
                    biasColor = biasColor,
                    openPosition = openPosition,
                    onOpenTrade = onOpenTrade,
                    onCloseTrade = onCloseTrade
                )
                Spacer(modifier = Modifier.height(12.dp))
                EvidenceCard(
                    analysisResult = analysisResult
                )
                Spacer(modifier = Modifier.height(12.dp))
                analysisResult.confirmations.forEach { confirmation ->
                    Text(
                        text = "${if (confirmation.passed) "PASS" else "WAIT"}  ${confirmation.name}: ${confirmation.details}",
                        color = if (confirmation.passed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
        }
    }
}

@Composable
private fun MtfaStatusCard(
    mtfaStatus: com.ex57.capital.model.MtfaStatus
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("MTFA Stack", fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(8.dp))
            Text("Macro: ${mtfaStatus.macro}")
            Spacer(modifier = Modifier.height(4.dp))
            Text("Structure: ${mtfaStatus.structure}")
            Spacer(modifier = Modifier.height(4.dp))
            Text("Setup: ${mtfaStatus.setup}")
            Spacer(modifier = Modifier.height(4.dp))
            Text("Trigger: ${mtfaStatus.trigger}")
        }
    }
}

@Composable
private fun MarketsPanel(
    selectedSymbol: TradingSymbol,
    onTrackSymbol: (TradingSymbol) -> Unit,
    selectedTimeframe: String,
    selectedMode: ConfirmationMode,
    onConnectSymbol: (TradingSymbol) -> Unit,
    onAnalyzeSymbol: (TradingSymbol) -> Unit,
    connectedFeed: String?
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Focused Market", fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "${selectedSymbol.label} is the current active market. Keep your list narrow and trade only when bias and timing agree.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    Spacer(modifier = Modifier.height(16.dp))
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Scan Rules", fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(8.dp))
            Text("Timeframe: $selectedTimeframe", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(4.dp))
            Text("Mode: ${selectedMode.label}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Use this tab to compare only a handful of markets. Broad scanning usually adds noise, not edge.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    Spacer(modifier = Modifier.height(16.dp))
    SupportedSymbols.forEach { symbol ->
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (symbol == selectedSymbol) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                } else {
                    MaterialTheme.colorScheme.surface.copy(alpha = 0.88f)
                },
                contentColor = MaterialTheme.colorScheme.onSurface
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("${symbol.code} • ${symbol.category}", fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(4.dp))
                Text(symbol.label, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    if (connectedFeed == symbol.derivSymbol) "Feed connected" else "Feed idle",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { onTrackSymbol(symbol) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (symbol == selectedSymbol) "Tracking" else "Track")
                    }
                    OutlinedButton(
                        onClick = { onConnectSymbol(symbol) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Connect")
                    }
                    Button(
                        onClick = { onAnalyzeSymbol(symbol) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Analyze")
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
    }
}

@Composable
private fun ProcessFlowCard(
    analysisResult: AnalysisResult,
    selectedSymbol: TradingSymbol
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Trader Flow", fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(8.dp))
            Text("1. Pair selected: ${selectedSymbol.code}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(4.dp))
            Text("2. Analysis complete: ${analysisResult.bias.label} bias", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(4.dp))
            Text("3. Setup family: ${analysisResult.setupType.label}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "4. Decision: ${analysisResult.decision.label}",
                color = when (analysisResult.decision) {
                    TradeDecision.ELIGIBLE -> MaterialTheme.colorScheme.primary
                    TradeDecision.WATCHLIST -> MaterialTheme.colorScheme.secondary
                    TradeDecision.REJECT -> MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    }
}

@Composable
private fun ChartCard(
    prices: List<Double>,
    candles: List<MarketCandle>,
    dataQuality: DataQuality,
    timeframe: String,
    analysisResult: AnalysisResult
) {
    var expanded by remember(analysisResult, timeframe) { mutableStateOf(false) }
    val chartCandles = remember(candles, prices, timeframe) {
        if (candles.isNotEmpty()) {
            candles.takeLast(50).map {
                PriceCandle(
                    open = it.open,
                    high = it.high,
                    low = it.low,
                    close = it.close
                )
            }
        } else {
            buildCandles(prices, timeframe)
        }
    }
    val overlays = remember(chartCandles) { buildChartOverlayState(chartCandles) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .animateContentSize()
        ) {
            Text("Market Chart", fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(8.dp))
            if (chartCandles.size < 2) {
                Text(
                    "Not enough live ticks to render candles yet. Keep the feed running and analyze again.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    if (dataQuality == DataQuality.EXCHANGE_OHLC) {
                        "Showing the most recent ${chartCandles.size} exchange OHLC candles for $timeframe."
                    } else {
                        "Showing the most recent ${chartCandles.size} estimated candles for $timeframe."
                    },
                    color = if (dataQuality == DataQuality.EXCHANGE_OHLC) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                PriceChart(
                    candles = chartCandles,
                    overlays = overlays,
                    analysisResult = analysisResult,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    "Overlays: fast trend, slow trend, bias line, and trade levels.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    TrendBadge("Fast", MaterialTheme.colorScheme.secondary, Modifier.weight(1f))
                    TrendBadge("Slow", MaterialTheme.colorScheme.tertiary, Modifier.weight(1f))
                    TrendBadge("Bias", MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                }
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = { expanded = !expanded },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (expanded) "Hide Trade Map" else "View More On Chart")
                }
                if (expanded) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Trade Map", fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Entry: ${analysisResult.tradeSetup.entry?.let(::formatDisplayPrice) ?: "-"}")
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Stop Loss: ${analysisResult.tradeSetup.stopLoss?.let(::formatDisplayPrice) ?: "-"}")
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Take Profit: ${analysisResult.tradeSetup.takeProfit?.let(::formatDisplayPrice) ?: "-"}")
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Risk/Reward: ${analysisResult.tradeSetup.riskReward}")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Trigger: ${analysisResult.nextTrigger}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        analysisResult.executionPlan,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun TrendBadge(
    label: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.14f),
            contentColor = color
        )
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun EvidenceCard(
    analysisResult: AnalysisResult
) {
    val evidence = analysisResult.evidence

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Evidence", fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Badge: ${evidence.confidenceBadge} • Robustness: ${evidence.robustness}",
                color = when (evidence.confidenceBadge) {
                    "Strong" -> MaterialTheme.colorScheme.primary
                    "Medium" -> MaterialTheme.colorScheme.secondary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text("Win rate: ${evidence.winRate}%")
            Spacer(modifier = Modifier.height(4.dp))
            Text("Profit factor: ${evidence.profitFactor}")
            Spacer(modifier = Modifier.height(4.dp))
            Text("Expectancy: ${evidence.expectancyR}")
            Spacer(modifier = Modifier.height(4.dp))
            Text("Sample size: ${evidence.sampleSize}")
            Spacer(modifier = Modifier.height(4.dp))
            Text("Max drawdown: ${evidence.maxDrawdownR}")
        }
    }
}

@Composable
private fun TradePlanCard(
    analysisResult: AnalysisResult,
    biasColor: Color,
    openPosition: TradePosition?,
    onOpenTrade: () -> Unit,
    onCloseTrade: () -> Unit
) {
    val tradeSetup = analysisResult.tradeSetup

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Trade Plan", fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                if (tradeSetup.shouldTrade) "Trade Candidate" else "Forfeit Candidate",
                color = if (tradeSetup.shouldTrade) biasColor else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text("Entry: ${tradeSetup.entry?.let { "%.5f".format(it) } ?: "-"}")
            Spacer(modifier = Modifier.height(4.dp))
            Text("Stop Loss: ${tradeSetup.stopLoss?.let { "%.5f".format(it) } ?: "-"}")
            Spacer(modifier = Modifier.height(4.dp))
            Text("Take Profit: ${tradeSetup.takeProfit?.let { "%.5f".format(it) } ?: "-"}")
            Spacer(modifier = Modifier.height(4.dp))
            Text("Risk/Reward: ${tradeSetup.riskReward}")
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                analysisResult.traderGuidance,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Trigger: ${analysisResult.nextTrigger}",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            if (openPosition == null) {
                Button(
                    onClick = onOpenTrade,
                    enabled = tradeSetup.shouldTrade && analysisResult.bias != TradeBias.NEUTRAL,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        if (analysisResult.bias == TradeBias.BEARISH) "Open Short Position" else "Open Long Position"
                    )
                }
            } else {
                OutlinedButton(
                    onClick = onCloseTrade,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Close Active Position")
                }
            }
        }
    }
}

@Composable
private fun ActivePositionCard(
    position: TradePosition,
    livePrice: Double?,
    analysisResult: AnalysisResult?,
    onCloseTrade: () -> Unit
) {
    val pnlPercent = livePrice?.let {
        when (position.side) {
            PositionSide.LONG -> ((it - position.entryPrice) / position.entryPrice) * 100.0
            PositionSide.SHORT -> ((position.entryPrice - it) / position.entryPrice) * 100.0
        }
    }
    val guidance = analysisResult?.positionGuidance
    val recommendationColor = when (guidance?.recommendation) {
        PositionRecommendation.HOLD -> MaterialTheme.colorScheme.primary
        PositionRecommendation.SCALE_OUT -> MaterialTheme.colorScheme.secondary
        PositionRecommendation.EXIT -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Active Position", fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(8.dp))
            Text("${position.symbolCode} • ${position.side.label} • ${position.timeframe}")
            Spacer(modifier = Modifier.height(4.dp))
            Text("Opened: ${formatTimestamp(position.openedAtEpochMillis)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(4.dp))
            Text("Entry: ${formatDisplayPrice(position.entryPrice)}")
            Spacer(modifier = Modifier.height(4.dp))
            Text("Live: ${livePrice?.let(::formatDisplayPrice) ?: "-"}")
            Spacer(modifier = Modifier.height(4.dp))
            Text("Stop: ${position.stopLoss?.let(::formatDisplayPrice) ?: "-"}")
            Spacer(modifier = Modifier.height(4.dp))
            Text("Target: ${position.takeProfit?.let(::formatDisplayPrice) ?: "-"}")
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Unrealized: ${pnlPercent?.let(::formatPercentSigned) ?: "-"}",
                color = when {
                    pnlPercent == null -> MaterialTheme.colorScheme.onSurfaceVariant
                    pnlPercent >= 0 -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.error
                }
            )
            guidance?.let {
                Spacer(modifier = Modifier.height(8.dp))
                Text(it.headline, color = recommendationColor, fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.height(4.dp))
                Text(it.detail, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(
                onClick = onCloseTrade,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Close Position")
            }
        }
    }
}

@Composable
private fun PriceChart(
    candles: List<PriceCandle>,
    overlays: ChartOverlayState,
    analysisResult: AnalysisResult,
    modifier: Modifier = Modifier
) {
    val outlineColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
    val bullishCandleColor = MaterialTheme.colorScheme.primary
    val bearishCandleColor = MaterialTheme.colorScheme.error
    val fastLineColor = MaterialTheme.colorScheme.secondary
    val slowLineColor = MaterialTheme.colorScheme.tertiary
    val biasLineColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.75f)
    val entryLineColor = MaterialTheme.colorScheme.tertiary
    val stopLineColor = MaterialTheme.colorScheme.error
    val targetLineColor = MaterialTheme.colorScheme.primary
    val topValue = listOfNotNull(
        candles.maxOfOrNull { it.high },
        analysisResult.tradeSetup.entry,
        analysisResult.tradeSetup.stopLoss,
        analysisResult.tradeSetup.takeProfit
    ).maxOrNull() ?: return
    val bottomValue = listOfNotNull(
        candles.minOfOrNull { it.low },
        analysisResult.tradeSetup.entry,
        analysisResult.tradeSetup.stopLoss,
        analysisResult.tradeSetup.takeProfit
    ).minOrNull() ?: return
    val range = (topValue - bottomValue).coerceAtLeast(0.00001)

    Canvas(modifier = modifier) {
        val candleWidth = size.width / candles.size.coerceAtLeast(1)
        val bodyWidth = candleWidth * 0.58f

        fun yFor(value: Double): Float {
            val normalized = ((topValue - value) / range).toFloat()
            return normalized * size.height
        }

        candles.forEachIndexed { index, candle ->
            val centerX = (index * candleWidth) + (candleWidth / 2f)
            val highY = yFor(candle.high)
            val lowY = yFor(candle.low)
            val openY = yFor(candle.open)
            val closeY = yFor(candle.close)
            val bullish = candle.close >= candle.open
            val candleColor = if (bullish) bullishCandleColor else bearishCandleColor

            drawLine(
                color = candleColor.copy(alpha = 0.9f),
                start = Offset(centerX, highY),
                end = Offset(centerX, lowY),
                strokeWidth = 2f
            )

            val bodyTop = minOf(openY, closeY)
            val bodyHeight = kotlin.math.abs(closeY - openY).coerceAtLeast(4f)
            drawRect(
                color = candleColor,
                topLeft = Offset(centerX - (bodyWidth / 2f), bodyTop),
                size = Size(bodyWidth, bodyHeight)
            )
        }

        fun drawSeries(values: List<Double>, color: Color, strokeWidth: Float) {
            if (values.size < 2) return
            values.zipWithNext().forEachIndexed { index, (startValue, endValue) ->
                val startX = (index * candleWidth) + (candleWidth / 2f)
                val endX = ((index + 1) * candleWidth) + (candleWidth / 2f)
                drawLine(
                    color = color,
                    start = Offset(startX, yFor(startValue)),
                    end = Offset(endX, yFor(endValue)),
                    strokeWidth = strokeWidth
                )
            }
        }

        fun drawLevel(value: Double?, color: Color) {
            if (value == null) return
            val y = yFor(value)
            drawLine(
                color = color,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 3f
            )
        }

        drawSeries(overlays.fastTrend, fastLineColor, 3f)
        drawSeries(overlays.slowTrend, slowLineColor, 3.4f)
        drawSeries(overlays.biasLine, biasLineColor, 2.6f)
        drawLevel(analysisResult.tradeSetup.entry, entryLineColor)
        drawLevel(analysisResult.tradeSetup.stopLoss, stopLineColor)
        drawLevel(analysisResult.tradeSetup.takeProfit, targetLineColor)

        drawRect(
            color = outlineColor,
            topLeft = Offset.Zero,
            size = size,
            style = Stroke(width = 2f)
        )
    }
}

private fun buildCandles(prices: List<Double>, timeframe: String): List<PriceCandle> {
    if (prices.size < 4) return emptyList()

    val targetCandles = 50
    val ticksPerCandle = when (timeframe) {
        "1m" -> 3
        "5m" -> 4
        "15m" -> 6
        "30m" -> 7
        "1h" -> 8
        "2h" -> 9
        "4h" -> 10
        "8h" -> 11
        "1d" -> 12
        else -> 4
    }
    val availableGroupSize = (prices.size / 2).coerceAtLeast(2)
    val groupSize = minOf(ticksPerCandle, availableGroupSize)
    val trimmedPrices = prices.takeLast((groupSize * targetCandles).coerceAtMost(prices.size))

    return trimmedPrices
        .chunked(groupSize)
        .filter { it.isNotEmpty() }
        .takeLast(targetCandles)
        .map { chunk ->
            PriceCandle(
                open = chunk.first(),
                high = chunk.maxOrNull() ?: chunk.first(),
                low = chunk.minOrNull() ?: chunk.first(),
                close = chunk.last()
            )
        }
}

private fun buildChartOverlayState(candles: List<PriceCandle>): ChartOverlayState {
    val closes = candles.map { it.close }
    return ChartOverlayState(
        fastTrend = movingAverageSeries(closes, period = 5),
        slowTrend = movingAverageSeries(closes, period = 12),
        biasLine = biasLineSeries(closes)
    )
}

private fun movingAverageSeries(values: List<Double>, period: Int): List<Double> {
    if (values.isEmpty()) return emptyList()
    return values.indices.map { index ->
        val start = (index - period + 1).coerceAtLeast(0)
        values.subList(start, index + 1).average()
    }
}

private fun biasLineSeries(values: List<Double>): List<Double> {
    if (values.size < 2) return values
    val start = values.first()
    val end = values.last()
    return values.indices.map { index ->
        val progress = index.toDouble() / (values.lastIndex.coerceAtLeast(1))
        start + ((end - start) * progress)
    }
}

@Composable
private fun AlertsPanel(
    analysisResult: AnalysisResult?,
    alertHistory: List<SignalAlertEntry>,
    selectedSymbol: TradingSymbol,
    selectedTimeframe: String,
    monitorState: AlertMonitorState,
    onMonitorIntervalChange: (Int) -> Unit,
    onStartMonitoring: () -> Unit,
    onStopMonitoring: () -> Unit,
    onClearAlerts: () -> Unit,
    onPinCurrentSignal: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Pair Monitor", fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                if (monitorState.enabled && monitorState.symbol != null) {
                    "Monitoring ${monitorState.symbol.code} on ${monitorState.timeframe}. The app reconnects and rechecks top-down bias, candle structure, and volatility pulse every ${monitorState.intervalMinutes} minutes while it stays open."
                } else {
                    "Use this screen to monitor one pair over time. The monitor rechecks the live pair on a schedule and pushes alerts when the directional read is non-neutral."
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text("Current watch target: ${selectedSymbol.code} • $selectedTimeframe", fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(6.dp))
            DropdownField(
                label = "Monitor Interval",
                value = "${monitorState.intervalMinutes}m",
                options = AlertMonitorIntervals.map { "${it}m" },
                onSelect = { label -> onMonitorIntervalChange(label.removeSuffix("m").toInt()) },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text("Last scheduled check: ${monitorState.lastCheckLabel}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            analysisResult?.let { result ->
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "Latest read: ${result.setupType.label} • ${result.decision.label}",
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text("Directional bias: ${result.bias.label} • ${result.confidence}%")
                result.mtfaStatus?.let { mtfa ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Macro: ${mtfa.macro}")
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Structure: ${mtfa.structure}")
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Setup: ${mtfa.setup}")
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Trigger: ${mtfa.trigger}")
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text("Entry alert: ${result.tradeSetup.entry?.let(::formatDisplayPrice) ?: "-"}")
                Spacer(modifier = Modifier.height(4.dp))
                Text("Risk line: ${result.tradeSetup.stopLoss?.let(::formatDisplayPrice) ?: "-"}")
                Spacer(modifier = Modifier.height(4.dp))
                Text("Target line: ${result.tradeSetup.takeProfit?.let(::formatDisplayPrice) ?: "-"}")
                Spacer(modifier = Modifier.height(8.dp))
                Text(result.nextTrigger, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = if (monitorState.enabled) onStopMonitoring else onStartMonitoring,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (monitorState.enabled) "Stop Monitor" else "Start Monitor")
                }
                OutlinedButton(
                    onClick = onPinCurrentSignal,
                    modifier = Modifier.weight(1f),
                    enabled = analysisResult != null
                ) {
                    Text("Pin Current")
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedButton(
                onClick = onClearAlerts,
                modifier = Modifier.fillMaxWidth(),
                enabled = alertHistory.isNotEmpty()
            ) {
                Text("Clear History")
            }
        }
    }
    Spacer(modifier = Modifier.height(16.dp))
    if (alertHistory.isEmpty()) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "No stored alerts yet. Run an analysis or pin the current signal to build a history.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    } else {
        alertHistory.forEach { alert ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (alert.approved) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                    } else {
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.88f)
                    },
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(alert.headline, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(alert.timestampLabel, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(alert.body, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(alert.trigger, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun BacktestPanel(
    analysisResult: AnalysisResult?,
    dataQuality: DataQuality,
    selectedSymbol: TradingSymbol,
    closedTrades: List<ClosedTradeRecord>
) {
    val symbolHistory = closedTrades.filter { it.symbolCode == selectedSymbol.code }
    val winRate = if (symbolHistory.isEmpty()) 0 else ((symbolHistory.count { it.pnlPercent >= 0.0 } * 100.0) / symbolHistory.size).toInt()
    val averagePnl = if (symbolHistory.isEmpty()) 0.0 else symbolHistory.map { it.pnlPercent }.average()

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Backtest Snapshot", fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Data source: ${dataQuality.label}",
                color = if (dataQuality == DataQuality.EXCHANGE_OHLC) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            if (analysisResult == null) {
                Text(
                    "Run an analysis first. This panel shows expectancy, sample size, and the last 20 outcomes for the current setup.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                val evidence = analysisResult.evidence
                Text(
                    "Live Estimate",
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "These values are generated from the current loaded market data. They are dynamic, but they are not yet replay-derived closed-trade statistics.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text("Decision Gate: ${analysisResult.decision.label}")
                Spacer(modifier = Modifier.height(4.dp))
                Text("Setup Type: ${analysisResult.setupType.label}")
                Spacer(modifier = Modifier.height(4.dp))
                Text("Expectancy estimate: ${evidence.expectancyR}")
                Spacer(modifier = Modifier.height(4.dp))
                Text("Win rate estimate: ${evidence.winRate}%")
                Spacer(modifier = Modifier.height(4.dp))
                Text("Profit factor estimate: ${evidence.profitFactor}")
                Spacer(modifier = Modifier.height(4.dp))
                Text("Avg R / Median R: ${evidence.avgR} / ${evidence.medianR}")
                Spacer(modifier = Modifier.height(4.dp))
                Text("Max drawdown estimate: ${evidence.maxDrawdownR}")
                Spacer(modifier = Modifier.height(4.dp))
                Text("Synthetic samples: ${evidence.sampleSize}")
                Spacer(modifier = Modifier.height(4.dp))
                Text("Confidence: ${evidence.confidenceBadge}")
                Spacer(modifier = Modifier.height(4.dp))
                Text("Robustness: ${evidence.robustness}")
                Spacer(modifier = Modifier.height(12.dp))
                Text("Last 20 outcomes", fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    evidence.lastOutcomes.joinToString("  ").ifBlank { "No outcomes yet" },
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text("Recorded Trades", fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(8.dp))
            if (symbolHistory.isEmpty()) {
                Text(
                    "No closed ${selectedSymbol.code} trades have been recorded yet.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text("Actual recorded win rate: $winRate%")
                Spacer(modifier = Modifier.height(4.dp))
                Text("Actual average P/L: ${formatPercentSigned(averagePnl)}")
                Spacer(modifier = Modifier.height(8.dp))
                symbolHistory.take(5).forEach { trade ->
                    Text(
                        "${trade.outcomeLabel} • ${formatPercentSigned(trade.pnlPercent)} • ${formatTimestamp(trade.closedAtEpochMillis)}",
                        color = if (trade.pnlPercent >= 0.0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
        }
    }
}

private fun formatDisplayPrice(value: Double): String = "%.5f".format(value)

private fun formatPercentSigned(value: Double): String {
    return if (value >= 0.0) "+${"%.2f".format(value)}%" else "${"%.2f".format(value)}%"
}

private fun formatTimestamp(epochMillis: Long): String {
    return DateTimeFormatter.ofPattern("MMM d, HH:mm")
        .withZone(ZoneId.systemDefault())
        .format(Instant.ofEpochMilli(epochMillis))
}

private fun currentTimeLabel(): String {
    return DateTimeFormatter.ofPattern("HH:mm:ss")
        .withZone(ZoneId.systemDefault())
        .format(Instant.now())
}

@Composable
private fun SettingsPanel(
    selectedMode: ConfirmationMode,
    onModeChange: (ConfirmationMode) -> Unit,
    autoAnalyzeLiveFeed: Boolean,
    onAutoAnalyzeChange: (Boolean) -> Unit,
    keepAlertHistory: Boolean,
    onKeepAlertHistoryChange: (Boolean) -> Unit,
    onDisconnectFeed: () -> Unit,
    connectedFeed: String?
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Execution Guardrails", fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Current mode: ${selectedMode.label}. ${selectedMode.riskProfile}.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Do not place controls inside the top or bottom system zones. The app now keeps those areas visually clear.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Auto-analyze live feed", fontWeight = FontWeight.Medium)
                    Text(
                        "Refresh the signal model as new ticks arrive for the tracked symbol.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp
                    )
                }
                Switch(checked = autoAnalyzeLiveFeed, onCheckedChange = onAutoAnalyzeChange)
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Keep alert history", fontWeight = FontWeight.Medium)
                    Text(
                        "Store non-neutral signals so you can review what the engine approved.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp
                    )
                }
                Switch(checked = keepAlertHistory, onCheckedChange = onKeepAlertHistoryChange)
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text("Mode Presets", fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                ConfirmationMode.values().forEach { mode ->
                    OutlinedButton(
                        onClick = { onModeChange(mode) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (mode == selectedMode) "${mode.label} *" else mode.label)
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "Connected feed: ${connectedFeed ?: "none"}",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(onClick = onDisconnectFeed, modifier = Modifier.fillMaxWidth()) {
                Text("Disconnect Feed")
            }
        }
    }
}

@Composable
private fun FloatingBottomNav(
    selectedTab: AppTab,
    onTabSelected: (AppTab) -> Unit,
    darkTheme: Boolean
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(28.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (darkTheme) {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.97f)
                } else {
                    MaterialTheme.colorScheme.surface.copy(alpha = 0.99f)
                },
                contentColor = MaterialTheme.colorScheme.onSurface
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppTab.values().forEach { tab ->
                    BottomNavItem(
                        tab = tab,
                        selected = tab == selectedTab,
                        onClick = { onTabSelected(tab) }
                    )
                }
            }
        }
    }
}

@Composable
private fun BottomNavItem(
    tab: AppTab,
    selected: Boolean,
    onClick: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.05f else 1f,
        animationSpec = spring(),
        label = "tabScale"
    )

    androidx.compose.material3.Surface(
        onClick = onClick,
        color = Color.Transparent,
        modifier = Modifier.scale(scale),
        shape = RoundedCornerShape(22.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = tab.icon,
                contentDescription = tab.label,
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = tab.label,
                fontSize = 11.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .width(20.dp)
                    .height(2.dp)
                    .background(
                        color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                        shape = RoundedCornerShape(999.dp)
                    )
            )
        }
    }
}
