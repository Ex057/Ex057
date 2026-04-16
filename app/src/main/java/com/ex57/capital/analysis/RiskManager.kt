package com.ex57.capital.analysis

import com.ex57.capital.model.AnalysisResult
import com.ex57.capital.model.ConfirmationMode
import com.ex57.capital.model.SetupType
import com.ex57.capital.model.TradeBias
import com.ex57.capital.model.TradeDecision
import com.ex57.capital.model.TradeSetup
import com.ex57.capital.model.TradingSymbol

internal object RiskManager {
    fun buildResult(
        symbol: TradingSymbol,
        input: AnalysisInput,
        features: FeatureExtractionResult,
        evaluation: StrategyEvaluation
    ): AnalysisResult {
        val summary = when {
            evaluation.bias == TradeBias.NEUTRAL ->
                "${symbol.code} is range-bound on ${input.timeframe}. The model sees mixed pressure, so it is better to wait."
            evaluation.approved ->
                "${evaluation.setupType.label} ${evaluation.bias.label.lowercase()} setup is active on ${input.timeframe}. Top-down confluence, forecast drift, sequence model agreement, and weighted score clear ${input.mode.label.lowercase()} mode."
            else ->
                "${evaluation.setupType.label} ${evaluation.bias.label.lowercase()} pressure exists, but the market still needs cleaner confluence, sequence-model support, or forecast agreement before execution."
        }

        val executionPlan = when (evaluation.setupType) {
            SetupType.TREND_PULLBACK -> when (evaluation.bias) {
                TradeBias.BULLISH -> "Favor long pullback entries while price holds above the short-term mean and trend structure remains intact."
                TradeBias.BEARISH -> "Favor short rebound fades while price stays below the short-term mean and trend structure remains intact."
                TradeBias.NEUTRAL -> "Stand aside until a cleaner pullback or stronger structure appears."
            }
            SetupType.BREAKOUT -> when (evaluation.bias) {
                TradeBias.BULLISH -> "Favor long entries only after range expansion confirms and the breakout remains above the recent high."
                TradeBias.BEARISH -> "Favor short entries only after downside range expansion confirms and price holds below the recent low."
                TradeBias.NEUTRAL -> "Stand aside until a range break confirms with cleaner momentum."
            }
            SetupType.NONE -> "Stand aside until price breaks the recent range with momentum and lower noise."
        }

        val riskNote = when {
            input.signalFilters.hasOverrides() ->
                "${input.signalFilters.summary()} Treat this run as a testing configuration, not a production-ready filter set."
            evaluation.performanceFeedback.strictModeActive ->
                evaluation.performanceFeedback.summary
            features.noiseScore == 0.0 ->
                "Noise is elevated for ${input.timeframe}. Expect more fakeouts and wider stop placement."
            evaluation.forecastResearch.bias == TradeBias.NEUTRAL ->
                "Forecast lane is mixed. Treat the current setup as exploratory until the next path becomes clearer."
            evaluation.forecastResearch.stabilityScore < 0.40 ->
                "Projected path stability is weak. Entries need extra patience because the regime is still unstable."
            evaluation.forecastModelMetrics.forecastDispersion > 0.65 ->
                "Sequence model dispersion is elevated. Favor waiting for lower-noise continuation before entry."
            evaluation.forecastModelMetrics.targetBeforeStopScore < 0.4 ->
                "Sequence model target-before-stop potential is weak. Setup needs a better location or stronger impulse."
            features.topDown.confluenceScore < 0.5 ->
                "Top-down confluence is weak. Treat this as a developing idea until structure and liquidity align."
            features.newsPulse.pulseScore >= 1.2 ->
                "Event-volatility pulse detected. Use smaller size and wait for candle-close confirmation."
            !evaluation.approved && evaluation.stage == 1 ->
                "Cold-start evidence mode is active. Structure can still qualify, but the setup needs cleaner supportive scores first."
            !evaluation.approved ->
                "The directional read is early. Wait for one more confirming impulse before committing capital."
            else ->
                "This is still a probability model. Validate the signal against your risk limits and execution spread."
        }

        val tradeSetup = buildTradeSetup(
            symbol = symbol,
            mode = input.mode,
            setupType = evaluation.setupType,
            bias = evaluation.bias,
            approved = evaluation.approved,
            last = features.last,
            averageStep = features.averageStep,
            noiseRatio = features.noiseRatio,
            noiseCeiling = features.noiseCeiling,
            supportLevel = features.topDown.supportLevel,
            resistanceLevel = features.topDown.resistanceLevel
        ).copy(shouldTrade = evaluation.decision == TradeDecision.ELIGIBLE)

        val traderGuidance = when (evaluation.decision) {
            TradeDecision.ELIGIBLE ->
                "Eligible setup. Wait for price to approach the planned entry, then execute only if the ${evaluation.setupType.label.lowercase()} structure and forecast drift still hold at candle close."
            TradeDecision.WATCHLIST ->
                "Watchlist only. ${evaluation.rejectionReasons.firstOrNull() ?: "Supportive scores or staged evidence are not strong enough for immediate execution."}"
            TradeDecision.REJECT ->
                "Reject this setup. ${evaluation.rejectionReasons.firstOrNull() ?: "Either expectancy is not positive or the market is not directional enough yet."}"
        }
        val nextTrigger = when {
            evaluation.decision == TradeDecision.ELIGIBLE ->
                "Set an alert at the planned entry and only act if the next close preserves the ${evaluation.setupType.label.lowercase()} structure."
            evaluation.setupType == SetupType.BREAKOUT && evaluation.bias == TradeBias.BULLISH ->
                "Eligible if the next candle closes above resistance with noise contained and HTF bias still bullish."
            evaluation.setupType == SetupType.BREAKOUT && evaluation.bias == TradeBias.BEARISH ->
                "Eligible if the next candle closes below support with noise contained and HTF bias still bearish."
            evaluation.setupType == SetupType.TREND_PULLBACK && evaluation.bias == TradeBias.BULLISH ->
                "Eligible if the pullback holds near demand/mean and momentum re-accelerates upward."
            evaluation.setupType == SetupType.TREND_PULLBACK && evaluation.bias == TradeBias.BEARISH ->
                "Eligible if the rebound stalls near supply/mean and momentum re-accelerates downward."
            else ->
                "Wait for either a cleaner trend pullback or a confirmed breakout before considering a trade."
        }

        return AnalysisResult(
            bias = evaluation.bias,
            confidence = evaluation.confidence,
            approved = evaluation.approved,
            summary = summary,
            executionPlan = executionPlan,
            riskNote = riskNote,
            confirmations = evaluation.confirmations,
            tradeSetup = tradeSetup,
            traderGuidance = traderGuidance,
            decision = evaluation.decision,
            rejectionReasons = evaluation.rejectionReasons,
            evidence = features.evidence,
            setupType = evaluation.setupType,
            nextTrigger = nextTrigger,
            mtfaStatus = AnalysisSupport.buildMtfaStatus(features.topDown),
            forecastResearch = evaluation.forecastResearch,
            forecastModelMetrics = evaluation.forecastModelMetrics,
            performanceFeedback = evaluation.performanceFeedback
        )
    }

    private fun buildTradeSetup(
        symbol: TradingSymbol,
        mode: ConfirmationMode,
        setupType: SetupType,
        bias: TradeBias,
        approved: Boolean,
        last: Double,
        averageStep: Double,
        noiseRatio: Double,
        noiseCeiling: Double,
        supportLevel: Double?,
        resistanceLevel: Double?
    ): TradeSetup {
        val spreadBuffer = maxOf(symbol.spec.typicalSpread * 1.1, averageStep * 0.18)
        val baseRisk = (
            averageStep * (2.0 + (noiseRatio / noiseCeiling).coerceAtMost(1.2))
            ).coerceAtLeast(maxOf(last * 0.0015, spreadBuffer * 2.5))
        val rewardMultiplier = AnalysisSupport.configFor(mode).rewardMultiplier
        val setupAdjustment = if (setupType == SetupType.BREAKOUT) 1.15 else 1.0

        return when (bias) {
            TradeBias.BULLISH -> {
                val entry = if (setupType == SetupType.BREAKOUT) {
                    last + (averageStep * 0.12) + spreadBuffer
                } else {
                    last - (averageStep * 0.25) + (spreadBuffer * 0.35)
                }
                val projectedStop = entry - (baseRisk * setupAdjustment)
                val structuralStop = supportLevel?.minus(spreadBuffer)?.takeIf { it < entry }
                val stopLoss = listOfNotNull(projectedStop, structuralStop).minOrNull() ?: projectedStop
                val projectedTarget = entry + (baseRisk * rewardMultiplier * setupAdjustment)
                val structuralTarget = resistanceLevel
                    ?.minus(spreadBuffer * 0.35)
                    ?.takeIf { it > entry + spreadBuffer }
                val takeProfit = structuralTarget ?: projectedTarget
                TradeSetup(
                    entry = entry,
                    stopLoss = stopLoss,
                    takeProfit = takeProfit,
                    riskReward = formatRiskReward(entry = entry, stopLoss = stopLoss, takeProfit = takeProfit),
                    shouldTrade = approved
                )
            }
            TradeBias.BEARISH -> {
                val entry = if (setupType == SetupType.BREAKOUT) {
                    last - (averageStep * 0.12) - spreadBuffer
                } else {
                    last + (averageStep * 0.25) - (spreadBuffer * 0.35)
                }
                val projectedStop = entry + (baseRisk * setupAdjustment)
                val structuralStop = resistanceLevel?.plus(spreadBuffer)?.takeIf { it > entry }
                val stopLoss = listOfNotNull(projectedStop, structuralStop).maxOrNull() ?: projectedStop
                val projectedTarget = entry - (baseRisk * rewardMultiplier * setupAdjustment)
                val structuralTarget = supportLevel
                    ?.plus(spreadBuffer * 0.35)
                    ?.takeIf { it < entry - spreadBuffer }
                val takeProfit = structuralTarget ?: projectedTarget
                TradeSetup(
                    entry = entry,
                    stopLoss = stopLoss,
                    takeProfit = takeProfit,
                    riskReward = formatRiskReward(entry = entry, stopLoss = stopLoss, takeProfit = takeProfit),
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
    }

    private fun formatRiskReward(
        entry: Double,
        stopLoss: Double,
        takeProfit: Double
    ): String {
        val risk = kotlin.math.abs(entry - stopLoss).coerceAtLeast(0.00001)
        val reward = kotlin.math.abs(takeProfit - entry)
        return "1:${"%.1f".format(reward / risk)}"
    }
}
