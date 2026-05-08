package com.ex57.capital.ai

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.min

class AiPromptComposer(
    private val maxPromptChars: Int = 6_500
) {
    fun compose(request: AiInsightRequest, provider: AiProvider): AiComposedPrompt {
        val systemPrompt = buildString {
            appendLine("You are an AI insights layer for a gold (XAUUSD) trading app.")
            appendLine("You do not place trades or guarantee outcomes.")
            appendLine("Use only the structured context provided.")
            appendLine("Never invent candles, indicators, prices, or broker data.")
            appendLine("Clearly distinguish heuristic evidence from realized evidence.")
            appendLine("State uncertainty when evidence is mixed.")
            appendLine("Never guarantee outcomes or promise accuracy.")
            appendLine("Think like a discretionary trader: context -> bias -> setup -> trigger -> invalidation -> risk.")
            appendLine("Prioritize gold-specific drivers: session timing, dollar/real-yield pressure proxies, and breakout quality.")
            appendLine("Explicitly reference ADX trend filter, EMA(20/200) alignment, ATR-based stop logic, and candlestick confirmations when present.")
            appendLine("Prefer concrete market structure language over generic finance text.")
            appendLine("Outside London or London-New York overlap, lower confidence unless momentum is exceptional.")
            appendLine("If evidence is weak, explicitly recommend wait/no trade and what must change.")
            appendLine("Be concise and actionable.")
            appendLine("Return valid JSON only with keys: title, summary, bullets, caution.")
            appendLine("bullets must be an array of 2 to 4 short strings.")
            appendLine("bullets must cover: (1) directional read, (2) setup/trigger, (3) invalidation or wait condition.")
            appendLine("For MARKET_DEEP_DIVE also include an extra key named entry_object with strict keys: entry_tf, entry_type, entry_level, stop, target, no_trade_reason.")
        }

        val userPrompt = buildUserPrompt(request)
        return AiComposedPrompt(
            systemPrompt = trim(systemPrompt),
            userPrompt = trim(userPrompt),
            requestHash = "${provider.name.lowercase()}-${request.action.name.lowercase()}-${request.context.analysisHash}"
        )
    }

    private fun buildUserPrompt(request: AiInsightRequest): String {
        val context = request.context
        val task = when (request.action) {
            AiInsightQuickAction.MARKET_DEEP_DIVE ->
                "Do an independent market read from the structured live market snapshot only. Do not reuse app confirmation labels. Build a trader-style read: regime context, directional bias (long/short/no trade/wait), setup quality, trigger condition, and invalidation condition. Use explicit top-down flow: HTF bias -> MTF structure -> LTF trigger (1m/5m). If multi-timeframe data conflicts, bias should default to wait/no trade. Return entry_object with strict keys: entry_tf, entry_type, entry_level, stop, target, no_trade_reason."
            AiInsightQuickAction.EXPLAIN_SIGNAL ->
                "Explain why the engine currently reads this setup this way. Prioritize top drivers, then the single most important trigger and invalidation condition."
            AiInsightQuickAction.EXPLAIN_RISK ->
                "Explain the main risks in this setup like a trader risk memo: regime risk, structure risk, and execution risk."
            AiInsightQuickAction.WHY_NOT_ELIGIBLE ->
                "Explain clearly why this setup is not yet eligible, or what still blocks it. If it is already eligible, explain what made it pass."
            AiInsightQuickAction.SUMMARIZE_TRADE_PLAN ->
                "Summarize the trade plan in plain language: direction, entry idea, stop, target, and exact invalidation condition."
            AiInsightQuickAction.MANAGE_OPEN_TRADE ->
                "Explain what makes sense for the already open trade. Focus on hold, reduce, or exit thinking using only the structured context."
        }

        val payload = JSONObject().apply {
            put("task", task)
            put("action", request.action.label)
            put("context", JSONObject().apply {
                put("symbol", context.symbolCode)
                put("symbol_label", context.symbolLabel)
                put("timeframe", context.timeframe)
                put("confirmation_mode", context.confirmationMode)
                put("data_quality", context.dataQualityLabel)
                put("feed_status", context.feedStatusLabel)
                put("analysis_hash", context.analysisHash)
                put("market", JSONObject().apply {
                    put("live_price", context.market.livePrice ?: "")
                    put("primary_candle_count", context.market.primaryCandleCount)
                    put("short_trend", context.market.shortTrend)
                    put("structure", context.market.structureLabel)
                    put("momentum", context.market.momentumLabel)
                    put("volatility", context.market.volatilityLabel)
                    put("range_percent", context.market.rangePercent)
                    put("recent_change_percent", context.market.recentChangePercent)
                    put("nearest_support", context.market.nearestSupport ?: "")
                    put("nearest_resistance", context.market.nearestResistance ?: "")
                    put("stacked_timeframes", JSONArray().apply {
                        context.market.stackedTimeframes.forEach { snapshot ->
                            put(
                                JSONObject()
                                    .put("timeframe", snapshot.timeframe)
                                    .put("candle_count", snapshot.candleCount)
                                    .put("trend", snapshot.trendLabel)
                                    .put("momentum", snapshot.momentumLabel)
                                    .put("range_percent", snapshot.rangePercent)
                                    .put("change_percent", snapshot.changePercent)
                                    .put("last_close", snapshot.lastClose ?: "")
                                    .put("recent_ohlc", JSONArray(snapshot.recentOhlc))
                            )
                        }
                    })
                })
                if (request.action == AiInsightQuickAction.MARKET_DEEP_DIVE) {
                    put("gold_checks", context.deepDive?.goldChecks?.let {
                        JSONObject()
                            .put("adx14_h4", it.adx14H4)
                            .put("ema20", it.ema20)
                            .put("ema200", it.ema200)
                            .put("atr14_h1", it.atr14H1)
                            .put("price_above_200ema", it.priceAbove200Ema)
                            .put("touches_20ema", it.touches20Ema)
                            .put("h4_bull_engulfing", it.h4BullEngulfing)
                            .put("h4_bear_engulfing", it.h4BearEngulfing)
                            .put("m5_bull_engulfing", it.m5BullEngulfing)
                            .put("m5_bear_engulfing", it.m5BearEngulfing)
                            .put("belt_hold", it.beltHold)
                            .put("long_line", it.longLine)
                            .put("is_london_session", it.isLondonSession)
                            .put("is_ny_overlap", it.isNyOverlap)
                    } ?: JSONObject.NULL)
                    put("top_down_block", context.deepDive?.topDown?.let {
                        JSONObject()
                            .put("htf_bias", it.htfBias)
                            .put("mtf_structure", it.mtfStructure)
                            .put("ltf_trigger", it.ltfTrigger)
                            .put("invalidation", it.invalidation)
                    } ?: JSONObject.NULL)
                    put("entry_object_candidate", context.deepDive?.entryObject?.let {
                        JSONObject()
                            .put("entry_tf", it.entryTf)
                            .put("entry_type", it.entryType)
                            .put("entry_level", it.entryLevel ?: "")
                            .put("stop", it.stop ?: "")
                            .put("target", it.target ?: "")
                            .put("no_trade_reason", it.noTradeReason ?: "")
                    } ?: JSONObject.NULL)
                }
                if (request.action != AiInsightQuickAction.MARKET_DEEP_DIVE) {
                    put("mtfa", context.mtfa?.let {
                        JSONObject()
                            .put("macro", it.macro)
                            .put("structure", it.structure)
                            .put("setup", it.setup)
                            .put("trigger", it.trigger)
                    } ?: JSONObject.NULL)
                    put("signal", context.signal?.let {
                        JSONObject().apply {
                            put("bias", it.bias)
                            put("confidence", it.confidence)
                            put("decision", it.decision)
                            put("approved", it.approved)
                            put("setup_type", it.setupType)
                            put("trade_candidate", it.tradeCandidate)
                            put("summary", it.summary)
                            put("execution_plan", it.executionPlan)
                            put("risk_note", it.riskNote)
                            put("trader_guidance", it.traderGuidance)
                            put("next_trigger", it.nextTrigger)
                            put("forecast_summary", it.forecastSummary ?: "")
                            put("performance_summary", it.performanceSummary ?: "")
                            put("rejection_reasons", JSONArray(it.rejectionReasons))
                        }
                    } ?: JSONObject.NULL)
                }
                if (request.action != AiInsightQuickAction.MARKET_DEEP_DIVE) {
                    put("trade_plan", context.tradePlan?.let {
                        JSONObject()
                            .put("entry", it.entry ?: "")
                            .put("stop_loss", it.stopLoss ?: "")
                            .put("take_profit", it.takeProfit ?: "")
                            .put("risk_reward", it.riskReward)
                            .put("should_trade", it.shouldTrade)
                    } ?: JSONObject.NULL)
                    put("confirmations", JSONArray().apply {
                        context.confirmations.forEach { confirmation ->
                            put(
                                JSONObject()
                                    .put("name", confirmation.name)
                                    .put("passed", confirmation.passed)
                                    .put("details", confirmation.details)
                            )
                        }
                    })
                    put("evidence", JSONObject().apply {
                        if (context.evidence != null) {
                            put("heuristic_summary", context.evidence.heuristicSummary)
                            put("heuristic_sample_size", context.evidence.heuristicSampleSize)
                            put("confidence_badge", context.evidence.confidenceBadge)
                            put("robustness", context.evidence.robustness)
                            put("expectancy_r", context.evidence.expectancyR)
                            put("profit_factor", context.evidence.profitFactor)
                            put("last_outcomes", JSONArray(context.evidence.lastOutcomes))
                            put("realized_summary", context.evidence.realizedSummary)
                        }
                    })
                    put("open_trade", context.openTrade?.let {
                        JSONObject()
                            .put("side", it.side)
                            .put("timeframe", it.timeframe)
                            .put("entry_price", it.entryPrice)
                            .put("live_price", it.livePrice ?: "")
                            .put("stop_loss", it.stopLoss ?: "")
                            .put("take_profit", it.takeProfit ?: "")
                            .put("unrealized_percent", it.unrealizedPercent ?: "")
                            .put("management_stage", it.managementStage)
                            .put("rationale", it.rationale)
                    } ?: JSONObject.NULL)
                    put("recent_trades", JSONArray().apply {
                        context.recentTrades.forEach { trade ->
                            put(
                                JSONObject()
                                    .put("side", trade.side)
                                    .put("lot_size", trade.lotSize)
                                    .put("opened", trade.openedAtLabel)
                                    .put("closed", trade.closedAtLabel)
                                    .put("outcome", trade.outcome)
                                    .put("pnl_usd", trade.pnlUsd)
                                    .put("pnl_percent", trade.pnlPercent)
                                    .put("rationale", trade.rationale)
                            )
                        }
                    })
                }
            })
        }
        return payload.toString(2)
    }

    private fun trim(text: String): String {
        return if (text.length <= maxPromptChars) text else text.take(min(text.length, maxPromptChars))
    }
}
