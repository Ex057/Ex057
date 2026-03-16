package com.ex57.capital.analysis

import kotlin.math.abs
import com.ex57.capital.model.AnalysisResult
import com.ex57.capital.model.Confirmation
import com.ex57.capital.model.ConfirmationMode
import com.ex57.capital.model.EvidenceSnapshot
import com.ex57.capital.model.MarketCandle
import com.ex57.capital.model.SetupType
import com.ex57.capital.model.TradeDecision
import com.ex57.capital.model.TradeSetup
import com.ex57.capital.model.TradeBias
import com.ex57.capital.model.TradingSymbol

object AnalysisStub {
    fun analyze(
        symbol: TradingSymbol,
        timeframe: String,
        mode: ConfirmationMode,
        candles: List<MarketCandle>,
        recentPrices: List<Double>
    ): AnalysisResult {
        val closingPrices = if (candles.size >= 12) {
            candles.takeLast(50).map { it.close }
        } else {
            recentPrices.takeLast(50)
        }

        if (closingPrices.size < 12) {
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
                nextTrigger = "Wait for more live candles before evaluating this market."
            )
        }

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
        val moveFloor = timeframeMoveFloor(timeframe) * averageStep
        val noiseCeiling = timeframeNoiseCeiling(timeframe)
        val evidence = buildEvidenceSnapshot(candles, prices)
        val sourceCandles = if (candles.size >= 8) candles.takeLast(80) else buildSyntheticCandles(prices)
        val topDown = buildTopDownContext(candles, prices, averageStep)
        val noiseScore = softScore(noiseCeiling - noiseRatio, noiseCeiling * 0.10, 0.0)
        val timeframeScore = softScore(abs(momentum) - moveFloor, moveFloor * 0.30, 0.0)
        val bullishTrendScore = softScore(trendStrength, 1.1, 0.35)
        val bearishTrendScore = softScore(-trendStrength, 1.1, 0.35)
        val bullishMomentumScore = softScore(momentum, moveFloor * 1.2, moveFloor * 0.45)
        val bearishMomentumScore = softScore(-momentum, moveFloor * 1.2, moveFloor * 0.45)
        val bullishPullbackScore = if (trendStrength > 0.2) rangedScore(pullbackDistance, -0.45, 0.55, -0.95, 1.10) else 0.0
        val bearishPullbackScore = if (trendStrength < -0.2) rangedScore(pullbackDistance, -0.55, 0.45, -1.10, 0.95) else 0.0
        val bullishBreakoutScore = if (breakoutUp) 1.0 else if (last >= (priorWindow.maxOrNull() ?: last) - (averageStep * 0.35)) 0.5 else 0.0
        val bearishBreakoutScore = if (breakoutDown) 1.0 else if (last <= (priorWindow.minOrNull() ?: last) + (averageStep * 0.35)) 0.5 else 0.0
        val htfBullScore = when (topDown.higherTimeframeBias) {
            TradeBias.BULLISH -> 1.0
            TradeBias.NEUTRAL -> 0.5
            TradeBias.BEARISH -> 0.0
        }
        val htfBearScore = when (topDown.higherTimeframeBias) {
            TradeBias.BEARISH -> 1.0
            TradeBias.NEUTRAL -> 0.5
            TradeBias.BULLISH -> 0.0
        }
        val structureBullScore = when (topDown.structureState) {
            "bullish_structure" -> 1.0
            "range_bound" -> 0.5
            else -> 0.0
        }
        val structureBearScore = when (topDown.structureState) {
            "bearish_structure" -> 1.0
            "range_bound" -> 0.5
            else -> 0.0
        }
        val zoneBullScore = directionalScore(topDown.zoneBias, bullish = true)
        val zoneBearScore = directionalScore(topDown.zoneBias, bullish = false)
        val liquidityBullScore = directionalScore(topDown.liquidityBias, bullish = true)
        val liquidityBearScore = directionalScore(topDown.liquidityBias, bullish = false)
        val imbalanceBullScore = directionalScore(topDown.imbalanceBias, bullish = true)
        val imbalanceBearScore = directionalScore(topDown.imbalanceBias, bullish = false)
        val patternSignals = analyzeCandlestickPatterns(sourceCandles, averageStep)
        val newsPulse = detectNewsPulse(sourceCandles)
        val iccPhase = analyzeIccPhase(
            sourceCandles = sourceCandles,
            bullishBreakoutScore = bullishBreakoutScore,
            bearishBreakoutScore = bearishBreakoutScore,
            bullishPullbackScore = bullishPullbackScore,
            bearishPullbackScore = bearishPullbackScore,
            bullishMomentumScore = bullishMomentumScore,
            bearishMomentumScore = bearishMomentumScore,
            trendStrength = trendStrength
        )
        val mtfContext = analyzeMultiTimeframeConsensus(sourceCandles)
        val bullishPatternScore = directionalScore(patternSignals.directionalBias, bullish = true)
        val bearishPatternScore = directionalScore(patternSignals.directionalBias, bullish = false)
        val bullishIccScore = directionalScore(iccPhase.directionalBias, bullish = true)
        val bearishIccScore = directionalScore(iccPhase.directionalBias, bullish = false)
        val bullishMtfScore = directionalScore(mtfContext.directionalBias, bullish = true)
        val bearishMtfScore = directionalScore(mtfContext.directionalBias, bullish = false)
        val pulseBoost = when {
            newsPulse.pulseScore >= 1.0 -> 0.35
            newsPulse.pulseScore >= 0.5 -> 0.2
            else -> 0.0
        }
        val topDownBullScore = (
            (htfBullScore * 0.35) +
                (structureBullScore * 0.20) +
                (zoneBullScore * 0.15) +
                (liquidityBullScore * 0.15) +
                (imbalanceBullScore * 0.15)
            )
        val topDownBearScore = (
            (htfBearScore * 0.35) +
                (structureBearScore * 0.20) +
                (zoneBearScore * 0.15) +
                (liquidityBearScore * 0.15) +
                (imbalanceBearScore * 0.15)
            )

        val trendPullbackBullCore = listOf(bullishTrendScore, noiseScore, timeframeScore, topDownBullScore, topDown.confluenceScore, bullishPatternScore, bullishIccScore, bullishMtfScore).count { it >= 0.5 }
        val trendPullbackBearCore = listOf(bearishTrendScore, noiseScore, timeframeScore, topDownBearScore, topDown.confluenceScore, bearishPatternScore, bearishIccScore, bearishMtfScore).count { it >= 0.5 }
        val breakoutBullCore = listOf(bullishBreakoutScore, noiseScore, timeframeScore, topDownBullScore, topDown.confluenceScore, bullishPatternScore, bullishIccScore, bullishMtfScore).count { it >= 0.5 }
        val breakoutBearCore = listOf(bearishBreakoutScore, noiseScore, timeframeScore, topDownBearScore, topDown.confluenceScore, bearishPatternScore, bearishIccScore, bearishMtfScore).count { it >= 0.5 }

        val trendPullbackBullScore = (bullishTrendScore * 1.8) + (bullishPullbackScore * 1.6) + (bullishMomentumScore * 1.0) + (noiseScore * 1.1) + (timeframeScore * 1.0) + (bullishBreakoutScore * 0.4) + (topDownBullScore * 1.6) + (topDown.confluenceScore * 1.0) + (bullishPatternScore * 1.1) + (bullishIccScore * 1.1) + (bullishMtfScore * 1.2)
        val trendPullbackBearScore = (bearishTrendScore * 1.8) + (bearishPullbackScore * 1.6) + (bearishMomentumScore * 1.0) + (noiseScore * 1.1) + (timeframeScore * 1.0) + (bearishBreakoutScore * 0.4) + (topDownBearScore * 1.6) + (topDown.confluenceScore * 1.0) + (bearishPatternScore * 1.1) + (bearishIccScore * 1.1) + (bearishMtfScore * 1.2)
        val breakoutBullScore = (bullishBreakoutScore * 1.9) + (bullishMomentumScore * 1.5) + (noiseScore * 1.0) + (timeframeScore * 1.2) + (bullishTrendScore * 0.8) + ((1.0 - bullishPullbackScore) * 0.3) + (topDownBullScore * 1.5) + (topDown.confluenceScore * 1.0) + (bullishPatternScore * 0.9) + (bullishIccScore * 1.2) + (bullishMtfScore * 1.0) + pulseBoost
        val breakoutBearScore = (bearishBreakoutScore * 1.9) + (bearishMomentumScore * 1.5) + (noiseScore * 1.0) + (timeframeScore * 1.2) + (bearishTrendScore * 0.8) + ((1.0 - bearishPullbackScore) * 0.3) + (topDownBearScore * 1.5) + (topDown.confluenceScore * 1.0) + (bearishPatternScore * 0.9) + (bearishIccScore * 1.2) + (bearishMtfScore * 1.0) + pulseBoost

        val candidateScores = listOf(
            CandidateSetup(SetupType.TREND_PULLBACK, TradeBias.BULLISH, trendPullbackBullCore, trendPullbackBullScore),
            CandidateSetup(SetupType.TREND_PULLBACK, TradeBias.BEARISH, trendPullbackBearCore, trendPullbackBearScore),
            CandidateSetup(SetupType.BREAKOUT, TradeBias.BULLISH, breakoutBullCore, breakoutBullScore),
            CandidateSetup(SetupType.BREAKOUT, TradeBias.BEARISH, breakoutBearCore, breakoutBearScore)
        )
        val bestCandidate = candidateScores.maxByOrNull { it.score }!!
        val runnerUp = candidateScores
            .filterNot { it === bestCandidate }
            .maxByOrNull { it.score }

        val setupType = bestCandidate.type
        val topDownDirectionalGate = if (bestCandidate.bias == TradeBias.BULLISH) topDownBullScore else topDownBearScore
        val bias = if (bestCandidate.corePassed >= minimumCoreRequired(mode) &&
            bestCandidate.score >= biasActivationThreshold(mode) &&
            topDownDirectionalGate >= topDownDirectionalThreshold(mode) &&
            ((bestCandidate.score - (runnerUp?.score ?: 0.0)) >= 0.55)
        ) {
            bestCandidate.bias
        } else {
            TradeBias.NEUTRAL
        }

        val confirmations = listOf(
            Confirmation(
                "Higher-Timeframe Bias",
                when (bias) {
                    TradeBias.BULLISH -> htfBullScore >= 0.5
                    TradeBias.BEARISH -> htfBearScore >= 0.5
                    TradeBias.NEUTRAL -> topDown.higherTimeframeBias == TradeBias.NEUTRAL
                },
                topDown.higherTimeframeSummary
            ),
            Confirmation(
                "Trend Structure",
                when (bias) {
                    TradeBias.BULLISH -> bullishTrendScore >= 0.5
                    TradeBias.BEARISH -> bearishTrendScore >= 0.5
                    TradeBias.NEUTRAL -> maxOf(bullishTrendScore, bearishTrendScore) < 0.5
                },
                "Score ${formatScore(maxOf(bullishTrendScore, bearishTrendScore))} from short mean ${formatPrice(shortAverage)} vs long mean ${formatPrice(longAverage)}"
            ),
            Confirmation(
                "Momentum",
                when (bias) {
                    TradeBias.BULLISH -> bullishMomentumScore >= 0.5
                    TradeBias.BEARISH -> bearishMomentumScore >= 0.5
                    TradeBias.NEUTRAL -> maxOf(bullishMomentumScore, bearishMomentumScore) < 0.5
                },
                "Score ${formatScore(maxOf(bullishMomentumScore, bearishMomentumScore))} from 4-candle impulse ${formatSigned(momentum)}"
            ),
            Confirmation(
                "Market Structure",
                when (bias) {
                    TradeBias.BULLISH -> structureBullScore >= 0.5
                    TradeBias.BEARISH -> structureBearScore >= 0.5
                    TradeBias.NEUTRAL -> topDown.structureState == "range_bound"
                },
                "State ${topDown.structureState.replace('_', ' ')} around S ${topDown.supportLevel?.let(::formatPrice) ?: "-"} / R ${topDown.resistanceLevel?.let(::formatPrice) ?: "-"}"
            ),
            Confirmation(
                "Pullback Quality",
                when (bias) {
                    TradeBias.BULLISH -> bullishPullbackScore >= 0.5
                    TradeBias.BEARISH -> bearishPullbackScore >= 0.5
                    TradeBias.NEUTRAL -> maxOf(bullishPullbackScore, bearishPullbackScore) < 0.5
                },
                "Score ${formatScore(maxOf(bullishPullbackScore, bearishPullbackScore))}; distance ${"%.2f".format(pullbackDistance)}x average step"
            ),
            Confirmation(
                "Breakout Pressure",
                when (bias) {
                    TradeBias.BULLISH -> bullishBreakoutScore >= 0.5
                    TradeBias.BEARISH -> bearishBreakoutScore >= 0.5
                    TradeBias.NEUTRAL -> maxOf(bullishBreakoutScore, bearishBreakoutScore) < 0.5
                },
                if (breakoutUp) "Price is pressing above the recent range"
                else if (breakoutDown) "Price is pressing below the recent range"
                else "Price remains inside the recent range"
            ),
            Confirmation(
                "Zone Context",
                when (bias) {
                    TradeBias.BULLISH -> zoneBullScore >= 0.5
                    TradeBias.BEARISH -> zoneBearScore >= 0.5
                    TradeBias.NEUTRAL -> abs(topDown.zoneBias) < 0.2
                },
                topDown.zoneSummary
            ),
            Confirmation(
                "Liquidity Map",
                when (bias) {
                    TradeBias.BULLISH -> liquidityBullScore >= 0.5
                    TradeBias.BEARISH -> liquidityBearScore >= 0.5
                    TradeBias.NEUTRAL -> abs(topDown.liquidityBias) < 0.2
                },
                topDown.liquiditySummary
            ),
            Confirmation(
                "Imbalance (FVG)",
                when (bias) {
                    TradeBias.BULLISH -> imbalanceBullScore >= 0.5
                    TradeBias.BEARISH -> imbalanceBearScore >= 0.5
                    TradeBias.NEUTRAL -> abs(topDown.imbalanceBias) < 0.2
                },
                topDown.imbalanceSummary
            ),
            Confirmation(
                "Noise Filter",
                noiseScore >= 0.5,
                "Score ${formatScore(noiseScore)}; average step is ${(noiseRatio * 100).formatPercent()} of current price"
            ),
            Confirmation(
                "Timeframe Fit",
                timeframeScore >= 0.5,
                "Score ${formatScore(timeframeScore)}; expected move floor for $timeframe is ${formatPrice(moveFloor)}"
            ),
            Confirmation(
                "Confluence Gate",
                topDown.confluenceScore >= 0.5,
                "Composite confluence score ${formatScore(topDown.confluenceScore)} from trend, structure, zones, liquidity and imbalance"
            ),
            Confirmation(
                "Candlestick Engine",
                when (bias) {
                    TradeBias.BULLISH -> bullishPatternScore >= 0.5
                    TradeBias.BEARISH -> bearishPatternScore >= 0.5
                    TradeBias.NEUTRAL -> abs(patternSignals.directionalBias) < 0.2
                },
                patternSignals.summary
            ),
            Confirmation(
                "ICC Phase",
                when (bias) {
                    TradeBias.BULLISH -> bullishIccScore >= 0.5
                    TradeBias.BEARISH -> bearishIccScore >= 0.5
                    TradeBias.NEUTRAL -> iccPhase.phase == "no_setup"
                },
                "Phase ${iccPhase.phase} (${formatScore(iccPhase.confidence)}): ${iccPhase.summary}"
            ),
            Confirmation(
                "Multi-TF Consensus",
                when (bias) {
                    TradeBias.BULLISH -> bullishMtfScore >= 0.5
                    TradeBias.BEARISH -> bearishMtfScore >= 0.5
                    TradeBias.NEUTRAL -> abs(mtfContext.directionalBias) < 0.2
                },
                mtfContext.summary
            ),
            Confirmation(
                "News Pulse Filter",
                newsPulse.pulseScore < 1.2 || bias != TradeBias.NEUTRAL,
                newsPulse.summary
            )
        )

        val supportiveScore = when (setupType) {
            SetupType.TREND_PULLBACK -> when (bias) {
                TradeBias.BULLISH -> bullishMomentumScore + bullishPullbackScore + bullishBreakoutScore + topDownBullScore + topDown.confluenceScore + bullishPatternScore + bullishIccScore + bullishMtfScore
                TradeBias.BEARISH -> bearishMomentumScore + bearishPullbackScore + bearishBreakoutScore + topDownBearScore + topDown.confluenceScore + bearishPatternScore + bearishIccScore + bearishMtfScore
                TradeBias.NEUTRAL -> 0.0
            }
            SetupType.BREAKOUT -> when (bias) {
                TradeBias.BULLISH -> bullishMomentumScore + bullishTrendScore + (1.0 - bullishPullbackScore) + topDownBullScore + topDown.confluenceScore + bullishPatternScore + bullishIccScore + bullishMtfScore
                TradeBias.BEARISH -> bearishMomentumScore + bearishTrendScore + (1.0 - bearishPullbackScore) + topDownBearScore + topDown.confluenceScore + bearishPatternScore + bearishIccScore + bearishMtfScore
                TradeBias.NEUTRAL -> 0.0
            }
            SetupType.NONE -> 0.0
        }
        val corePassed = bestCandidate.corePassed
        val stage = when {
            evidence.sampleSize >= 200 -> 3
            evidence.sampleSize >= 40 -> 2
            else -> 1
        }
        val expectancyValue = evidence.expectancyR.removeSuffix("R").toDoubleOrNull() ?: 0.0
        val baseApproval = bias != TradeBias.NEUTRAL &&
            corePassed >= minimumCoreRequired(mode) &&
            topDown.confluenceScore >= confluenceGateForMode(mode) &&
            supportiveScore >= modeSupportThreshold(mode, setupType)
        val approved = when (stage) {
            1 -> baseApproval
            2 -> baseApproval && expectancyValue > -0.05
            else -> baseApproval && expectancyValue > 0.0
        }
        val passedCount = confirmations.count { it.passed }
        val directionalEdge = (((bestCandidate.score - (runnerUp?.score ?: 0.0)).coerceAtLeast(0.0)) * 8).toInt()
        val confidence = (
            32 +
                (passedCount * 9) +
                (directionalEdge * 4) +
                if (approved) 8 else 0
            )
            .coerceIn(20, 92)

        val summary = when {
            bias == TradeBias.NEUTRAL ->
                "${symbol.code} is range-bound on $timeframe. The model sees mixed pressure, so it is better to wait."
            approved ->
                "${setupType.label} ${bias.label.lowercase()} setup is active on $timeframe. Top-down confluence is aligned and the weighted score clears ${mode.label.lowercase()} mode."
            else ->
                "${setupType.label} ${bias.label.lowercase()} pressure exists, but top-down confluence is still below the ${mode.label.lowercase()} activation threshold."
        }

        val executionPlan = when (setupType) {
            SetupType.TREND_PULLBACK -> when (bias) {
                TradeBias.BULLISH -> "Favor long pullback entries while price holds above the short-term mean and trend structure remains intact."
                TradeBias.BEARISH -> "Favor short rebound fades while price stays below the short-term mean and trend structure remains intact."
                TradeBias.NEUTRAL -> "Stand aside until a cleaner pullback or stronger structure appears."
            }
            SetupType.BREAKOUT -> when (bias) {
                TradeBias.BULLISH -> "Favor long entries only after range expansion confirms and the breakout remains above the recent high."
                TradeBias.BEARISH -> "Favor short entries only after downside range expansion confirms and price holds below the recent low."
                TradeBias.NEUTRAL -> "Stand aside until a range break confirms with cleaner momentum."
            }
            SetupType.NONE -> "Stand aside until price breaks the recent range with momentum and lower noise."
        }

        val riskNote = when {
            noiseScore == 0.0 ->
                "Noise is elevated for $timeframe. Expect more fakeouts and wider stop placement."
            topDown.confluenceScore < 0.5 ->
                "Top-down confluence is weak. Treat this as a developing idea until structure and liquidity align."
            newsPulse.pulseScore >= 1.2 ->
                "Event-volatility pulse detected. Use smaller size and wait for candle-close confirmation."
            !approved && stage == 1 ->
                "Cold-start evidence mode is active. Structure can still qualify, but the setup needs cleaner supportive scores first."
            !approved ->
                "The directional read is early. Wait for one more confirming impulse before committing capital."
            else ->
                "This is still a probability model. Validate the signal against your risk limits and execution spread."
        }

        val baseRisk = (averageStep * (2.0 + (noiseRatio / noiseCeiling).coerceAtMost(1.2))).coerceAtLeast(last * 0.0015)
        val rewardMultiplier = when (mode) {
            ConfirmationMode.CONSERVATIVE -> 2.4
            ConfirmationMode.MODERATE -> 1.9
            ConfirmationMode.AGGRESSIVE -> 1.5
        }
        val setupAdjustment = if (setupType == SetupType.BREAKOUT) 1.15 else 1.0

        val preliminaryTradeSetup = when (bias) {
            TradeBias.BULLISH -> {
                val entry = if (setupType == SetupType.BREAKOUT) last + (averageStep * 0.12) else last - (averageStep * 0.25)
                val stopLoss = entry - (baseRisk * setupAdjustment)
                val takeProfit = entry + (baseRisk * rewardMultiplier * setupAdjustment)
                TradeSetup(
                    entry = entry,
                    stopLoss = stopLoss,
                    takeProfit = takeProfit,
                    riskReward = "1:${"%.1f".format(rewardMultiplier * setupAdjustment)}",
                    shouldTrade = approved
                )
            }
            TradeBias.BEARISH -> {
                val entry = if (setupType == SetupType.BREAKOUT) last - (averageStep * 0.12) else last + (averageStep * 0.25)
                val stopLoss = entry + (baseRisk * setupAdjustment)
                val takeProfit = entry - (baseRisk * rewardMultiplier * setupAdjustment)
                TradeSetup(
                    entry = entry,
                    stopLoss = stopLoss,
                    takeProfit = takeProfit,
                    riskReward = "1:${"%.1f".format(rewardMultiplier * setupAdjustment)}",
                    shouldTrade = approved
                )
            }
            TradeBias.NEUTRAL -> TradeSetup(
                entry = null,
                stopLoss = null,
                takeProfit = null,
                riskReward = "-",
                shouldTrade = false
            )
        }

        val decision = when {
            bias == TradeBias.NEUTRAL || corePassed < minimumCoreRequired(mode) ->
                TradeDecision.REJECT
            approved && (stage == 1 || expectancyValue > 0.0) ->
                TradeDecision.ELIGIBLE
            baseApproval ->
                TradeDecision.WATCHLIST
            stage >= 3 && expectancyValue <= 0.0 ->
                TradeDecision.REJECT
            else ->
                TradeDecision.WATCHLIST
        }

        val tradeSetup = preliminaryTradeSetup.copy(
            shouldTrade = decision == TradeDecision.ELIGIBLE
        )

        val traderGuidance = when (decision) {
            TradeDecision.ELIGIBLE ->
                "Eligible setup. Wait for price to approach the planned entry, then execute only if the ${setupType.label.lowercase()} structure still holds at candle close."
            TradeDecision.WATCHLIST ->
                "Watchlist only. The setup shape is promising, but either supportive scores or staged evidence are not strong enough for immediate execution."
            TradeDecision.REJECT ->
                "Reject this setup. Either expectancy is not positive or the market is not directional enough yet."
        }
        val nextTrigger = when {
            decision == TradeDecision.ELIGIBLE ->
                "Set an alert at the planned entry and only act if the next close preserves the ${setupType.label.lowercase()} structure."
            setupType == SetupType.BREAKOUT && bias == TradeBias.BULLISH ->
                "Eligible if the next candle closes above resistance with noise contained and HTF bias still bullish."
            setupType == SetupType.BREAKOUT && bias == TradeBias.BEARISH ->
                "Eligible if the next candle closes below support with noise contained and HTF bias still bearish."
            setupType == SetupType.TREND_PULLBACK && bias == TradeBias.BULLISH ->
                "Eligible if the pullback holds near demand/mean and momentum re-accelerates upward."
            setupType == SetupType.TREND_PULLBACK && bias == TradeBias.BEARISH ->
                "Eligible if the rebound stalls near supply/mean and momentum re-accelerates downward."
            else ->
                "Wait for either a cleaner trend pullback or a confirmed breakout before considering a trade."
        }

        return AnalysisResult(
            bias = bias,
            confidence = confidence,
            approved = approved,
            summary = summary,
            executionPlan = executionPlan,
            riskNote = riskNote,
            confirmations = confirmations,
            tradeSetup = tradeSetup,
            traderGuidance = traderGuidance,
            decision = decision,
            evidence = evidence,
            setupType = if (bias == TradeBias.NEUTRAL) SetupType.NONE else setupType,
            nextTrigger = nextTrigger
        )
    }

    private fun timeframeMoveFloor(timeframe: String): Double {
        return when (timeframe) {
            "1m" -> 0.7
            "5m" -> 0.9
            "15m" -> 1.1
            "1h" -> 1.3
            "4h" -> 1.5
            "1d" -> 1.8
            else -> 1.0
        }
    }

    private fun timeframeNoiseCeiling(timeframe: String): Double {
        return when (timeframe) {
            "1m" -> 0.0045
            "5m" -> 0.004
            "15m" -> 0.0035
            "1h" -> 0.003
            "4h" -> 0.0025
            "1d" -> 0.002
            else -> 0.0035
        }
    }

    private fun formatPrice(value: Double): String {
        return "%.5f".format(value)
    }

    private fun formatSigned(value: Double): String {
        return if (value >= 0) "+${formatPrice(value)}" else formatPrice(value)
    }

    private fun Double.formatPercent(): String {
        return "%.2f".format(this)
    }

    private fun formatScore(value: Double): String {
        return "%.1f".format(value)
    }

    private fun softScore(value: Double, passAt: Double, partialAt: Double): Double {
        return when {
            value >= passAt -> 1.0
            value >= partialAt -> 0.5
            else -> 0.0
        }
    }

    private fun rangedScore(
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

    private fun modeSupportThreshold(mode: ConfirmationMode, setupType: SetupType): Double {
        val base = when (mode) {
            ConfirmationMode.CONSERVATIVE -> 3.2
            ConfirmationMode.MODERATE -> 2.5
            ConfirmationMode.AGGRESSIVE -> 1.9
        }
        return if (setupType == SetupType.BREAKOUT && mode != ConfirmationMode.CONSERVATIVE) base - 0.1 else base
    }

    private fun minimumCoreRequired(mode: ConfirmationMode): Int {
        return when (mode) {
            ConfirmationMode.CONSERVATIVE -> 5
            ConfirmationMode.MODERATE -> 4
            ConfirmationMode.AGGRESSIVE -> 3
        }
    }

    private fun biasActivationThreshold(mode: ConfirmationMode): Double {
        return when (mode) {
            ConfirmationMode.CONSERVATIVE -> 6.2
            ConfirmationMode.MODERATE -> 5.4
            ConfirmationMode.AGGRESSIVE -> 4.7
        }
    }

    private fun topDownDirectionalThreshold(mode: ConfirmationMode): Double {
        return when (mode) {
            ConfirmationMode.CONSERVATIVE -> 0.55
            ConfirmationMode.MODERATE -> 0.45
            ConfirmationMode.AGGRESSIVE -> 0.35
        }
    }

    private fun confluenceGateForMode(mode: ConfirmationMode): Double {
        return when (mode) {
            ConfirmationMode.CONSERVATIVE -> 0.58
            ConfirmationMode.MODERATE -> 0.50
            ConfirmationMode.AGGRESSIVE -> 0.42
        }
    }

    private fun analyzeCandlestickPatterns(
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

    private fun detectNewsPulse(candles: List<MarketCandle>): NewsPulseContext {
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

    private fun analyzeIccPhase(
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

    private fun analyzeMultiTimeframeConsensus(sourceCandles: List<MarketCandle>): MultiTimeframeContext {
        if (sourceCandles.size < 12) return MultiTimeframeContext(0.0, 0.0, "Insufficient candles for multi-timeframe consensus")
        val tf1 = trendDirectionFromCandles(sourceCandles.takeLast(20))
        val tf2 = trendDirectionFromCandles(aggregateCandles(sourceCandles, 2).takeLast(20))
        val tf4 = trendDirectionFromCandles(aggregateCandles(sourceCandles, 4).takeLast(20))
        val weightedBias = ((tf1 * 0.2) + (tf2 * 0.35) + (tf4 * 0.45)).coerceIn(-1.0, 1.0)
        val signs = listOf(tf1, tf2, tf4).map {
            when {
                it > 0.15 -> 1
                it < -0.15 -> -1
                else -> 0
            }
        }
        val aligned = signs.count { it != 0 && it == signs.firstOrNull { s -> s != 0 } }
        val alignmentScore = (aligned.toDouble() / 3.0).coerceIn(0.0, 1.0)
        val summary = "TF consensus 1x/2x/4x bias ${formatSigned(weightedBias)} with alignment ${formatScore(alignmentScore)}"
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

    private fun buildTopDownContext(
        candles: List<MarketCandle>,
        prices: List<Double>,
        averageStep: Double
    ): TopDownContext {
        val source = if (candles.size >= 16) candles.takeLast(60) else buildSyntheticCandles(prices)
        if (source.size < 12) {
            return TopDownContext(
                higherTimeframeBias = TradeBias.NEUTRAL,
                higherTimeframeSummary = "Insufficient candles for higher-timeframe context",
                structureState = "range_bound",
                supportLevel = null,
                resistanceLevel = null,
                zoneBias = 0.0,
                zoneSummary = "Not enough structure for supply/demand zones",
                liquidityBias = 0.0,
                liquiditySummary = "Not enough structure for liquidity mapping",
                imbalanceBias = 0.0,
                imbalanceSummary = "No clear imbalance profile",
                confluenceScore = 0.0
            )
        }

        val higher = aggregateCandles(source, 4).ifEmpty { source.takeLast(16) }
        val higherCloses = higher.map { it.close }
        val higherShort = higherCloses.takeLast(minOf(4, higherCloses.size)).average()
        val higherLong = higherCloses.takeLast(minOf(8, higherCloses.size)).average()
        val higherRange = higher.map { (it.high - it.low).coerceAtLeast(0.00001) }.average().coerceAtLeast(0.00001)
        val htfTrend = (higherShort - higherLong) / higherRange
        val higherBias = when {
            htfTrend > 0.35 -> TradeBias.BULLISH
            htfTrend < -0.35 -> TradeBias.BEARISH
            else -> TradeBias.NEUTRAL
        }
        val htfSummary = "HTF drift ${formatSigned(htfTrend)} using ${higher.size} aggregated candles"

        val last = source.last().close
        val (swingHighs, swingLows) = findSwingLevels(higher)
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
            supportLevel = support,
            resistanceLevel = resistance,
            zoneBias = zoneContext.bias,
            zoneSummary = zoneContext.summary,
            liquidityBias = liquidityContext.bias,
            liquiditySummary = liquidityContext.summary,
            imbalanceBias = imbalanceContext.bias,
            imbalanceSummary = imbalanceContext.summary,
            confluenceScore = contextStrength.coerceIn(0.0, 1.0)
        )
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

    private fun directionalScore(signedBias: Double, bullish: Boolean): Double {
        val directional = if (bullish) signedBias else -signedBias
        return when {
            directional >= 0.35 -> 1.0
            directional >= 0.15 -> 0.5
            else -> 0.0
        }
    }

    private fun emptyEvidence(): EvidenceSnapshot {
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

    private fun buildEvidenceSnapshot(
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

    private fun buildSyntheticCandles(prices: List<Double>): List<MarketCandle> {
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

    private data class CandidateSetup(
        val type: SetupType,
        val bias: TradeBias,
        val corePassed: Int,
        val score: Double
    )

    private data class TopDownContext(
        val higherTimeframeBias: TradeBias,
        val higherTimeframeSummary: String,
        val structureState: String,
        val supportLevel: Double?,
        val resistanceLevel: Double?,
        val zoneBias: Double,
        val zoneSummary: String,
        val liquidityBias: Double,
        val liquiditySummary: String,
        val imbalanceBias: Double,
        val imbalanceSummary: String,
        val confluenceScore: Double
    )

    private data class DirectionalContext(
        val bias: Double,
        val summary: String
    )

    private data class PatternSignalSummary(
        val directionalBias: Double,
        val summary: String
    )

    private data class NewsPulseContext(
        val pulseScore: Double,
        val summary: String
    )

    private data class IccPhaseContext(
        val phase: String,
        val confidence: Double,
        val directionalBias: Double,
        val summary: String
    )

    private data class MultiTimeframeContext(
        val directionalBias: Double,
        val alignmentScore: Double,
        val summary: String
    )
}
