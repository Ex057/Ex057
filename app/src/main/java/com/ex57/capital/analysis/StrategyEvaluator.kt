package com.ex57.capital.analysis

import com.ex57.capital.model.Confirmation
import com.ex57.capital.model.ConfirmationMode
import com.ex57.capital.model.SetupType
import com.ex57.capital.model.TradeBias
import com.ex57.capital.model.TradeDecision
import com.ex57.capital.model.TradingSymbol
import kotlin.math.abs

internal object StrategyEvaluator {
    fun evaluate(
        symbol: TradingSymbol,
        input: AnalysisInput,
        features: FeatureExtractionResult
    ): StrategyEvaluation {
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

        val candidateScores = listOf(
            CandidateSetup(
                SetupType.TREND_PULLBACK,
                TradeBias.BULLISH,
                bullishDirectionalCount,
                (bullishDirectionalStrength * 2.6) + (trendPullbackBullSetupScore * 2.1) + (topDownBullScore * 1.2) + (confluenceMetaScore * 1.1) - riskPenalty
            ),
            CandidateSetup(
                SetupType.TREND_PULLBACK,
                TradeBias.BEARISH,
                bearishDirectionalCount,
                (bearishDirectionalStrength * 2.6) + (trendPullbackBearSetupScore * 2.1) + (topDownBearScore * 1.2) + (confluenceMetaScore * 1.1) - riskPenalty
            ),
            CandidateSetup(
                SetupType.BREAKOUT,
                TradeBias.BULLISH,
                bullishDirectionalCount,
                (bullishDirectionalStrength * 2.4) + (breakoutBullSetupScore * 2.2) + (topDownBullScore * 1.1) + (confluenceMetaScore * 1.0) - riskPenalty
            ),
            CandidateSetup(
                SetupType.BREAKOUT,
                TradeBias.BEARISH,
                bearishDirectionalCount,
                (bearishDirectionalStrength * 2.4) + (breakoutBearSetupScore * 2.2) + (topDownBearScore * 1.1) + (confluenceMetaScore * 1.0) - riskPenalty
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
        val setupStateAllowed = when (input.mode) {
            ConfirmationMode.CONSERVATIVE -> features.topDown.setupState in setOf("pullback", "aligned") && features.topDown.triggerState in setOf("entry_ready", "consolidating")
            ConfirmationMode.MODERATE -> features.topDown.setupState in setOf("pullback", "aligned", "mixed")
            ConfirmationMode.AGGRESSIVE -> features.topDown.setupState != "undetermined"
            ConfirmationMode.LENIENT -> true
        }
        val hardBlock = when (input.mode) {
            ConfirmationMode.CONSERVATIVE -> severeNoise || severeNews
            ConfirmationMode.MODERATE -> severeNoise && severeNews
            ConfirmationMode.AGGRESSIVE -> severeNoise && severeNews && confluenceMetaScore < 0.4
            ConfirmationMode.LENIENT -> severeNoise && severeNews && topDownDirectionalGate < 0.25
        }

        val bias = if (!hardBlock &&
            bestCandidate.corePassed >= AnalysisSupport.minimumCoreRequired(input.mode) &&
            directionalStrength >= directionalStrengthThreshold(input.mode) &&
            bestCandidate.score >= AnalysisSupport.biasActivationThreshold(input.mode) &&
            topDownDirectionalGate >= AnalysisSupport.topDownDirectionalThreshold(input.mode) &&
            directionalEdge >= directionalEdgeThreshold(input.mode) &&
            setupStateAllowed
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
            bestCandidate.corePassed >= AnalysisSupport.minimumCoreRequired(input.mode) &&
            directionalStrength >= directionalStrengthThreshold(input.mode) &&
            setupQualityScore >= setupQualityThreshold(input.mode, bestCandidate.type) &&
            setupQualityCount >= requiredSetupConfirmations(input.mode) &&
            confluenceMetaScore >= AnalysisSupport.confluenceGateForMode(input.mode) &&
            riskPenalty <= maxRiskPenalty(input.mode)
        val approved = when (stage) {
            1 -> baseApproval
            2 -> baseApproval && expectancyValue > -0.05
            else -> baseApproval && expectancyValue > 0.0
        }
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
            ).toInt().coerceIn(20, 89)

        val decision = when {
            bias == TradeBias.NEUTRAL || bestCandidate.corePassed < AnalysisSupport.minimumCoreRequired(input.mode) -> TradeDecision.REJECT
            approved && (stage == 1 || expectancyValue > 0.0) -> TradeDecision.ELIGIBLE
            input.mode == ConfirmationMode.LENIENT && bias != TradeBias.NEUTRAL && !hardBlock -> TradeDecision.WATCHLIST
            baseApproval -> TradeDecision.WATCHLIST
            stage >= 3 && expectancyValue <= 0.0 -> TradeDecision.REJECT
            else -> TradeDecision.WATCHLIST
        }

        return StrategyEvaluation(
            setupType = if (bias == TradeBias.NEUTRAL) SetupType.NONE else bestCandidate.type,
            bias = bias,
            approved = approved,
            decision = decision,
            confidence = confidence,
            confirmations = confirmations,
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

    private fun directionalStrengthThreshold(mode: ConfirmationMode): Double {
        return when (mode) {
            ConfirmationMode.CONSERVATIVE -> 0.66
            ConfirmationMode.MODERATE -> 0.56
            ConfirmationMode.AGGRESSIVE -> 0.48
            ConfirmationMode.LENIENT -> 0.40
        }
    }

    private fun setupQualityThreshold(mode: ConfirmationMode, setupType: SetupType): Double {
        val base = when (mode) {
            ConfirmationMode.CONSERVATIVE -> 0.60
            ConfirmationMode.MODERATE -> 0.50
            ConfirmationMode.AGGRESSIVE -> 0.42
            ConfirmationMode.LENIENT -> 0.34
        }
        return if (setupType == SetupType.BREAKOUT && mode != ConfirmationMode.CONSERVATIVE) base - 0.03 else base
    }

    private fun requiredSetupConfirmations(mode: ConfirmationMode): Int {
        return when (mode) {
            ConfirmationMode.CONSERVATIVE -> 2
            ConfirmationMode.MODERATE -> 1
            ConfirmationMode.AGGRESSIVE -> 1
            ConfirmationMode.LENIENT -> 0
        }
    }

    private fun maxRiskPenalty(mode: ConfirmationMode): Double {
        return when (mode) {
            ConfirmationMode.CONSERVATIVE -> 0.42
            ConfirmationMode.MODERATE -> 0.78
            ConfirmationMode.AGGRESSIVE -> 1.00
            ConfirmationMode.LENIENT -> 1.20
        }
    }

    private fun directionalEdgeThreshold(mode: ConfirmationMode): Double {
        return when (mode) {
            ConfirmationMode.CONSERVATIVE -> 0.45
            ConfirmationMode.MODERATE -> 0.32
            ConfirmationMode.AGGRESSIVE -> 0.20
            ConfirmationMode.LENIENT -> 0.10
        }
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
