package com.ex57.capital.data

import android.content.Context
import com.ex57.capital.model.ClosedTradeRecord
import com.ex57.capital.model.PositionSide
import com.ex57.capital.model.SetupType
import com.ex57.capital.model.TradePosition
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class TradeMemoryStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun loadDemoBalance(): Double {
        return prefs.getFloat(KEY_DEMO_BALANCE, DEFAULT_DEMO_BALANCE.toFloat()).toDouble()
    }

    fun loadOpenPositions(): List<TradePosition> {
        return readArray(KEY_OPEN_POSITIONS).mapNotNull(::positionFromJson)
    }

    fun loadClosedTrades(): List<ClosedTradeRecord> {
        return readArray(KEY_CLOSED_TRADES).mapNotNull(::closedTradeFromJson)
    }

    fun upsertOpenPosition(position: TradePosition): List<TradePosition> {
        val updated = loadOpenPositions()
            .filterNot { it.symbolCode == position.symbolCode && it.timeframe == position.timeframe }
            .plus(position)
            .sortedByDescending { it.openedAtEpochMillis }
        saveOpenPositions(updated)
        return updated
    }

    fun closePosition(
        symbolCode: String,
        timeframe: String,
        exitPrice: Double,
        closedAtEpochMillis: Long
    ): Pair<List<TradePosition>, List<ClosedTradeRecord>> {
        val openPositions = loadOpenPositions()
        val target = openPositions.firstOrNull { it.symbolCode == symbolCode && it.timeframe == timeframe }
            ?: return openPositions to loadClosedTrades()

        val remaining = openPositions.filterNot { it.id == target.id }
        val pnlPercent = when (target.side) {
            PositionSide.LONG -> ((exitPrice - target.entryPrice) / target.entryPrice) * 100.0
            PositionSide.SHORT -> ((target.entryPrice - exitPrice) / target.entryPrice) * 100.0
        }
        val pnlUsd = target.stakeUsd * (pnlPercent / 100.0)
        val outcomeLabel = when {
            target.takeProfit != null && (
                (target.side == PositionSide.LONG && exitPrice >= target.takeProfit) ||
                    (target.side == PositionSide.SHORT && exitPrice <= target.takeProfit)
                ) -> "Target Hit"
            target.stopLoss != null && (
                (target.side == PositionSide.LONG && exitPrice <= target.stopLoss) ||
                    (target.side == PositionSide.SHORT && exitPrice >= target.stopLoss)
                ) -> "Stopped Out"
            pnlPercent >= 0.0 -> "Closed Green"
            else -> "Closed Red"
        }

        val closedTrade = ClosedTradeRecord(
            id = target.id,
            symbolCode = target.symbolCode,
            timeframe = target.timeframe,
            side = target.side,
            stakeUsd = target.stakeUsd,
            entryPrice = target.entryPrice,
            exitPrice = exitPrice,
            stopLoss = target.stopLoss,
            takeProfit = target.takeProfit,
            openedAtEpochMillis = target.openedAtEpochMillis,
            closedAtEpochMillis = closedAtEpochMillis,
            outcomeLabel = outcomeLabel,
            pnlUsd = pnlUsd,
            pnlPercent = pnlPercent,
            rationale = target.rationale
        )

        val history = listOf(closedTrade) + loadClosedTrades()
        val updatedBalance = loadDemoBalance() + pnlUsd
        saveOpenPositions(remaining)
        saveClosedTrades(history.take(MAX_CLOSED_TRADES))
        saveDemoBalance(updatedBalance)
        return remaining to history.take(MAX_CLOSED_TRADES)
    }

    fun newPositionId(): String = UUID.randomUUID().toString()

    private fun saveOpenPositions(positions: List<TradePosition>) {
        saveArray(KEY_OPEN_POSITIONS, positions.map(::positionToJson))
    }

    private fun saveClosedTrades(trades: List<ClosedTradeRecord>) {
        saveArray(KEY_CLOSED_TRADES, trades.map(::closedTradeToJson))
    }

    private fun saveDemoBalance(balance: Double) {
        prefs.edit().putFloat(KEY_DEMO_BALANCE, balance.toFloat()).apply()
    }

    private fun readArray(key: String): List<JSONObject> {
        val raw = prefs.getString(key, null) ?: return emptyList()
        val array = JSONArray(raw)
        return buildList {
            for (index in 0 until array.length()) {
                add(array.getJSONObject(index))
            }
        }
    }

    private fun saveArray(key: String, objects: List<JSONObject>) {
        val array = JSONArray()
        objects.forEach(array::put)
        prefs.edit().putString(key, array.toString()).apply()
    }

    private fun positionToJson(position: TradePosition): JSONObject {
        return JSONObject()
            .put("id", position.id)
            .put("symbolCode", position.symbolCode)
            .put("derivSymbol", position.derivSymbol)
            .put("timeframe", position.timeframe)
            .put("side", position.side.name)
            .put("stakeUsd", position.stakeUsd)
            .put("entryPrice", position.entryPrice)
            .put("stopLoss", position.stopLoss)
            .put("takeProfit", position.takeProfit)
            .put("openedAtEpochMillis", position.openedAtEpochMillis)
            .put("setupType", position.setupType.name)
            .put("rationale", position.rationale)
            .put("confidence", position.confidence)
    }

    private fun positionFromJson(json: JSONObject): TradePosition? {
        return runCatching {
            TradePosition(
                id = json.getString("id"),
                symbolCode = json.getString("symbolCode"),
                derivSymbol = json.optString("derivSymbol"),
                timeframe = json.getString("timeframe"),
                side = PositionSide.valueOf(json.getString("side")),
                stakeUsd = json.optDoubleOrNull("stakeUsd") ?: DEFAULT_STAKE_USD,
                entryPrice = json.getDouble("entryPrice"),
                stopLoss = json.optDoubleOrNull("stopLoss"),
                takeProfit = json.optDoubleOrNull("takeProfit"),
                openedAtEpochMillis = json.getLong("openedAtEpochMillis"),
                setupType = SetupType.valueOf(json.getString("setupType")),
                rationale = json.optString("rationale"),
                confidence = json.optInt("confidence")
            )
        }.getOrNull()
    }

    private fun closedTradeToJson(trade: ClosedTradeRecord): JSONObject {
        return JSONObject()
            .put("id", trade.id)
            .put("symbolCode", trade.symbolCode)
            .put("timeframe", trade.timeframe)
            .put("side", trade.side.name)
            .put("stakeUsd", trade.stakeUsd)
            .put("entryPrice", trade.entryPrice)
            .put("exitPrice", trade.exitPrice)
            .put("stopLoss", trade.stopLoss)
            .put("takeProfit", trade.takeProfit)
            .put("openedAtEpochMillis", trade.openedAtEpochMillis)
            .put("closedAtEpochMillis", trade.closedAtEpochMillis)
            .put("outcomeLabel", trade.outcomeLabel)
            .put("pnlUsd", trade.pnlUsd)
            .put("pnlPercent", trade.pnlPercent)
            .put("rationale", trade.rationale)
    }

    private fun closedTradeFromJson(json: JSONObject): ClosedTradeRecord? {
        return runCatching {
            ClosedTradeRecord(
                id = json.getString("id"),
                symbolCode = json.getString("symbolCode"),
                timeframe = json.getString("timeframe"),
                side = PositionSide.valueOf(json.getString("side")),
                stakeUsd = json.optDoubleOrNull("stakeUsd") ?: DEFAULT_STAKE_USD,
                entryPrice = json.getDouble("entryPrice"),
                exitPrice = json.getDouble("exitPrice"),
                stopLoss = json.optDoubleOrNull("stopLoss"),
                takeProfit = json.optDoubleOrNull("takeProfit"),
                openedAtEpochMillis = json.getLong("openedAtEpochMillis"),
                closedAtEpochMillis = json.getLong("closedAtEpochMillis"),
                outcomeLabel = json.optString("outcomeLabel"),
                pnlUsd = json.optDoubleOrNull("pnlUsd") ?: ((json.optDouble("pnlPercent") / 100.0) * (json.optDoubleOrNull("stakeUsd") ?: DEFAULT_STAKE_USD)),
                pnlPercent = json.optDouble("pnlPercent"),
                rationale = json.optString("rationale")
            )
        }.getOrNull()
    }

    private fun JSONObject.optDoubleOrNull(key: String): Double? {
        return if (isNull(key) || !has(key)) null else optDouble(key)
    }

    companion object {
        private const val PREFS_NAME = "trade_memory_store"
        private const val KEY_OPEN_POSITIONS = "open_positions"
        private const val KEY_CLOSED_TRADES = "closed_trades"
        private const val KEY_DEMO_BALANCE = "demo_balance"
        private const val MAX_CLOSED_TRADES = 50
        private const val DEFAULT_DEMO_BALANCE = 10_000.0
        private const val DEFAULT_STAKE_USD = 1_000.0
    }
}
