package com.ex57.capital.analysis

import kotlin.math.abs

internal object FeatureExtractor {
    fun extract(input: AnalysisInput): FeatureExtractionResult? {
        val setupCandles = input.candleStack[input.timeframePlan.setup].orEmpty().ifEmpty { input.candles }
        val triggerCandles = input.candleStack[input.timeframePlan.trigger].orEmpty()
        val primaryCandles = setupCandles.ifEmpty { input.candles }
        val closingPrices = if (primaryCandles.size >= 12) {
            primaryCandles.takeLast(50).map { it.close }
        } else {
            input.recentPrices.takeLast(50)
        }
        if (closingPrices.size < 12) return null

        val prices = closingPrices
        val last = prices.last()
        val shortWindow = prices.takeLast(minOf(5, prices.size))
        val longWindow = prices.takeLast(minOf(12, prices.size))
        val shortAverage = shortWindow.average()
        val longAverage = longWindow.average()
        val stepMoves = prices.zipWithNext { a, b -> b - a }
        val averageStep = stepMoves.map(::abs).average().coerceAtLeast(last * 0.0002)
        val trendStrength = (shortAverage - longAverage) / averageStep
        val momentum = last - prices[prices.lastIndex - minOf(4, prices.lastIndex)]
        val pullbackDistance = (last - shortAverage) / averageStep
        val priorWindow = prices.dropLast(1).takeLast(minOf(8, prices.size - 1))
        val breakoutUp = priorWindow.isNotEmpty() && last > (priorWindow.maxOrNull() ?: last)
        val breakoutDown = priorWindow.isNotEmpty() && last < (priorWindow.minOrNull() ?: last)
        val noiseRatio = averageStep / last.coerceAtLeast(0.0001)
        val moveFloor = AnalysisSupport.timeframeMoveFloor(input.timeframe) * averageStep
        val noiseCeiling = AnalysisSupport.timeframeNoiseCeiling(input.timeframe)
        val evidence = AnalysisSupport.buildEvidenceSnapshot(primaryCandles, input.recentPrices)
        val sourceCandles = if (primaryCandles.size >= 8) primaryCandles.takeLast(80) else AnalysisSupport.buildSyntheticCandles(prices)
        val topDown = AnalysisSupport.buildTopDownContext(
            timeframePlan = input.timeframePlan,
            candleStack = input.candleStack,
            candles = primaryCandles,
            prices = prices,
            averageStep = averageStep
        )
        val noiseScore = AnalysisSupport.softScore(noiseCeiling - noiseRatio, noiseCeiling * 0.10, 0.0)
        val timeframeScore = AnalysisSupport.softScore(abs(momentum) - moveFloor, moveFloor * 0.30, 0.0)
        val bullishTrendScore = AnalysisSupport.softScore(trendStrength, 1.1, 0.35)
        val bearishTrendScore = AnalysisSupport.softScore(-trendStrength, 1.1, 0.35)
        val bullishMomentumScore = AnalysisSupport.softScore(momentum, moveFloor * 1.2, moveFloor * 0.45)
        val bearishMomentumScore = AnalysisSupport.softScore(-momentum, moveFloor * 1.2, moveFloor * 0.45)
        val bullishPullbackScore = if (trendStrength > 0.2) {
            AnalysisSupport.rangedScore(pullbackDistance, -0.45, 0.55, -0.95, 1.10)
        } else {
            0.0
        }
        val bearishPullbackScore = if (trendStrength < -0.2) {
            AnalysisSupport.rangedScore(pullbackDistance, -0.55, 0.45, -1.10, 0.95)
        } else {
            0.0
        }
        val bullishBreakoutScore = if (breakoutUp) 1.0 else if (last >= (priorWindow.maxOrNull() ?: last) - (averageStep * 0.35)) 0.5 else 0.0
        val bearishBreakoutScore = if (breakoutDown) 1.0 else if (last <= (priorWindow.minOrNull() ?: last) + (averageStep * 0.35)) 0.5 else 0.0
        val patternSignals = AnalysisSupport.analyzeCandlestickPatterns(
            candles = triggerCandles.ifEmpty { sourceCandles },
            averageStep = averageStep
        )
        val newsPulse = AnalysisSupport.detectNewsPulse(sourceCandles)
        val iccPhase = AnalysisSupport.analyzeIccPhase(
            sourceCandles = sourceCandles,
            bullishBreakoutScore = bullishBreakoutScore,
            bearishBreakoutScore = bearishBreakoutScore,
            bullishPullbackScore = bullishPullbackScore,
            bearishPullbackScore = bearishPullbackScore,
            bullishMomentumScore = bullishMomentumScore,
            bearishMomentumScore = bearishMomentumScore,
            trendStrength = trendStrength
        )
        val mtfContext = AnalysisSupport.analyzeMultiTimeframeConsensus(
            timeframePlan = input.timeframePlan,
            candleStack = input.candleStack,
            fallbackCandles = sourceCandles
        )

        return FeatureExtractionResult(
            prices = prices,
            sourceCandles = sourceCandles,
            last = last,
            shortAverage = shortAverage,
            longAverage = longAverage,
            averageStep = averageStep,
            trendStrength = trendStrength,
            momentum = momentum,
            pullbackDistance = pullbackDistance,
            breakoutUp = breakoutUp,
            breakoutDown = breakoutDown,
            noiseRatio = noiseRatio,
            moveFloor = moveFloor,
            noiseCeiling = noiseCeiling,
            evidence = evidence,
            topDown = topDown,
            patternSignals = patternSignals,
            newsPulse = newsPulse,
            iccPhase = iccPhase,
            mtfContext = mtfContext,
            noiseScore = noiseScore,
            timeframeScore = timeframeScore,
            bullishTrendScore = bullishTrendScore,
            bearishTrendScore = bearishTrendScore,
            bullishMomentumScore = bullishMomentumScore,
            bearishMomentumScore = bearishMomentumScore,
            bullishPullbackScore = bullishPullbackScore,
            bearishPullbackScore = bearishPullbackScore,
            bullishBreakoutScore = bullishBreakoutScore,
            bearishBreakoutScore = bearishBreakoutScore
        )
    }
}
