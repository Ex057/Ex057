package com.ex57.capital.analysis

import com.ex57.capital.model.AnalysisResult
import com.ex57.capital.model.Confirmation
import com.ex57.capital.model.PositionGuidance
import com.ex57.capital.model.PositionRecommendation
import com.ex57.capital.model.PositionSide
import com.ex57.capital.model.TradeBias
import com.ex57.capital.model.TradeDecision
import com.ex57.capital.model.TradePosition
import com.ex57.capital.model.TradingSymbol

internal object PositionManager {
    fun applyPositionContext(
        result: AnalysisResult,
        openPosition: TradePosition?,
        currentPrice: Double,
        symbol: TradingSymbol,
        timeframe: String
    ): AnalysisResult {
        if (openPosition == null) return result

        val pnlPercent = when (openPosition.side) {
            PositionSide.LONG -> ((currentPrice - openPosition.entryPrice) / openPosition.entryPrice) * 100.0
            PositionSide.SHORT -> ((openPosition.entryPrice - currentPrice) / openPosition.entryPrice) * 100.0
        }
        val distanceToStopPercent = openPosition.stopLoss?.let { stop ->
            when (openPosition.side) {
                PositionSide.LONG -> ((currentPrice - stop) / currentPrice) * 100.0
                PositionSide.SHORT -> ((stop - currentPrice) / currentPrice) * 100.0
            }
        }
        val distanceToTargetPercent = openPosition.takeProfit?.let { target ->
            when (openPosition.side) {
                PositionSide.LONG -> ((target - currentPrice) / currentPrice) * 100.0
                PositionSide.SHORT -> ((currentPrice - target) / currentPrice) * 100.0
            }
        }
        val sameDirection = (openPosition.side == PositionSide.LONG && result.bias == TradeBias.BULLISH) ||
            (openPosition.side == PositionSide.SHORT && result.bias == TradeBias.BEARISH)
        val invalidated = (openPosition.side == PositionSide.LONG && result.bias == TradeBias.BEARISH && result.confidence >= 60) ||
            (openPosition.side == PositionSide.SHORT && result.bias == TradeBias.BULLISH && result.confidence >= 60)
        val stopThreat = distanceToStopPercent != null && distanceToStopPercent <= 0.25
        val nearTarget = distanceToTargetPercent != null && distanceToTargetPercent <= 0.35

        val recommendation = when {
            stopThreat || invalidated -> PositionRecommendation.EXIT
            nearTarget || pnlPercent >= 1.0 -> PositionRecommendation.SCALE_OUT
            sameDirection -> PositionRecommendation.HOLD
            else -> PositionRecommendation.WAIT
        }
        val headline = when (recommendation) {
            PositionRecommendation.HOLD -> "Open ${openPosition.side.label.lowercase()} position remains aligned."
            PositionRecommendation.SCALE_OUT -> "Open ${openPosition.side.label.lowercase()} position is working. Protect gains."
            PositionRecommendation.EXIT -> "Open ${openPosition.side.label.lowercase()} position is at risk."
            PositionRecommendation.WAIT -> "Open ${openPosition.side.label.lowercase()} position is active, but momentum is mixed."
        }
        val detail = when (recommendation) {
            PositionRecommendation.HOLD ->
                "Maintain the trade while ${symbol.code} keeps respecting the original structure on $timeframe."
            PositionRecommendation.SCALE_OUT ->
                "Price is close to target or the open profit is extended. Consider partial profit-taking and tightening risk."
            PositionRecommendation.EXIT ->
                "The live read is either near the stop or leaning against your open position. Reduce or close exposure."
            PositionRecommendation.WAIT ->
                "Do not add size here. Let the market either realign with your entry thesis or invalidate it cleanly."
        }

        return result.copy(
            approved = false,
            summary = "Position memory active. ${openPosition.side.label} ${symbol.code} from ${AnalysisSupport.formatPrice(openPosition.entryPrice)} is open; new entries are suppressed.",
            executionPlan = "Manage the active position instead of treating this as a fresh setup.",
            traderGuidance = detail,
            nextTrigger = when (recommendation) {
                PositionRecommendation.HOLD -> "Hold while price stays away from the stop and the directional structure remains intact."
                PositionRecommendation.SCALE_OUT -> "Scale out into strength and trail the stop to protect open profit."
                PositionRecommendation.EXIT -> "Exit if the next close confirms the adverse move or stop pressure persists."
                PositionRecommendation.WAIT -> "Wait for re-alignment before making any adjustment."
            },
            decision = when (recommendation) {
                PositionRecommendation.EXIT -> TradeDecision.REJECT
                else -> TradeDecision.WATCHLIST
            },
            tradeSetup = result.tradeSetup.copy(shouldTrade = false),
            confirmations = listOf(
                Confirmation(
                    name = "Position Memory",
                    passed = recommendation != PositionRecommendation.EXIT,
                    details = "Open ${openPosition.side.label.lowercase()} from ${AnalysisSupport.formatPrice(openPosition.entryPrice)}; unrealized ${AnalysisSupport.formatPercentSigned(pnlPercent)}"
                )
            ) + result.confirmations,
            positionGuidance = PositionGuidance(
                recommendation = recommendation,
                headline = headline,
                detail = detail,
                unrealizedPercent = pnlPercent,
                distanceToStopPercent = distanceToStopPercent,
                distanceToTargetPercent = distanceToTargetPercent
            )
        )
    }
}
