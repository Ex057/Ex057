package com.ex57.capital.model

data class TradingSymbol(
    val code: String,
    val label: String,
    val category: String,
    val derivSymbol: String
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
    val evidence: EvidenceSnapshot,
    val setupType: SetupType,
    val nextTrigger: String,
    val mtfaStatus: MtfaStatus? = null,
    val positionGuidance: PositionGuidance? = null
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
    val stakeUsd: Double,
    val entryPrice: Double,
    val stopLoss: Double?,
    val takeProfit: Double?,
    val openedAtEpochMillis: Long,
    val setupType: SetupType,
    val rationale: String,
    val confidence: Int
)

data class ClosedTradeRecord(
    val id: String,
    val symbolCode: String,
    val timeframe: String,
    val side: PositionSide,
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
