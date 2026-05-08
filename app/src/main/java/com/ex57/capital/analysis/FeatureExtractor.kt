package com.ex57.capital.analysis

import com.ex57.capital.model.MarketCandle
import kotlin.math.abs
import java.time.Instant
import java.time.ZoneOffset

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
        val rsi = calculateRsi(prices, period = 14)
        val macdHistogram = calculateMacdHistogram(prices)
        val bollingerPosition = calculateBollingerPosition(prices)
        val ema20 = calculateEma(prices, 20)
        val ema200 = calculateEma(prices, 200)
        val h1Candles = input.candleStack["1h"].orEmpty().ifEmpty { sourceCandles }
        val h4Candles = input.candleStack["4h"].orEmpty().ifEmpty { sourceCandles }
        val m5Candles = input.candleStack["5m"].orEmpty().ifEmpty { sourceCandles }
        val atr14H1 = calculateAtr(h1Candles, 14).coerceAtLeast(averageStep * 1.1)
        val adx14H4 = calculateAdxProxy(h4Candles, 14)
        val priceAbove200Ema = last >= ema200
        val touches20Ema = kotlin.math.abs(last - ema20) <= (averageStep * 0.8)
        val h4BullEngulfing = hasBullishEngulfing(h4Candles)
        val h4BearEngulfing = hasBearishEngulfing(h4Candles)
        val m5BullEngulfing = hasBullishEngulfing(m5Candles)
        val m5BearEngulfing = hasBearishEngulfing(m5Candles)
        val beltHold = hasBeltHold(m5Candles)
        val longLine = hasLongLine(m5Candles, atr14H1)
        val bullishIndicatorScore = listOf(
            AnalysisSupport.softScore(55.0 - rsi, 10.0, 0.0),
            AnalysisSupport.softScore(macdHistogram, averageStep * 0.9, averageStep * 0.15),
            AnalysisSupport.rangedScore(bollingerPosition, -0.15, 0.75, -0.55, 1.05)
        ).average().coerceIn(0.0, 1.0)
        val bearishIndicatorScore = listOf(
            AnalysisSupport.softScore(rsi - 45.0, 10.0, 0.0),
            AnalysisSupport.softScore(-macdHistogram, averageStep * 0.9, averageStep * 0.15),
            AnalysisSupport.rangedScore(-bollingerPosition, -0.15, 0.75, -0.55, 1.05)
        ).average().coerceIn(0.0, 1.0)
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
        val sessionContext = buildSessionContext(sourceCandles.lastOrNull()?.epoch)

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
            bearishBreakoutScore = bearishBreakoutScore,
            rsi = rsi,
            macdHistogram = macdHistogram,
            bollingerPosition = bollingerPosition,
            ema20 = ema20,
            ema200 = ema200,
            atr14H1 = atr14H1,
            adx14H4 = adx14H4,
            priceAbove200Ema = priceAbove200Ema,
            touches20Ema = touches20Ema,
            h4BullEngulfing = h4BullEngulfing,
            h4BearEngulfing = h4BearEngulfing,
            m5BullEngulfing = m5BullEngulfing,
            m5BearEngulfing = m5BearEngulfing,
            beltHold = beltHold,
            longLine = longLine,
            bullishIndicatorScore = bullishIndicatorScore,
            bearishIndicatorScore = bearishIndicatorScore,
            sessionContext = sessionContext
        )
    }

    private fun buildSessionContext(lastEpochSeconds: Long?): SessionContext {
        val hourUtc = lastEpochSeconds?.let {
            Instant.ofEpochSecond(it).atOffset(ZoneOffset.UTC).hour
        } ?: Instant.now().atOffset(ZoneOffset.UTC).hour
        val isLondonOpen = hourUtc in 7..9
        val isLondonNyOverlap = hourUtc in 13..16
        val isQuietSession = hourUtc in 0..5 || hourUtc in 21..23
        val sessionScore = when {
            isLondonNyOverlap -> 1.0
            isLondonOpen -> 0.65
            isQuietSession -> 0.2
            else -> 0.45
        }
        val sessionLabel = when {
            isLondonNyOverlap -> "London-New York overlap"
            isLondonOpen -> "London open"
            isQuietSession -> "Quiet session"
            else -> "Transition session"
        }
        return SessionContext(
            sessionLabel = sessionLabel,
            isLondonOpen = isLondonOpen,
            isLondonNyOverlap = isLondonNyOverlap,
            isQuietSession = isQuietSession,
            sessionScore = sessionScore
        )
    }

    private fun calculateRsi(prices: List<Double>, period: Int): Double {
        val deltas = prices.zipWithNext { previous, current -> current - previous }.takeLast(period)
        if (deltas.isEmpty()) return 50.0
        val gains = deltas.filter { it > 0.0 }.sum()
        val losses = deltas.filter { it < 0.0 }.sumOf { abs(it) }
        if (losses == 0.0) return 100.0
        val rs = (gains / period) / (losses / period)
        return 100.0 - (100.0 / (1.0 + rs))
    }

    private fun calculateMacdHistogram(prices: List<Double>): Double {
        if (prices.size < 26) return 0.0
        val fast = prices.takeLast(12).average()
        val slow = prices.takeLast(26).average()
        val macd = fast - slow
        val signal = prices.takeLast(9).average() - slow
        return macd - signal
    }

    private fun calculateBollingerPosition(prices: List<Double>): Double {
        val window = prices.takeLast(minOf(20, prices.size))
        if (window.size < 8) return 0.0
        val mean = window.average()
        val variance = window.map { (it - mean) * (it - mean) }.average()
        val bandWidth = (kotlin.math.sqrt(variance) * 2.0).coerceAtLeast(mean * 0.0001)
        return ((window.last() - mean) / bandWidth).coerceIn(-2.0, 2.0)
    }

    private fun calculateEma(prices: List<Double>, period: Int): Double {
        if (prices.isEmpty()) return 0.0
        val alpha = 2.0 / (period + 1.0)
        var ema = prices.first()
        prices.drop(1).forEach { close ->
            ema = (close * alpha) + (ema * (1.0 - alpha))
        }
        return ema
    }

    private fun calculateAtr(candles: List<MarketCandle>, period: Int): Double {
        if (candles.size < 3) return 0.0
        val trueRanges = candles.zipWithNext { prev, curr ->
            maxOf(
                curr.high - curr.low,
                kotlin.math.abs(curr.high - prev.close),
                kotlin.math.abs(curr.low - prev.close)
            )
        }
        return trueRanges.takeLast(period).average()
    }

    private fun calculateAdxProxy(candles: List<MarketCandle>, period: Int): Double {
        if (candles.size < period + 2) return 15.0
        val moves = candles.zipWithNext { a, b -> b.close - a.close }.takeLast(period)
        val directionalMove = kotlin.math.abs(moves.sum())
        val volatility = moves.sumOf { kotlin.math.abs(it) }.coerceAtLeast(0.00001)
        return ((directionalMove / volatility) * 100.0).coerceIn(0.0, 100.0)
    }

    private fun hasBullishEngulfing(candles: List<MarketCandle>): Boolean {
        if (candles.size < 2) return false
        val prev = candles[candles.lastIndex - 1]
        val curr = candles.last()
        return prev.close < prev.open &&
            curr.close > curr.open &&
            curr.open <= prev.close &&
            curr.close >= prev.open
    }

    private fun hasBearishEngulfing(candles: List<MarketCandle>): Boolean {
        if (candles.size < 2) return false
        val prev = candles[candles.lastIndex - 1]
        val curr = candles.last()
        return prev.close > prev.open &&
            curr.close < curr.open &&
            curr.open >= prev.close &&
            curr.close <= prev.open
    }

    private fun hasBeltHold(candles: List<MarketCandle>): Boolean {
        val curr = candles.lastOrNull() ?: return false
        val body = kotlin.math.abs(curr.close - curr.open)
        val range = (curr.high - curr.low).coerceAtLeast(0.00001)
        val openAtEdge = kotlin.math.abs(curr.open - curr.low) <= (range * 0.05) ||
            kotlin.math.abs(curr.open - curr.high) <= (range * 0.05)
        return openAtEdge && body >= (range * 0.65)
    }

    private fun hasLongLine(candles: List<MarketCandle>, atr: Double): Boolean {
        val curr = candles.lastOrNull() ?: return false
        val range = curr.high - curr.low
        return range >= (atr * 0.85)
    }
}
