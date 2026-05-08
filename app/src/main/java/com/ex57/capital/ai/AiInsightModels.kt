package com.ex57.capital.ai

enum class AiProvider(val label: String) {
    MOCK("Mock"),
    OPENAI("OpenAI"),
    OLLAMA("Ollama"),
    LOCALAI("LocalAI / LM Studio")
}

enum class AiInsightQuickAction(val label: String) {
    MARKET_DEEP_DIVE("Market Deep Dive"),
    EXPLAIN_SIGNAL("Explain Signal"),
    EXPLAIN_RISK("Explain Risk"),
    WHY_NOT_ELIGIBLE("Why Not Eligible"),
    SUMMARIZE_TRADE_PLAN("Summarize Trade Plan"),
    MANAGE_OPEN_TRADE("Manage Open Trade")
}

data class AiInsightContext(
    val symbolCode: String,
    val symbolLabel: String,
    val timeframe: String,
    val confirmationMode: String,
    val dataQualityLabel: String,
    val feedStatusLabel: String,
    val analysisHash: String,
    val generatedAtEpochMillis: Long,
    val market: AiMarketSnapshot,
    val mtfa: AiMtfaSnapshot?,
    val signal: AiSignalSnapshot?,
    val tradePlan: AiTradePlanSnapshot?,
    val confirmations: List<AiConfirmationSnapshot>,
    val evidence: AiEvidenceSnapshot?,
    val deepDive: AiDeepDiveSnapshot?,
    val openTrade: AiOpenTradeSnapshot?,
    val recentTrades: List<AiRecentTradeSnapshot>
)

data class AiMarketSnapshot(
    val livePrice: String?,
    val primaryCandleCount: Int,
    val shortTrend: String,
    val structureLabel: String,
    val momentumLabel: String,
    val volatilityLabel: String,
    val rangePercent: String,
    val recentChangePercent: String,
    val nearestSupport: String?,
    val nearestResistance: String?,
    val stackedTimeframes: List<AiMarketTimeframeSnapshot>
)

data class AiMarketTimeframeSnapshot(
    val timeframe: String,
    val candleCount: Int,
    val trendLabel: String,
    val momentumLabel: String,
    val rangePercent: String,
    val changePercent: String,
    val lastClose: String?,
    val recentOhlc: List<String> = emptyList()
)

data class AiDeepDiveSnapshot(
    val goldChecks: AiGoldChecksSnapshot,
    val topDown: AiTopDownSnapshot,
    val entryObject: AiEntryObjectSnapshot
)

data class AiGoldChecksSnapshot(
    val adx14H4: Double,
    val ema20: Double,
    val ema200: Double,
    val atr14H1: Double,
    val priceAbove200Ema: Boolean,
    val touches20Ema: Boolean,
    val h4BullEngulfing: Boolean,
    val h4BearEngulfing: Boolean,
    val m5BullEngulfing: Boolean,
    val m5BearEngulfing: Boolean,
    val beltHold: Boolean,
    val longLine: Boolean,
    val isLondonSession: Boolean,
    val isNyOverlap: Boolean
)

data class AiTopDownSnapshot(
    val htfBias: String,
    val mtfStructure: String,
    val ltfTrigger: String,
    val invalidation: String
)

data class AiEntryObjectSnapshot(
    val entryTf: String,
    val entryType: String,
    val entryLevel: String?,
    val stop: String?,
    val target: String?,
    val noTradeReason: String?
)

data class AiMtfaSnapshot(
    val macro: String,
    val structure: String,
    val setup: String,
    val trigger: String
)

data class AiSignalSnapshot(
    val bias: String,
    val confidence: Int,
    val decision: String,
    val approved: Boolean,
    val setupType: String,
    val tradeCandidate: Boolean,
    val summary: String,
    val executionPlan: String,
    val riskNote: String,
    val traderGuidance: String,
    val nextTrigger: String,
    val forecastSummary: String?,
    val performanceSummary: String?,
    val rejectionReasons: List<String>
)

data class AiTradePlanSnapshot(
    val entry: String?,
    val stopLoss: String?,
    val takeProfit: String?,
    val riskReward: String,
    val shouldTrade: Boolean
)

data class AiConfirmationSnapshot(
    val name: String,
    val passed: Boolean,
    val details: String
)

data class AiEvidenceSnapshot(
    val heuristicSummary: String,
    val heuristicSampleSize: Int,
    val confidenceBadge: String,
    val robustness: String,
    val expectancyR: String,
    val profitFactor: String,
    val lastOutcomes: List<String>,
    val realizedSummary: String,
    val realizedTradeCount: Int,
    val realizedWinRate: Int?,
    val realizedNetPnlUsd: Double?
)

data class AiOpenTradeSnapshot(
    val side: String,
    val timeframe: String,
    val entryPrice: String,
    val livePrice: String?,
    val stopLoss: String?,
    val takeProfit: String?,
    val unrealizedPercent: String?,
    val managementStage: Int,
    val rationale: String
)

data class AiRecentTradeSnapshot(
    val side: String,
    val lotSize: String,
    val openedAtLabel: String,
    val closedAtLabel: String,
    val outcome: String,
    val pnlUsd: String,
    val pnlPercent: String,
    val rationale: String
)

data class AiInsightRequest(
    val action: AiInsightQuickAction,
    val context: AiInsightContext,
    val forceRefresh: Boolean = false
)

data class AiInsightResponse(
    val provider: AiProvider,
    val action: AiInsightQuickAction,
    val title: String,
    val summary: String,
    val bullets: List<String>,
    val caution: String,
    val cached: Boolean,
    val requestHash: String,
    val generatedAtEpochMillis: Long
)

data class AiProviderConfig(
    val provider: AiProvider = AiProvider.LOCALAI,
    val openAiApiKey: String? = null,
    val openAiModel: String = "gpt-4.1-mini",
    val openAiBaseUrl: String = "https://api.openai.com",
    val ollamaBaseUrl: String = "http://10.0.2.2:11434",
    val ollamaModel: String = "llama3.1:8b",
    val localAiBaseUrl: String = "http://10.0.2.2:8080",
    val localAiModel: String = "local-model",
    val timeoutMillis: Long = 12_000L
)

data class AiComposedPrompt(
    val systemPrompt: String,
    val userPrompt: String,
    val requestHash: String
)

data class AiInsightParsedPayload(
    val title: String,
    val summary: String,
    val bullets: List<String>,
    val caution: String
)

data class AiInsightsUiState(
    val provider: AiProvider = AiProvider.LOCALAI,
    val activeAction: AiInsightQuickAction? = null,
    val isLoading: Boolean = false,
    val response: AiInsightResponse? = null,
    val errorMessage: String? = null
)
