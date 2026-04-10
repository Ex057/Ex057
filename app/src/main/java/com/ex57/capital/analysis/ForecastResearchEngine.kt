package com.ex57.capital.analysis

import com.ex57.capital.model.ForecastResearch
import com.ex57.capital.model.MarketCandle
import com.ex57.capital.model.TradeBias
import kotlin.math.abs

internal object ForecastResearchEngine {
    fun analyze(
        input: AnalysisInput,
        features: FeatureExtractionResult
    ): ForecastResearch {
        val contextCandles = input.candleStack[input.timeframePlan.trigger]
            .orEmpty()
            .ifEmpty { features.sourceCandles }
            .takeLast(96)

        if (contextCandles.size < 16) {
            return ForecastResearch(
                bias = TradeBias.NEUTRAL,
                strengthScore = 0.0,
                stabilityScore = 0.0,
                expectedMovePercent = 0.0,
                summary = "Forecast research is waiting for a deeper candle window."
            )
        }

        val closes = contextCandles.map(MarketCandle::close)
        val returns = closes.zipWithNext { a, b ->
            (b - a) / a.coerceAtLeast(0.00001)
        }
        val recentReturns = returns.takeLast(8)
        val mediumReturns = returns.takeLast(24)
        val recentDrift = recentReturns.average()
        val mediumDrift = mediumReturns.average()
        val realizedVolatility = mediumReturns.map(::abs).average().coerceAtLeast(0.00001)
        val normalizedDrift = (((recentDrift * 1.45) + (mediumDrift * 0.9)) / realizedVolatility)
            .coerceIn(-3.0, 3.0)

        val bias = when {
            normalizedDrift >= 0.35 -> TradeBias.BULLISH
            normalizedDrift <= -0.35 -> TradeBias.BEARISH
            else -> TradeBias.NEUTRAL
        }

        val dominantDirection = when (bias) {
            TradeBias.BULLISH -> 1.0
            TradeBias.BEARISH -> -1.0
            TradeBias.NEUTRAL -> 0.0
        }
        val alignedReturnRatio = if (dominantDirection == 0.0) {
            0.5
        } else {
            recentReturns.count { it * dominantDirection > 0.0 }.toDouble() / recentReturns.size.coerceAtLeast(1)
        }
        val recentRange = contextCandles.takeLast(8)
            .map { (it.high - it.low) / it.close.coerceAtLeast(0.00001) }
            .average()
            .coerceAtLeast(0.00001)
        val wickNoise = contextCandles.takeLast(8).map { candle ->
            val body = abs(candle.close - candle.open)
            val range = (candle.high - candle.low).coerceAtLeast(0.00001)
            1.0 - (body / range).coerceIn(0.0, 1.0)
        }.average()
        val strengthScore = (abs(normalizedDrift) / 2.2).coerceIn(0.0, 1.0)
        val stabilityScore = (
            (alignedReturnRatio * 0.55) +
                ((1.0 - (wickNoise * 0.65)).coerceIn(0.0, 1.0) * 0.25) +
                ((1.0 - (recentRange / (realizedVolatility * 4.0))).coerceIn(0.0, 1.0) * 0.20)
            ).coerceIn(0.0, 1.0)
        val expectedMovePercent = (abs(recentDrift) * 6.0 + abs(mediumDrift) * 12.0) * 100.0

        val summary = when (bias) {
            TradeBias.BULLISH ->
                "Forecast lane sees upward drift with ${percentLabel(alignedReturnRatio)} path agreement and ${percentLabel(stabilityScore)} regime stability."
            TradeBias.BEARISH ->
                "Forecast lane sees downward drift with ${percentLabel(alignedReturnRatio)} path agreement and ${percentLabel(stabilityScore)} regime stability."
            TradeBias.NEUTRAL ->
                "Forecast lane is mixed. Recent drift does not separate cleanly from noise."
        }

        return ForecastResearch(
            bias = bias,
            strengthScore = strengthScore,
            stabilityScore = stabilityScore,
            expectedMovePercent = expectedMovePercent,
            summary = summary
        )
    }

    private fun percentLabel(value: Double): String = "${(value * 100.0).toInt()}%"
}
