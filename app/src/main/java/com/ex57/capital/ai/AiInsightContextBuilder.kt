package com.ex57.capital.ai

import com.ex57.capital.model.AnalysisResult
import com.ex57.capital.model.ClosedTradeRecord
import com.ex57.capital.model.ConfirmationMode
import com.ex57.capital.model.DataQuality
import com.ex57.capital.model.FeedState
import com.ex57.capital.model.MarketCandle
import com.ex57.capital.model.TradePosition
import com.ex57.capital.model.TradingSymbol
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.max

object AiInsightContextBuilder {
    fun build(
        symbol: TradingSymbol,
        timeframe: String,
        mode: ConfirmationMode,
        dataQuality: DataQuality,
        feedState: FeedState,
        candles: List<MarketCandle>,
        candleStack: Map<String, List<MarketCandle>>,
        analysisResult: AnalysisResult?,
        openTrade: TradePosition?,
        recentTrades: List<ClosedTradeRecord>,
        livePrice: Double?
    ): AiInsightContext? {
        val marketSnapshot = buildMarketSnapshot(
            symbol = symbol,
            timeframe = timeframe,
            candles = candles,
            candleStack = candleStack,
            livePrice = livePrice
        ) ?: return null

        val scopedTrades = recentTrades
            .filter { it.symbolCode == symbol.code && it.timeframe == timeframe }
            .sortedByDescending { it.closedAtEpochMillis }
            .take(6)
        val realizedTradeCount = scopedTrades.size
        val realizedWins = scopedTrades.count { it.pnlUsd > 0.0 }
        val realizedWinRate = if (realizedTradeCount == 0) null else ((realizedWins.toDouble() / realizedTradeCount) * 100.0).toInt()
        val realizedNetPnlUsd = scopedTrades.takeIf { it.isNotEmpty() }?.sumOf { it.pnlUsd }

        val context = AiInsightContext(
            symbolCode = symbol.code,
            symbolLabel = symbol.label,
            timeframe = timeframe,
            confirmationMode = mode.label,
            dataQualityLabel = dataQuality.label,
            feedStatusLabel = feedState.message,
            analysisHash = "",
            generatedAtEpochMillis = System.currentTimeMillis(),
            market = marketSnapshot,
            mtfa = analysisResult?.mtfaStatus?.let {
                AiMtfaSnapshot(
                    macro = it.macro,
                    structure = it.structure,
                    setup = it.setup,
                    trigger = it.trigger
                )
            },
            signal = analysisResult?.let {
                AiSignalSnapshot(
                    bias = it.bias.label,
                    confidence = it.confidence,
                    decision = it.decision.label,
                    approved = it.approved,
                    setupType = it.setupType.label,
                    tradeCandidate = it.tradeSetup.shouldTrade,
                    summary = it.summary,
                    executionPlan = it.executionPlan,
                    riskNote = it.riskNote,
                    traderGuidance = it.traderGuidance,
                    nextTrigger = it.nextTrigger,
                    forecastSummary = it.forecastResearch?.summary,
                    performanceSummary = it.performanceFeedback?.summary,
                    rejectionReasons = it.rejectionReasons.take(4)
                )
            },
            tradePlan = analysisResult?.let { result ->
                result.tradeSetup.entry?.let {
                    AiTradePlanSnapshot(
                        entry = formatPrice(result.tradeSetup.entry, symbol.spec.pricePrecision),
                        stopLoss = formatPrice(result.tradeSetup.stopLoss, symbol.spec.pricePrecision),
                        takeProfit = formatPrice(result.tradeSetup.takeProfit, symbol.spec.pricePrecision),
                        riskReward = result.tradeSetup.riskReward,
                        shouldTrade = result.tradeSetup.shouldTrade
                    )
                }
            },
            confirmations = analysisResult?.confirmations?.take(10)?.map {
                AiConfirmationSnapshot(
                    name = it.name,
                    passed = it.passed,
                    details = it.details
                )
            } ?: emptyList(),
            evidence = analysisResult?.let {
                AiEvidenceSnapshot(
                    heuristicSummary = "Heuristic evidence badge ${it.evidence.confidenceBadge}, robustness ${it.evidence.robustness}, expectancy ${it.evidence.expectancyR}, profit factor ${it.evidence.profitFactor}.",
                    heuristicSampleSize = it.evidence.sampleSize,
                    confidenceBadge = it.evidence.confidenceBadge,
                    robustness = it.evidence.robustness,
                    expectancyR = it.evidence.expectancyR,
                    profitFactor = it.evidence.profitFactor,
                    lastOutcomes = it.evidence.lastOutcomes.take(5),
                    realizedSummary = when {
                        realizedTradeCount == 0 -> "No recent realized trades recorded for this symbol and timeframe."
                        else -> "Recent realized trades: $realizedTradeCount, win rate ${realizedWinRate ?: 0}%, net ${formatSignedUsd(realizedNetPnlUsd ?: 0.0)}."
                    },
                    realizedTradeCount = realizedTradeCount,
                    realizedWinRate = realizedWinRate,
                    realizedNetPnlUsd = realizedNetPnlUsd
                )
            },
            openTrade = openTrade?.let { position ->
                AiOpenTradeSnapshot(
                    side = position.side.label,
                    timeframe = position.timeframe,
                    entryPrice = formatPrice(position.entryPrice, symbol.spec.pricePrecision) ?: "-",
                    livePrice = formatPrice(livePrice, symbol.spec.pricePrecision),
                    stopLoss = formatPrice(position.stopLoss, symbol.spec.pricePrecision),
                    takeProfit = formatPrice(position.takeProfit, symbol.spec.pricePrecision),
                    unrealizedPercent = livePrice?.let { formatPercentSigned(unrealizedPercent(position, it)) },
                    managementStage = position.managementStage,
                    rationale = position.rationale
                )
            },
            recentTrades = scopedTrades.map {
                AiRecentTradeSnapshot(
                    side = it.side.label,
                    lotSize = formatLot(it.lotSize),
                    openedAtLabel = formatTimestamp(it.openedAtEpochMillis),
                    closedAtLabel = formatTimestamp(it.closedAtEpochMillis),
                    outcome = it.outcomeLabel,
                    pnlUsd = formatSignedUsd(it.pnlUsd),
                    pnlPercent = formatPercentSigned(it.pnlPercent),
                    rationale = it.rationale
                )
            }
        )
        return context.copy(analysisHash = hashContext(context))
    }

    private fun buildMarketSnapshot(
        symbol: TradingSymbol,
        timeframe: String,
        candles: List<MarketCandle>,
        candleStack: Map<String, List<MarketCandle>>,
        livePrice: Double?
    ): AiMarketSnapshot? {
        val primaryCandles = candles.takeLast(180)
        if (primaryCandles.isEmpty() && livePrice == null) return null

        val stacked = buildList {
            add(buildTimeframeSnapshot(timeframe, primaryCandles, symbol))
            candleStack.entries
                .sortedBy { timeframeRank(it.key) }
                .forEach { (stackTimeframe, stackCandles) ->
                    if (stackTimeframe != timeframe) {
                        add(buildTimeframeSnapshot(stackTimeframe, stackCandles.takeLast(180), symbol))
                    }
                }
        }.filter { it.candleCount > 0 }

        val effectivePrice = livePrice ?: primaryCandles.lastOrNull()?.close
        val support = nearestLevelBelow(primaryCandles, effectivePrice)
        val resistance = nearestLevelAbove(primaryCandles, effectivePrice)

        return AiMarketSnapshot(
            livePrice = formatPrice(effectivePrice, symbol.spec.pricePrecision),
            primaryCandleCount = primaryCandles.size,
            shortTrend = deriveTrendLabel(primaryCandles),
            structureLabel = deriveStructureLabel(primaryCandles),
            momentumLabel = deriveMomentumLabel(primaryCandles),
            volatilityLabel = deriveVolatilityLabel(primaryCandles),
            rangePercent = percentString(primaryCandles.takeIf { it.isNotEmpty() }?.let {
                percentMove(it.minOf { candle -> candle.low }, it.maxOf { candle -> candle.high })
            }),
            recentChangePercent = percentString(primaryCandles.takeIf { it.size >= 2 }?.let {
                percentMove(it.first().open, it.last().close)
            }),
            nearestSupport = formatPrice(support, symbol.spec.pricePrecision),
            nearestResistance = formatPrice(resistance, symbol.spec.pricePrecision),
            stackedTimeframes = stacked.take(4)
        )
    }

    private fun buildTimeframeSnapshot(
        timeframe: String,
        candles: List<MarketCandle>,
        symbol: TradingSymbol
    ): AiMarketTimeframeSnapshot {
        val trimmed = candles.takeLast(180)
        return AiMarketTimeframeSnapshot(
            timeframe = timeframe,
            candleCount = trimmed.size,
            trendLabel = deriveTrendLabel(trimmed),
            momentumLabel = deriveMomentumLabel(trimmed),
            rangePercent = percentString(trimmed.takeIf { it.isNotEmpty() }?.let {
                percentMove(it.minOf { candle -> candle.low }, it.maxOf { candle -> candle.high })
            }),
            changePercent = percentString(trimmed.takeIf { it.size >= 2 }?.let {
                percentMove(it.first().open, it.last().close)
            }),
            lastClose = formatPrice(trimmed.lastOrNull()?.close, symbol.spec.pricePrecision)
        )
    }

    private fun unrealizedPercent(position: TradePosition, livePrice: Double): Double {
        val rawPercent = when (position.side.label) {
            "Long" -> ((livePrice - position.entryPrice) / position.entryPrice) * 100.0
            else -> ((position.entryPrice - livePrice) / position.entryPrice) * 100.0
        }
        return if (position.stakeUsd <= 0.0) rawPercent else (((position.stakeUsd * (rawPercent / 100.0)) - position.estimatedSpreadCostUsd) / position.stakeUsd) * 100.0
    }

    private fun hashContext(context: AiInsightContext): String {
        val payload = buildString {
            append(context.symbolCode)
            append('|').append(context.timeframe)
            append('|').append(context.confirmationMode)
            append('|').append(context.feedStatusLabel)
            append('|').append(context.market.livePrice)
            append('|').append(context.market.shortTrend)
            append('|').append(context.market.structureLabel)
            append('|').append(context.market.momentumLabel)
            append('|').append(context.market.volatilityLabel)
            append('|').append(context.market.rangePercent)
            append('|').append(context.market.recentChangePercent)
            append('|').append(context.market.nearestSupport)
            append('|').append(context.market.nearestResistance)
            context.market.stackedTimeframes.forEach {
                append('|').append(it.timeframe).append(':').append(it.trendLabel).append(':').append(it.momentumLabel).append(':').append(it.changePercent)
            }
            append('|').append(context.signal?.bias)
            append('|').append(context.signal?.confidence)
            append('|').append(context.signal?.decision)
            append('|').append(context.signal?.summary)
            append('|').append(context.signal?.riskNote)
            append('|').append(context.tradePlan?.entry)
            append('|').append(context.tradePlan?.stopLoss)
            append('|').append(context.tradePlan?.takeProfit)
            append('|').append(context.evidence?.heuristicSummary)
            append('|').append(context.evidence?.realizedSummary)
            context.confirmations.forEach {
                append('|').append(it.name).append(':').append(it.passed).append(':').append(it.details)
            }
            context.recentTrades.forEach {
                append('|').append(it.outcome).append(':').append(it.pnlUsd).append(':').append(it.closedAtLabel)
            }
        }
        val digest = MessageDigest.getInstance("SHA-256").digest(payload.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }.take(16)
    }

    private fun deriveTrendLabel(candles: List<MarketCandle>): String {
        if (candles.size < 8) return "Insufficient trend data"
        val closes = candles.map { it.close }
        val shortMean = closes.takeLast(max(4, closes.size / 6)).average()
        val longMean = closes.average()
        val slope = percentMove(closes.first(), closes.last())
        return when {
            shortMean > longMean && slope > 0.35 -> "Uptrend"
            shortMean < longMean && slope < -0.35 -> "Downtrend"
            abs(slope) < 0.18 -> "Sideways"
            slope >= 0.0 -> "Recovering"
            else -> "Softening"
        }
    }

    private fun deriveStructureLabel(candles: List<MarketCandle>): String {
        if (candles.size < 10) return "Limited structure"
        val recent = candles.takeLast(10)
        val highs = recent.map { it.high }
        val lows = recent.map { it.low }
        val latestClose = recent.last().close
        val band = (highs.maxOrNull() ?: latestClose) - (lows.minOrNull() ?: latestClose)
        return when {
            band <= latestClose * 0.0025 -> "Compression"
            latestClose >= (highs.dropLast(1).maxOrNull() ?: latestClose) -> "Range high pressure"
            latestClose <= (lows.dropLast(1).minOrNull() ?: latestClose) -> "Range low pressure"
            else -> "Balanced range"
        }
    }

    private fun deriveMomentumLabel(candles: List<MarketCandle>): String {
        if (candles.size < 6) return "Limited momentum"
        val recent = candles.takeLast(4)
        val bodyBias = recent.sumOf { it.close - it.open }
        val impulse = percentMove(recent.first().open, recent.last().close)
        return when {
            bodyBias > 0.0 && impulse > 0.30 -> "Bullish impulse"
            bodyBias < 0.0 && impulse < -0.30 -> "Bearish impulse"
            abs(impulse) < 0.10 -> "Flat momentum"
            bodyBias >= 0.0 -> "Bullish drift"
            else -> "Bearish drift"
        }
    }

    private fun deriveVolatilityLabel(candles: List<MarketCandle>): String {
        if (candles.size < 8) return "Limited volatility data"
        val recent = candles.takeLast(8)
        val averageRange = recent.map { it.high - it.low }.average()
        val averageClose = recent.map { it.close }.average().takeIf { it != 0.0 } ?: return "Limited volatility data"
        val normalized = (averageRange / averageClose) * 100.0
        return when {
            normalized >= 1.2 -> "High volatility"
            normalized >= 0.45 -> "Moderate volatility"
            else -> "Compressed volatility"
        }
    }

    private fun percentMove(from: Double, to: Double): Double {
        if (from == 0.0) return 0.0
        return ((to - from) / from) * 100.0
    }

    private fun percentString(value: Double?): String = value?.let { "${"%.2f".format(it)}%" } ?: "-"

    private fun nearestLevelBelow(candles: List<MarketCandle>, price: Double?): Double? {
        val pivot = price ?: return candles.lastOrNull()?.low
        return candles.map { it.low }.filter { it <= pivot }.maxOrNull()
    }

    private fun nearestLevelAbove(candles: List<MarketCandle>, price: Double?): Double? {
        val pivot = price ?: return candles.lastOrNull()?.high
        return candles.map { it.high }.filter { it >= pivot }.minOrNull()
    }

    private fun timeframeRank(timeframe: String): Int {
        return when (timeframe) {
            "1m" -> 1
            "5m" -> 2
            "15m" -> 3
            "30m" -> 4
            "1h" -> 5
            "4h" -> 6
            "1d" -> 7
            else -> 99
        }
    }

    private fun formatPrice(value: Double?, precision: Int = 5): String? = value?.let { "%.${precision}f".format(it) }

    private fun formatLot(value: Double): String {
        return when {
            value >= 1.0 -> "%.2f".format(value)
            value >= 0.1 -> "%.3f".format(value)
            else -> "%.4f".format(value)
        }
    }

    private fun formatSignedUsd(value: Double): String {
        return if (value >= 0.0) "+$${"%.2f".format(value)}" else "-$${"%.2f".format(abs(value))}"
    }

    private fun formatPercentSigned(value: Double): String {
        return if (value >= 0.0) "+${"%.2f".format(value)}%" else "${"%.2f".format(value)}%"
    }

    private fun formatTimestamp(epochMillis: Long): String {
        return DateTimeFormatter.ofPattern("MMM d, HH:mm")
            .withZone(ZoneId.systemDefault())
            .format(Instant.ofEpochMilli(epochMillis))
    }
}
