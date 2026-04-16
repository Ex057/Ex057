package com.ex57.capital.analysis

import com.ex57.capital.model.ForecastModelMetrics
import com.ex57.capital.model.TradeBias
import kotlin.math.abs

internal object ForecastModelEngine {
    fun analyze(
        input: AnalysisInput,
        features: FeatureExtractionResult
    ): ForecastModelMetrics {
        val directionalSignal = (
            (features.trendStrength * 0.28) +
                (features.mtfContext.directionalBias * 0.30) +
                (features.patternSignals.directionalBias * 0.14) +
                (features.iccPhase.directionalBias * 0.14) +
                (if (features.breakoutUp) 0.08 else if (features.breakoutDown) -0.08 else 0.0) +
                (if (features.rsi >= 55.0) 0.06 else if (features.rsi <= 45.0) -0.06 else 0.0)
            ).coerceIn(-1.0, 1.0)

        val bullishProbability = ((directionalSignal + 1.0) / 2.0).coerceIn(0.0, 1.0)
        val bearishProbability = (1.0 - bullishProbability).coerceIn(0.0, 1.0)
        val directionConfidence = abs(directionalSignal).coerceIn(0.0, 1.0)
        val forecastDispersion = ((features.noiseRatio / features.noiseCeiling).coerceIn(0.0, 2.0) / 2.0)
            .coerceIn(0.0, 1.0)
        val directionalMovePercent = ((features.moveFloor / features.last.coerceAtLeast(0.00001)) * 100.0)
            .coerceIn(0.01, 10.0)
        val impulseMovePercent = abs((features.momentum / features.last.coerceAtLeast(0.00001)) * 100.0)
        val expectedMovePercent = (
            (directionConfidence * directionalMovePercent * 1.8) +
                (impulseMovePercent * 0.8)
            ).coerceAtLeast(0.05)
        val targetBeforeStopScore = (directionConfidence * (1.0 - (forecastDispersion * 0.8)))
            .coerceIn(0.0, 1.0)

        val bias = when {
            directionConfidence < 0.18 -> TradeBias.NEUTRAL
            bullishProbability > bearishProbability -> TradeBias.BULLISH
            bearishProbability > bullishProbability -> TradeBias.BEARISH
            else -> TradeBias.NEUTRAL
        }
        val summary = when (bias) {
            TradeBias.BULLISH -> "Sequence model leans bullish with ${percent(directionConfidence)} confidence and ${percent(1.0 - forecastDispersion)} stability."
            TradeBias.BEARISH -> "Sequence model leans bearish with ${percent(directionConfidence)} confidence and ${percent(1.0 - forecastDispersion)} stability."
            TradeBias.NEUTRAL -> "Sequence model is mixed for ${input.timeframe}; directional edge is weak."
        }

        return ForecastModelMetrics(
            modelName = "Sequence Forecast v1",
            bias = bias,
            bullishProbability = bullishProbability,
            bearishProbability = bearishProbability,
            directionConfidence = directionConfidence,
            expectedMovePercent = expectedMovePercent,
            forecastDispersion = forecastDispersion,
            targetBeforeStopScore = targetBeforeStopScore,
            summary = summary
        )
    }

    private fun percent(value: Double): String = "${(value * 100.0).toInt()}%"
}
