package com.ex57.capital

import android.os.Bundle
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.outlined.CallSplit
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ex57.capital.ai.AiInsightContext
import com.ex57.capital.ai.AiInsightContextBuilder
import com.ex57.capital.ai.AiInsightQuickAction
import com.ex57.capital.ai.AiInsightResponse
import com.ex57.capital.ai.AiInsightsUiState
import com.ex57.capital.ai.AiInsightsViewModel
import com.ex57.capital.ai.AiProvider
import com.ex57.capital.ai.AiProviderConfig
import com.ex57.capital.analysis.AnalysisStub
import com.ex57.capital.analysis.PositionSizingEngine
import com.ex57.capital.data.PriceViewModel
import com.ex57.capital.model.AnalysisResult
import com.ex57.capital.model.ConfirmationMode
import com.ex57.capital.model.DataQuality
import com.ex57.capital.model.FeedPhase
import com.ex57.capital.model.FeedState
import com.ex57.capital.model.ForecastResearch
import com.ex57.capital.model.ForecastModelMetrics
import com.ex57.capital.model.MarketCandle
import com.ex57.capital.model.PositionRecommendation
import com.ex57.capital.model.SignalFilterSettings
import com.ex57.capital.model.PositionSide
import com.ex57.capital.model.TradePosition
import com.ex57.capital.model.TradeBias
import com.ex57.capital.model.TradeDecision
import com.ex57.capital.model.TradingSymbol
import com.ex57.capital.ui.ControlsPanel
import com.ex57.capital.ui.DropdownField
import com.ex57.capital.ui.SupportedSymbols
import com.ex57.capital.ui.SupportedTimeframes
import com.ex57.capital.ui.formatLot
import com.ex57.capital.ui.theme.AppThemeMode
import com.ex57.capital.ui.theme.EX57Theme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
            val context = LocalContext.current
            val prefs = remember { context.getSharedPreferences("display_settings", Context.MODE_PRIVATE) }
            var themeMode by rememberSaveable {
                mutableStateOf(
                    prefs.getString("theme_mode", null)
                        ?.let { runCatching { AppThemeMode.valueOf(it) }.getOrNull() }
                        ?: AppThemeMode.SYSTEM
                )
            }
            EX57Theme(themeMode = themeMode) {
                EX57App(
                    themeMode = themeMode,
                    onThemeModeChange = { updated ->
                        themeMode = updated
                        prefs.edit().putString("theme_mode", updated.name).apply()
                    }
                )
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
        title = "Gold Signal Home",
        subtitle = "Live XAUUSD setup scoring tuned for session-aware directional reads."
    ),
    CHARTS(
        label = "Charts",
        icon = Icons.AutoMirrored.Outlined.ShowChart,
        title = "Gold Charts",
        subtitle = "Gold-only chart view with timeframe access and live feed context."
    ),
    VALIDATION(
        label = "Validation",
        icon = Icons.Outlined.CallSplit,
        title = "Validation",
        subtitle = "Run walk-forward and deterministic checks on the gold analysis engine."
    ),
    SETTINGS(
        label = "Settings",
        icon = Icons.Outlined.Settings,
        title = "Gold Workspace",
        subtitle = "Adjust filtering, execution discipline, and display behavior for XAUUSD."
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
    val epoch: Long? = null,
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

private enum class ValidationExpectation(val label: String) {
    LEAN_BULLISH("Lean bullish"),
    LEAN_BEARISH("Lean bearish"),
    STAY_DEFENSIVE("Stay defensive")
}

private data class ValidationScenario(
    val name: String,
    val timeframe: String,
    val description: String,
    val expectation: ValidationExpectation,
    val candles: List<MarketCandle>
)

private data class ValidationRunResult(
    val scenarioName: String,
    val mode: ConfirmationMode,
    val expectation: ValidationExpectation,
    val passed: Boolean,
    val actualBias: TradeBias,
    val decision: TradeDecision,
    val confidence: Int,
    val modelBias: TradeBias?,
    val modelConfidence: Int?,
    val passedConfirmations: Int,
    val totalConfirmations: Int,
    val notes: String
)

private data class OneShotValidationResult(
    val mode: ConfirmationMode,
    val timeframe: String,
    val candlesUsed: Int,
    val result: AnalysisResult
)

private enum class PredictionOutcome(val label: String) {
    WIN("Win"),
    LOSS("Loss"),
    NO_HIT("No Hit"),
    NOT_TRIGGERED("Not Triggered")
}

private data class WalkForwardPredictionRow(
    val mode: ConfirmationMode,
    val signalEpoch: Long,
    val signalIndex: Int,
    val bias: TradeBias,
    val decision: TradeDecision,
    val confidence: Int,
    val entry: Double,
    val stopLoss: Double,
    val takeProfit: Double,
    val outcome: PredictionOutcome,
    val outcomeReason: String
)

private data class WalkForwardModeScore(
    val mode: ConfirmationMode,
    val totalSignals: Int,
    val triggeredSignals: Int,
    val wins: Int,
    val losses: Int,
    val noHit: Int,
    val notTriggered: Int,
    val winRateResolved: Int
)

private data class WalkForwardRunResult(
    val datasetSize: Int,
    val horizonCandles: Int,
    val modeScores: List<WalkForwardModeScore>,
    val rows: List<WalkForwardPredictionRow>
)

private data class HomeProbabilityStats(
    val mode: ConfirmationMode,
    val totalSignals: Int,
    val resolvedSignals: Int,
    val wins: Int,
    val losses: Int,
    val pTpBeforeSl: Int,
    val pSlBeforeTp: Int
)

private val AlertMonitorIntervals = listOf(5, 15, 30, 60)
private val SignalFilterSettingsSaver = listSaver<SignalFilterSettings, Boolean>(
    save = {
        listOf(
            it.enforceHardBlocks,
            it.requireForecastSupport,
            it.requireSetupState,
            it.requireSetupConfirmations,
            it.requireConfluence,
            it.requireDirectionalEdge,
            it.requireRiskPenalty,
            it.requireExpectancy
        )
    },
    restore = {
        SignalFilterSettings(
            enforceHardBlocks = it[0],
            requireForecastSupport = it[1],
            requireSetupState = it[2],
            requireSetupConfirmations = it[3],
            requireConfluence = it[4],
            requireDirectionalEdge = it[5],
            requireRiskPenalty = it[6],
            requireExpectancy = it[7]
        )
    }
)

private enum class ChartDensity(val label: String, val candleSpacing: Int) {
    TIGHT("Tight", 10),
    STANDARD("Standard", 14),
    WIDE("Wide", 18)
}

@Composable
fun EX57App(
    themeMode: AppThemeMode,
    onThemeModeChange: (AppThemeMode) -> Unit
) {
    val priceViewModel: PriceViewModel = viewModel()
    val aiInsightsViewModel: AiInsightsViewModel = viewModel()
    val livePrice by priceViewModel.price.collectAsState()
    val recentPrices by priceViewModel.priceHistory.collectAsState()
    val candleHistory by priceViewModel.candleHistory.collectAsState()
    val candleStack by priceViewModel.candleStack.collectAsState()
    val dataQuality by priceViewModel.dataQuality.collectAsState()
    val feedState by priceViewModel.feedState.collectAsState()
    val connectedFeed by priceViewModel.symbol.collectAsState()
    val openPositions by priceViewModel.openPositions.collectAsState()
    val pendingOrders by priceViewModel.pendingOrders.collectAsState()
    val demoBalance by priceViewModel.demoBalance.collectAsState()
    val aiInsightsState by aiInsightsViewModel.uiState.collectAsState()
    val aiProviderConfig by aiInsightsViewModel.config.collectAsState()
    val darkTheme = androidx.compose.foundation.isSystemInDarkTheme()
    var selectedTab by rememberSaveable { mutableStateOf(AppTab.HOME) }
    var selectedSymbol by remember { mutableStateOf(SupportedSymbols.first()) }
    var selectedTimeframe by remember { mutableStateOf(SupportedTimeframes[2]) }
    var selectedMode by rememberSaveable { mutableStateOf(ConfirmationMode.MODERATE) }
    var signalFilterSettings by rememberSaveable(stateSaver = SignalFilterSettingsSaver) { mutableStateOf(SignalFilterSettings()) }
    var analysisResult by remember { mutableStateOf<AnalysisResult?>(null) }
    var riskPercentInput by rememberSaveable { mutableStateOf("1.0") }
    var alertHistory by remember { mutableStateOf<List<SignalAlertEntry>>(emptyList()) }
    var alertMonitor by remember { mutableStateOf(AlertMonitorState()) }
    var isAnalyzingMarket by rememberSaveable { mutableStateOf(false) }
    var autoAnalyzeLiveFeed by rememberSaveable { mutableStateOf(false) }
    var keepAlertHistory by rememberSaveable { mutableStateOf(true) }
    var chartFollowLatest by rememberSaveable { mutableStateOf(true) }
    var chartDensity by rememberSaveable { mutableStateOf(ChartDensity.STANDARD) }
    var chartPinchZoomScale by rememberSaveable { mutableStateOf(1f) }
    val colors = MaterialTheme.colorScheme
    val coroutineScope = rememberCoroutineScope()
    val activeOpenPosition = openPositions.filter {
        it.symbolCode == selectedSymbol.code && it.timeframe == selectedTimeframe
    }.maxByOrNull { it.openedAtEpochMillis }
    val aiInsightContext = remember(
        selectedSymbol,
        selectedTimeframe,
        selectedMode,
        dataQuality,
        feedState,
        candleHistory,
        candleStack,
        analysisResult,
        activeOpenPosition,
        livePrice
    ) {
        AiInsightContextBuilder.build(
            symbol = selectedSymbol,
            timeframe = selectedTimeframe,
            mode = selectedMode,
            dataQuality = dataQuality,
            feedState = feedState,
            candles = candleHistory,
            candleStack = candleStack,
            analysisResult = analysisResult,
            openTrade = activeOpenPosition,
            recentTrades = emptyList(),
            livePrice = livePrice
        )
    }

    LaunchedEffect(
        selectedSymbol.code,
        selectedTimeframe,
        selectedMode,
        analysisResult?.summary,
        analysisResult?.decision,
        activeOpenPosition?.id
    ) {
        aiInsightsViewModel.clear()
    }

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
            signalFilters = signalFilterSettings,
            closedTrades = emptyList(),
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

    fun analyzeMarket(targetSymbol: TradingSymbol = selectedSymbol, source: String = "Manual") {
        isAnalyzingMarket = true
        analysisResult = null
        coroutineScope.launch {
            try {
                priceViewModel.connect(targetSymbol.derivSymbol, selectedTimeframe)
                delay(900)
                priceViewModel.refreshCandles(targetSymbol.derivSymbol, selectedTimeframe)
                delay(1_100)
                runAnalysis(targetSymbol = targetSymbol, recordAlert = true, source = source)
            } finally {
                isAnalyzingMarket = false
            }
        }
    }

    LaunchedEffect(autoAnalyzeLiveFeed, recentPrices, selectedSymbol, selectedTimeframe, selectedMode, signalFilterSettings, connectedFeed, openPositions) {
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

    LaunchedEffect(selectedMode, signalFilterSettings) {
        if (analysisResult != null && candleHistory.isNotEmpty()) {
            runAnalysis(recordAlert = false, source = "Settings")
        }
    }

    LaunchedEffect(selectedSymbol.code) {
        analysisResult = null
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
                    .then(
                        if (selectedTab == AppTab.CHARTS) {
                            Modifier
                        } else {
                            Modifier.verticalScroll(rememberScrollState())
                        }
                    )
                    .padding(padding)
                    .padding(horizontal = if (selectedTab == AppTab.HOME || selectedTab == AppTab.SETTINGS || selectedTab == AppTab.VALIDATION) 16.dp else 0.dp)
                    .padding(bottom = if (selectedTab == AppTab.CHARTS) 0.dp else 12.dp)
            ) {
                if (selectedTab == AppTab.HOME || selectedTab == AppTab.SETTINGS || selectedTab == AppTab.VALIDATION) {
                    FloatingHeader(tab = selectedTab)
                    Spacer(modifier = Modifier.height(16.dp))
                }
                when (selectedTab) {
                    AppTab.HOME -> HomeDashboard(
                        priceViewModel = priceViewModel,
                        livePrice = livePrice,
                        recentPrices = recentPrices,
                        selectedSymbol = selectedSymbol,
                        onSymbolChange = {
                            selectedSymbol = it
                            analysisResult = null
                        },
                        selectedTimeframe = selectedTimeframe,
                        onTimeframeChange = {
                            selectedTimeframe = it
                            analysisResult = null
                        },
                        selectedMode = selectedMode,
                        onModeChange = { selectedMode = it },
                        signalFilters = signalFilterSettings,
                        analysisResult = analysisResult,
                        aiInsightContext = aiInsightContext,
                        aiInsightsState = aiInsightsState,
                        onAiInsightAction = { action ->
                            aiInsightContext?.let { context ->
                                aiInsightsViewModel.requestInsight(action, context)
                            }
                        },
                        onRetryAiInsight = { aiInsightsViewModel.retry() },
                        onClearAiInsight = { aiInsightsViewModel.clear() },
                        openPosition = activeOpenPosition,
                        riskPercentInput = riskPercentInput,
                        onRiskPercentInputChange = { riskPercentInput = it },
                        accountEquityUsd = demoBalance,
                        onAnalyzeMarket = { analyzeMarket(source = "Manual") },
                        isAnalyzingMarket = isAnalyzingMarket,
                        candles = candleHistory,
                        dataQuality = dataQuality,
                        feedState = feedState
                    )
                    AppTab.CHARTS -> ChartsPanel(
                        selectedSymbol = selectedSymbol,
                        selectedTimeframe = selectedTimeframe,
                        onTimeframeChange = {
                            selectedTimeframe = it
                            analysisResult = null
                        },
                        livePrice = livePrice,
                        recentPrices = recentPrices,
                        candles = candleHistory,
                        dataQuality = dataQuality,
                        analysisResult = analysisResult,
                        connectedFeed = connectedFeed,
                        followLatest = chartFollowLatest,
                        onFollowLatestChange = { chartFollowLatest = it },
                        chartDensity = chartDensity,
                        onChartDensityChange = { chartDensity = it },
                        pinchZoomScale = chartPinchZoomScale,
                        onPinchZoomScaleChange = { chartPinchZoomScale = it },
                        onAnalyzeMarket = { analyzeMarket(source = "Chart") },
                        isAnalyzingMarket = isAnalyzingMarket
                    )
                    AppTab.VALIDATION -> ValidationPanel(
                        selectedSymbol = selectedSymbol,
                        selectedTimeframe = selectedTimeframe,
                        candles = candleHistory,
                        signalFilters = signalFilterSettings
                    )
                    AppTab.SETTINGS -> SettingsPanel(
                        selectedMode = selectedMode,
                        onModeChange = { selectedMode = it },
                        signalFilters = signalFilterSettings,
                        onSignalFiltersChange = {
                            signalFilterSettings = it
                        },
                        aiProvider = aiInsightsState.provider,
                        onAiProviderChange = { aiInsightsViewModel.updateProvider(it) },
                        aiProviderConfig = aiProviderConfig,
                        onOpenAiApiKeyChange = { aiInsightsViewModel.updateOpenAiApiKey(it) },
                        onOpenAiModelChange = { aiInsightsViewModel.updateOpenAiModel(it) },
                        onLocalAiBaseUrlChange = { aiInsightsViewModel.updateLocalAiBaseUrl(it) },
                        onLocalAiModelChange = { aiInsightsViewModel.updateLocalAiModel(it) },
                        onOllamaBaseUrlChange = { aiInsightsViewModel.updateOllamaBaseUrl(it) },
                        onOllamaModelChange = { aiInsightsViewModel.updateOllamaModel(it) },
                        themeMode = themeMode,
                        onThemeModeChange = onThemeModeChange,
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
    val fontScale = LocalDensity.current.fontScale
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(top = 8.dp)
    ) {
        Text(
            text = tab.title,
            fontSize = if (fontScale > 1.12f) 28.sp else 34.sp,
            lineHeight = if (fontScale > 1.12f) 31.sp else 38.sp,
            fontWeight = FontWeight.Bold,
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
    feedState: FeedState,
    selectedSymbol: TradingSymbol,
    onSymbolChange: (TradingSymbol) -> Unit,
    selectedTimeframe: String,
    onTimeframeChange: (String) -> Unit,
    selectedMode: ConfirmationMode,
    onModeChange: (ConfirmationMode) -> Unit,
    signalFilters: SignalFilterSettings,
    analysisResult: AnalysisResult?,
    aiInsightContext: AiInsightContext?,
    aiInsightsState: AiInsightsUiState,
    onAiInsightAction: (AiInsightQuickAction) -> Unit,
    onRetryAiInsight: () -> Unit,
    onClearAiInsight: () -> Unit,
    openPosition: TradePosition?,
    riskPercentInput: String,
    onRiskPercentInputChange: (String) -> Unit,
    accountEquityUsd: Double,
    onAnalyzeMarket: () -> Unit,
    isAnalyzingMarket: Boolean
) {
    var showOverview by rememberSaveable { mutableStateOf(false) }
    var showControls by rememberSaveable { mutableStateOf(true) }
    var showHistoryProjection by rememberSaveable { mutableStateOf(false) }
    var showPredictionSummary by rememberSaveable { mutableStateOf(true) }
    var showAiInsights by rememberSaveable { mutableStateOf(false) }
    var showSignalConsole by rememberSaveable { mutableStateOf(false) }
    var homeProbabilityStats by remember { mutableStateOf<HomeProbabilityStats?>(null) }
    var homeProbabilityLoading by remember { mutableStateOf(false) }

    LaunchedEffect(
        analysisResult,
        candles,
        selectedSymbol.code,
        selectedTimeframe,
        selectedMode,
        signalFilters
    ) {
        if (analysisResult == null) {
            homeProbabilityStats = null
            homeProbabilityLoading = false
            return@LaunchedEffect
        }
        homeProbabilityLoading = true
        val stats = withContext(Dispatchers.Default) {
            computeHomeProbabilityStats(
                symbol = selectedSymbol,
                timeframe = selectedTimeframe,
                mode = selectedMode,
                candles = candles,
                signalFilters = signalFilters
            )
        }
        homeProbabilityStats = stats
        homeProbabilityLoading = false
    }

    CollapsibleSection(
        title = "Overview",
        expanded = showOverview,
        onExpandedChange = { showOverview = it }
    ) {
        HeroCard(selectedMode)
    }
    Spacer(modifier = Modifier.height(12.dp))
    CollapsibleSection(
        title = "Controls",
        expanded = showControls,
        onExpandedChange = { showControls = it }
    ) {
        ControlsPanel(
            selectedSymbol = selectedSymbol,
            onSymbolChange = onSymbolChange,
            selectedTimeframe = selectedTimeframe,
            onTimeframeChange = onTimeframeChange,
            selectedMode = selectedMode,
            onModeChange = onModeChange,
            onAnalyzeMarket = onAnalyzeMarket,
            isAnalyzingMarket = isAnalyzingMarket,
            riskPercentInput = riskPercentInput,
            onRiskPercentInputChange = onRiskPercentInputChange
        )
    }
    Spacer(modifier = Modifier.height(12.dp))
    CollapsibleSection(
        title = "Historical + Future",
        expanded = showHistoryProjection,
        onExpandedChange = { showHistoryProjection = it }
    ) {
        HistoricalAndForecastCard(
            candles = candles,
            analysisResult = analysisResult,
            timeframe = selectedTimeframe,
            pricePrecision = selectedSymbol.spec.pricePrecision
        )
    }
    Spacer(modifier = Modifier.height(12.dp))
    analysisResult?.let { result ->
        CollapsibleSection(
            title = "Prediction Summary",
            expanded = showPredictionSummary,
            onExpandedChange = { showPredictionSummary = it }
        ) {
            PredictionSummaryCard(
                analysisResult = result,
                selectedMode = selectedMode,
                candles = candles,
                pricePrecision = selectedSymbol.spec.pricePrecision,
                probabilityStats = homeProbabilityStats,
                probabilityLoading = homeProbabilityLoading
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
    }

    if (analysisResult != null) {
        CollapsibleSection(
            title = "AI Insights",
            expanded = showAiInsights,
            onExpandedChange = { showAiInsights = it }
        ) {
            AiInsightsCard(
                insightContext = aiInsightContext,
                uiState = aiInsightsState,
                onActionSelected = onAiInsightAction,
                onRetry = onRetryAiInsight,
                onClear = onClearAiInsight,
                hasOpenTrade = openPosition != null
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        CollapsibleSection(
            title = "Signal Console",
            expanded = showSignalConsole,
            onExpandedChange = { showSignalConsole = it }
        ) {
            SignalCard(
                priceViewModel = priceViewModel,
                recentPrices = recentPrices,
                candles = candles,
                dataQuality = dataQuality,
                feedState = feedState,
                selectedSymbol = selectedSymbol,
                selectedTimeframe = selectedTimeframe,
                selectedMode = selectedMode,
                signalFilters = signalFilters,
                analysisResult = analysisResult,
                openPosition = openPosition,
                riskPercentInput = riskPercentInput,
                accountEquityUsd = accountEquityUsd
            )
        }
    } else {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("Signal Console and AI Insights are hidden.", fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Tap Analyse Market to generate a fresh signal, then these sections will appear.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun CollapsibleSection(
    title: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    content: @Composable () -> Unit
) {
    val darkTheme = androidx.compose.foundation.isSystemInDarkTheme()
    val headerColor = if (darkTheme) {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.98f)
    } else {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
    }
    Surface(
        onClick = { onExpandedChange(!expanded) },
        shape = RoundedCornerShape(8.dp),
        color = headerColor,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = if (darkTheme) 0.65f else 0.35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 46.dp)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                title,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            IconButton(onClick = { onExpandedChange(!expanded) }) {
                Icon(
                    imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = if (expanded) "Collapse $title" else "Expand $title",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
    if (expanded) {
        Spacer(modifier = Modifier.height(8.dp))
        content()
    }
}

@Composable
private fun HistoricalAndForecastCard(
    candles: List<MarketCandle>,
    analysisResult: AnalysisResult?,
    timeframe: String,
    pricePrecision: Int
) {
    var showRecentOhlc by rememberSaveable { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Historical Context", fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(8.dp))
            if (candles.isEmpty()) {
                Text(
                    "No candle history yet. Connect and run analysis to populate context.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                return@Column
            }

            val recent = candles.takeLast(120)
            val first = recent.firstOrNull()?.open ?: recent.first().close
            val last = recent.last().close
            val movePercent = if (first == 0.0) 0.0 else ((last - first) / first) * 100.0
            val high = recent.maxOf { it.high }
            val low = recent.minOf { it.low }
            val biasColor = when (analysisResult?.bias) {
                TradeBias.BULLISH -> MaterialTheme.colorScheme.primary
                TradeBias.BEARISH -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }

            Text(
                "History (${recent.size} candles @ $timeframe): change ${formatPercentSigned(movePercent)}, range ${formatPrice(low, pricePrecision)} - ${formatPrice(high, pricePrecision)}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Last close: ${formatPrice(last, pricePrecision)}",
                color = biasColor,
                fontSize = 12.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(onClick = { showRecentOhlc = !showRecentOhlc }) {
                Text(if (showRecentOhlc) "Hide Recent OHLC" else "Show Recent OHLC")
            }
            if (showRecentOhlc) {
                Spacer(modifier = Modifier.height(6.dp))
                recent.takeLast(4).forEach { candle ->
                    Text(
                        "O ${formatPrice(candle.open, pricePrecision)} H ${formatPrice(candle.high, pricePrecision)} L ${formatPrice(candle.low, pricePrecision)} C ${formatPrice(candle.close, pricePrecision)}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                }
            }
        }
    }
}

private fun buildProjectedCandles(
    candles: List<MarketCandle>,
    analysisResult: AnalysisResult,
    steps: Int
): List<MarketCandle> {
    if (candles.isEmpty() || steps <= 0) return emptyList()
    val recent = candles.takeLast(60)
    val last = recent.last()
    val avgRange = recent.map { (it.high - it.low).coerceAtLeast(0.00001) }.average()
    val expectedMovePercent = analysisResult.forecastModelMetrics?.expectedMovePercent
        ?: analysisResult.forecastResearch?.expectedMovePercent
        ?: 0.3
    val confidenceScale = (analysisResult.confidence / 100.0).coerceIn(0.2, 1.0)
    val direction = when (analysisResult.bias) {
        TradeBias.BULLISH -> 1.0
        TradeBias.BEARISH -> -1.0
        TradeBias.NEUTRAL -> 0.0
    }
    val neutralDriftScale = if (direction == 0.0) 0.3 else 1.0
    val totalMove = last.close * (expectedMovePercent / 100.0) * confidenceScale * neutralDriftScale * direction
    val perStepMove = totalMove / steps.toDouble()

    val output = ArrayList<MarketCandle>(steps)
    var prevClose = last.close
    var epoch = last.epoch
    repeat(steps) { step ->
        val oscillation = kotlin.math.sin((step + 1) * 0.9) * (avgRange * 0.18)
        val close = (prevClose + perStepMove + oscillation).coerceAtLeast(0.00001)
        val open = prevClose
        val wick = maxOf(avgRange * 0.5, kotlin.math.abs(close - open) * 0.8)
        val high = maxOf(open, close) + (wick * 0.45)
        val low = minOf(open, close) - (wick * 0.45)
        epoch += 60L
        output += MarketCandle(
            epoch = epoch,
            open = open,
            high = high,
            low = low,
            close = close
        )
        prevClose = close
    }
    return output
}

@Composable
private fun PredictionSummaryCard(
    analysisResult: AnalysisResult,
    selectedMode: ConfirmationMode,
    candles: List<MarketCandle>,
    pricePrecision: Int,
    probabilityStats: HomeProbabilityStats?,
    probabilityLoading: Boolean
) {
    val projected = remember(candles, analysisResult) { buildProjectedCandles(candles, analysisResult, steps = 8) }
    val biasColor = when (analysisResult.bias) {
        TradeBias.BULLISH -> MaterialTheme.colorScheme.primary
        TradeBias.BEARISH -> MaterialTheme.colorScheme.error
        TradeBias.NEUTRAL -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Prediction Summary", fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "${analysisResult.bias.label} • ${analysisResult.decision.label} • ${analysisResult.confidence}%",
                color = biasColor,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                analysisResult.summary,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            analysisResult.tradeSetup.entry?.let { entry ->
                Text(
                    "Entry ${formatPrice(entry, pricePrecision)}  |  TP ${analysisResult.tradeSetup.takeProfit?.let { formatPrice(it, pricePrecision) } ?: "-"}  |  SL ${analysisResult.tradeSetup.stopLoss?.let { formatPrice(it, pricePrecision) } ?: "-"}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
            }
            analysisResult.forecastModelMetrics?.let { model ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Model ${model.bias.label} • confidence ${(model.directionConfidence * 100).toInt()}% • dispersion ${(model.forecastDispersion * 100).toInt()}% • expected move ${"%.2f".format(model.expectedMovePercent)}%",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text("Projected Candles (T+1 ... T+8)", fontWeight = FontWeight.Medium, fontSize = 13.sp)
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                projected.forEachIndexed { index, candle ->
                    val prev = if (index == 0) candles.lastOrNull()?.close ?: candle.open else projected[index - 1].close
                    val bullish = candle.close >= prev
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (bullish) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.34f)
                        } else {
                            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.30f)
                        }
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                            Text("T+${index + 1}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                formatPrice(candle.close, pricePrecision),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text("Mode Probability (${selectedMode.label})", fontWeight = FontWeight.Medium, fontSize = 13.sp)
            Spacer(modifier = Modifier.height(4.dp))
            when {
                probabilityLoading -> Text(
                    "Computing recent walk-forward probabilities...",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
                probabilityStats == null -> Text(
                    "Not enough recent data to estimate TP-before-SL probability.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
                else -> {
                    Text(
                        "P(TP before SL): ${probabilityStats.pTpBeforeSl}%   •   P(SL before TP): ${probabilityStats.pSlBeforeTp}%",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        "Signals ${probabilityStats.totalSignals}, resolved ${probabilityStats.resolvedSignals}, wins ${probabilityStats.wins}, losses ${probabilityStats.losses}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                }
            }

            if (analysisResult.decision != TradeDecision.ELIGIBLE) {
                Spacer(modifier = Modifier.height(10.dp))
                Text("Why Not Eligible", fontWeight = FontWeight.Medium, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(4.dp))
                analysisResult.rejectionReasons.take(2).forEach { reason ->
                    Text("• $reason", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(2.dp))
                }
                val keyGates = setOf(
                    "Higher-Timeframe Bias",
                    "Confluence Gate",
                    "Model Direction Agreement",
                    "Model Direction Confidence",
                    "Model Dispersion Check",
                    "Model Target-First Potential"
                )
                val gateRows = analysisResult.confirmations.filter { it.name in keyGates }
                if (gateRows.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        gateRows.forEach { gate ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (gate.passed) {
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.30f)
                                } else {
                                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.34f)
                                }
                            ) {
                                Text(
                                    "${if (gate.passed) "PASS" else "FAIL"} • ${gate.name}",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun computeHomeProbabilityStats(
    symbol: TradingSymbol,
    timeframe: String,
    mode: ConfirmationMode,
    candles: List<MarketCandle>,
    signalFilters: SignalFilterSettings,
    horizonCandles: Int = 18
): HomeProbabilityStats? {
    if (candles.size < 140) return null
    val startIndex = maxOf(100, candles.size - 220)
    val lastSignalIndex = candles.lastIndex - horizonCandles
    if (lastSignalIndex <= startIndex) return null

    var totalSignals = 0
    var wins = 0
    var losses = 0
    for (index in startIndex..lastSignalIndex) {
        val history = candles.subList(0, index + 1)
        val signal = AnalysisStub.analyze(
            symbol = symbol,
            timeframe = timeframe,
            mode = mode,
            candles = history,
            candleStack = mapOf(timeframe to history),
            recentPrices = history.map { it.close },
            signalFilters = signalFilters,
            closedTrades = emptyList(),
            openPosition = null
        )
        val entry = signal.tradeSetup.entry ?: continue
        val stopLoss = signal.tradeSetup.stopLoss ?: continue
        val takeProfit = signal.tradeSetup.takeProfit ?: continue
        if (signal.bias == TradeBias.NEUTRAL || signal.decision == TradeDecision.REJECT) continue

        totalSignals += 1
        val future = candles.subList(index + 1, (index + 1 + horizonCandles).coerceAtMost(candles.size))
        when (evaluateWalkForwardOutcome(signal.bias, entry, stopLoss, takeProfit, future).first) {
            PredictionOutcome.WIN -> wins += 1
            PredictionOutcome.LOSS -> losses += 1
            else -> Unit
        }
    }
    if (totalSignals == 0) return null
    val resolved = wins + losses
    val pTp = if (resolved == 0) 0 else ((wins.toDouble() / resolved) * 100.0).toInt()
    val pSl = if (resolved == 0) 0 else ((losses.toDouble() / resolved) * 100.0).toInt()
    return HomeProbabilityStats(
        mode = mode,
        totalSignals = totalSignals,
        resolvedSignals = resolved,
        wins = wins,
        losses = losses,
        pTpBeforeSl = pTp,
        pSlBeforeTp = pSl
    )
}

@Composable
private fun CompactMetricCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
            contentColor = MaterialTheme.colorScheme.onSurface
        )
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(4.dp))
            Text(value, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
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
private fun AiInsightsCard(
    insightContext: AiInsightContext?,
    uiState: AiInsightsUiState,
    onActionSelected: (AiInsightQuickAction) -> Unit,
    onRetry: () -> Unit,
    onClear: () -> Unit,
    hasOpenTrade: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
            contentColor = MaterialTheme.colorScheme.onSurface
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("AI Insights", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Independent market read from live Deriv data, plus optional engine-aware explanations.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp
                    )
                }
                ModeChip(uiState.provider.label)
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                if (insightContext == null) {
                    "Connect a feed first. Market Deep Dive needs live price plus loaded candles."
                } else {
                    "Market Deep Dive uses independent candle structure for ${insightContext.symbolCode} on ${insightContext.timeframe}. Other actions still use engine context when available."
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp
            )
            Spacer(modifier = Modifier.height(12.dp))
            val actionRows = listOf(
                listOf(
                    AiInsightQuickAction.MARKET_DEEP_DIVE,
                    AiInsightQuickAction.EXPLAIN_SIGNAL,
                    AiInsightQuickAction.EXPLAIN_RISK
                ),
                listOf(
                    AiInsightQuickAction.WHY_NOT_ELIGIBLE,
                    AiInsightQuickAction.SUMMARIZE_TRADE_PLAN
                )
            )
            actionRows.forEachIndexed { rowIndex, actions ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    actions.forEach { action ->
                        val hasMarketContext = insightContext != null && insightContext.market.primaryCandleCount >= 20
                        val hasEngineContext = insightContext?.signal != null
                        val enabled = when (action) {
                            AiInsightQuickAction.MARKET_DEEP_DIVE ->
                                hasMarketContext && !uiState.isLoading
                            AiInsightQuickAction.MANAGE_OPEN_TRADE ->
                                hasEngineContext && hasOpenTrade && !uiState.isLoading
                            else ->
                                hasEngineContext && !uiState.isLoading
                        }
                        OutlinedButton(
                            onClick = { onActionSelected(action) },
                            enabled = enabled,
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)
                        ) {
                            Text(
                                action.label,
                                fontSize = 11.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    repeat((3 - actions.size).coerceAtLeast(0)) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
                if (rowIndex != actionRows.lastIndex) {
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            when {
                uiState.isLoading -> {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text(
                            "Generating ${uiState.activeAction?.label?.lowercase() ?: "insight"}...",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp
                        )
                    }
                }
                uiState.errorMessage != null -> {
                    Text(
                        uiState.errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onRetry) {
                            Text("Retry")
                        }
                        TextButton(onClick = onClear) {
                            Text("Clear")
                        }
                    }
                }
                uiState.response != null -> {
                    AiInsightResponseView(
                        response = uiState.response,
                        onRetry = onRetry,
                        onClear = onClear
                    )
                }
            }
        }
    }
}

@Composable
private fun AiInsightResponseView(
    response: AiInsightResponse,
    onRetry: () -> Unit,
    onClear: () -> Unit
) {
    androidx.compose.material3.Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(response.title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Text(
                    if (response.cached) "Cached" else response.provider.label,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(response.summary, fontSize = 13.sp)
            Spacer(modifier = Modifier.height(10.dp))
            response.bullets.forEach { bullet ->
                Text(
                    "• $bullet",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                response.caution,
                color = MaterialTheme.colorScheme.secondary,
                fontSize = 11.sp
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onRetry) {
                    Text("Retry")
                }
                TextButton(onClick = onClear) {
                    Text("Clear")
                }
            }
        }
    }
}

@Composable
private fun SignalCard(
    priceViewModel: PriceViewModel,
    recentPrices: List<Double>,
    candles: List<MarketCandle>,
    dataQuality: DataQuality,
    feedState: FeedState,
    selectedSymbol: TradingSymbol,
    selectedTimeframe: String,
    selectedMode: ConfirmationMode,
    signalFilters: SignalFilterSettings,
    analysisResult: AnalysisResult?,
    openPosition: TradePosition? = null,
    riskPercentInput: String,
    accountEquityUsd: Double
) {
    val price by priceViewModel.price.collectAsState()
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
            Text(
                text = "Status: ${feedState.message}",
                color = when (feedState.phase) {
                    FeedPhase.LIVE -> MaterialTheme.colorScheme.primary
                    FeedPhase.STALE, FeedPhase.ERROR -> MaterialTheme.colorScheme.error
                    FeedPhase.RECONNECTING, FeedPhase.WAITING_FOR_DATA -> MaterialTheme.colorScheme.secondary
                    else -> MaterialTheme.colorScheme.onSurface
                }
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text("Feed: ${symbol ?: "-"}")
            Spacer(modifier = Modifier.height(6.dp))
            Text("Configured: ${selectedSymbol.code} • $selectedTimeframe • ${selectedMode.label}")
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                signalFilters.summary(),
                color = if (signalFilters.hasOverrides()) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp
            )
            if (signalFilters.hasOverrides()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Disabled gates stop blocking entry approval, even though the raw confirmation rows below still show current market weakness.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text("Live Price: ${price?.let { "%.5f".format(it) } ?: "-"}")
            Spacer(modifier = Modifier.height(6.dp))
            Text("Samples: ${recentPrices.size}/240")
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                "Last tick: ${feedState.lastTickEpochMillis?.let(::formatTimestampLabel) ?: "No live tick yet"}",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                "Data: ${dataQuality.label} ${if (dataQuality == DataQuality.EXCHANGE_OHLC) "OK" else "WARN"}",
                color = when (dataQuality) {
                    DataQuality.EXCHANGE_OHLC -> MaterialTheme.colorScheme.primary
                    DataQuality.CACHED -> MaterialTheme.colorScheme.secondary
                    DataQuality.ESTIMATED -> MaterialTheme.colorScheme.secondary
                }
            )
            Spacer(modifier = Modifier.height(12.dp))

            if (analysisResult == null) {
                Text(
                    "Flow: pick a pair, tap Analyse Market, then review the fresh signal, chart, and risk-sized plan.",
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
                analysisResult.forecastResearch?.let { research ->
                    ForecastResearchCard(research)
                    Spacer(modifier = Modifier.height(12.dp))
                }
                analysisResult.forecastModelMetrics?.let { modelMetrics ->
                    ForecastModelCard(modelMetrics)
                    Spacer(modifier = Modifier.height(12.dp))
                }
                ProcessFlowCard(
                    analysisResult = analysisResult,
                    selectedSymbol = selectedSymbol
                )
                Spacer(modifier = Modifier.height(12.dp))
                TradePlanCard(
                    symbol = selectedSymbol,
                    analysisResult = analysisResult,
                    biasColor = biasColor,
                    openPosition = openPosition,
                    riskPercentInput = riskPercentInput,
                    accountEquityUsd = accountEquityUsd
                )
                Spacer(modifier = Modifier.height(12.dp))
                if (analysisResult.rejectionReasons.isNotEmpty()) {
                    RejectionReasonsCard(analysisResult.rejectionReasons)
                    Spacer(modifier = Modifier.height(12.dp))
                }
                EvidenceCard(
                    analysisResult = analysisResult
                )
                Spacer(modifier = Modifier.height(12.dp))
                ConfirmationAnalysisCard(
                    analysisResult = analysisResult,
                    signalFilters = signalFilters
                )
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
private fun ForecastResearchCard(
    research: ForecastResearch
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Forecast Lane", fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Bias: ${research.bias.label}",
                color = when (research.bias) {
                    TradeBias.BULLISH -> MaterialTheme.colorScheme.primary
                    TradeBias.BEARISH -> MaterialTheme.colorScheme.error
                    TradeBias.NEUTRAL -> MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text("Strength: ${(research.strengthScore * 100.0).toInt()}%")
            Spacer(modifier = Modifier.height(4.dp))
            Text("Stability: ${(research.stabilityScore * 100.0).toInt()}%")
            Spacer(modifier = Modifier.height(4.dp))
            Text("Expected move: ${"%.2f".format(research.expectedMovePercent)}%")
            Spacer(modifier = Modifier.height(8.dp))
            Text(research.summary, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ForecastModelCard(
    modelMetrics: ForecastModelMetrics
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Sequence Model", fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Bias: ${modelMetrics.bias.label} (${modelMetrics.modelName})",
                color = when (modelMetrics.bias) {
                    TradeBias.BULLISH -> MaterialTheme.colorScheme.primary
                    TradeBias.BEARISH -> MaterialTheme.colorScheme.error
                    TradeBias.NEUTRAL -> MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text("Bullish probability: ${(modelMetrics.bullishProbability * 100.0).toInt()}%")
            Spacer(modifier = Modifier.height(4.dp))
            Text("Bearish probability: ${(modelMetrics.bearishProbability * 100.0).toInt()}%")
            Spacer(modifier = Modifier.height(4.dp))
            Text("Direction confidence: ${(modelMetrics.directionConfidence * 100.0).toInt()}%")
            Spacer(modifier = Modifier.height(4.dp))
            Text("Forecast dispersion: ${(modelMetrics.forecastDispersion * 100.0).toInt()}%")
            Spacer(modifier = Modifier.height(4.dp))
            Text("Target-before-stop: ${(modelMetrics.targetBeforeStopScore * 100.0).toInt()}%")
            Spacer(modifier = Modifier.height(4.dp))
            Text("Expected move: ${"%.2f".format(modelMetrics.expectedMovePercent)}%")
            Spacer(modifier = Modifier.height(8.dp))
            Text(modelMetrics.summary, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun confirmationBypassReason(
    confirmationName: String,
    signalFilters: SignalFilterSettings
): String? {
    return when {
        confirmationName in setOf("Noise Filter", "News Pulse Filter") && !signalFilters.enforceHardBlocks ->
            "hard blocks disabled in Settings"
        confirmationName in setOf("Higher-Timeframe Bias", "Multi-TF Consensus") && !signalFilters.requireSetupState ->
            "setup-state gate disabled in Settings"
        confirmationName == "Confluence Gate" && !signalFilters.requireConfluence ->
            "confluence gate disabled in Settings"
        confirmationName in setOf(
            "Model Direction Agreement",
            "Model Direction Confidence",
            "Model Dispersion Check",
            "Model Target-First Potential"
        ) && !signalFilters.requireForecastSupport ->
            "forecast gate disabled in Settings"
        confirmationName in setOf(
            "Trend Structure",
            "Momentum",
            "Market Structure",
            "Pullback Quality",
            "Breakout Pressure",
            "Zone Context",
            "Liquidity Map",
            "Imbalance (FVG)",
            "Timeframe Fit",
            "Candlestick Engine",
            "ICC Phase"
        ) && !signalFilters.requireSetupConfirmations ->
            "setup-count gate disabled in Settings"
        else -> null
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
                    Button(
                        onClick = { onAnalyzeSymbol(symbol) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Analyse Market")
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
private fun ChartsPanel(
    selectedSymbol: TradingSymbol,
    selectedTimeframe: String,
    onTimeframeChange: (String) -> Unit,
    livePrice: Double?,
    recentPrices: List<Double>,
    candles: List<MarketCandle>,
    dataQuality: DataQuality,
    analysisResult: AnalysisResult?,
    connectedFeed: String?,
    followLatest: Boolean,
    onFollowLatestChange: (Boolean) -> Unit,
    chartDensity: ChartDensity,
    onChartDensityChange: (ChartDensity) -> Unit,
    pinchZoomScale: Float,
    onPinchZoomScaleChange: (Float) -> Unit,
    onAnalyzeMarket: () -> Unit,
    isAnalyzingMarket: Boolean
) {
    val chartCandles = remember(candles, recentPrices, selectedTimeframe) {
        if (candles.isNotEmpty()) {
            candles.takeLast(480).map {
                PriceCandle(
                    epoch = it.epoch,
                    open = it.open,
                    high = it.high,
                    low = it.low,
                    close = it.close
                )
            }
        } else {
            buildCandles(recentPrices, selectedTimeframe)
        }
    }
    val overlays = remember(chartCandles) { buildChartOverlayState(chartCandles) }
    val effectiveCandleSpacing = (chartDensity.candleSpacing.dp * pinchZoomScale).coerceIn(4.dp, 72.dp)
    val priceRange = remember(chartCandles) {
        val high = chartCandles.maxOfOrNull { it.high }
        val low = chartCandles.minOfOrNull { it.low }
        if (high == null || low == null) null else high to low
    }
    val isLiveFeed = connectedFeed == selectedSymbol.derivSymbol
    Surface(
        color = Color(0xFF000000),
        contentColor = Color.White,
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 8.dp, bottom = 4.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("${selectedSymbol.code} · $selectedTimeframe", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(selectedSymbol.label, color = Color(0xFF6B7280), fontSize = 11.sp)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    LiveStateChip(active = isLiveFeed)
                    Text(livePrice?.let(::formatDisplayPrice) ?: "-", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    ChartActionButton(
                        icon = Icons.Outlined.Refresh,
                        label = if (isAnalyzingMarket) "Analysing market" else "Analyse market",
                        tint = Color(0xFFFFD60A),
                        onClick = onAnalyzeMarket
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf("1m", "5m", "15m", "30m", "1h", "4h").forEach { timeframe ->
                    androidx.compose.material3.Surface(
                        onClick = { onTimeframeChange(timeframe) },
                        color = if (selectedTimeframe == timeframe) Color(0xFF1F2937) else Color.Transparent,
                        contentColor = if (selectedTimeframe == timeframe) Color.White else Color(0xFF9CA3AF),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text(
                            timeframe.uppercase(),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                "${chartCandles.size} candles • drag for older candles • pinch or use zoom ${"%.1f".format(pinchZoomScale)}x",
                    color = Color(0xFF6B7280),
                    fontSize = 11.sp
                )
                ToggleTerminalChip(
                    label = if (followLatest) "Auto" else "Manual",
                    active = followLatest,
                    onClick = { onFollowLatestChange(!followLatest) }
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ChartDensity.values().forEach { density ->
                    ToggleTerminalChip(
                        label = density.label,
                        active = chartDensity == density,
                        onClick = { onChartDensityChange(density) }
                    )
                }
                ToggleTerminalChip(
                    label = "Zoom -",
                    active = false,
                    onClick = { onPinchZoomScaleChange((pinchZoomScale * 0.82f).coerceIn(0.45f, 3.5f)) }
                )
                ToggleTerminalChip(
                    label = "Zoom +",
                    active = false,
                    onClick = { onPinchZoomScaleChange((pinchZoomScale * 1.22f).coerceIn(0.45f, 3.5f)) }
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 0.dp, vertical = 4.dp)
            ) {
                if (chartCandles.size > 1) {
                    PriceChart(
                        candles = chartCandles,
                        overlays = overlays,
                        analysisResult = analysisResult,
                        showFastTrend = true,
                        showSlowTrend = true,
                        showBiasLine = true,
                        followLatest = followLatest,
                        candleSpacing = effectiveCandleSpacing,
                        onPinchZoom = { zoomChange ->
                            onPinchZoomScaleChange((pinchZoomScale * zoomChange).coerceIn(0.45f, 3.5f))
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF05070B)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Connect feed and analyze to load chart.", color = Color(0xFF9CA3AF), fontSize = 13.sp)
                    }
                }
            }
            ChartTimeAxis(candles = chartCandles)
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ChartStatPill(
                    label = "High",
                    value = priceRange?.first?.let(::formatDisplayPrice) ?: "-",
                    modifier = Modifier.weight(1f)
                )
                ChartStatPill(
                    label = "Low",
                    value = priceRange?.second?.let(::formatDisplayPrice) ?: "-",
                    modifier = Modifier.weight(1f)
                )
                ChartStatPill(
                    label = "Range",
                    value = priceRange?.let { (high, low) -> formatPercent(((high - low) / low.coerceAtLeast(0.00001)) * 100.0) } ?: "-",
                    modifier = Modifier.weight(1f)
                )
            }
            Text(
                "${selectedSymbol.label} • ${dataQuality.label} • ${if (followLatest) "latest candle follows live feed" else "manual scroll locked"}",
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                color = Color(0xFF6B7280),
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun LiveStateChip(active: Boolean) {
    androidx.compose.material3.Surface(
        color = if (active) Color(0xFF0F2A25) else Color(0xFF111827),
        contentColor = if (active) Color(0xFF5EEAD4) else Color(0xFF9CA3AF),
        shape = RoundedCornerShape(999.dp)
    ) {
        Text(
            if (active) "Live" else "Idle",
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun ChartActionButton(
    icon: ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit
) {
    androidx.compose.material3.Surface(
        onClick = onClick,
        color = Color(0xFF111827),
        contentColor = tint,
        shape = RoundedCornerShape(12.dp)
    ) {
        Box(
            modifier = Modifier.size(38.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = tint,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun ToggleTerminalChip(
    label: String,
    active: Boolean,
    onClick: () -> Unit
) {
    androidx.compose.material3.Surface(
        onClick = onClick,
        color = if (active) Color(0xFF1F2937) else Color(0xFF0B1220),
        contentColor = if (active) Color.White else Color(0xFF9CA3AF),
        shape = RoundedCornerShape(999.dp)
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun ChartTimeAxis(candles: List<PriceCandle>) {
    if (candles.isEmpty()) return
    val start = formatChartEpochLabel(candles.firstOrNull()?.epoch, fallback = "Older")
    val middle = formatChartEpochLabel(candles.getOrNull(candles.lastIndex / 2)?.epoch, fallback = "Mid")
    val end = formatChartEpochLabel(candles.lastOrNull()?.epoch, fallback = "Latest")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(start, color = Color(0xFF6B7280), fontSize = 10.sp)
        Text(middle, color = Color(0xFF6B7280), fontSize = 10.sp)
        Text(end, color = Color(0xFF6B7280), fontSize = 10.sp)
    }
}

@Composable
private fun ChartNavigatorStrip(
    candles: List<PriceCandle>,
    bias: TradeBias,
    modifier: Modifier = Modifier
) {
    val closes = remember(candles) { candles.map { it.close } }
    val lineColor = when (bias) {
        TradeBias.BULLISH -> Color(0xFF5EEAD4)
        TradeBias.BEARISH -> Color(0xFFF87171)
        TradeBias.NEUTRAL -> Color(0xFF93C5FD)
    }
    androidx.compose.material3.Surface(
        modifier = modifier,
        color = Color(0xFF05070B),
        contentColor = Color.White,
        shape = RoundedCornerShape(14.dp)
    ) {
        if (closes.size < 2) {
            Text(
                "Navigator appears once enough candles are loaded.",
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                color = Color(0xFF6B7280),
                fontSize = 11.sp
            )
        } else {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                val maxClose = closes.maxOrNull() ?: return@Canvas
                val minClose = closes.minOrNull() ?: return@Canvas
                val range = (maxClose - minClose).coerceAtLeast(0.00001)
                closes.zipWithNext().forEachIndexed { index, (start, end) ->
                    val startX = (index.toFloat() / (closes.lastIndex.coerceAtLeast(1))) * size.width
                    val endX = ((index + 1).toFloat() / (closes.lastIndex.coerceAtLeast(1))) * size.width
                    val startY = ((maxClose - start) / range).toFloat() * size.height
                    val endY = ((maxClose - end) / range).toFloat() * size.height
                    drawLine(
                        color = lineColor,
                        start = Offset(startX, startY),
                        end = Offset(endX, endY),
                        strokeWidth = 2.2f
                    )
                }
                drawLine(
                    color = Color.White.copy(alpha = 0.35f),
                    start = Offset(size.width, 0f),
                    end = Offset(size.width, size.height),
                    strokeWidth = 2f
                )
            }
        }
    }
}

@Composable
private fun ChartStatPill(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    androidx.compose.material3.Surface(
        color = Color(0xFF0B1220),
        contentColor = Color.White,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(label, color = Color(0xFF6B7280), fontSize = 10.sp)
            Spacer(modifier = Modifier.height(2.dp))
            Text(value, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
        }
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
private fun ConfirmationAnalysisCard(
    analysisResult: AnalysisResult,
    signalFilters: SignalFilterSettings
) {
    var expanded by rememberSaveable(analysisResult.summary) { mutableStateOf(false) }
    val validPassCount = analysisResult.confirmations.count {
        it.passed && confirmationBypassReason(it.name, signalFilters) == null
    }
    val bypassCount = analysisResult.confirmations.count {
        confirmationBypassReason(it.name, signalFilters) != null
    }
    val waitCount = analysisResult.confirmations.size - validPassCount - bypassCount

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Pass / Wait Analysis", fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "$validPassCount valid pass • $waitCount wait • $bypassCount bypassed",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                }
                OutlinedButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) "Hide" else "More Explanation")
                }
            }
            if (expanded) {
                Spacer(modifier = Modifier.height(12.dp))
                analysisResult.confirmations.forEachIndexed { index, confirmation ->
                    val bypassReason = confirmationBypassReason(
                        confirmationName = confirmation.name,
                        signalFilters = signalFilters
                    )
                    val bypassed = bypassReason != null
                    val statusLabel = when {
                        bypassed -> "BYPASSED"
                        confirmation.passed -> "VALID PASS"
                        else -> "NOT VALID YET"
                    }
                    val statusColor = when {
                        bypassed -> MaterialTheme.colorScheme.secondary
                        confirmation.passed -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                "$statusLabel • ${confirmation.name}",
                                color = statusColor,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                if (bypassed) {
                                    "This check is not currently allowed to block the strategy because $bypassReason. Raw read: ${confirmation.details}"
                                } else if (confirmation.passed) {
                                    "Valid because the current market data satisfies this check: ${confirmation.details}"
                                } else {
                                    "Not valid yet because the current market data does not satisfy this check: ${confirmation.details}"
                                },
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp,
                                lineHeight = 17.sp
                            )
                        }
                    }
                    if (index != analysisResult.confirmations.lastIndex) {
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun RejectionReasonsCard(reasons: List<String>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Decision Reasons", fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(8.dp))
            reasons.forEach { reason ->
                Text(reason, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(6.dp))
            }
        }
    }
}

@Composable
private fun TradePlanCard(
    symbol: TradingSymbol,
    analysisResult: AnalysisResult,
    biasColor: Color,
    openPosition: TradePosition?,
    riskPercentInput: String,
    accountEquityUsd: Double
) {
    val tradeSetup = analysisResult.tradeSetup
    val riskPercent = riskPercentInput.toDoubleOrNull() ?: 1.0
    val sizingQuote = tradeSetup.entry?.let { entry ->
        PositionSizingEngine.quoteRiskBased(
            symbol = symbol,
            entryPrice = entry,
            stopLoss = tradeSetup.stopLoss,
            accountEquityUsd = accountEquityUsd,
            riskPercent = riskPercent
        )
    }
    val hasOpenPosition = openPosition != null

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
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Sizing is informational only. It uses account equity, stop distance, contract value, and broker lot rules.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            sizingQuote?.let { quote ->
                Text(
                    "Risk model: ${"%.2f".format(riskPercent)}% of ${formatCurrency(accountEquityUsd)} -> estimated risk ${quote.estimatedRiskUsd?.let(::formatCurrency) ?: "-"}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Suggested size: ${formatLot(quote.normalizedLotSize)} lots • Stop distance: ${quote.stopDistancePercent?.let(::formatPercent) ?: "-"} • Notional: ${formatCurrency(quote.notionalUsd)}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Used margin: ${formatCurrency(quote.usedMarginUsd)} • Spread cost: ${formatCurrency(quote.estimatedSpreadCostUsd)} • Lot rules: min ${formatLot(symbol.spec.minLot)}, step ${formatLot(symbol.spec.lotStep)}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
            }
            if (hasOpenPosition) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "A saved position record exists for this symbol. It is shown as context only; signal generation no longer creates orders.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun ActivePositionCard(
    position: TradePosition,
    livePrice: Double?,
    analysisResult: AnalysisResult?
) {
    val pnlPercent = positionPnlPercent(position, livePrice)
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
            Text("${position.symbolCode} • ${position.side.label} ${formatLot(position.lotSize)} • ${position.timeframe}")
            Spacer(modifier = Modifier.height(4.dp))
            Text("Opened: ${formatTimestamp(position.openedAtEpochMillis)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(4.dp))
            Text("Entry: ${formatDisplayPrice(position.entryPrice)}")
            Spacer(modifier = Modifier.height(4.dp))
            Text("Live: ${livePrice?.let(::formatDisplayPrice) ?: "-"}")
            Spacer(modifier = Modifier.height(4.dp))
            Text("Remaining size: ${formatLot(position.lotSize)} / ${formatLot(position.initialLotSize)} lots")
            Spacer(modifier = Modifier.height(4.dp))
            Text("Stop: ${position.stopLoss?.let(::formatDisplayPrice) ?: "-"}")
            Spacer(modifier = Modifier.height(4.dp))
            Text("Target: ${position.takeProfit?.let(::formatDisplayPrice) ?: "-"}")
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Unrealized: ${positionPnlUsd(position, livePrice)?.let(::formatCurrencySigned) ?: "-"} ${pnlPercent?.let { "(${formatPercentSigned(it)})" } ?: ""}",
                color = when {
                    pnlPercent == null -> MaterialTheme.colorScheme.onSurfaceVariant
                    pnlPercent >= 0 -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.error
                }
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Locked profit: ${formatCurrencySigned(position.realizedPnlUsd)} • Management stage ${position.managementStage}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Used margin: ${formatCurrency(position.usedMarginUsd)} • Spread cost: ${formatCurrency(position.estimatedSpreadCostUsd)}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp
            )
            guidance?.let {
                Spacer(modifier = Modifier.height(8.dp))
                Text(it.headline, color = recommendationColor, fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.height(4.dp))
                Text(it.detail, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                "Manage exits and partial closes from Trades.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun PriceChart(
    candles: List<PriceCandle>,
    overlays: ChartOverlayState,
    analysisResult: AnalysisResult?,
    showFastTrend: Boolean,
    showSlowTrend: Boolean,
    showBiasLine: Boolean,
    followLatest: Boolean = true,
    candleSpacing: androidx.compose.ui.unit.Dp = 14.dp,
    onPinchZoom: ((Float) -> Unit)? = null,
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
    val tradeSetup = analysisResult?.tradeSetup
    val candleTop = candles.maxOfOrNull { it.high } ?: return
    val candleBottom = candles.minOfOrNull { it.low } ?: return
    val candleRange = (candleTop - candleBottom).coerceAtLeast(0.00001)
    val visibleTradeLevels = listOfNotNull(
        tradeSetup?.entry,
        tradeSetup?.stopLoss,
        tradeSetup?.takeProfit
    ).filter { level ->
        level in (candleBottom - (candleRange * 0.45))..(candleTop + (candleRange * 0.45))
    }
    val rawTop = maxOf(candleTop, visibleTradeLevels.maxOrNull() ?: candleTop)
    val rawBottom = minOf(candleBottom, visibleTradeLevels.minOrNull() ?: candleBottom)
    val paddedRange = (rawTop - rawBottom).coerceAtLeast(candleRange)
    val topPadding = paddedRange * 0.28
    val bottomPadding = paddedRange * 0.18
    val topValue = rawTop + topPadding
    val bottomValue = rawBottom - bottomPadding
    val range = (topValue - bottomValue).coerceAtLeast(0.00001)

    BoxWithConstraints(modifier = modifier) {
        val scrollState = rememberScrollState()
        val density = LocalDensity.current
        val chartWidth = maxOf(maxWidth, candleSpacing * candles.size.coerceAtLeast(1))
        val firstEpoch = candles.firstOrNull()?.epoch
        val lastEpoch = candles.lastOrNull()?.epoch

        LaunchedEffect(firstEpoch, lastEpoch, candles.size, followLatest) {
            if (followLatest) {
                scrollState.scrollTo(scrollState.maxValue)
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(candles.size) {
                    awaitEachGesture {
                        var keepTracking = true
                        while (keepTracking) {
                            val event = awaitPointerEvent()
                            if (event.changes.count { it.pressed } > 1) {
                                val zoomChange = event.calculateZoom()
                                if (zoomChange != 1f) {
                                    onPinchZoom?.invoke(zoomChange)
                                    event.changes.forEach { change -> change.consume() }
                                }
                            }
                            keepTracking = event.changes.any { it.pressed }
                        }
                    }
                }
                .horizontalScroll(scrollState)
        ) {
            Canvas(
                modifier = Modifier
                    .width(chartWidth)
                    .fillMaxHeight()
            ) {
                val requestedCandleWidth = with(density) { candleSpacing.toPx() }
                val candleWidth = maxOf(requestedCandleWidth, size.width / candles.size.coerceAtLeast(1))
                val bodyWidth = candleWidth * 0.58f
                val topInset = size.height * 0.16f
                val bottomInset = size.height * 0.10f
                val drawableHeight = (size.height - topInset - bottomInset).coerceAtLeast(1f)

                fun yFor(value: Double): Float {
                    val normalized = ((topValue - value) / range).toFloat().coerceIn(0f, 1f)
                    return topInset + (normalized * drawableHeight)
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

                if (showFastTrend) {
                    drawSeries(overlays.fastTrend, fastLineColor, 3f)
                }
                if (showSlowTrend) {
                    drawSeries(overlays.slowTrend, slowLineColor, 3.4f)
                }
                if (showBiasLine) {
                    drawSeries(overlays.biasLine, biasLineColor, 2.6f)
                }
                drawLevel(tradeSetup?.entry, entryLineColor)
                drawLevel(tradeSetup?.stopLoss, stopLineColor)
                drawLevel(tradeSetup?.takeProfit, targetLineColor)

                drawRect(
                    color = outlineColor,
                    topLeft = Offset.Zero,
                    size = size,
                    style = Stroke(width = 2f)
                )
            }
        }
    }
}

private fun buildCandles(prices: List<Double>, timeframe: String): List<PriceCandle> {
    if (prices.size < 4) return emptyList()

    val targetCandles = 240
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
                epoch = null,
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
                Text("Clear Alerts")
            }
        }
    }
    Spacer(modifier = Modifier.height(16.dp))
    if (alertHistory.isEmpty()) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "No stored alerts yet. Run an analysis or pin the current signal to keep recent signal notes.",
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
private fun TradesPanel(
    openPositions: List<TradePosition>,
    pendingOrders: List<com.ex57.capital.model.PendingTradeOrder>,
    demoBalance: Double,
    livePrice: Double?,
    connectedFeed: String?,
    resolveLatestKnownPrice: (TradePosition) -> Double?,
    onTrackPosition: (TradePosition) -> Unit,
    onChartPosition: (TradePosition) -> Unit,
    onPartialClosePosition: (TradePosition) -> Unit,
    onClosePosition: (TradePosition) -> Unit,
    onCancelPendingOrder: (String) -> Unit
) {
    val openPnlUsd = openPositions.sumOf { position ->
        val currentPrice = if (position.derivSymbol == connectedFeed) {
            livePrice ?: resolveLatestKnownPrice(position)
        } else {
            resolveLatestKnownPrice(position)
        }
        positionPnlUsd(position, currentPrice) ?: 0.0
    }
    val equity = demoBalance + openPnlUsd
    val usedMargin = openPositions.sumOf {
        if (it.usedMarginUsd > 0.0) it.usedMarginUsd else it.stakeUsd * 0.10
    }
    val freeMargin = equity - usedMargin
    val marginLevel = if (usedMargin <= 0.0) null else (equity / usedMargin) * 100.0
    var closeCandidate by remember { mutableStateOf<TradePosition?>(null) }

    closeCandidate?.let { position ->
        ConfirmCloseTradeDialog(
            position = position,
            onDismiss = { closeCandidate = null },
            onConfirm = {
                onClosePosition(position)
                closeCandidate = null
            }
        )
    }

    Surface(
        color = Color(0xFF06080C),
        contentColor = Color(0xFFF3F4F6),
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 12.dp)) {
            Text(
                formatCurrencySigned(openPnlUsd),
                color = if (openPnlUsd >= 0.0) Color(0xFF3B82F6) else Color(0xFFEF4444),
                fontWeight = FontWeight.SemiBold,
                fontSize = 22.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            TerminalMetricRow("Balance:", formatCurrency(demoBalance))
            TerminalMetricRow("Equity:", formatCurrency(equity))
            TerminalMetricRow("Margin:", formatCurrency(usedMargin))
            TerminalMetricRow("Free margin:", formatCurrency(freeMargin))
            TerminalMetricRow(
                "Margin Level (%):",
                marginLevel?.let { "%.2f".format(it) } ?: "-"
            )
            Spacer(modifier = Modifier.height(6.dp))
            if (pendingOrders.isNotEmpty()) {
                TradeSectionBar("Orders")
            }
            pendingOrders.take(8).forEach { order ->
                PendingOrderRow(order = order, onCancel = { onCancelPendingOrder(order.id) })
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(Color(0xFF1F2937))
                )
            }
            if (openPositions.isNotEmpty()) {
                TradeSectionBar("Positions")
            }
            openPositions.take(15).forEachIndexed { index, position ->
                val currentPrice = if (position.derivSymbol == connectedFeed) {
                    livePrice ?: resolveLatestKnownPrice(position)
                } else {
                    resolveLatestKnownPrice(position)
                }
                TradeSwipeRow(
                    position = position,
                    currentPrice = currentPrice,
                    isTracked = position.derivSymbol == connectedFeed,
                    onTrackPosition = { onTrackPosition(position) },
                    onChartPosition = { onChartPosition(position) },
                    onPartialClosePosition = { onPartialClosePosition(position) },
                    onClosePosition = { closeCandidate = position }
                )
                if (index != minOf(openPositions.size, 15) - 1) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(Color(0xFF1F2937))
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TradeSwipeRow(
    position: TradePosition,
    currentPrice: Double?,
    isTracked: Boolean,
    onTrackPosition: () -> Unit,
    onChartPosition: () -> Unit,
    onPartialClosePosition: () -> Unit,
    onClosePosition: () -> Unit
) {
    val pnlPercent = positionPnlPercent(position, currentPrice)
    val pnlUsd = positionPnlUsd(position, currentPrice)
    val scope = rememberCoroutineScope()
    val dismissState = rememberSwipeToDismissBoxState(
        positionalThreshold = { distance -> distance * 0.30f }
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 88.dp)
                    .background(Color(0xFF0B1220)),
                horizontalArrangement = Arrangement.End
            ) {
                TradeActionStrip(
                    icon = Icons.Outlined.Visibility,
                    label = if (isTracked) "Live" else "Track",
                    background = Color(0xFF1D4ED8),
                    onClick = {
                        onTrackPosition()
                        scope.launch { dismissState.reset() }
                    }
                )
                TradeActionStrip(
                    icon = Icons.AutoMirrored.Outlined.ShowChart,
                    label = "Chart",
                    background = Color(0xFF0F766E),
                    onClick = {
                        onChartPosition()
                        scope.launch { dismissState.reset() }
                    }
                )
                TradeActionStrip(
                    icon = Icons.Outlined.CallSplit,
                    label = "Part",
                    background = Color(0xFF7C3AED),
                    onClick = {
                        onPartialClosePosition()
                        scope.launch { dismissState.reset() }
                    }
                )
                TradeActionStrip(
                    icon = Icons.Outlined.Close,
                    label = "Close",
                    background = Color(0xFFB91C1C),
                    onClick = {
                        onClosePosition()
                        scope.launch { dismissState.reset() }
                    }
                )
            }
        }
    ) {
        Surface(
            color = Color(0xFF05070B),
            contentColor = Color(0xFFF9FAFB),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "${position.symbolCode}, ${position.side.name.lowercase()} ${formatLot(position.lotSize)}",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            "${formatDisplayPrice(position.entryPrice)} -> ${currentPrice?.let(::formatDisplayPrice) ?: "-"}",
                            color = Color(0xFF9CA3AF),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            pnlUsd?.let(::formatCurrencySigned) ?: "--",
                            color = when {
                                pnlUsd == null -> Color(0xFF9CA3AF)
                                pnlUsd >= 0.0 -> Color(0xFF3B82F6)
                                else -> Color(0xFFEF4444)
                            },
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp
                        )
                        if (pnlPercent != null) {
                            Text(
                                formatPercentSigned(pnlPercent),
                                color = if (pnlPercent >= 0.0) Color(0xFF60A5FA) else Color(0xFFF87171),
                                fontSize = 10.sp
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "${position.timeframe} • Opened ${formatTimestamp(position.openedAtEpochMillis)}",
                    color = Color(0xFF9CA3AF),
                    fontSize = 10.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    "Locked ${formatCurrencySigned(position.realizedPnlUsd)} • Remaining ${formatLot(position.lotSize)} / ${formatLot(position.initialLotSize)} lots • Stage ${position.managementStage}",
                    color = Color(0xFF6B7280),
                    fontSize = 9.sp
                )
            }
        }
    }
}

@Composable
private fun TradeActionStrip(
    icon: ImageVector,
    label: String,
    background: Color,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = background,
        contentColor = Color.White,
        modifier = Modifier
            .width(80.dp)
            .defaultMinSize(minHeight = 88.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(22.dp))
            Spacer(modifier = Modifier.height(4.dp))
            Text(label, fontWeight = FontWeight.Medium, fontSize = 10.sp)
        }
    }
}

@Composable
private fun PendingOrderRow(
    order: com.ex57.capital.model.PendingTradeOrder,
    onCancel: () -> Unit
) {
    Surface(
        color = Color(0xFF05070B),
        contentColor = Color(0xFFF9FAFB),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "${order.symbolCode}, ${if (order.side == PositionSide.LONG) "buy limit" else "sell limit"} ${formatLot(order.lotSize)}",
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    "at ${formatDisplayPrice(order.targetEntryPrice)}",
                    color = Color(0xFF9CA3AF),
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    "S/L ${order.stopLoss?.let(::formatDisplayPrice) ?: "-"} • T/P ${order.takeProfit?.let(::formatDisplayPrice) ?: "-"}",
                    color = Color(0xFF6B7280),
                    fontSize = 10.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    "Margin ${formatCurrency(order.usedMarginUsd)} • Spread ${formatCurrency(order.estimatedSpreadCostUsd)}",
                    color = Color(0xFF6B7280),
                    fontSize = 10.sp
                )
            }
            OutlinedButton(onClick = onCancel) {
                Text("Cancel")
            }
        }
    }
}

@Composable
private fun TradeSectionBar(title: String) {
    Surface(
        color = Color(0xFF30343A),
        contentColor = Color(0xFFD1D5DB),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text("•••", color = Color(0xFF9CA3AF), fontSize = 16.sp)
        }
    }
}

@Composable
private fun ConfirmCloseTradeDialog(
    position: TradePosition,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = Color(0xFF2B2F36),
            contentColor = Color.White
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text("Close Trade", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    "Are you sure you want to close ${position.symbolCode} ${position.side.name.lowercase()} ${formatLot(position.lotSize)}?",
                    color = Color(0xFFD1D5DB),
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "This closes the saved position record using the current available price.",
                    color = Color(0xFF9CA3AF),
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                        Text("Cancel")
                    }
                    Button(onClick = onConfirm, modifier = Modifier.weight(1f)) {
                        Text("Close")
                    }
                }
            }
        }
    }
}

private fun formatDisplayPrice(value: Double): String = "%.5f".format(value)

@Composable
private fun SummaryMetricRow(
    label: String,
    value: String,
    dark: Boolean = true
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            color = if (dark) MaterialTheme.colorScheme.onSurfaceVariant else Color(0xFF6B7280)
        )
        Text(
            value,
            color = if (dark) MaterialTheme.colorScheme.onSurface else Color(0xFF111827),
            fontWeight = FontWeight.Medium
        )
    }
    Spacer(modifier = Modifier.height(4.dp))
}

@Composable
private fun TerminalMetricRow(
    label: String,
    value: String,
    accent: Color = Color.White
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = Color(0xFFD1D5DB), fontWeight = FontWeight.Medium)
        Text(value, color = accent, fontWeight = FontWeight.Bold)
    }
    Spacer(modifier = Modifier.height(6.dp))
}

private fun positionPnlPercent(position: TradePosition, currentPrice: Double?): Double? {
    currentPrice ?: return null
    val rawPercent = when (position.side) {
        PositionSide.LONG -> ((currentPrice - position.entryPrice) / position.entryPrice) * 100.0
        PositionSide.SHORT -> ((position.entryPrice - currentPrice) / position.entryPrice) * 100.0
    }
    return if (position.stakeUsd <= 0.0) {
        rawPercent
    } else {
        (((position.stakeUsd * (rawPercent / 100.0)) - position.estimatedSpreadCostUsd) / position.stakeUsd) * 100.0
    }
}

private fun positionPnlUsd(position: TradePosition, currentPrice: Double?): Double? {
    val pnlPercent = positionPnlPercent(position, currentPrice) ?: return null
    return position.stakeUsd * (pnlPercent / 100.0)
}

private fun formatCurrency(value: Double): String = "$${"%.2f".format(value)}"

private fun formatPrice(value: Double, precision: Int): String {
    return "%.${precision}f".format(value)
}

private fun formatCurrencySigned(value: Double): String {
    return if (value >= 0.0) "+$${"%.2f".format(value)}" else "-$${"%.2f".format(kotlin.math.abs(value))}"
}

private fun formatPercent(value: Double): String = "${"%.2f".format(value)}%"

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

private fun formatChartEpochLabel(epochSeconds: Long?, fallback: String): String {
    epochSeconds ?: return fallback
    return DateTimeFormatter.ofPattern("dd MMM HH:mm")
        .withZone(ZoneId.systemDefault())
        .format(Instant.ofEpochSecond(epochSeconds))
}

@Composable
private fun ValidationPanel(
    selectedSymbol: TradingSymbol,
    selectedTimeframe: String,
    candles: List<MarketCandle>,
    signalFilters: SignalFilterSettings
) {
    val darkTheme = androidx.compose.foundation.isSystemInDarkTheme()
    val primaryTextColor = if (darkTheme) Color(0xFFF3F4F6) else MaterialTheme.colorScheme.onSurface
    val secondaryTextColor = if (darkTheme) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
    val scope = rememberCoroutineScope()
    var selectedMode by rememberSaveable { mutableStateOf(ConfirmationMode.MODERATE) }
    var runningOneShot by remember { mutableStateOf(false) }
    var runningWalkForward by remember { mutableStateOf(false) }
    var runningScenarioSuite by remember { mutableStateOf(false) }
    var oneShotResult by remember { mutableStateOf<OneShotValidationResult?>(null) }
    var walkForwardResult by remember { mutableStateOf<WalkForwardRunResult?>(null) }
    var selectedWalkForwardRow by remember { mutableStateOf<WalkForwardPredictionRow?>(null) }
    var lastWalkForwardRunLabel by remember { mutableStateOf("Not run yet") }
    var walkForwardRunCounter by remember { mutableStateOf(0) }
    var scenarioResults by remember { mutableStateOf<List<ValidationRunResult>>(emptyList()) }
    val scenarioTotal = scenarioResults.size
    val scenarioPassed = scenarioResults.count { it.passed }
    val recentPrices = remember(candles) { candles.map { it.close } }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("In-App Analysis Validation", fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Validate one-by-one on current data and run walk-forward on recent history using AnalysisStub.",
                color = secondaryTextColor
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                "Symbol: ${selectedSymbol.code} • TF: $selectedTimeframe • Candles: ${candles.size}",
                color = secondaryTextColor,
                fontSize = 12.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                "Using current signal gates: ${signalFilters.summary(maxItems = 4)}",
                color = secondaryTextColor,
                fontSize = 12.sp
            )
            Spacer(modifier = Modifier.height(12.dp))
            DropdownField(
                label = "Validation Mode",
                value = selectedMode.label,
                options = ConfirmationMode.entries.map { it.label },
                onSelect = { label ->
                    selectedMode = ConfirmationMode.entries.first { it.label == label }
                },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = {
                    scope.launch {
                        runningOneShot = true
                        walkForwardResult = null
                        selectedWalkForwardRow = null
                        scenarioResults = emptyList()
                        oneShotResult = null
                        val oneShot = withContext(Dispatchers.Default) {
                            OneShotValidationResult(
                                mode = selectedMode,
                                timeframe = selectedTimeframe,
                                candlesUsed = candles.size,
                                result = AnalysisStub.analyze(
                                    symbol = selectedSymbol,
                                    timeframe = selectedTimeframe,
                                    mode = selectedMode,
                                    candles = candles,
                                    candleStack = mapOf(selectedTimeframe to candles),
                                    recentPrices = recentPrices,
                                    signalFilters = signalFilters,
                                    closedTrades = emptyList(),
                                    openPosition = null
                                )
                            )
                        }
                        oneShotResult = oneShot
                        runningOneShot = false
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !runningOneShot && candles.size >= 50
            ) {
                if (runningOneShot) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Running One-By-One Check")
                } else {
                    Text("Run One On Current Data")
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Quick check: runs one analysis on the latest loaded candles for the selected mode.",
                color = secondaryTextColor,
                fontSize = 12.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    scope.launch {
                        runningWalkForward = true
                        oneShotResult = null
                        scenarioResults = emptyList()
                        walkForwardResult = null
                        selectedWalkForwardRow = null
                        val walkForward = withContext(Dispatchers.Default) {
                            runWalkForwardValidation(
                                symbol = selectedSymbol,
                                timeframe = selectedTimeframe,
                                candles = candles,
                                signalFilters = signalFilters
                            )
                        }
                        walkForwardResult = walkForward
                        selectedWalkForwardRow = walkForward.rows.firstOrNull()
                        lastWalkForwardRunLabel = currentTimeLabel()
                        walkForwardRunCounter += 1
                        runningWalkForward = false
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !runningWalkForward && candles.size >= 140
            ) {
                if (runningWalkForward) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Running Walk-Forward")
                } else {
                    Text("Run Walk-Forward (All Modes)")
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Historical test: replays past-only analysis, then checks forward candles for TP/SL order. Scorecard shows resolved performance per mode.",
                color = secondaryTextColor,
                fontSize = 12.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    scope.launch {
                        runningScenarioSuite = true
                        oneShotResult = null
                        walkForwardResult = null
                        selectedWalkForwardRow = null
                        scenarioResults = emptyList()
                        val results = withContext(Dispatchers.Default) {
                            runValidationSuite(
                                symbol = selectedSymbol,
                                signalFilters = signalFilters
                            )
                        }
                        scenarioResults = results
                        runningScenarioSuite = false
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !runningScenarioSuite
            ) {
                Text("Run Synthetic Scenario Suite")
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Deterministic stress checks on synthetic trend/range/noise scenarios.",
                color = secondaryTextColor,
                fontSize = 12.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(
                onClick = {
                    oneShotResult = null
                    walkForwardResult = null
                    selectedWalkForwardRow = null
                    scenarioResults = emptyList()
                    lastWalkForwardRunLabel = "Not run yet"
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Clear Validation Output")
            }
            if (scenarioResults.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "Synthetic suite: $scenarioPassed / $scenarioTotal checks passed",
                    color = if (scenarioPassed == scenarioTotal) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }

    if (oneShotResult != null) {
        Spacer(modifier = Modifier.height(12.dp))
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("One-By-One Result (${oneShotResult!!.mode.label})", fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "Bias: ${oneShotResult!!.result.bias.label} | Decision: ${oneShotResult!!.result.decision.label} | Confidence: ${oneShotResult!!.result.confidence}%",
                    color = secondaryTextColor
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    oneShotResult!!.result.summary,
                    color = secondaryTextColor,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Next trigger: ${oneShotResult!!.result.nextTrigger}",
                    color = secondaryTextColor,
                    fontSize = 12.sp
                )
                oneShotResult!!.result.rejectionReasons.firstOrNull()?.let { reason ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Top blocker: $reason",
                        color = secondaryTextColor,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }

    if (walkForwardResult != null) {
        Spacer(modifier = Modifier.height(12.dp))
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("Walk-Forward Scorecard", fontWeight = FontWeight.SemiBold, color = primaryTextColor)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "Run #$walkForwardRunCounter • Last run: $lastWalkForwardRunLabel • Dataset: ${walkForwardResult!!.datasetSize} candles • Horizon: ${walkForwardResult!!.horizonCandles} candles",
                    color = secondaryTextColor,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                walkForwardResult!!.modeScores.forEach { score ->
                    Text(
                        "${score.mode.label}: signals ${score.totalSignals}, triggered ${score.triggeredSignals}, W ${score.wins}, L ${score.losses}, no-hit ${score.noHit}, not-triggered ${score.notTriggered}, resolved WR ${score.winRateResolved}%",
                        color = secondaryTextColor,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
        }
        if (walkForwardResult!!.rows.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            Text("Predictions (tap to inspect)", fontWeight = FontWeight.SemiBold, color = primaryTextColor)
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                "Each row is one historical signal evaluated over the walk-forward horizon.",
                color = secondaryTextColor,
                fontSize = 12.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            walkForwardResult!!.rows.take(40).forEach { row ->
                Surface(
                    onClick = { selectedWalkForwardRow = row },
                    shape = RoundedCornerShape(8.dp),
                    tonalElevation = if (selectedWalkForwardRow == row) 2.dp else 0.dp,
                    color = when (row.outcome) {
                        PredictionOutcome.WIN -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f)
                        PredictionOutcome.LOSS -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.28f)
                        else -> MaterialTheme.colorScheme.surface
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            "${row.mode.label} • ${row.bias.label} • ${row.outcome.label} • ${row.decision.label}",
                            fontWeight = FontWeight.Medium,
                            fontSize = 13.sp,
                            color = primaryTextColor
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            "Signal @ ${formatChartEpochLabel(row.signalEpoch, fallback = row.signalIndex.toString())} • Conf ${row.confidence}%",
                            color = secondaryTextColor,
                            fontSize = 12.sp
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
            }
            selectedWalkForwardRow?.let { row ->
                Spacer(modifier = Modifier.height(10.dp))
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Prediction Detail", fontWeight = FontWeight.SemiBold, color = primaryTextColor)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "${row.mode.label} • ${row.bias.label} • ${row.outcome.label}",
                            color = secondaryTextColor,
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Entry ${"%.5f".format(row.entry)} | SL ${"%.5f".format(row.stopLoss)} | TP ${"%.5f".format(row.takeProfit)}",
                            color = secondaryTextColor,
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            row.outcomeReason,
                            color = secondaryTextColor,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }

    if (scenarioResults.isNotEmpty()) {
        Spacer(modifier = Modifier.height(12.dp))
        scenarioResults.forEach { row ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (row.passed) {
                        MaterialTheme.colorScheme.surface
                    } else {
                        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.24f)
                    }
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        "${if (row.passed) "PASS" else "FAIL"} • ${row.scenarioName} • ${row.mode.label}",
                        fontWeight = FontWeight.SemiBold,
                        color = if (row.passed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Expected: ${row.expectation.label} | Actual: ${row.actualBias.label}, ${row.decision.label}, ${row.confidence}%",
                        color = secondaryTextColor,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Model: ${row.modelBias?.label ?: "-"} (${row.modelConfidence?.let { "$it%" } ?: "-"})",
                        color = secondaryTextColor,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Confirmations: ${row.passedConfirmations}/${row.totalConfirmations}",
                        color = secondaryTextColor,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(row.notes, color = secondaryTextColor, fontSize = 12.sp)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

private fun runWalkForwardValidation(
    symbol: TradingSymbol,
    timeframe: String,
    candles: List<MarketCandle>,
    signalFilters: SignalFilterSettings,
    horizonCandles: Int = 18
): WalkForwardRunResult {
    if (candles.size < 140) {
        return WalkForwardRunResult(
            datasetSize = candles.size,
            horizonCandles = horizonCandles,
            modeScores = ConfirmationMode.entries.map {
                WalkForwardModeScore(it, 0, 0, 0, 0, 0, 0, 0)
            },
            rows = emptyList()
        )
    }
    val startIndex = 100
    val lastSignalIndex = candles.lastIndex - horizonCandles
    if (lastSignalIndex <= startIndex) {
        return WalkForwardRunResult(
            datasetSize = candles.size,
            horizonCandles = horizonCandles,
            modeScores = ConfirmationMode.entries.map {
                WalkForwardModeScore(it, 0, 0, 0, 0, 0, 0, 0)
            },
            rows = emptyList()
        )
    }

    val rows = mutableListOf<WalkForwardPredictionRow>()
    ConfirmationMode.entries.forEach { mode ->
        for (index in startIndex..lastSignalIndex) {
            val history = candles.subList(0, index + 1)
            val signalResult = AnalysisStub.analyze(
                symbol = symbol,
                timeframe = timeframe,
                mode = mode,
                candles = history,
                candleStack = mapOf(timeframe to history),
                recentPrices = history.map { it.close },
                signalFilters = signalFilters,
                closedTrades = emptyList(),
                openPosition = null
            )
            val entry = signalResult.tradeSetup.entry
            val stopLoss = signalResult.tradeSetup.stopLoss
            val takeProfit = signalResult.tradeSetup.takeProfit
            if (signalResult.bias == TradeBias.NEUTRAL || signalResult.decision == TradeDecision.REJECT) continue
            if (entry == null || stopLoss == null || takeProfit == null) continue

            val future = candles.subList(index + 1, (index + 1 + horizonCandles).coerceAtMost(candles.size))
            val outcome = evaluateWalkForwardOutcome(
                bias = signalResult.bias,
                entry = entry,
                stopLoss = stopLoss,
                takeProfit = takeProfit,
                future = future
            )
            rows += WalkForwardPredictionRow(
                mode = mode,
                signalEpoch = history.last().epoch,
                signalIndex = index,
                bias = signalResult.bias,
                decision = signalResult.decision,
                confidence = signalResult.confidence,
                entry = entry,
                stopLoss = stopLoss,
                takeProfit = takeProfit,
                outcome = outcome.first,
                outcomeReason = outcome.second
            )
        }
    }

    val modeScores = ConfirmationMode.entries.map { mode ->
        val modeRows = rows.filter { it.mode == mode }
        val wins = modeRows.count { it.outcome == PredictionOutcome.WIN }
        val losses = modeRows.count { it.outcome == PredictionOutcome.LOSS }
        val noHit = modeRows.count { it.outcome == PredictionOutcome.NO_HIT }
        val notTriggered = modeRows.count { it.outcome == PredictionOutcome.NOT_TRIGGERED }
        val triggered = wins + losses + noHit
        val resolved = wins + losses
        val winRate = if (resolved == 0) 0 else ((wins.toDouble() / resolved) * 100.0).toInt()
        WalkForwardModeScore(
            mode = mode,
            totalSignals = modeRows.size,
            triggeredSignals = triggered,
            wins = wins,
            losses = losses,
            noHit = noHit,
            notTriggered = notTriggered,
            winRateResolved = winRate
        )
    }

    return WalkForwardRunResult(
        datasetSize = candles.size,
        horizonCandles = horizonCandles,
        modeScores = modeScores,
        rows = rows.sortedByDescending { it.signalEpoch }
    )
}

private fun evaluateWalkForwardOutcome(
    bias: TradeBias,
    entry: Double,
    stopLoss: Double,
    takeProfit: Double,
    future: List<MarketCandle>
): Pair<PredictionOutcome, String> {
    val triggerIndex = future.indexOfFirst { candle -> candle.low <= entry && candle.high >= entry }
    if (triggerIndex < 0) {
        return PredictionOutcome.NOT_TRIGGERED to "Entry was never touched within the evaluation horizon."
    }
    for (index in triggerIndex until future.size) {
        val candle = future[index]
        val stopHit = candle.low <= stopLoss && candle.high >= stopLoss
        val targetHit = candle.low <= takeProfit && candle.high >= takeProfit
        if (stopHit && targetHit) {
            return PredictionOutcome.LOSS to "Stop and target were both touched in the same candle; counted as loss."
        }
        when (bias) {
            TradeBias.BULLISH -> {
                if (targetHit) return PredictionOutcome.WIN to "Target was hit before stop."
                if (stopHit) return PredictionOutcome.LOSS to "Stop was hit before target."
            }
            TradeBias.BEARISH -> {
                if (targetHit) return PredictionOutcome.WIN to "Target was hit before stop."
                if (stopHit) return PredictionOutcome.LOSS to "Stop was hit before target."
            }
            TradeBias.NEUTRAL -> return PredictionOutcome.NO_HIT to "Neutral bias has no directional outcome."
        }
    }
    return PredictionOutcome.NO_HIT to "Entry was triggered, but neither target nor stop was reached in horizon."
}

private fun runValidationSuite(
    symbol: TradingSymbol,
    signalFilters: SignalFilterSettings
): List<ValidationRunResult> {
    val scenarios = buildValidationScenarios()
    val runs = mutableListOf<ValidationRunResult>()
    ConfirmationMode.entries.forEach { mode ->
        scenarios.forEach { scenario ->
            val result = AnalysisStub.analyze(
                symbol = symbol,
                timeframe = scenario.timeframe,
                mode = mode,
                candles = scenario.candles,
                candleStack = mapOf(scenario.timeframe to scenario.candles),
                recentPrices = scenario.candles.map { it.close },
                signalFilters = signalFilters,
                closedTrades = emptyList(),
                openPosition = null
            )
            val passedConfirmations = result.confirmations.count { it.passed }
            val pass = validationPassesExpectation(scenario.expectation, mode, result)
            val reason = if (pass) {
                "Expectation met."
            } else {
                result.rejectionReasons.firstOrNull() ?: "Expectation failed with no explicit rejection reason."
            }
            runs += ValidationRunResult(
                scenarioName = scenario.name,
                mode = mode,
                expectation = scenario.expectation,
                passed = pass,
                actualBias = result.bias,
                decision = result.decision,
                confidence = result.confidence,
                modelBias = result.forecastModelMetrics?.bias,
                modelConfidence = result.forecastModelMetrics?.let { (it.directionConfidence * 100.0).toInt() },
                passedConfirmations = passedConfirmations,
                totalConfirmations = result.confirmations.size,
                notes = "${scenario.description} $reason"
            )
        }
    }
    return runs
}

private fun validationPassesExpectation(
    expectation: ValidationExpectation,
    mode: ConfirmationMode,
    result: AnalysisResult
): Boolean {
    val strictMode = mode == ConfirmationMode.CONSERVATIVE || mode == ConfirmationMode.MODERATE
    val modelConfidence = result.forecastModelMetrics?.directionConfidence ?: 0.0
    return when (expectation) {
        ValidationExpectation.LEAN_BULLISH -> {
            if (strictMode) {
                result.bias == TradeBias.BULLISH &&
                    result.decision != TradeDecision.REJECT &&
                    modelConfidence >= 0.35
            } else {
                result.bias != TradeBias.BEARISH &&
                    result.decision != TradeDecision.REJECT
            }
        }
        ValidationExpectation.LEAN_BEARISH -> {
            if (strictMode) {
                result.bias == TradeBias.BEARISH &&
                    result.decision != TradeDecision.REJECT &&
                    modelConfidence >= 0.35
            } else {
                result.bias != TradeBias.BULLISH &&
                    result.decision != TradeDecision.REJECT
            }
        }
        ValidationExpectation.STAY_DEFENSIVE -> {
            if (strictMode) {
                result.decision == TradeDecision.REJECT || result.bias == TradeBias.NEUTRAL
            } else {
                result.decision != TradeDecision.ELIGIBLE &&
                    (result.bias == TradeBias.NEUTRAL || modelConfidence < 0.55)
            }
        }
    }
}

private fun buildValidationScenarios(): List<ValidationScenario> {
    return listOf(
        ValidationScenario(
            name = "Strong Uptrend",
            timeframe = "15m",
            description = "Rising structure with pullbacks should not lean bearish.",
            expectation = ValidationExpectation.LEAN_BULLISH,
            candles = syntheticCandles(
                count = 280,
                start = 100.0,
                drift = 0.28,
                wave = 0.22,
                noise = 0.05
            )
        ),
        ValidationScenario(
            name = "Strong Downtrend",
            timeframe = "15m",
            description = "Falling structure with weak rebounds should not lean bullish.",
            expectation = ValidationExpectation.LEAN_BEARISH,
            candles = syntheticCandles(
                count = 280,
                start = 100.0,
                drift = -0.28,
                wave = 0.22,
                noise = 0.05
            )
        ),
        ValidationScenario(
            name = "Range Compression",
            timeframe = "15m",
            description = "Flat range should avoid immediate eligibility until breakout structure appears.",
            expectation = ValidationExpectation.STAY_DEFENSIVE,
            candles = syntheticCandles(
                count = 280,
                start = 100.0,
                drift = 0.0,
                wave = 0.08,
                noise = 0.03
            )
        ),
        ValidationScenario(
            name = "Noisy Whipsaw",
            timeframe = "15m",
            description = "High-noise alternating swings should remain defensive.",
            expectation = ValidationExpectation.STAY_DEFENSIVE,
            candles = syntheticCandles(
                count = 280,
                start = 100.0,
                drift = 0.0,
                wave = 0.55,
                noise = 0.35
            )
        ),
        ValidationScenario(
            name = "Late Trend Exhaustion",
            timeframe = "15m",
            description = "Steep move followed by unstable reversal pressure should remain defensive in strict modes.",
            expectation = ValidationExpectation.STAY_DEFENSIVE,
            candles = syntheticCandlesWithExhaustion(
                count = 280,
                start = 100.0
            )
        )
    )
}

private fun syntheticCandles(
    count: Int,
    start: Double,
    drift: Double,
    wave: Double,
    noise: Double
): List<MarketCandle> {
    val candles = ArrayList<MarketCandle>(count)
    var price = start
    val startEpoch = 1_700_000_000L
    repeat(count) { index ->
        val cycle = kotlin.math.sin(index / 8.0) * wave
        val jagged = kotlin.math.sin(index * 1.9) * noise
        val step = drift + cycle + jagged
        val close = (price + step).coerceAtLeast(0.1)
        val open = price
        val wick = (kotlin.math.abs(step) * 0.35).coerceAtLeast(0.04)
        val high = maxOf(open, close) + wick
        val low = minOf(open, close) - wick
        candles += MarketCandle(
            epoch = startEpoch + (index * 60L),
            open = open,
            high = high,
            low = low,
            close = close
        )
        price = close
    }
    return candles
}

private fun syntheticCandlesWithExhaustion(
    count: Int,
    start: Double
): List<MarketCandle> {
    val candles = ArrayList<MarketCandle>(count)
    var price = start
    val startEpoch = 1_700_200_000L
    repeat(count) { index ->
        val drift = when {
            index < (count * 0.55).toInt() -> 0.34
            index < (count * 0.75).toInt() -> 0.10
            else -> -0.22
        }
        val noise = when {
            index < (count * 0.55).toInt() -> 0.03
            index < (count * 0.75).toInt() -> 0.18
            else -> 0.32
        }
        val cycle = kotlin.math.sin(index / 5.0) * 0.24
        val jagged = kotlin.math.sin(index * 2.3) * noise
        val step = drift + cycle + jagged
        val close = (price + step).coerceAtLeast(0.1)
        val open = price
        val wick = (kotlin.math.abs(step) * 0.45).coerceAtLeast(0.05)
        val high = maxOf(open, close) + wick
        val low = minOf(open, close) - wick
        candles += MarketCandle(
            epoch = startEpoch + (index * 60L),
            open = open,
            high = high,
            low = low,
            close = close
        )
        price = close
    }
    return candles
}

@Composable
private fun SettingsPanel(
    selectedMode: ConfirmationMode,
    onModeChange: (ConfirmationMode) -> Unit,
    signalFilters: SignalFilterSettings,
    onSignalFiltersChange: (SignalFilterSettings) -> Unit,
    aiProvider: AiProvider,
    onAiProviderChange: (AiProvider) -> Unit,
    aiProviderConfig: AiProviderConfig,
    onOpenAiApiKeyChange: (String) -> Unit,
    onOpenAiModelChange: (String) -> Unit,
    onLocalAiBaseUrlChange: (String) -> Unit,
    onLocalAiModelChange: (String) -> Unit,
    onOllamaBaseUrlChange: (String) -> Unit,
    onOllamaModelChange: (String) -> Unit,
    themeMode: AppThemeMode,
    onThemeModeChange: (AppThemeMode) -> Unit,
    autoAnalyzeLiveFeed: Boolean,
    onAutoAnalyzeChange: (Boolean) -> Unit,
    keepAlertHistory: Boolean,
    onKeepAlertHistoryChange: (Boolean) -> Unit,
    onDisconnectFeed: () -> Unit,
    connectedFeed: String?
) {
    val compactLayout = LocalDensity.current.fontScale > 1.08f
    val modeRows = remember { ConfirmationMode.values().toList().chunked(2) }
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
                signalFilters.summary(),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text("Mode Presets", fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(8.dp))
            modeRows.forEachIndexed { index, rowModes ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    rowModes.forEach { mode ->
                        OutlinedButton(
                            onClick = { onModeChange(mode) },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 12.dp)
                        ) {
                            Text(
                                text = if (mode == selectedMode) "${mode.label} *" else mode.label,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontSize = 13.sp
                            )
                        }
                    }
                    if (rowModes.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
                if (index != modeRows.lastIndex) {
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text("Display", fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(8.dp))
            DropdownField(
                label = "Theme",
                value = themeMode.label,
                options = AppThemeMode.values().map { it.label },
                onSelect = { label ->
                    onThemeModeChange(AppThemeMode.values().first { it.label == label })
                },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text("AI Provider", fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(8.dp))
            DropdownField(
                label = "Provider",
                value = aiProvider.label,
                options = AiProvider.values().map { it.label },
                onSelect = { label ->
                    onAiProviderChange(AiProvider.values().first { it.label == label })
                },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                "Free local options: Ollama, LocalAI, or an OpenAI-compatible local server such as LM Studio. Android emulator uses 10.0.2.2 to reach your computer. Mock remains the offline fallback.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = if (compactLayout) 12.sp else 13.sp,
                lineHeight = if (compactLayout) 16.sp else 18.sp
            )
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedTextField(
                value = aiProviderConfig.localAiBaseUrl,
                onValueChange = onLocalAiBaseUrlChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("LocalAI Base URL") },
                singleLine = true
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = aiProviderConfig.localAiModel,
                onValueChange = onLocalAiModelChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("LocalAI Model") },
                singleLine = true
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = aiProviderConfig.ollamaBaseUrl,
                onValueChange = onOllamaBaseUrlChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Ollama Base URL") },
                singleLine = true
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = aiProviderConfig.ollamaModel,
                onValueChange = onOllamaModelChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Ollama Model") },
                singleLine = true
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = aiProviderConfig.openAiApiKey.orEmpty(),
                onValueChange = onOpenAiApiKeyChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("OpenAI API Key") },
                supportingText = { Text("Optional. OpenAI is not free, but useful as a fallback provider.") },
                singleLine = true
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = aiProviderConfig.openAiModel,
                onValueChange = onOpenAiModelChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("OpenAI Model") },
                singleLine = true
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text("Strategy", fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(8.dp))
            DropdownField(
                label = "Signal Strategy",
                value = "Multi-factor confluence",
                options = listOf("Multi-factor confluence"),
                onSelect = {},
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                "Strategy presets should be added as tested rule bundles, not copied blindly. Good candidates later: trend pullback, breakout retest, mean reversion, and volatility compression breakout.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = if (compactLayout) 12.sp else 13.sp,
                lineHeight = if (compactLayout) 16.sp else 18.sp
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text("Signal Gate Switches", fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                "Disable only the blockers you want to test. This lets forecast-friendly setups through without editing code.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = if (compactLayout) 12.sp else 13.sp,
                lineHeight = if (compactLayout) 16.sp else 18.sp
            )
            Spacer(modifier = Modifier.height(10.dp))
            SettingsToggleRow(
                title = "Enforce hard blocks",
                description = "Noise, news, and severe forecast conflict can still neutralize a setup.",
                checked = signalFilters.enforceHardBlocks,
                onCheckedChange = { onSignalFiltersChange(signalFilters.copy(enforceHardBlocks = it)) }
            )
            SettingsToggleRow(
                title = "Require forecast gate",
                description = "Keep the forecast lane aligned before allowing an entry bias.",
                checked = signalFilters.requireForecastSupport,
                onCheckedChange = { onSignalFiltersChange(signalFilters.copy(requireForecastSupport = it)) }
            )
            SettingsToggleRow(
                title = "Require setup state",
                description = "Respect the setup and trigger-state gate for the chosen mode.",
                checked = signalFilters.requireSetupState,
                onCheckedChange = { onSignalFiltersChange(signalFilters.copy(requireSetupState = it)) }
            )
            SettingsToggleRow(
                title = "Require setup count",
                description = "Demand the full number of setup confirmations before approval.",
                checked = signalFilters.requireSetupConfirmations,
                onCheckedChange = { onSignalFiltersChange(signalFilters.copy(requireSetupConfirmations = it)) }
            )
            SettingsToggleRow(
                title = "Require confluence",
                description = "Keep cross-factor agreement between structure, zones, and trigger logic.",
                checked = signalFilters.requireConfluence,
                onCheckedChange = { onSignalFiltersChange(signalFilters.copy(requireConfluence = it)) }
            )
            SettingsToggleRow(
                title = "Require directional edge",
                description = "Force the winning setup to beat the runner-up by a safe margin.",
                checked = signalFilters.requireDirectionalEdge,
                onCheckedChange = { onSignalFiltersChange(signalFilters.copy(requireDirectionalEdge = it)) }
            )
            SettingsToggleRow(
                title = "Require risk cap",
                description = "Reject entries when the penalty model says noise and structure are too costly.",
                checked = signalFilters.requireRiskPenalty,
                onCheckedChange = { onSignalFiltersChange(signalFilters.copy(requireRiskPenalty = it)) }
            )
            SettingsToggleRow(
                title = "Require expectancy",
                description = "Keep staged historical expectancy positive before upgrading to eligible.",
                checked = signalFilters.requireExpectancy,
                onCheckedChange = { onSignalFiltersChange(signalFilters.copy(requireExpectancy = it)) }
            )
            if (signalFilters.hasOverrides()) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { onSignalFiltersChange(SignalFilterSettings()) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Reset Research Overrides")
                }
            }
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
                        fontSize = if (compactLayout) 12.sp else 13.sp,
                        lineHeight = if (compactLayout) 16.sp else 18.sp
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
                    Text("Keep signal alerts", fontWeight = FontWeight.Medium)
                    Text(
                        "Store non-neutral signals so you can review what the engine approved.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = if (compactLayout) 12.sp else 13.sp,
                        lineHeight = if (compactLayout) 16.sp else 18.sp
                    )
                }
                Switch(checked = keepAlertHistory, onCheckedChange = onKeepAlertHistoryChange)
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
private fun SettingsToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                description,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                lineHeight = 16.sp
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun FloatingBottomNav(
    selectedTab: AppTab,
    onTabSelected: (AppTab) -> Unit,
    darkTheme: Boolean
) {
    val compactLabels = LocalDensity.current.fontScale > 1.15f
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (darkTheme) {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.98f)
                } else {
                    MaterialTheme.colorScheme.surface.copy(alpha = 0.99f)
                }
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(
                    if (darkTheme) Color(0xFF1F2937) else MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)
                )
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(
                    horizontal = if (compactLabels) 4.dp else 6.dp,
                    vertical = if (compactLabels) 4.dp else 6.dp
                ),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppTab.values().forEach { tab ->
                BottomNavItem(
                    tab = tab,
                    selected = tab == selectedTab,
                    compactLabels = compactLabels,
                    onClick = { onTabSelected(tab) }
                )
            }
        }
    }
}

@Composable
private fun BottomNavItem(
    tab: AppTab,
    selected: Boolean,
    compactLabels: Boolean,
    onClick: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.01f else 1f,
        animationSpec = spring(),
        label = "tabScale"
    )

    androidx.compose.material3.Surface(
        onClick = onClick,
        color = Color.Transparent,
        modifier = Modifier.scale(scale),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = if (compactLabels) 10.dp else 12.dp,
                vertical = if (compactLabels) 6.dp else 5.dp
            ),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = tab.icon,
                contentDescription = tab.label,
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(19.dp)
            )
            if (!compactLabels) {
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = tab.label,
                    fontSize = 10.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(3.dp))
            } else {
                Spacer(modifier = Modifier.height(5.dp))
            }
            Box(
                modifier = Modifier
                    .width(18.dp)
                    .height(2.dp)
                    .background(
                        color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                        shape = RoundedCornerShape(999.dp)
                    )
            )
        }
    }
}

private fun formatTimestampLabel(epochMillis: Long): String {
    return Instant.ofEpochMilli(epochMillis)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("MMM d, HH:mm:ss"))
}
