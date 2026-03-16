package com.ex57.capital.model

data class TradingSymbol(
    val code: String,
    val label: String,
    val category: String,
    val derivSymbol: String
)

enum class ConfirmationMode(
    val label: String,
    val minimumSignalsRequired: Int,
    val signalStyle: String,
    val riskProfile: String
) {
    CONSERVATIVE("Conservative", 5, "Strong trend only", "Lower frequency, tighter filtering"),
    MODERATE("Moderate", 4, "Balanced continuation", "Balanced risk and opportunity"),
    AGGRESSIVE("Aggressive", 3, "Early momentum", "More setups, higher noise")
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

enum class TradeBias(val label: String) {
    BULLISH("Bullish"),
    BEARISH("Bearish"),
    NEUTRAL("Neutral")
}

enum class DataQuality(val label: String) {
    EXCHANGE_OHLC("Exchange OHLC"),
    ESTIMATED("Estimated")
}

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
    val nextTrigger: String
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
