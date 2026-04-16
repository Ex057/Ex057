package com.ex57.capital.model

data class TradingSymbol(
    val code: String,
    val label: String,
    val category: String,
    val derivSymbol: String,
    val spec: SymbolTradingSpec = SymbolTradingSpec()
)

data class SymbolTradingSpec(
    val contractSize: Double = 1.0,
    val minLot: Double = 0.01,
    val maxLot: Double = 50.0,
    val lotStep: Double = 0.01,
    val typicalSpread: Double = 0.0001,
    val effectiveLeverage: Double = 100.0,
    val tickSize: Double = 0.0001,
    val pricePrecision: Int = 5
)

enum class PositionSide(val label: String) {
    LONG("Long"),
    SHORT("Short")
}

enum class PositionRecommendation(val label: String) {
    HOLD("Hold"),
    SCALE_OUT("Scale Out"),
    EXIT("Exit"),
    WAIT("Wait")
}

enum class ConfirmationMode(
    val label: String,
    val minimumSignalsRequired: Int,
    val signalStyle: String,
    val riskProfile: String
) {
    CONSERVATIVE("Conservative", 5, "Strong trend only", "Lower frequency, tighter filtering"),
    MODERATE("Moderate", 4, "Balanced continuation", "Balanced risk and opportunity"),
    AGGRESSIVE("Aggressive", 3, "Early momentum", "More setups, higher noise"),
    LENIENT("Lenient", 3, "Three credible confirmations", "Earliest entries, loosest filtering")
}

data class SignalFilterSettings(
    val enforceHardBlocks: Boolean = true,
    val requireForecastSupport: Boolean = true,
    val requireSetupState: Boolean = true,
    val requireSetupConfirmations: Boolean = true,
    val requireConfluence: Boolean = true,
    val requireDirectionalEdge: Boolean = true,
    val requireRiskPenalty: Boolean = true,
    val requireExpectancy: Boolean = true
) {
    fun hasOverrides(): Boolean = disabledGateLabels().isNotEmpty()

    fun disabledGateLabels(): List<String> = buildList {
        if (!enforceHardBlocks) add("hard blocks")
        if (!requireForecastSupport) add("forecast gate")
        if (!requireSetupState) add("setup state")
        if (!requireSetupConfirmations) add("setup count")
        if (!requireConfluence) add("confluence")
        if (!requireDirectionalEdge) add("directional edge")
        if (!requireRiskPenalty) add("risk cap")
        if (!requireExpectancy) add("expectancy")
    }

    fun summary(maxItems: Int = 3): String {
        val labels = disabledGateLabels()
        if (labels.isEmpty()) return "All signal guardrails are active."
        val visible = labels.take(maxItems)
        val suffix = if (labels.size > maxItems) " +${labels.size - maxItems} more" else ""
        return "Research overrides active: ${visible.joinToString()} disabled$suffix."
    }
}

data class ModeConfig(
    val minimumCoreRequired: Int,
    val biasActivationThreshold: Double,
    val topDownDirectionalThreshold: Double,
    val confluenceGate: Double,
    val directionalStrengthThreshold: Double,
    val setupQualityThreshold: Double,
    val breakoutSetupQualityAdjustment: Double,
    val requiredSetupConfirmations: Int,
    val maxRiskPenalty: Double,
    val directionalEdgeThreshold: Double,
    val supportThreshold: Double,
    val rewardMultiplier: Double
)

data class Confirmation(
    val name: String,
    val passed: Boolean,
    val details: String
)

data class MarketCandle(
    val epoch: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double
)

data class AnalysisTimeframePlan(
    val macro: String,
    val structure: String,
    val setup: String,
    val trigger: String
)

enum class TradeBias(val label: String) {
    BULLISH("Bullish"),
    BEARISH("Bearish"),
    NEUTRAL("Neutral")
}

enum class DataQuality(val label: String) {
    EXCHANGE_OHLC("Exchange OHLC"),
    CACHED("Cached"),
    ESTIMATED("Estimated")
}

enum class FeedPhase(val label: String) {
    DISCONNECTED("Disconnected"),
    CONNECTING("Connecting"),
    CONNECTED("Connected"),
    WAITING_FOR_DATA("Waiting for market data"),
    LIVE("Live"),
    STALE("Stale"),
    RECONNECTING("Reconnecting"),
    ERROR("Feed error")
}

data class FeedState(
    val phase: FeedPhase = FeedPhase.DISCONNECTED,
    val message: String = FeedPhase.DISCONNECTED.label,
    val reconnectAttempt: Int = 0,
    val lastTickEpochMillis: Long? = null
)

enum class TradeDecision(val label: String) {
    ELIGIBLE("Eligible"),
    WATCHLIST("Watchlist"),
    REJECT("Reject")
}

enum class SetupType(val label: String) {
    TREND_PULLBACK("Trend Pullback"),
    BREAKOUT("Breakout"),
    NONE("None")
}

data class AnalysisResult(
    val bias: TradeBias,
    val confidence: Int,
    val approved: Boolean,
    val summary: String,
    val executionPlan: String,
    val riskNote: String,
    val confirmations: List<Confirmation>,
    val tradeSetup: TradeSetup,
    val traderGuidance: String,
    val decision: TradeDecision,
    val rejectionReasons: List<String>,
    val evidence: EvidenceSnapshot,
    val setupType: SetupType,
    val nextTrigger: String,
    val mtfaStatus: MtfaStatus? = null,
    val forecastResearch: ForecastResearch? = null,
    val forecastModelMetrics: ForecastModelMetrics? = null,
    val performanceFeedback: TradePerformanceFeedback? = null,
    val positionGuidance: PositionGuidance? = null
)

data class ForecastResearch(
    val bias: TradeBias,
    val strengthScore: Double,
    val stabilityScore: Double,
    val expectedMovePercent: Double,
    val summary: String
)

data class ForecastModelMetrics(
    val modelName: String,
    val bias: TradeBias,
    val bullishProbability: Double,
    val bearishProbability: Double,
    val directionConfidence: Double,
    val expectedMovePercent: Double,
    val forecastDispersion: Double,
    val targetBeforeStopScore: Double,
    val summary: String
)

data class TradePerformanceFeedback(
    val sampleSize: Int,
    val consecutiveLosses: Int,
    val recentWinRate: Int,
    val recentNetPnlUsd: Double,
    val strictModeActive: Boolean,
    val summary: String
)

data class MtfaStatus(
    val macro: String,
    val structure: String,
    val setup: String,
    val trigger: String
)

data class TradeSetup(
    val entry: Double?,
    val stopLoss: Double?,
    val takeProfit: Double?,
    val riskReward: String,
    val shouldTrade: Boolean
)

data class PendingTradeOrder(
    val id: String,
    val symbolCode: String,
    val derivSymbol: String,
    val timeframe: String,
    val side: PositionSide,
    val lotSize: Double,
    val stakeUsd: Double,
    val usedMarginUsd: Double = 0.0,
    val estimatedSpreadCostUsd: Double = 0.0,
    val targetEntryPrice: Double,
    val stopLoss: Double?,
    val takeProfit: Double?,
    val createdAtEpochMillis: Long,
    val note: String
)

data class EvidenceSnapshot(
    val winRate: Int,
    val profitFactor: String,
    val expectancyR: String,
    val maxDrawdownR: String,
    val avgR: String,
    val medianR: String,
    val sampleSize: Int,
    val confidenceBadge: String,
    val robustness: String,
    val lastOutcomes: List<String>
)

data class TradePosition(
    val id: String,
    val symbolCode: String,
    val derivSymbol: String,
    val timeframe: String,
    val side: PositionSide,
    val lotSize: Double,
    val initialLotSize: Double,
    val stakeUsd: Double,
    val initialStakeUsd: Double,
    val usedMarginUsd: Double = 0.0,
    val estimatedSpreadCostUsd: Double = 0.0,
    val entryPrice: Double,
    val stopLoss: Double?,
    val takeProfit: Double?,
    val openedAtEpochMillis: Long,
    val setupType: SetupType,
    val rationale: String,
    val confidence: Int,
    val realizedPnlUsd: Double = 0.0,
    val managementStage: Int = 0
)

data class ClosedTradeRecord(
    val id: String,
    val symbolCode: String,
    val timeframe: String,
    val side: PositionSide,
    val lotSize: Double,
    val stakeUsd: Double,
    val entryPrice: Double,
    val exitPrice: Double,
    val stopLoss: Double?,
    val takeProfit: Double?,
    val openedAtEpochMillis: Long,
    val closedAtEpochMillis: Long,
    val outcomeLabel: String,
    val pnlUsd: Double,
    val pnlPercent: Double,
    val rationale: String
)

data class PositionGuidance(
    val recommendation: PositionRecommendation,
    val headline: String,
    val detail: String,
    val unrealizedPercent: Double,
    val distanceToStopPercent: Double?,
    val distanceToTargetPercent: Double?
)
