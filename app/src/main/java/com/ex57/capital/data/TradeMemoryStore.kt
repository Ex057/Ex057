package com.ex57.capital.data

import android.content.Context
import com.ex57.capital.model.ClosedTradeRecord
import com.ex57.capital.model.PendingTradeOrder
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

    fun loadPendingOrders(): List<PendingTradeOrder> {
        return readArray(KEY_PENDING_ORDERS).mapNotNull(::pendingOrderFromJson)
    }

    fun upsertOpenPosition(position: TradePosition): List<TradePosition> {
        val updated = loadOpenPositions()
            .filterNot { it.id == position.id }
            .plus(position)
            .sortedByDescending { it.openedAtEpochMillis }
        saveOpenPositions(updated)
        return updated
    }

    fun updateOpenPosition(position: TradePosition): List<TradePosition> {
        val updated = loadOpenPositions()
            .map { existing -> if (existing.id == position.id) position else existing }
            .sortedByDescending { it.openedAtEpochMillis }
        saveOpenPositions(updated)
        return updated
    }

    fun applyManagedUpdate(position: TradePosition, realizedDeltaUsd: Double): Pair<List<TradePosition>, Double> {
        val updatedPositions = upsertOpenPosition(position)
        val updatedBalance = loadDemoBalance() + realizedDeltaUsd
        saveDemoBalance(updatedBalance)
        return updatedPositions to updatedBalance
    }

    fun savePendingOrder(order: PendingTradeOrder): List<PendingTradeOrder> {
        val updated = loadPendingOrders()
            .filterNot { it.id == order.id }
            .plus(order)
            .sortedByDescending { it.createdAtEpochMillis }
        savePendingOrders(updated)
        return updated
    }

    fun removePendingOrder(orderId: String): List<PendingTradeOrder> {
        val updated = loadPendingOrders().filterNot { it.id == orderId }
        savePendingOrders(updated)
        return updated
    }

    fun partialClosePositionById(
        positionId: String,
        exitPrice: Double,
        closedAtEpochMillis: Long,
        fraction: Double
    ): Triple<List<TradePosition>, List<ClosedTradeRecord>, Double> {
        val openPositions = loadOpenPositions()
        val target = openPositions.firstOrNull { it.id == positionId }
            ?: return Triple(openPositions, loadClosedTrades(), loadDemoBalance())
        val safeFraction = fraction.coerceIn(0.05, 0.95)
        val closingStake = (target.stakeUsd * safeFraction).coerceAtLeast(0.0)
        val closingLots = (target.lotSize * safeFraction).coerceAtLeast(0.0)
        val remainingUsedMargin = (target.usedMarginUsd * (1.0 - safeFraction)).coerceAtLeast(0.0)
        val remainingSpreadCost = (target.estimatedSpreadCostUsd * (1.0 - safeFraction)).coerceAtLeast(0.0)
        if (closingStake <= 1.0 || closingLots <= MIN_LOT_SIZE) {
            val (remaining, history) = closePositionById(positionId, exitPrice, closedAtEpochMillis)
            return Triple(remaining, history, loadDemoBalance())
        }

        val pnlPercent = when (target.side) {
            PositionSide.LONG -> ((exitPrice - target.entryPrice) / target.entryPrice) * 100.0
            PositionSide.SHORT -> ((target.entryPrice - exitPrice) / target.entryPrice) * 100.0
        }
        val realizedUsd = (closingStake * (pnlPercent / 100.0)) - (target.estimatedSpreadCostUsd * safeFraction)
        val realizedPercent = if (closingStake <= 0.0) 0.0 else (realizedUsd / closingStake) * 100.0
        val updatedPosition = target.copy(
            lotSize = (target.lotSize - closingLots).coerceAtLeast(MIN_LOT_SIZE),
            stakeUsd = (target.stakeUsd - closingStake).coerceAtLeast(1.0),
            usedMarginUsd = remainingUsedMargin,
            estimatedSpreadCostUsd = remainingSpreadCost,
            realizedPnlUsd = target.realizedPnlUsd + realizedUsd
        )
        val remaining = updateOpenPosition(updatedPosition)
        val updatedBalance = loadDemoBalance() + realizedUsd
        saveDemoBalance(updatedBalance)
        val partialTrade = ClosedTradeRecord(
            id = "${target.id}-partial-$closedAtEpochMillis",
            symbolCode = target.symbolCode,
            timeframe = target.timeframe,
            side = target.side,
            lotSize = closingLots,
            stakeUsd = closingStake,
            entryPrice = target.entryPrice,
            exitPrice = exitPrice,
            stopLoss = target.stopLoss,
            takeProfit = target.takeProfit,
            openedAtEpochMillis = target.openedAtEpochMillis,
            closedAtEpochMillis = closedAtEpochMillis,
            outcomeLabel = "Partial Close",
            pnlUsd = realizedUsd,
            pnlPercent = realizedPercent,
            rationale = target.rationale
        )
        val updatedHistory = listOf(partialTrade) + loadClosedTrades()
        saveClosedTrades(updatedHistory.take(MAX_CLOSED_TRADES))
        return Triple(remaining, updatedHistory.take(MAX_CLOSED_TRADES), updatedBalance)
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

        return closePositionById(
            positionId = target.id,
            exitPrice = exitPrice,
            closedAtEpochMillis = closedAtEpochMillis
        )
    }

    fun closePositionById(
        positionId: String,
        exitPrice: Double,
        closedAtEpochMillis: Long
    ): Pair<List<TradePosition>, List<ClosedTradeRecord>> {
        val openPositions = loadOpenPositions()
        val target = openPositions.firstOrNull { it.id == positionId }
            ?: return openPositions to loadClosedTrades()

        val remaining = openPositions.filterNot { it.id == target.id }
        val remainingPnlPercent = when (target.side) {
            PositionSide.LONG -> ((exitPrice - target.entryPrice) / target.entryPrice) * 100.0
            PositionSide.SHORT -> ((target.entryPrice - exitPrice) / target.entryPrice) * 100.0
        }
        val remainingPnlUsd = (target.stakeUsd * (remainingPnlPercent / 100.0)) - target.estimatedSpreadCostUsd
        val totalPnlUsd = target.realizedPnlUsd + remainingPnlUsd
        val pnlPercent = if (target.initialStakeUsd <= 0.0) {
            0.0
        } else {
            (totalPnlUsd / target.initialStakeUsd) * 100.0
        }
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
            lotSize = target.initialLotSize,
            stakeUsd = target.initialStakeUsd,
            entryPrice = target.entryPrice,
            exitPrice = exitPrice,
            stopLoss = target.stopLoss,
            takeProfit = target.takeProfit,
            openedAtEpochMillis = target.openedAtEpochMillis,
            closedAtEpochMillis = closedAtEpochMillis,
            outcomeLabel = outcomeLabel,
            pnlUsd = totalPnlUsd,
            pnlPercent = pnlPercent,
            rationale = target.rationale
        )

        val history = listOf(closedTrade) + loadClosedTrades()
        val updatedBalance = loadDemoBalance() + remainingPnlUsd
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

    private fun savePendingOrders(orders: List<PendingTradeOrder>) {
        saveArray(KEY_PENDING_ORDERS, orders.map(::pendingOrderToJson))
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
            .put("lotSize", position.lotSize)
            .put("initialLotSize", position.initialLotSize)
            .put("stakeUsd", position.stakeUsd)
            .put("initialStakeUsd", position.initialStakeUsd)
            .put("usedMarginUsd", position.usedMarginUsd)
            .put("estimatedSpreadCostUsd", position.estimatedSpreadCostUsd)
            .put("entryPrice", position.entryPrice)
            .put("stopLoss", position.stopLoss)
            .put("takeProfit", position.takeProfit)
            .put("openedAtEpochMillis", position.openedAtEpochMillis)
            .put("setupType", position.setupType.name)
            .put("rationale", position.rationale)
            .put("confidence", position.confidence)
            .put("realizedPnlUsd", position.realizedPnlUsd)
            .put("managementStage", position.managementStage)
    }

    private fun positionFromJson(json: JSONObject): TradePosition? {
        return runCatching {
            TradePosition(
                id = json.getString("id"),
                symbolCode = json.getString("symbolCode"),
                derivSymbol = json.optString("derivSymbol"),
                timeframe = json.getString("timeframe"),
                side = PositionSide.valueOf(json.getString("side")),
                lotSize = json.optDoubleOrNull("lotSize") ?: defaultLotSizeFor(json.optDoubleOrNull("stakeUsd") ?: DEFAULT_STAKE_USD),
                initialLotSize = json.optDoubleOrNull("initialLotSize") ?: (json.optDoubleOrNull("lotSize") ?: defaultLotSizeFor(json.optDoubleOrNull("stakeUsd") ?: DEFAULT_STAKE_USD)),
                stakeUsd = json.optDoubleOrNull("stakeUsd") ?: DEFAULT_STAKE_USD,
                initialStakeUsd = json.optDoubleOrNull("initialStakeUsd") ?: (json.optDoubleOrNull("stakeUsd") ?: DEFAULT_STAKE_USD),
                usedMarginUsd = json.optDoubleOrNull("usedMarginUsd") ?: ((json.optDoubleOrNull("stakeUsd") ?: DEFAULT_STAKE_USD) * 0.10),
                estimatedSpreadCostUsd = json.optDoubleOrNull("estimatedSpreadCostUsd") ?: 0.0,
                entryPrice = json.getDouble("entryPrice"),
                stopLoss = json.optDoubleOrNull("stopLoss"),
                takeProfit = json.optDoubleOrNull("takeProfit"),
                openedAtEpochMillis = json.getLong("openedAtEpochMillis"),
                setupType = SetupType.valueOf(json.getString("setupType")),
                rationale = json.optString("rationale"),
                confidence = json.optInt("confidence"),
                realizedPnlUsd = json.optDoubleOrNull("realizedPnlUsd") ?: 0.0,
                managementStage = json.optInt("managementStage", 0)
            )
        }.getOrNull()
    }

    private fun closedTradeToJson(trade: ClosedTradeRecord): JSONObject {
        return JSONObject()
            .put("id", trade.id)
            .put("symbolCode", trade.symbolCode)
            .put("timeframe", trade.timeframe)
            .put("side", trade.side.name)
            .put("lotSize", trade.lotSize)
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
                lotSize = json.optDoubleOrNull("lotSize") ?: defaultLotSizeFor(json.optDoubleOrNull("stakeUsd") ?: DEFAULT_STAKE_USD),
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

    private fun pendingOrderToJson(order: PendingTradeOrder): JSONObject {
        return JSONObject()
            .put("id", order.id)
            .put("symbolCode", order.symbolCode)
            .put("derivSymbol", order.derivSymbol)
            .put("timeframe", order.timeframe)
            .put("side", order.side.name)
            .put("lotSize", order.lotSize)
            .put("stakeUsd", order.stakeUsd)
            .put("usedMarginUsd", order.usedMarginUsd)
            .put("estimatedSpreadCostUsd", order.estimatedSpreadCostUsd)
            .put("targetEntryPrice", order.targetEntryPrice)
            .put("stopLoss", order.stopLoss)
            .put("takeProfit", order.takeProfit)
            .put("createdAtEpochMillis", order.createdAtEpochMillis)
            .put("note", order.note)
    }

    private fun pendingOrderFromJson(json: JSONObject): PendingTradeOrder? {
        return runCatching {
            PendingTradeOrder(
                id = json.getString("id"),
                symbolCode = json.getString("symbolCode"),
                derivSymbol = json.optString("derivSymbol"),
                timeframe = json.getString("timeframe"),
                side = PositionSide.valueOf(json.getString("side")),
                lotSize = json.optDoubleOrNull("lotSize") ?: defaultLotSizeFor(json.optDoubleOrNull("stakeUsd") ?: DEFAULT_STAKE_USD),
                stakeUsd = json.optDoubleOrNull("stakeUsd") ?: DEFAULT_STAKE_USD,
                usedMarginUsd = json.optDoubleOrNull("usedMarginUsd") ?: ((json.optDoubleOrNull("stakeUsd") ?: DEFAULT_STAKE_USD) * 0.10),
                estimatedSpreadCostUsd = json.optDoubleOrNull("estimatedSpreadCostUsd") ?: 0.0,
                targetEntryPrice = json.getDouble("targetEntryPrice"),
                stopLoss = json.optDoubleOrNull("stopLoss"),
                takeProfit = json.optDoubleOrNull("takeProfit"),
                createdAtEpochMillis = json.getLong("createdAtEpochMillis"),
                note = json.optString("note")
            )
        }.getOrNull()
    }

    private fun JSONObject.optDoubleOrNull(key: String): Double? {
        return if (isNull(key) || !has(key)) null else optDouble(key)
    }

    private fun defaultLotSizeFor(stakeUsd: Double): Double {
        return (stakeUsd / STAKE_PER_LOT_USD).coerceAtLeast(MIN_LOT_SIZE)
    }

    companion object {
        private const val PREFS_NAME = "trade_memory_store"
        private const val KEY_OPEN_POSITIONS = "open_positions"
        private const val KEY_CLOSED_TRADES = "closed_trades"
        private const val KEY_PENDING_ORDERS = "pending_orders"
        private const val KEY_DEMO_BALANCE = "demo_balance"
        private const val MAX_CLOSED_TRADES = 50
        private const val DEFAULT_DEMO_BALANCE = 10_000.0
        private const val DEFAULT_STAKE_USD = 1_000.0
        private const val STAKE_PER_LOT_USD = 10_000.0
        private const val MIN_LOT_SIZE = 0.001
    }
}
