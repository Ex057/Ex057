package com.ex57.capital.analysis

import com.ex57.capital.model.AnalysisResult
import com.ex57.capital.model.AnalysisTimeframePlan
import com.ex57.capital.model.Confirmation
import com.ex57.capital.model.ConfirmationMode
import com.ex57.capital.model.EvidenceSnapshot
import com.ex57.capital.model.MarketCandle
import com.ex57.capital.model.MtfaStatus
import com.ex57.capital.model.SetupType
import com.ex57.capital.model.TradeBias
import com.ex57.capital.model.TradeDecision
import com.ex57.capital.model.TradeSetup
import kotlin.math.abs

internal object AnalysisSupport {
    fun buildTimeframePlan(timeframe: String): AnalysisTimeframePlan {
        return when (timeframe) {
            "1m" -> AnalysisTimeframePlan(macro = "15m", structure = "5m", setup = "1m", trigger = "1m")
            "5m" -> AnalysisTimeframePlan(macro = "1h", structure = "15m", setup = "5m", trigger = "1m")
            "15m" -> AnalysisTimeframePlan(macro = "4h", structure = "1h", setup = "15m", trigger = "5m")
            "30m" -> AnalysisTimeframePlan(macro = "4h", structure = "1h", setup = "30m", trigger = "15m")
            "1h" -> AnalysisTimeframePlan(macro = "1d", structure = "4h", setup = "1h", trigger = "15m")
            "2h" -> AnalysisTimeframePlan(macro = "1d", structure = "4h", setup = "2h", trigger = "30m")
            "4h" -> AnalysisTimeframePlan(macro = "1d", structure = "8h", setup = "4h", trigger = "1h")
            "8h" -> AnalysisTimeframePlan(macro = "1d", structure = "8h", setup = "8h", trigger = "2h")
            "1d" -> AnalysisTimeframePlan(macro = "1d", structure = "8h", setup = "1d", trigger = "4h")
            else -> AnalysisTimeframePlan(macro = timeframe, structure = timeframe, setup = timeframe, trigger = timeframe)
        }
    }

    fun insufficientDataResult(): AnalysisResult {
        return AnalysisResult(
            bias = TradeBias.NEUTRAL,
            confidence = 20,
            approved = false,
            summary = "Not enough live data yet. Connect the feed and wait for at least 12 ticks.",
            executionPlan = "No trade. Build a larger sample before trusting the signal model.",
            riskNote = "Thin data means the model cannot judge trend quality or noise reliably.",
            confirmations = listOf(
                Confirmation("Trend Structure", false, "Need a larger tick sample"),
                Confirmation("Momentum", false, "Not enough price movement captured"),
                Confirmation("Breakout Pressure", false, "Insufficient recent range data"),
                Confirmation("Noise Filter", false, "Volatility baseline not established")
            ),
            tradeSetup = TradeSetup(
                entry = null,
                stopLoss = null,
                takeProfit = null,
                riskReward = "-",
                shouldTrade = false
            ),
            traderGuidance = "Forfeit for now. Build more price history before trusting any setup.",
            decision = TradeDecision.REJECT,
            evidence = emptyEvidence(),
            setupType = SetupType.NONE,
            nextTrigger = "Wait for more live candles before evaluating this market.",
            mtfaStatus = MtfaStatus(
                macro = "Undetermined",
                structure = "Undetermined",
                setup = "Waiting",
                trigger = "Waiting"
            )
        )
    }

    fun buildMtfaStatus(topDown: TopDownContext): MtfaStatus {
        val macro = when (topDown.higherTimeframeBias) {
            TradeBias.BULLISH -> "Bullish"
            TradeBias.BEARISH -> "Bearish"
            TradeBias.NEUTRAL -> "Neutral"
        }
        val structure = when (topDown.structureState) {
            "bullish_structure" -> "Bullish"
            "bearish_structure" -> "Bearish"
            "range_bound" -> "Range"
            else -> "Mixed"
        }
        val setup = when (topDown.setupState) {
            "pullback" -> "Pullback"
            "aligned" -> "Forming"
            "mixed" -> "Mixed"
            "undetermined" -> "Undetermined"
            else -> topDown.setupState.replace('_', ' ').replaceFirstChar { it.uppercase() }
        }
        val trigger = when (topDown.triggerState) {
            "entry_ready" -> "Entry Ready"
            "consolidating" -> "Consolidating"
            "early_reversal" -> "Early Reversal"
            "undetermined" -> "Waiting"
            else -> topDown.triggerState.replace('_', ' ').replaceFirstChar { it.uppercase() }
        }
        return MtfaStatus(
            macro = macro,
            structure = structure,
            setup = setup,
            trigger = trigger
        )
    }

    fun timeframeMoveFloor(timeframe: String): Double {
        return when (timeframe) {
            "1m" -> 0.7
            "5m" -> 0.9
            "15m" -> 1.1
            "30m" -> 1.2
            "1h" -> 1.3
            "2h" -> 1.4
            "4h" -> 1.5
            "8h" -> 1.65
            "1d" -> 1.8
            else -> 1.0
        }
    }

    fun timeframeNoiseCeiling(timeframe: String): Double {
        return when (timeframe) {
            "1m" -> 0.0045
            "5m" -> 0.004
            "15m" -> 0.0035
            "30m" -> 0.00325
            "1h" -> 0.003
            "2h" -> 0.00275
            "4h" -> 0.0025
            "8h" -> 0.00225
            "1d" -> 0.002
            else -> 0.0035
        }
    }

    fun formatPrice(value: Double): String = "%.5f".format(value)

    fun formatSigned(value: Double): String = if (value >= 0) "+${formatPrice(value)}" else formatPrice(value)

    fun formatPercentSigned(value: Double): String {
        return if (value >= 0) "+${"%.2f".format(value)}%" else "${"%.2f".format(value)}%"
    }

    fun formatPercent(value: Double): String = "%.2f".format(value)

    fun formatScore(value: Double): String = "%.1f".format(value)

    fun softScore(value: Double, passAt: Double, partialAt: Double): Double {
        return when {
            value >= passAt -> 1.0
            value >= partialAt -> 0.5
            else -> 0.0
        }
    }

    fun rangedScore(
        value: Double,
        idealLow: Double,
        idealHigh: Double,
        partialLow: Double,
        partialHigh: Double
    ): Double {
        return when {
            value in idealLow..idealHigh -> 1.0
            value in partialLow..partialHigh -> 0.5
            else -> 0.0
        }
    }

    fun modeSupportThreshold(mode: ConfirmationMode, setupType: SetupType): Double {
        val base = when (mode) {
            ConfirmationMode.CONSERVATIVE -> 3.2
            ConfirmationMode.MODERATE -> 2.5
            ConfirmationMode.AGGRESSIVE -> 1.9
            ConfirmationMode.LENIENT -> 1.35
        }
        return if (setupType == SetupType.BREAKOUT && mode != ConfirmationMode.CONSERVATIVE) base - 0.1 else base
    }

    fun minimumCoreRequired(mode: ConfirmationMode): Int {
        return when (mode) {
            ConfirmationMode.CONSERVATIVE -> 5
            ConfirmationMode.MODERATE -> 4
            ConfirmationMode.AGGRESSIVE -> 3
            ConfirmationMode.LENIENT -> 3
        }
    }

    fun biasActivationThreshold(mode: ConfirmationMode): Double {
        return when (mode) {
            ConfirmationMode.CONSERVATIVE -> 6.2
            ConfirmationMode.MODERATE -> 5.4
            ConfirmationMode.AGGRESSIVE -> 4.7
            ConfirmationMode.LENIENT -> 3.9
        }
    }

    fun topDownDirectionalThreshold(mode: ConfirmationMode): Double {
        return when (mode) {
            ConfirmationMode.CONSERVATIVE -> 0.55
            ConfirmationMode.MODERATE -> 0.45
            ConfirmationMode.AGGRESSIVE -> 0.35
            ConfirmationMode.LENIENT -> 0.22
        }
    }

    fun confluenceGateForMode(mode: ConfirmationMode): Double {
        return when (mode) {
            ConfirmationMode.CONSERVATIVE -> 0.58
            ConfirmationMode.MODERATE -> 0.50
            ConfirmationMode.AGGRESSIVE -> 0.42
            ConfirmationMode.LENIENT -> 0.30
        }
    }

    fun analyzeCandlestickPatterns(
        candles: List<MarketCandle>,
        averageStep: Double
    ): PatternSignalSummary {
        if (candles.size < 4) return PatternSignalSummary(0.0, "No pattern edge")
        val recent = candles.takeLast(5)
        val last = recent.last()
        val prev = recent[recent.lastIndex - 1]

        val lastRange = (last.high - last.low).coerceAtLeast(0.00001)
        val lastBody = abs(last.close - last.open)
        val lastUpperShadow = last.high - maxOf(last.close, last.open)
        val lastLowerShadow = minOf(last.close, last.open) - last.low

        val bullishPin = lastLowerShadow >= lastRange * 0.6 && lastBody <= lastRange * 0.25 && lastUpperShadow <= lastRange * 0.2
        val bearishPin = lastUpperShadow >= lastRange * 0.6 && lastBody <= lastRange * 0.25 && lastLowerShadow <= lastRange * 0.2
        val bullishEngulfing = prev.close < prev.open && last.close > last.open && last.open <= prev.close && last.close >= prev.open
        val bearishEngulfing = prev.close > prev.open && last.close < last.open && last.open >= prev.close && last.close <= prev.open

        val firstOfThree = if (recent.size >= 3) recent[recent.lastIndex - 2] else prev
        val morningStar = firstOfThree.close < firstOfThree.open && last.close > last.open &&
            last.close > ((firstOfThree.open + firstOfThree.close) / 2.0)
        val eveningStar = firstOfThree.close > firstOfThree.open && last.close < last.open &&
            last.close < ((firstOfThree.open + firstOfThree.close) / 2.0)

        var bias = 0.0
        val tags = mutableListOf<String>()
        if (bullishEngulfing) {
            bias += 0.65
            tags += "bullish engulfing"
        }
        if (bearishEngulfing) {
            bias -= 0.65
            tags += "bearish engulfing"
        }
        if (bullishPin) {
            bias += 0.55
            tags += "bullish pin bar"
        }
        if (bearishPin) {
            bias -= 0.55
            tags += "bearish pin bar"
        }
        if (morningStar) {
            bias += 0.75
            tags += "morning star"
        }
        if (eveningStar) {
            bias -= 0.75
            tags += "evening star"
        }

        val breakoutBody = lastBody >= averageStep * 1.5
        if (bias == 0.0 && breakoutBody) {
            bias = if (last.close > last.open) 0.3 else -0.3
            tags += "single-candle impulse"
        }

        val summary = if (tags.isEmpty()) "No high-quality candle pattern" else "Detected ${tags.joinToString(", ")}"
        return PatternSignalSummary(bias.coerceIn(-1.0, 1.0), summary)
    }

    fun detectNewsPulse(candles: List<MarketCandle>): NewsPulseContext {
        if (candles.size < 12) return NewsPulseContext(0.0, "No event-volatility pulse")
        val sample = candles.takeLast(12)
        val recentBody = sample.takeLast(3).map { abs(it.close - it.open) }.average()
        val baselineBody = sample.take(9).map { abs(it.close - it.open) }.average().coerceAtLeast(0.00001)
        val recentRange = sample.takeLast(3).map { (it.high - it.low).coerceAtLeast(0.00001) }.average()
        val baselineRange = sample.take(9).map { (it.high - it.low).coerceAtLeast(0.00001) }.average().coerceAtLeast(0.00001)
        val bodySpike = recentBody / baselineBody
        val rangeSpike = recentRange / baselineRange
        val pulse = ((bodySpike + rangeSpike) / 2.0 - 1.0).coerceAtLeast(0.0)
        val summary = when {
            pulse >= 1.2 -> "High-volatility pulse (news-like move) detected"
            pulse >= 0.5 -> "Moderate volatility expansion detected"
            else -> "No event-volatility pulse"
        }
        return NewsPulseContext(pulseScore = pulse, summary = summary)
    }

    fun analyzeIccPhase(
        sourceCandles: List<MarketCandle>,
        bullishBreakoutScore: Double,
        bearishBreakoutScore: Double,
        bullishPullbackScore: Double,
        bearishPullbackScore: Double,
        bullishMomentumScore: Double,
        bearishMomentumScore: Double,
        trendStrength: Double
    ): IccPhaseContext {
        if (sourceCandles.size < 12) return IccPhaseContext("no_setup", 0.0, 0.0, "Insufficient candles for ICC phase")
        val bullishBias = (bullishBreakoutScore * 0.4) + (bullishPullbackScore * 0.25) + (bullishMomentumScore * 0.35)
        val bearishBias = (bearishBreakoutScore * 0.4) + (bearishPullbackScore * 0.25) + (bearishMomentumScore * 0.35)
        val directionalBias = (bullishBias - bearishBias).coerceIn(-1.0, 1.0)
        val setupDirection = if (directionalBias >= 0) 1 else -1

        val recent = sourceCandles.takeLast(8)
        val prior = recent.dropLast(1)
        val last = recent.last()
        val priorHigh = prior.maxOf { it.high }
        val priorLow = prior.minOf { it.low }
        val breakoutDetected = (setupDirection > 0 && last.close > priorHigh) || (setupDirection < 0 && last.close < priorLow)
        val pullbackActive = if (setupDirection > 0) bullishPullbackScore >= 0.5 else bearishPullbackScore >= 0.5
        val internalConfirm = if (setupDirection > 0) {
            val last3 = sourceCandles.takeLast(3)
            last3.count { it.close > it.open } >= 2 && last3.last().low > last3.first().low
        } else {
            val last3 = sourceCandles.takeLast(3)
            last3.count { it.close < it.open } >= 2 && last3.last().high < last3.first().high
        }

        val phase = when {
            breakoutDetected && pullbackActive && internalConfirm -> "confirmation"
            breakoutDetected && pullbackActive -> "correction"
            breakoutDetected -> "indication"
            else -> "no_setup"
        }
        val confidence = ((if (breakoutDetected) 0.35 else 0.0) +
            (if (pullbackActive) 0.30 else 0.0) +
            (if (internalConfirm) 0.20 else 0.0) +
            (if (abs(trendStrength) >= 0.35) 0.15 else 0.05)
            ).coerceIn(0.0, 0.95)
        val summary = when (phase) {
            "confirmation" -> "BOS, correction, and internal confirmation are aligned"
            "correction" -> "BOS found and correction is active; waiting internal confirmation"
            "indication" -> "Early BOS indication; wait for pullback into better zone"
            else -> "No ICC setup currently active"
        }
        return IccPhaseContext(phase = phase, confidence = confidence, directionalBias = directionalBias, summary = summary)
    }

    fun analyzeMultiTimeframeConsensus(
        timeframePlan: AnalysisTimeframePlan,
        candleStack: Map<String, List<MarketCandle>>,
        fallbackCandles: List<MarketCandle>
    ): MultiTimeframeContext {
        val macroCandles = candleStack[timeframePlan.macro].orEmpty().ifEmpty { fallbackCandles }
        val structureCandles = candleStack[timeframePlan.structure].orEmpty().ifEmpty { fallbackCandles }
        val setupCandles = candleStack[timeframePlan.setup].orEmpty().ifEmpty { fallbackCandles }
        val triggerCandles = candleStack[timeframePlan.trigger].orEmpty().ifEmpty { fallbackCandles }
        if (setupCandles.size < 12) return MultiTimeframeContext(0.0, 0.0, "Insufficient candles for multi-timeframe consensus")
        val macroBias = trendDirectionFromCandles(macroCandles.takeLast(20))
        val structureBias = trendDirectionFromCandles(structureCandles.takeLast(20))
        val setupBias = trendDirectionFromCandles(setupCandles.takeLast(20))
        val triggerBias = trendDirectionFromCandles(triggerCandles.takeLast(20))
        val weightedBias = ((macroBias * 0.40) + (structureBias * 0.30) + (setupBias * 0.20) + (triggerBias * 0.10)).coerceIn(-1.0, 1.0)
        val signs = listOf(macroBias, structureBias, setupBias, triggerBias).map {
            when {
                it > 0.15 -> 1
                it < -0.15 -> -1
                else -> 0
            }
        }
        val anchor = signs.firstOrNull { it != 0 } ?: 0
        val aligned = signs.count { it != 0 && it == anchor }
        val alignmentScore = (aligned.toDouble() / 4.0).coerceIn(0.0, 1.0)
        val summary = "Stack ${timeframePlan.macro}/${timeframePlan.structure}/${timeframePlan.setup}/${timeframePlan.trigger} bias ${formatSigned(weightedBias)} with alignment ${formatScore(alignmentScore)}"
        return MultiTimeframeContext(weightedBias, alignmentScore, summary)
    }

    private fun trendDirectionFromCandles(candles: List<MarketCandle>): Double {
        if (candles.size < 6) return 0.0
        val closes = candles.map { it.close }
        val fast = closes.takeLast(minOf(4, closes.size)).average()
        val slow = closes.takeLast(minOf(10, closes.size)).average()
        val avgRange = candles.map { (it.high - it.low).coerceAtLeast(0.00001) }.average().coerceAtLeast(0.00001)
        return ((fast - slow) / avgRange).coerceIn(-1.0, 1.0)
    }

    fun buildTopDownContext(
        timeframePlan: AnalysisTimeframePlan,
        candleStack: Map<String, List<MarketCandle>>,
        candles: List<MarketCandle>,
        prices: List<Double>,
        averageStep: Double
    ): TopDownContext {
        val macroSource = candleStack[timeframePlan.macro].orEmpty()
        val structureSource = candleStack[timeframePlan.structure].orEmpty()
        val setupSource = candleStack[timeframePlan.setup].orEmpty().ifEmpty {
            if (candles.size >= 16) candles.takeLast(60) else buildSyntheticCandles(prices)
        }
        val triggerSource = candleStack[timeframePlan.trigger].orEmpty().ifEmpty { setupSource }
        val source = structureSource.ifEmpty { setupSource }
        if (setupSource.size < 12) {
            return TopDownContext(
                higherTimeframeBias = TradeBias.NEUTRAL,
                higherTimeframeSummary = "Insufficient candles for explicit timeframe stack",
                structureState = "range_bound",
                setupState = "undetermined",
                triggerState = "undetermined",
                supportLevel = null,
                resistanceLevel = null,
                zoneBias = 0.0,
                zoneSummary = "Not enough structure for supply/demand zones",
                liquidityBias = 0.0,
                liquiditySummary = "Not enough structure for liquidity mapping",
                imbalanceBias = 0.0,
                imbalanceSummary = "No clear imbalance profile",
                stackAlignmentScore = 0.0,
                confluenceScore = 0.0
            )
        }

        val macro = macroSource.ifEmpty { source.takeLast(20) }
        val higherCloses = macro.map { it.close }
        val higherShort = higherCloses.takeLast(minOf(4, higherCloses.size)).average()
        val higherLong = higherCloses.takeLast(minOf(8, higherCloses.size)).average()
        val higherRange = macro.map { (it.high - it.low).coerceAtLeast(0.00001) }.average().coerceAtLeast(0.00001)
        val htfTrend = (higherShort - higherLong) / higherRange
        val higherBias = when {
            htfTrend > 0.35 -> TradeBias.BULLISH
            htfTrend < -0.35 -> TradeBias.BEARISH
            else -> TradeBias.NEUTRAL
        }
        val structureBias = trendDirectionFromCandles(source.takeLast(20))
        val setupBias = trendDirectionFromCandles(setupSource.takeLast(20))
        val triggerBias = trendDirectionFromCandles(triggerSource.takeLast(20))
        val stackDirections = listOf(
            biasDirection(htfTrend),
            biasDirection(structureBias),
            biasDirection(setupBias),
            biasDirection(triggerBias)
        )
        val stackAnchor = stackDirections.firstOrNull { it != 0 } ?: 0
        val alignedLayers = stackDirections.count { it != 0 && it == stackAnchor }
        val stackAlignment = (alignedLayers.toDouble() / 4.0).coerceIn(0.0, 1.0)
        val setupState = when {
            biasDirection(htfTrend) != 0 && biasDirection(setupBias) == -biasDirection(htfTrend) -> "pullback"
            biasDirection(setupBias) == biasDirection(htfTrend) && biasDirection(htfTrend) != 0 -> "aligned"
            else -> "mixed"
        }
        val triggerState = when {
            biasDirection(triggerBias) == biasDirection(setupBias) && biasDirection(setupBias) != 0 -> "entry_ready"
            biasDirection(triggerBias) == 0 -> "consolidating"
            else -> "early_reversal"
        }
        val htfSummary = "Stack ${timeframePlan.macro}/${timeframePlan.structure}/${timeframePlan.setup}/${timeframePlan.trigger}: macro ${formatSigned(htfTrend)}, structure ${formatSigned(structureBias)}, setup $setupState, trigger $triggerState"

        val last = setupSource.last().close
        val (swingHighs, swingLows) = findSwingLevels(source)
        val support = swingLows.filter { it <= last }.maxOrNull()
        val resistance = swingHighs.filter { it >= last }.minOrNull()
        val structureState = when {
            resistance != null && last > resistance + (averageStep * 0.20) -> "bullish_structure"
            support != null && last < support - (averageStep * 0.20) -> "bearish_structure"
            else -> "range_bound"
        }

        val zoneContext = computeZoneContext(source, last, averageStep)
        val liquidityContext = computeLiquidityContext(source, last, averageStep)
        val imbalanceContext = computeImbalanceContext(source, averageStep)
        val trendStrength = softScore(abs(htfTrend), 0.85, 0.35)
        val structureStrength = if (structureState == "range_bound") 0.5 else 1.0
        val contextStrength = (
            (trendStrength * 0.35) +
                (structureStrength * 0.20) +
                (abs(zoneContext.bias).coerceAtMost(1.0) * 0.15) +
                (abs(liquidityContext.bias).coerceAtMost(1.0) * 0.15) +
                (abs(imbalanceContext.bias).coerceAtMost(1.0) * 0.15)
            )

        return TopDownContext(
            higherTimeframeBias = higherBias,
            higherTimeframeSummary = htfSummary,
            structureState = structureState,
            setupState = setupState,
            triggerState = triggerState,
            supportLevel = support,
            resistanceLevel = resistance,
            zoneBias = zoneContext.bias,
            zoneSummary = zoneContext.summary,
            liquidityBias = liquidityContext.bias,
            liquiditySummary = liquidityContext.summary,
            imbalanceBias = imbalanceContext.bias,
            imbalanceSummary = imbalanceContext.summary,
            stackAlignmentScore = stackAlignment,
            confluenceScore = ((contextStrength * 0.75) + (stackAlignment * 0.25)).coerceIn(0.0, 1.0)
        )
    }

    private fun biasDirection(value: Double): Int {
        return when {
            value > 0.15 -> 1
            value < -0.15 -> -1
            else -> 0
        }
    }

    private fun aggregateCandles(candles: List<MarketCandle>, chunkSize: Int): List<MarketCandle> {
        if (candles.isEmpty() || chunkSize <= 1) return candles
        return candles.chunked(chunkSize).map { chunk ->
            MarketCandle(
                epoch = chunk.first().epoch,
                open = chunk.first().open,
                high = chunk.maxOf { it.high },
                low = chunk.minOf { it.low },
                close = chunk.last().close
            )
        }
    }

    private fun findSwingLevels(candles: List<MarketCandle>): Pair<List<Double>, List<Double>> {
        if (candles.size < 7) return emptyList<Double>() to emptyList()
        val highs = mutableListOf<Double>()
        val lows = mutableListOf<Double>()
        for (i in 2 until candles.lastIndex - 1) {
            val center = candles[i]
            val neighborhood = candles.subList(i - 2, i + 3)
            if (center.high == neighborhood.maxOf { it.high }) highs += center.high
            if (center.low == neighborhood.minOf { it.low }) lows += center.low
        }
        return highs.takeLast(5) to lows.takeLast(5)
    }

    private fun computeZoneContext(
        candles: List<MarketCandle>,
        lastPrice: Double,
        averageStep: Double
    ): DirectionalContext {
        val sample = candles.takeLast(30)
        val demandLevels = mutableListOf<Double>()
        val supplyLevels = mutableListOf<Double>()
        sample.forEach { candle ->
            val range = (candle.high - candle.low).coerceAtLeast(0.00001)
            val bodyTop = maxOf(candle.open, candle.close)
            val bodyBottom = minOf(candle.open, candle.close)
            val lowerWick = bodyBottom - candle.low
            val upperWick = candle.high - bodyTop
            if (lowerWick >= range * 0.55 && candle.close >= candle.open) demandLevels += candle.low
            if (upperWick >= range * 0.55 && candle.close <= candle.open) supplyLevels += candle.high
        }

        val nearestDemand = demandLevels.minOfOrNull { abs(lastPrice - it) }
        val nearestSupply = supplyLevels.minOfOrNull { abs(lastPrice - it) }
        val bias = when {
            nearestDemand != null && nearestSupply != null && nearestDemand + (averageStep * 0.10) < nearestSupply ->
                ((nearestSupply - nearestDemand) / nearestSupply.coerceAtLeast(averageStep)).coerceIn(0.0, 1.0)
            nearestDemand != null && nearestSupply != null && nearestSupply + (averageStep * 0.10) < nearestDemand ->
                -((nearestDemand - nearestSupply) / nearestDemand.coerceAtLeast(averageStep)).coerceIn(0.0, 1.0)
            nearestDemand != null && nearestSupply == null -> 0.35
            nearestSupply != null && nearestDemand == null -> -0.35
            else -> 0.0
        }
        val summary = "Nearest demand ${nearestDemand?.let(::formatPrice) ?: "-"}, supply ${nearestSupply?.let(::formatPrice) ?: "-"}"
        return DirectionalContext(bias = bias, summary = summary)
    }

    private fun computeLiquidityContext(
        candles: List<MarketCandle>,
        lastPrice: Double,
        averageStep: Double
    ): DirectionalContext {
        if (candles.size < 10) return DirectionalContext(0.0, "Insufficient candles for liquidity map")
        val recent = candles.takeLast(8)
        val latest = recent.last()
        val prior = recent.dropLast(1)
        val priorHigh = prior.maxOf { it.high }
        val priorLow = prior.minOf { it.low }
        val tolerance = averageStep * 0.20

        val buySideSweep = latest.high > priorHigh + tolerance && latest.close < priorHigh
        val sellSideSweep = latest.low < priorLow - tolerance && latest.close > priorLow
        val equalHighCount = prior.zipWithNext().count { abs(it.first.high - it.second.high) <= tolerance }
        val equalLowCount = prior.zipWithNext().count { abs(it.first.low - it.second.low) <= tolerance }

        val bias = when {
            sellSideSweep -> 1.0
            buySideSweep -> -1.0
            equalLowCount > equalHighCount -> 0.5
            equalHighCount > equalLowCount -> -0.5
            else -> 0.0
        }
        val summary = when {
            sellSideSweep -> "Sell-side liquidity was swept below ${formatPrice(priorLow)} and price reclaimed"
            buySideSweep -> "Buy-side liquidity was swept above ${formatPrice(priorHigh)} and price rejected"
            else -> "Liquidity pools balanced near ${formatPrice(lastPrice)} (eqH $equalHighCount / eqL $equalLowCount)"
        }
        return DirectionalContext(bias = bias, summary = summary)
    }

    private fun computeImbalanceContext(
        candles: List<MarketCandle>,
        averageStep: Double
    ): DirectionalContext {
        val sample = candles.takeLast(24)
        if (sample.size < 3) return DirectionalContext(0.0, "Not enough candles for FVG scan")
        var bullishGap = 0.0
        var bearishGap = 0.0
        for (i in 1 until sample.lastIndex) {
            val prev = sample[i - 1]
            val next = sample[i + 1]
            if (prev.high < next.low) {
                bullishGap = maxOf(bullishGap, (next.low - prev.high) / averageStep.coerceAtLeast(0.00001))
            }
            if (prev.low > next.high) {
                bearishGap = maxOf(bearishGap, (prev.low - next.high) / averageStep.coerceAtLeast(0.00001))
            }
        }
        val bias = ((bullishGap - bearishGap) / 3.0).coerceIn(-1.0, 1.0)
        val summary = when {
            bias > 0.2 -> "Bullish imbalance stack is stronger than bearish gaps"
            bias < -0.2 -> "Bearish imbalance stack is stronger than bullish gaps"
            else -> "No dominant imbalance edge"
        }
        return DirectionalContext(bias = bias, summary = summary)
    }

    fun directionalScore(signedBias: Double, bullish: Boolean): Double {
        val directional = if (bullish) signedBias else -signedBias
        return when {
            directional >= 0.35 -> 1.0
            directional >= 0.15 -> 0.5
            else -> 0.0
        }
    }

    fun emptyEvidence(): EvidenceSnapshot {
        return EvidenceSnapshot(
            winRate = 0,
            profitFactor = "-",
            expectancyR = "0.00R",
            maxDrawdownR = "0.00R",
            avgR = "0.00R",
            medianR = "0.00R",
            sampleSize = 0,
            confidenceBadge = "Unproven",
            robustness = "Unstable",
            lastOutcomes = emptyList()
        )
    }

    fun buildEvidenceSnapshot(
        candles: List<MarketCandle>,
        fallbackPrices: List<Double>
    ): EvidenceSnapshot {
        val sourceCandles = if (candles.size >= 8) {
            candles.takeLast(60)
        } else {
            buildSyntheticCandles(fallbackPrices)
        }
        if (sourceCandles.size < 8) return emptyEvidence()

        val averageRange = sourceCandles
            .map { (it.high - it.low).coerceAtLeast(0.00001) }
            .average()
            .coerceAtLeast(0.00001)

        val outcomes = mutableListOf<Double>()
        for (index in 3 until sourceCandles.lastIndex) {
            val current = sourceCandles[index]
            val prior = sourceCandles[index - 3]
            val next = sourceCandles[index + 1]
            val directionalMove = when {
                current.close > prior.close -> (next.close - current.close) / averageRange
                current.close < prior.close -> (current.close - next.close) / averageRange
                else -> 0.0
            }
            outcomes += directionalMove.coerceIn(-2.5, 2.5)
        }
        if (outcomes.isEmpty()) return emptyEvidence()

        val wins = outcomes.count { it > 0.0 }
        val grossWin = outcomes.filter { it > 0.0 }.sum()
        val grossLoss = outcomes.filter { it < 0.0 }.sumOf { abs(it) }
        val expectancy = outcomes.average()
        val sorted = outcomes.sorted()
        val median = sorted[sorted.size / 2]

        var equity = 0.0
        var peak = 0.0
        var maxDrawdown = 0.0
        outcomes.forEach { result ->
            equity += result
            if (equity > peak) peak = equity
            maxDrawdown = maxOf(maxDrawdown, peak - equity)
        }

        val midpoint = outcomes.size / 2
        val firstHalf = outcomes.take(midpoint).ifEmpty { outcomes }
        val secondHalf = outcomes.drop(midpoint).ifEmpty { outcomes }
        val robustness = if (abs(firstHalf.average() - secondHalf.average()) <= 0.15) {
            "Stable"
        } else {
            "Unstable"
        }

        val confidenceBadge = when {
            expectancy > 0.15 && outcomes.size > 200 -> "Strong"
            expectancy > 0.0 && outcomes.size >= 50 -> "Medium"
            else -> "Unproven"
        }

        return EvidenceSnapshot(
            winRate = ((wins.toDouble() / outcomes.size) * 100).toInt(),
            profitFactor = if (grossLoss > 0.0) "%.2f".format(grossWin / grossLoss) else "Inf",
            expectancyR = "%.2fR".format(expectancy),
            maxDrawdownR = "%.2fR".format(maxDrawdown),
            avgR = "%.2fR".format(expectancy),
            medianR = "%.2fR".format(median),
            sampleSize = outcomes.size,
            confidenceBadge = confidenceBadge,
            robustness = robustness,
            lastOutcomes = outcomes.takeLast(20).map { if (it >= 0.0) "W ${"%.2f".format(it)}R" else "L ${"%.2f".format(it)}R" }
        )
    }

    fun buildSyntheticCandles(prices: List<Double>): List<MarketCandle> {
        if (prices.size < 4) return emptyList()
        return prices
            .takeLast(120)
            .chunked(2)
            .mapIndexed { index, chunk ->
                val open = chunk.first()
                val close = chunk.last()
                MarketCandle(
                    epoch = index.toLong(),
                    open = open,
                    high = chunk.maxOrNull() ?: open,
                    low = chunk.minOrNull() ?: open,
                    close = close
                )
            }
    }
}
