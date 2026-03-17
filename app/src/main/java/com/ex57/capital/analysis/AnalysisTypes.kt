package com.ex57.capital.analysis

import com.ex57.capital.model.Confirmation
import com.ex57.capital.model.AnalysisTimeframePlan
import com.ex57.capital.model.ConfirmationMode
import com.ex57.capital.model.EvidenceSnapshot
import com.ex57.capital.model.MarketCandle
import com.ex57.capital.model.SetupType
import com.ex57.capital.model.TradeBias
import com.ex57.capital.model.TradeDecision

internal data class AnalysisInput(
    val timeframe: String,
    val timeframePlan: AnalysisTimeframePlan,
    val mode: ConfirmationMode,
    val candles: List<MarketCandle>,
    val candleStack: Map<String, List<MarketCandle>>,
    val recentPrices: List<Double>
)

internal data class FeatureExtractionResult(
    val prices: List<Double>,
    val sourceCandles: List<MarketCandle>,
    val last: Double,
    val shortAverage: Double,
    val longAverage: Double,
    val averageStep: Double,
    val trendStrength: Double,
    val momentum: Double,
    val pullbackDistance: Double,
    val breakoutUp: Boolean,
    val breakoutDown: Boolean,
    val noiseRatio: Double,
    val moveFloor: Double,
    val noiseCeiling: Double,
    val evidence: EvidenceSnapshot,
    val topDown: TopDownContext,
    val patternSignals: PatternSignalSummary,
    val newsPulse: NewsPulseContext,
    val iccPhase: IccPhaseContext,
    val mtfContext: MultiTimeframeContext,
    val noiseScore: Double,
    val timeframeScore: Double,
    val bullishTrendScore: Double,
    val bearishTrendScore: Double,
    val bullishMomentumScore: Double,
    val bearishMomentumScore: Double,
    val bullishPullbackScore: Double,
    val bearishPullbackScore: Double,
    val bullishBreakoutScore: Double,
    val bearishBreakoutScore: Double
)

internal data class StrategyEvaluation(
    val setupType: SetupType,
    val bias: TradeBias,
    val approved: Boolean,
    val decision: TradeDecision,
    val confidence: Int,
    val confirmations: List<Confirmation>,
    val topDownBullScore: Double,
    val topDownBearScore: Double,
    val corePassed: Int,
    val stage: Int,
    val expectancyValue: Double
)

internal data class CandidateSetup(
    val type: SetupType,
    val bias: TradeBias,
    val corePassed: Int,
    val score: Double
)

internal data class TopDownContext(
    val higherTimeframeBias: TradeBias,
    val higherTimeframeSummary: String,
    val structureState: String,
    val setupState: String,
    val triggerState: String,
    val supportLevel: Double?,
    val resistanceLevel: Double?,
    val zoneBias: Double,
    val zoneSummary: String,
    val liquidityBias: Double,
    val liquiditySummary: String,
    val imbalanceBias: Double,
    val imbalanceSummary: String,
    val stackAlignmentScore: Double,
    val confluenceScore: Double
)

internal data class DirectionalContext(
    val bias: Double,
    val summary: String
)

internal data class PatternSignalSummary(
    val directionalBias: Double,
    val summary: String
)

internal data class NewsPulseContext(
    val pulseScore: Double,
    val summary: String
)

internal data class IccPhaseContext(
    val phase: String,
    val confidence: Double,
    val directionalBias: Double,
    val summary: String
)

internal data class MultiTimeframeContext(
    val directionalBias: Double,
    val alignmentScore: Double,
    val summary: String
)
