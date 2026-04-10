package com.ex57.capital.analysis

import com.ex57.capital.model.Confirmation
import com.ex57.capital.model.ConfirmationMode
import com.ex57.capital.model.ClosedTradeRecord
import com.ex57.capital.model.ForecastResearch
import com.ex57.capital.model.SetupType
import com.ex57.capital.model.TradeBias
import com.ex57.capital.model.TradeDecision
import com.ex57.capital.model.TradePerformanceFeedback
import com.ex57.capital.model.TradingSymbol
import kotlin.math.abs

internal object StrategyEvaluator {
    fun evaluate(
        symbol: TradingSymbol,
        input: AnalysisInput,
        features: FeatureExtractionResult,
        forecastResearch: ForecastResearch
    ): StrategyEvaluation {
        val modeConfig = AnalysisSupport.configFor(input.mode)
        val filterSettings = input.signalFilters
        val htfBullScore = AnalysisSupport.directionalScore(
            when (features.topDown.higherTimeframeBias) {
                TradeBias.BULLISH -> 1.0
                TradeBias.BEARISH -> -1.0
                TradeBias.NEUTRAL -> 0.0
            },
            bullish = true
        )
        val htfBearScore = AnalysisSupport.directionalScore(
            when (features.topDown.higherTimeframeBias) {
                TradeBias.BULLISH -> 1.0
                TradeBias.BEARISH -> -1.0
                TradeBias.NEUTRAL -> 0.0
            },
            bullish = false
        )
        val structureBullScore = AnalysisSupport.directionalScore(
            when (features.topDown.structureState) {
                "bullish_structure" -> 1.0
                "bearish_structure" -> -1.0
                else -> 0.0
            },
            bullish = true
        )
        val structureBearScore = AnalysisSupport.directionalScore(
            when (features.topDown.structureState) {
                "bullish_structure" -> 1.0
                "bearish_structure" -> -1.0
                else -> 0.0
            },
            bullish = false
        )
        val zoneBullScore = AnalysisSupport.directionalScore(features.topDown.zoneBias, bullish = true)
        val zoneBearScore = AnalysisSupport.directionalScore(features.topDown.zoneBias, bullish = false)
        val liquidityBullScore = AnalysisSupport.directionalScore(features.topDown.liquidityBias, bullish = true)
        val liquidityBearScore = AnalysisSupport.directionalScore(features.topDown.liquidityBias, bullish = false)
        val imbalanceBullScore = AnalysisSupport.directionalScore(features.topDown.imbalanceBias, bullish = true)
        val imbalanceBearScore = AnalysisSupport.directionalScore(features.topDown.imbalanceBias, bullish = false)
        val bullishPatternScore = AnalysisSupport.directionalScore(features.patternSignals.directionalBias, bullish = true)
        val bearishPatternScore = AnalysisSupport.directionalScore(features.patternSignals.directionalBias, bullish = false)
        val bullishIccScore = AnalysisSupport.directionalScore(features.iccPhase.directionalBias, bullish = true)
        val bearishIccScore = AnalysisSupport.directionalScore(features.iccPhase.directionalBias, bullish = false)
        val bullishMtfScore = AnalysisSupport.directionalScore(features.mtfContext.directionalBias, bullish = true)
        val bearishMtfScore = AnalysisSupport.directionalScore(features.mtfContext.directionalBias, bullish = false)
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
        val directionBullComponents = listOf(
            htfBullScore to 1.6,
            features.bullishTrendScore to 1.2,
            features.bullishMomentumScore to 1.0,
            structureBullScore to 1.3,
            bullishMtfScore to 1.4
        )
        val directionBearComponents = listOf(
            htfBearScore to 1.6,
            features.bearishTrendScore to 1.2,
            features.bearishMomentumScore to 1.0,
            structureBearScore to 1.3,
            bearishMtfScore to 1.4
        )
        val bullishDirectionalStrength = weightedAverage(directionBullComponents)
        val bearishDirectionalStrength = weightedAverage(directionBearComponents)
        val bullishDirectionalCount = directionBullComponents.count { it.first >= 0.5 }
        val bearishDirectionalCount = directionBearComponents.count { it.first >= 0.5 }

        val pullbackBullComponents = listOf(
            features.bullishPullbackScore to 1.8,
            zoneBullScore to 1.1,
            liquidityBullScore to 1.0,
            imbalanceBullScore to 0.9,
            bullishPatternScore to 1.0,
            bullishIccScore to 1.1,
            features.timeframeScore to 0.9,
            features.bullishBreakoutScore to 0.4
        )
        val pullbackBearComponents = listOf(
            features.bearishPullbackScore to 1.8,
            zoneBearScore to 1.1,
            liquidityBearScore to 1.0,
            imbalanceBearScore to 0.9,
            bearishPatternScore to 1.0,
            bearishIccScore to 1.1,
            features.timeframeScore to 0.9,
            features.bearishBreakoutScore to 0.4
        )
        val breakoutBullComponents = listOf(
            features.bullishBreakoutScore to 1.8,
            zoneBullScore to 0.8,
            liquidityBullScore to 1.1,
            imbalanceBullScore to 1.0,
            bullishPatternScore to 1.0,
            bullishIccScore to 1.0,
            features.timeframeScore to 0.9,
            features.bullishMomentumScore to 0.9
        )
        val breakoutBearComponents = listOf(
            features.bearishBreakoutScore to 1.8,
            zoneBearScore to 0.8,
            liquidityBearScore to 1.1,
            imbalanceBearScore to 1.0,
            bearishPatternScore to 1.0,
            bearishIccScore to 1.0,
            features.timeframeScore to 0.9,
            features.bearishMomentumScore to 0.9
        )
        val trendPullbackBullSetupScore = weightedAverage(pullbackBullComponents)
        val trendPullbackBearSetupScore = weightedAverage(pullbackBearComponents)
        val breakoutBullSetupScore = weightedAverage(breakoutBullComponents)
        val breakoutBearSetupScore = weightedAverage(breakoutBearComponents)
        val trendPullbackBullSetupCount = pullbackBullComponents.count { it.first >= 0.5 }
        val trendPullbackBearSetupCount = pullbackBearComponents.count { it.first >= 0.5 }
        val breakoutBullSetupCount = breakoutBullComponents.count { it.first >= 0.5 }
        val breakoutBearSetupCount = breakoutBearComponents.count { it.first >= 0.5 }

        val confluenceMetaScore = buildConfluenceMetaScore(
            directionalStrength = maxOf(bullishDirectionalStrength, bearishDirectionalStrength),
            zoneStrength = maxOf(zoneBullScore, zoneBearScore),
            liquidityStrength = maxOf(liquidityBullScore, liquidityBearScore),
            imbalanceStrength = maxOf(imbalanceBullScore, imbalanceBearScore),
            triggerStrength = maxOf(
                bullishPatternScore,
                bearishPatternScore,
                bullishIccScore,
                bearishIccScore,
                features.bullishBreakoutScore,
                features.bearishBreakoutScore,
                features.bullishPullbackScore,
                features.bearishPullbackScore
            ),
            noiseScore = features.noiseScore,
            timeframeScore = features.timeframeScore,
            stackAlignment = features.topDown.stackAlignmentScore
        )
        val severeNoise = features.noiseScore == 0.0
        val severeNews = features.newsPulse.pulseScore >= 1.2
        val riskPenalty = buildRiskPenalty(
            noiseScore = features.noiseScore,
            newsPulse = features.newsPulse.pulseScore,
            confluenceMetaScore = confluenceMetaScore
        )
        val bullishForecastBonus = forecastBiasScore(forecastResearch, bullish = true)
        val bearishForecastBonus = forecastBiasScore(forecastResearch, bullish = false)

        val candidateScores = listOf(
            CandidateSetup(
                SetupType.TREND_PULLBACK,
                TradeBias.BULLISH,
                bullishDirectionalCount,
                (bullishDirectionalStrength * 2.6) +
                    (trendPullbackBullSetupScore * 2.1) +
                    (topDownBullScore * 1.2) +
                    (confluenceMetaScore * 1.1) +
                    (bullishForecastBonus * 0.9) -
                    riskPenalty
            ),
            CandidateSetup(
                SetupType.TREND_PULLBACK,
                TradeBias.BEARISH,
                bearishDirectionalCount,
                (bearishDirectionalStrength * 2.6) +
                    (trendPullbackBearSetupScore * 2.1) +
                    (topDownBearScore * 1.2) +
                    (confluenceMetaScore * 1.1) +
                    (bearishForecastBonus * 0.9) -
                    riskPenalty
            ),
            CandidateSetup(
                SetupType.BREAKOUT,
                TradeBias.BULLISH,
                bullishDirectionalCount,
                (bullishDirectionalStrength * 2.4) +
                    (breakoutBullSetupScore * 2.2) +
                    (topDownBullScore * 1.1) +
                    (confluenceMetaScore * 1.0) +
                    (bullishForecastBonus * 0.9) -
                    riskPenalty
            ),
            CandidateSetup(
                SetupType.BREAKOUT,
                TradeBias.BEARISH,
                bearishDirectionalCount,
                (bearishDirectionalStrength * 2.4) +
                    (breakoutBearSetupScore * 2.2) +
                    (topDownBearScore * 1.1) +
                    (confluenceMetaScore * 1.0) +
                    (bearishForecastBonus * 0.9) -
                    riskPenalty
            )
        )
        val bestCandidate = candidateScores.maxByOrNull { it.score }!!
        val runnerUp = candidateScores.filterNot { it === bestCandidate }.maxByOrNull { it.score }

        val topDownDirectionalGate = if (bestCandidate.bias == TradeBias.BULLISH) topDownBullScore else topDownBearScore
        val directionalStrength = if (bestCandidate.bias == TradeBias.BULLISH) bullishDirectionalStrength else bearishDirectionalStrength
        val setupQualityScore = when (bestCandidate.type) {
            SetupType.TREND_PULLBACK -> if (bestCandidate.bias == TradeBias.BULLISH) trendPullbackBullSetupScore else trendPullbackBearSetupScore
            SetupType.BREAKOUT -> if (bestCandidate.bias == TradeBias.BULLISH) breakoutBullSetupScore else breakoutBearSetupScore
            SetupType.NONE -> 0.0
        }
        val setupQualityCount = when (bestCandidate.type) {
            SetupType.TREND_PULLBACK -> if (bestCandidate.bias == TradeBias.BULLISH) trendPullbackBullSetupCount else trendPullbackBearSetupCount
            SetupType.BREAKOUT -> if (bestCandidate.bias == TradeBias.BULLISH) breakoutBullSetupCount else breakoutBearSetupCount
            SetupType.NONE -> 0
        }
        val directionalEdge = (bestCandidate.score - (runnerUp?.score ?: 0.0)).coerceAtLeast(0.0)
        val forecastSupportScore = forecastSupportScore(
            forecastResearch = forecastResearch,
            bias = bestCandidate.bias
        )
        val forecastHardConflict = forecastResearch.stabilityScore >= 0.70 &&
            forecastResearch.bias != TradeBias.NEUTRAL &&
            forecastResearch.bias != bestCandidate.bias
        val performanceFeedback = buildTradePerformanceFeedback(
            symbolCode = symbol.code,
            timeframe = input.timeframe,
            closedTrades = input.closedTrades
        )
        val setupStateAllowed = when (input.mode) {
            ConfirmationMode.CONSERVATIVE -> features.topDown.setupState in setOf("pullback", "aligned") && features.topDown.triggerState in setOf("entry_ready", "consolidating")
            ConfirmationMode.MODERATE -> features.topDown.setupState in setOf("pullback", "aligned", "mixed")
            ConfirmationMode.AGGRESSIVE -> features.topDown.setupState != "undetermined"
            ConfirmationMode.LENIENT -> true
        }
        val hardBlock = when (input.mode) {
            ConfirmationMode.CONSERVATIVE -> filterSettings.enforceHardBlocks && (severeNoise || severeNews || forecastHardConflict)
            ConfirmationMode.MODERATE -> filterSettings.enforceHardBlocks && ((severeNoise && severeNews) || forecastHardConflict)
            ConfirmationMode.AGGRESSIVE -> filterSettings.enforceHardBlocks && severeNoise && severeNews && confluenceMetaScore < 0.4
            ConfirmationMode.LENIENT -> filterSettings.enforceHardBlocks && severeNoise && severeNews && topDownDirectionalGate < 0.25
        }
        val forecastThreshold = when (input.mode) {
            ConfirmationMode.CONSERVATIVE -> 0.42
            ConfirmationMode.MODERATE -> 0.30
            ConfirmationMode.AGGRESSIVE -> 0.18
            ConfirmationMode.LENIENT -> 0.0
        }
        val forecastGatePassed = !filterSettings.requireForecastSupport || forecastSupportScore >= forecastThreshold
        val setupStateGatePassed = !filterSettings.requireSetupState || setupStateAllowed
        val directionalEdgeGatePassed = !filterSettings.requireDirectionalEdge || directionalEdge >= modeConfig.directionalEdgeThreshold
        val setupCountGatePassed = !filterSettings.requireSetupConfirmations || setupQualityCount >= modeConfig.requiredSetupConfirmations
        val confluenceGatePassed = !filterSettings.requireConfluence || confluenceMetaScore >= modeConfig.confluenceGate
        val riskPenaltyGatePassed = !filterSettings.requireRiskPenalty || riskPenalty <= modeConfig.maxRiskPenalty
        val setupQualityFloor = setupQualityThreshold(modeConfig, bestCandidate.type)
        val historyGatePassed = !performanceFeedback.strictModeActive || (
            setupQualityScore >= (setupQualityFloor + 0.10) &&
                forecastSupportScore >= maxOf(forecastThreshold, 0.35) &&
                confluenceMetaScore >= maxOf(modeConfig.confluenceGate, 0.55) &&
                directionalEdge >= maxOf(modeConfig.directionalEdgeThreshold, 0.12)
            )

        val bias = if (!hardBlock &&
            bestCandidate.corePassed >= modeConfig.minimumCoreRequired &&
            directionalStrength >= modeConfig.directionalStrengthThreshold &&
            bestCandidate.score >= modeConfig.biasActivationThreshold &&
            topDownDirectionalGate >= modeConfig.topDownDirectionalThreshold &&
            directionalEdgeGatePassed &&
            forecastGatePassed &&
            setupStateGatePassed
        ) {
            bestCandidate.bias
        } else {
            TradeBias.NEUTRAL
        }

        val confirmations = buildConfirmations(
            symbol = symbol,
            timeframe = input.timeframe,
            bias = bias,
            features = features,
            htfBullScore = htfBullScore,
            htfBearScore = htfBearScore,
            structureBullScore = structureBullScore,
            structureBearScore = structureBearScore,
            zoneBullScore = zoneBullScore,
            zoneBearScore = zoneBearScore,
            liquidityBullScore = liquidityBullScore,
            liquidityBearScore = liquidityBearScore,
            imbalanceBullScore = imbalanceBullScore,
            imbalanceBearScore = imbalanceBearScore,
            bullishPatternScore = bullishPatternScore,
            bearishPatternScore = bearishPatternScore,
            bullishIccScore = bullishIccScore,
            bearishIccScore = bearishIccScore,
            bullishMtfScore = bullishMtfScore,
            bearishMtfScore = bearishMtfScore
        )

        val stage = when {
            features.evidence.sampleSize >= 200 -> 3
            features.evidence.sampleSize >= 40 -> 2
            else -> 1
        }
        val expectancyValue = features.evidence.expectancyR.removeSuffix("R").toDoubleOrNull() ?: 0.0
        val baseApproval = bias != TradeBias.NEUTRAL &&
            !hardBlock &&
            bestCandidate.corePassed >= modeConfig.minimumCoreRequired &&
            directionalStrength >= modeConfig.directionalStrengthThreshold &&
            setupQualityScore >= setupQualityFloor &&
            setupCountGatePassed &&
            confluenceGatePassed &&
            forecastGatePassed &&
            riskPenaltyGatePassed &&
            historyGatePassed
        val expectancyGatePassed = if (!filterSettings.requireExpectancy) {
            true
        } else {
            when (stage) {
                1 -> true
                2 -> expectancyValue > -0.05
                else -> expectancyValue > 0.0
            }
        }
        val approved = baseApproval && expectancyGatePassed
        val passedCount = confirmations.count { it.passed }
        val confirmationRatio = passedCount.toDouble() / confirmations.size.coerceAtLeast(1)
        val coreRatio = bestCandidate.corePassed.toDouble() / 8.0
        val edgeScore = (directionalEdge / 2.4).coerceIn(0.0, 1.0)
        val bestScoreStrength = (bestCandidate.score / 6.5).coerceIn(0.0, 1.0)
        val expectancyScore = when {
            expectancyValue >= 0.35 -> 1.0
            expectancyValue >= 0.15 -> 0.75
            expectancyValue > 0.0 -> 0.55
            expectancyValue > -0.1 -> 0.35
            else -> 0.15
        }
        val sampleScore = when {
            features.evidence.sampleSize >= 120 -> 1.0
            features.evidence.sampleSize >= 60 -> 0.75
            features.evidence.sampleSize >= 24 -> 0.5
            else -> 0.25
        }
        val approvalScore = if (approved) 1.0 else if (baseApproval) 0.72 else 0.35
        val riskScore = (1.0 - (riskPenalty / 1.4)).coerceIn(0.0, 1.0)
        val confidence = (
            18 +
                (confirmationRatio * 12) +
                (coreRatio * 18) +
                (directionalStrength * 16) +
                (setupQualityScore * 12) +
                (edgeScore * 10) +
                (bestScoreStrength * 6) +
                (expectancyScore * 8) +
                (sampleScore * 4) +
                (riskScore * 5) +
                (approvalScore * 6)
            ).toInt().let { raw ->
                if (performanceFeedback.strictModeActive) raw - (performanceFeedback.consecutiveLosses * 3) else raw
            }.coerceIn(20, 89)

        val decision = when {
            bias == TradeBias.NEUTRAL || bestCandidate.corePassed < AnalysisSupport.minimumCoreRequired(input.mode) -> TradeDecision.REJECT
            approved -> TradeDecision.ELIGIBLE
            performanceFeedback.strictModeActive && bias != TradeBias.NEUTRAL -> TradeDecision.WATCHLIST
            filterSettings.requireForecastSupport && forecastSupportScore < forecastThreshold && bestCandidate.bias != TradeBias.NEUTRAL -> TradeDecision.WATCHLIST
            input.mode == ConfirmationMode.LENIENT && bias != TradeBias.NEUTRAL && !hardBlock -> TradeDecision.WATCHLIST
            baseApproval -> TradeDecision.WATCHLIST
            filterSettings.requireExpectancy && stage >= 3 && expectancyValue <= 0.0 -> TradeDecision.REJECT
            else -> TradeDecision.WATCHLIST
        }
        val rejectionReasons = buildRejectionReasons(
            mode = input.mode,
            filterSettings = filterSettings,
            hardBlock = hardBlock,
            severeNoise = severeNoise,
            severeNews = severeNews,
            corePassed = bestCandidate.corePassed,
            minimumCoreRequired = modeConfig.minimumCoreRequired,
            directionalStrength = directionalStrength,
            directionalStrengthThreshold = modeConfig.directionalStrengthThreshold,
            bestScore = bestCandidate.score,
            biasActivationThreshold = modeConfig.biasActivationThreshold,
            topDownDirectionalGate = topDownDirectionalGate,
            topDownDirectionalThreshold = modeConfig.topDownDirectionalThreshold,
            directionalEdge = directionalEdge,
            directionalEdgeThreshold = modeConfig.directionalEdgeThreshold,
            forecastResearch = forecastResearch,
            forecastSupportScore = forecastSupportScore,
            forecastThreshold = forecastThreshold,
            setupStateAllowed = setupStateGatePassed,
            setupQualityScore = setupQualityScore,
            setupQualityThreshold = setupQualityThreshold(modeConfig, bestCandidate.type),
            setupQualityCount = setupQualityCount,
            requiredSetupConfirmations = modeConfig.requiredSetupConfirmations,
            confluenceMetaScore = confluenceMetaScore,
            confluenceGate = modeConfig.confluenceGate,
            riskPenalty = riskPenalty,
            maxRiskPenalty = modeConfig.maxRiskPenalty,
            stage = stage,
            expectancyValue = expectancyValue,
            approved = approved,
            decision = decision,
            performanceFeedback = performanceFeedback
        )

        return StrategyEvaluation(
            setupType = if (bias == TradeBias.NEUTRAL) SetupType.NONE else bestCandidate.type,
            bias = bias,
            approved = approved,
            decision = decision,
            rejectionReasons = rejectionReasons,
            confidence = confidence,
            confirmations = confirmations,
            forecastResearch = forecastResearch,
            performanceFeedback = performanceFeedback,
            topDownBullScore = topDownBullScore,
            topDownBearScore = topDownBearScore,
            corePassed = bestCandidate.corePassed,
            stage = stage,
            expectancyValue = expectancyValue
        )
    }

    private fun weightedAverage(components: List<Pair<Double, Double>>): Double {
        val weightedSum = components.sumOf { (score, weight) -> score * weight }
        val totalWeight = components.sumOf { it.second }.coerceAtLeast(0.0001)
        return (weightedSum / totalWeight).coerceIn(0.0, 1.0)
    }

    private fun forecastBiasScore(
        forecastResearch: ForecastResearch,
        bullish: Boolean
    ): Double {
        return when (forecastResearch.bias) {
            TradeBias.NEUTRAL -> forecastResearch.stabilityScore * 0.25
            TradeBias.BULLISH -> if (bullish) {
                (forecastResearch.strengthScore * 0.70) + (forecastResearch.stabilityScore * 0.30)
            } else {
                0.0
            }
            TradeBias.BEARISH -> if (!bullish) {
                (forecastResearch.strengthScore * 0.70) + (forecastResearch.stabilityScore * 0.30)
            } else {
                0.0
            }
        }.coerceIn(0.0, 1.0)
    }

    private fun forecastSupportScore(
        forecastResearch: ForecastResearch,
        bias: TradeBias
    ): Double {
        return when {
            bias == TradeBias.NEUTRAL -> 0.0
            forecastResearch.bias == TradeBias.NEUTRAL -> forecastResearch.stabilityScore * 0.35
            forecastResearch.bias != bias -> {
                ((1.0 - forecastResearch.stabilityScore) * 0.20).coerceIn(0.0, 0.20)
            }
            else -> {
                (forecastResearch.strengthScore * 0.60) + (forecastResearch.stabilityScore * 0.40)
            }
        }.coerceIn(0.0, 1.0)
    }

    private fun buildTradePerformanceFeedback(
        symbolCode: String,
        timeframe: String,
        closedTrades: List<ClosedTradeRecord>
    ): TradePerformanceFeedback {
        val recentTrades = closedTrades
            .filter { it.symbolCode == symbolCode && it.timeframe == timeframe }
            .sortedByDescending { it.closedAtEpochMillis }
            .take(6)
        if (recentTrades.isEmpty()) {
            return TradePerformanceFeedback(
                sampleSize = 0,
                consecutiveLosses = 0,
                recentWinRate = 0,
                recentNetPnlUsd = 0.0,
                strictModeActive = false,
                summary = "No closed-trade history yet for $symbolCode $timeframe. The engine is relying on live structure only."
            )
        }

        val consecutiveLosses = recentTrades.takeWhile { it.pnlUsd < 0.0 }.count()
        val winRate = ((recentTrades.count { it.pnlUsd > 0.0 }.toDouble() / recentTrades.size) * 100.0).toInt()
        val recentNetPnlUsd = recentTrades.sumOf { it.pnlUsd }
        val averagePnlPercent = recentTrades.map { it.pnlPercent }.average()
        val strictModeActive = recentTrades.size >= 3 && (
            consecutiveLosses >= 2 ||
                (winRate <= 34 && recentNetPnlUsd < 0.0) ||
                averagePnlPercent <= -0.35
        )
        val summary = if (strictModeActive) {
            "Recent $symbolCode $timeframe history is weak: $consecutiveLosses consecutive losses, $winRate% win rate, net ${formatSignedUsd(recentNetPnlUsd)}. Require cleaner setup quality before entry."
        } else {
            "Recent $symbolCode $timeframe history is stable enough: $winRate% win rate across ${recentTrades.size} trades with net ${formatSignedUsd(recentNetPnlUsd)}."
        }

        return TradePerformanceFeedback(
            sampleSize = recentTrades.size,
            consecutiveLosses = consecutiveLosses,
            recentWinRate = winRate,
            recentNetPnlUsd = recentNetPnlUsd,
            strictModeActive = strictModeActive,
            summary = summary
        )
    }

    private fun buildConfluenceMetaScore(
        directionalStrength: Double,
        zoneStrength: Double,
        liquidityStrength: Double,
        imbalanceStrength: Double,
        triggerStrength: Double,
        noiseScore: Double,
        timeframeScore: Double,
        stackAlignment: Double
    ): Double {
        var buckets = 0.0
        if (directionalStrength >= 0.5) buckets += 1.0
        if (maxOf(zoneStrength, liquidityStrength, imbalanceStrength) >= 0.5) buckets += 1.0
        if (triggerStrength >= 0.5) buckets += 1.0
        if (noiseScore >= 0.5 && timeframeScore >= 0.5) buckets += 1.0
        val diversityScore = (buckets / 4.0).coerceIn(0.0, 1.0)
        return ((diversityScore * 0.60) + (stackAlignment * 0.40)).coerceIn(0.0, 1.0)
    }

    private fun formatSignedUsd(value: Double): String {
        return if (value >= 0.0) {
            "+$${"%.2f".format(value)}"
        } else {
            "-$${"%.2f".format(abs(value))}"
        }
    }

    private fun buildRiskPenalty(
        noiseScore: Double,
        newsPulse: Double,
        confluenceMetaScore: Double
    ): Double {
        val noisePenalty = when (noiseScore) {
            1.0 -> 0.0
            0.5 -> 0.25
            else -> 0.75
        }
        val newsPenalty = when {
            newsPulse >= 1.2 -> 0.75
            newsPulse >= 0.5 -> 0.35
            else -> 0.0
        }
        val confluencePenalty = when {
            confluenceMetaScore >= 0.65 -> 0.0
            confluenceMetaScore >= 0.45 -> 0.18
            else -> 0.40
        }
        return noisePenalty + newsPenalty + confluencePenalty
    }

    private fun setupQualityThreshold(modeConfig: com.ex57.capital.model.ModeConfig, setupType: SetupType): Double {
        return if (setupType == SetupType.BREAKOUT) {
            modeConfig.setupQualityThreshold + modeConfig.breakoutSetupQualityAdjustment
        } else {
            modeConfig.setupQualityThreshold
        }
    }

    private fun buildRejectionReasons(
        mode: ConfirmationMode,
        filterSettings: com.ex57.capital.model.SignalFilterSettings,
        hardBlock: Boolean,
        severeNoise: Boolean,
        severeNews: Boolean,
        corePassed: Int,
        minimumCoreRequired: Int,
        directionalStrength: Double,
        directionalStrengthThreshold: Double,
        bestScore: Double,
        biasActivationThreshold: Double,
        topDownDirectionalGate: Double,
        topDownDirectionalThreshold: Double,
        directionalEdge: Double,
        directionalEdgeThreshold: Double,
        forecastResearch: ForecastResearch,
        forecastSupportScore: Double,
        forecastThreshold: Double,
        setupStateAllowed: Boolean,
        setupQualityScore: Double,
        setupQualityThreshold: Double,
        setupQualityCount: Int,
        requiredSetupConfirmations: Int,
        confluenceMetaScore: Double,
        confluenceGate: Double,
        riskPenalty: Double,
        maxRiskPenalty: Double,
        stage: Int,
        expectancyValue: Double,
        approved: Boolean,
        decision: TradeDecision,
        performanceFeedback: TradePerformanceFeedback
    ): List<String> {
        val reasons = mutableListOf<String>()
        if (filterSettings.enforceHardBlocks && hardBlock) {
            if (severeNoise) reasons += "Noise hard-blocked the setup for ${mode.label.lowercase()} mode."
            if (severeNews) reasons += "Event-volatility pulse is too high for immediate execution."
        }
        if (corePassed < minimumCoreRequired) {
            reasons += "Only $corePassed core confirmations passed; ${minimumCoreRequired} are required."
        }
        if (directionalStrength < directionalStrengthThreshold) {
            reasons += "Directional strength ${AnalysisSupport.formatScore(directionalStrength)} is below the ${AnalysisSupport.formatScore(directionalStrengthThreshold)} threshold."
        }
        if (bestScore < biasActivationThreshold) {
            reasons += "Weighted setup score ${AnalysisSupport.formatScore(bestScore)} did not reach the activation threshold."
        }
        if (topDownDirectionalGate < topDownDirectionalThreshold) {
            reasons += "Higher-timeframe directional alignment is too weak."
        }
        if (filterSettings.requireDirectionalEdge && directionalEdge < directionalEdgeThreshold) {
            reasons += "Directional edge over the runner-up setup is too small."
        }
        if (filterSettings.requireForecastSupport && forecastSupportScore < forecastThreshold) {
            reasons += when (forecastResearch.bias) {
                TradeBias.NEUTRAL -> "Forecast lane is too mixed to support an immediate entry."
                else -> "Forecast lane disagrees with the setup or shows unstable path behavior."
            }
        }
        if (filterSettings.requireSetupState && !setupStateAllowed) {
            reasons += "Current setup/trigger state is not valid for this mode."
        }
        if (setupQualityScore < setupQualityThreshold) {
            reasons += "Setup quality is below the minimum for execution."
        }
        if (filterSettings.requireSetupConfirmations && setupQualityCount < requiredSetupConfirmations) {
            reasons += "Not enough setup confirmations are active yet."
        }
        if (filterSettings.requireConfluence && confluenceMetaScore < confluenceGate) {
            reasons += "Cross-factor confluence is too weak."
        }
        if (filterSettings.requireRiskPenalty && riskPenalty > maxRiskPenalty) {
            reasons += "Risk penalty is too high relative to the selected mode."
        }
        if (filterSettings.requireExpectancy && stage >= 2 && expectancyValue <= 0.0) {
            reasons += "Historical expectancy is not positive enough for approval."
        }
        if (performanceFeedback.strictModeActive) {
            reasons += "Recent closed-trade history is weak here, so the next setup needs stronger confirmation before execution."
        }
        if (forecastResearch.stabilityScore < 0.35) {
            reasons += "Recent path stability is weak, so the projected move is too noisy."
        }
        if (decision == TradeDecision.WATCHLIST && !approved) {
            reasons += "Setup is usable for monitoring, but not strong enough for immediate approval."
        }
        return reasons.distinct()
    }

    private fun buildConfirmations(
        symbol: TradingSymbol,
        timeframe: String,
        bias: TradeBias,
        features: FeatureExtractionResult,
        htfBullScore: Double,
        htfBearScore: Double,
        structureBullScore: Double,
        structureBearScore: Double,
        zoneBullScore: Double,
        zoneBearScore: Double,
        liquidityBullScore: Double,
        liquidityBearScore: Double,
        imbalanceBullScore: Double,
        imbalanceBearScore: Double,
        bullishPatternScore: Double,
        bearishPatternScore: Double,
        bullishIccScore: Double,
        bearishIccScore: Double,
        bullishMtfScore: Double,
        bearishMtfScore: Double
    ): List<Confirmation> {
        return listOf(
            Confirmation(
                "Higher-Timeframe Bias",
                when (bias) {
                    TradeBias.BULLISH -> htfBullScore >= 0.5
                    TradeBias.BEARISH -> htfBearScore >= 0.5
                    TradeBias.NEUTRAL -> features.topDown.higherTimeframeBias == TradeBias.NEUTRAL
                },
                features.topDown.higherTimeframeSummary
            ),
            Confirmation(
                "Trend Structure",
                when (bias) {
                    TradeBias.BULLISH -> features.bullishTrendScore >= 0.5
                    TradeBias.BEARISH -> features.bearishTrendScore >= 0.5
                    TradeBias.NEUTRAL -> maxOf(features.bullishTrendScore, features.bearishTrendScore) < 0.5
                },
                "Score ${AnalysisSupport.formatScore(maxOf(features.bullishTrendScore, features.bearishTrendScore))} from short mean ${AnalysisSupport.formatPrice(features.shortAverage)} vs long mean ${AnalysisSupport.formatPrice(features.longAverage)}"
            ),
            Confirmation(
                "Momentum",
                when (bias) {
                    TradeBias.BULLISH -> features.bullishMomentumScore >= 0.5
                    TradeBias.BEARISH -> features.bearishMomentumScore >= 0.5
                    TradeBias.NEUTRAL -> maxOf(features.bullishMomentumScore, features.bearishMomentumScore) < 0.5
                },
                "${describeMomentum(features)}; score ${AnalysisSupport.formatScore(maxOf(features.bullishMomentumScore, features.bearishMomentumScore))} from 4-candle impulse ${AnalysisSupport.formatSigned(features.momentum)}"
            ),
            Confirmation(
                "Market Structure",
                when (bias) {
                    TradeBias.BULLISH -> structureBullScore >= 0.5
                    TradeBias.BEARISH -> structureBearScore >= 0.5
                    TradeBias.NEUTRAL -> features.topDown.structureState == "range_bound"
                },
                "State ${features.topDown.structureState.replace('_', ' ')} around S ${features.topDown.supportLevel?.let(AnalysisSupport::formatPrice) ?: "-"} / R ${features.topDown.resistanceLevel?.let(AnalysisSupport::formatPrice) ?: "-"}"
            ),
            Confirmation(
                "Pullback Quality",
                when (bias) {
                    TradeBias.BULLISH -> features.bullishPullbackScore >= 0.5
                    TradeBias.BEARISH -> features.bearishPullbackScore >= 0.5
                    TradeBias.NEUTRAL -> maxOf(features.bullishPullbackScore, features.bearishPullbackScore) < 0.5
                },
                "${describePullback(features)}; score ${AnalysisSupport.formatScore(maxOf(features.bullishPullbackScore, features.bearishPullbackScore))}; distance ${"%.2f".format(features.pullbackDistance)}x average step"
            ),
            Confirmation(
                "Breakout Pressure",
                when (bias) {
                    TradeBias.BULLISH -> features.bullishBreakoutScore >= 0.5
                    TradeBias.BEARISH -> features.bearishBreakoutScore >= 0.5
                    TradeBias.NEUTRAL -> maxOf(features.bullishBreakoutScore, features.bearishBreakoutScore) < 0.5
                },
                describeBreakout(features)
            ),
            Confirmation(
                "Zone Context",
                when (bias) {
                    TradeBias.BULLISH -> zoneBullScore >= 0.5
                    TradeBias.BEARISH -> zoneBearScore >= 0.5
                    TradeBias.NEUTRAL -> abs(features.topDown.zoneBias) < 0.2
                },
                features.topDown.zoneSummary
            ),
            Confirmation(
                "Liquidity Map",
                when (bias) {
                    TradeBias.BULLISH -> liquidityBullScore >= 0.5
                    TradeBias.BEARISH -> liquidityBearScore >= 0.5
                    TradeBias.NEUTRAL -> abs(features.topDown.liquidityBias) < 0.2
                },
                features.topDown.liquiditySummary
            ),
            Confirmation(
                "Imbalance (FVG)",
                when (bias) {
                    TradeBias.BULLISH -> imbalanceBullScore >= 0.5
                    TradeBias.BEARISH -> imbalanceBearScore >= 0.5
                    TradeBias.NEUTRAL -> abs(features.topDown.imbalanceBias) < 0.2
                },
                features.topDown.imbalanceSummary
            ),
            Confirmation(
                "Noise Filter",
                features.noiseScore >= 0.5,
                "${describeNoise(features)}; score ${AnalysisSupport.formatScore(features.noiseScore)}; average step is ${AnalysisSupport.formatPercent(features.noiseRatio * 100)} of current price"
            ),
            Confirmation(
                "Timeframe Fit",
                features.timeframeScore >= 0.5,
                "Score ${AnalysisSupport.formatScore(features.timeframeScore)}; expected move floor for $timeframe is ${AnalysisSupport.formatPrice(features.moveFloor)}"
            ),
            Confirmation(
                "Confluence Gate",
                features.topDown.confluenceScore >= 0.5,
                "${describeConsensus(features)}; confluence ${AnalysisSupport.formatScore(features.topDown.confluenceScore)} with stack alignment ${AnalysisSupport.formatScore(features.topDown.stackAlignmentScore)}"
            ),
            Confirmation(
                "Candlestick Engine",
                when (bias) {
                    TradeBias.BULLISH -> bullishPatternScore >= 0.5
                    TradeBias.BEARISH -> bearishPatternScore >= 0.5
                    TradeBias.NEUTRAL -> abs(features.patternSignals.directionalBias) < 0.2
                },
                features.patternSignals.summary
            ),
            Confirmation(
                "ICC Phase",
                when (bias) {
                    TradeBias.BULLISH -> bullishIccScore >= 0.5
                    TradeBias.BEARISH -> bearishIccScore >= 0.5
                    TradeBias.NEUTRAL -> features.iccPhase.phase == "no_setup"
                },
                "Phase ${features.iccPhase.phase} (${AnalysisSupport.formatScore(features.iccPhase.confidence)}): ${features.iccPhase.summary}"
            ),
            Confirmation(
                "Multi-TF Consensus",
                when (bias) {
                    TradeBias.BULLISH -> bullishMtfScore >= 0.5
                    TradeBias.BEARISH -> bearishMtfScore >= 0.5
                    TradeBias.NEUTRAL -> abs(features.mtfContext.directionalBias) < 0.2
                },
                "${features.mtfContext.summary}; setup ${features.topDown.setupState}, trigger ${features.topDown.triggerState}"
            ),
            Confirmation(
                "News Pulse Filter",
                features.newsPulse.pulseScore < 1.2 || bias != TradeBias.NEUTRAL,
                "${describeNews(features)}; ${features.newsPulse.summary}"
            )
        )
    }

    private fun describeMomentum(features: FeatureExtractionResult): String {
        return when {
            maxOf(features.bullishMomentumScore, features.bearishMomentumScore) >= 1.0 && maxOf(features.bullishTrendScore, features.bearishTrendScore) >= 0.5 -> "Strong continuation"
            maxOf(features.bullishMomentumScore, features.bearishMomentumScore) >= 0.5 && maxOf(features.bullishTrendScore, features.bearishTrendScore) >= 0.5 -> "Weak continuation"
            maxOf(features.bullishMomentumScore, features.bearishMomentumScore) >= 0.5 && maxOf(features.bullishTrendScore, features.bearishTrendScore) < 0.5 -> "Reversal impulse"
            abs(features.momentum) > features.moveFloor * 1.8 -> "Exhaustion risk"
            else -> "Flat momentum"
        }
    }

    private fun describePullback(features: FeatureExtractionResult): String {
        val score = maxOf(features.bullishPullbackScore, features.bearishPullbackScore)
        return when {
            score >= 1.0 -> "Ideal pullback"
            score >= 0.5 -> "Acceptable pullback"
            kotlin.math.abs(features.pullbackDistance) > 1.0 -> "Too deep / trend at risk"
            else -> "Stretched or absent pullback"
        }
    }

    private fun describeBreakout(features: FeatureExtractionResult): String {
        return when {
            (features.breakoutUp || features.breakoutDown) && maxOf(features.bullishBreakoutScore, features.bearishBreakoutScore) >= 1.0 -> "Clean breakout pressure"
            maxOf(features.bullishBreakoutScore, features.bearishBreakoutScore) >= 0.5 -> "Weak breakout pressure"
            else -> "No clean breakout pressure"
        }
    }

    private fun describeNoise(features: FeatureExtractionResult): String {
        return when (features.noiseScore) {
            1.0 -> "Clean"
            0.5 -> "Tolerable"
            else -> "Highly noisy"
        }
    }

    private fun describeConsensus(features: FeatureExtractionResult): String {
        return when {
            features.topDown.stackAlignmentScore >= 0.85 -> "Aligned"
            features.topDown.stackAlignmentScore >= 0.60 && features.topDown.setupState == "pullback" -> "Aligned but pulling back"
            features.topDown.stackAlignmentScore >= 0.45 -> "Mixed"
            else -> "Conflicted"
        }
    }

    private fun describeNews(features: FeatureExtractionResult): String {
        return when {
            features.newsPulse.pulseScore >= 1.2 -> "High-impact event risk"
            features.newsPulse.pulseScore >= 0.5 -> "Post-event unstable period"
            else -> "No news veto"
        }
    }
}
