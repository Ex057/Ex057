package com.ex57.capital.data

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ex57.capital.model.AnalysisResult
import com.ex57.capital.model.ClosedTradeRecord
import com.ex57.capital.model.DataQuality
import com.ex57.capital.model.FeedState
import com.ex57.capital.model.MarketCandle
import com.ex57.capital.model.PendingTradeOrder
import com.ex57.capital.model.PositionSide
import com.ex57.capital.model.TradeBias
import com.ex57.capital.model.TradePosition
import com.ex57.capital.model.TradingSymbol
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.stateIn

class PriceViewModel(application: Application) : AndroidViewModel(application) {
    private val client: MarketDataSource = DerivWebSocketClient(application)
    private val tradeMemoryStore = TradeMemoryStore(application)
    private val marketSessionStore = MarketSessionStore(application)
    private val _openPositions = MutableStateFlow(tradeMemoryStore.loadOpenPositions())
    private val _closedTrades = MutableStateFlow(tradeMemoryStore.loadClosedTrades())
    private val _pendingOrders = MutableStateFlow(tradeMemoryStore.loadPendingOrders())
    private val _demoBalance = MutableStateFlow(tradeMemoryStore.loadDemoBalance())
    private val lastObservedPriceBySymbol = mutableMapOf<String, Double>()

    val price: StateFlow<Double?> = client.priceFlow.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        null
    )

    val priceHistory: StateFlow<List<Double>> = client.priceHistoryFlow.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList()
    )

    val candleHistory: StateFlow<List<MarketCandle>> = client.candleHistoryFlow.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList()
    )

    val candleStack: StateFlow<Map<String, List<MarketCandle>>> = client.candleStackFlow.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyMap()
    )

    val dataQuality: StateFlow<DataQuality> = client.dataQualityFlow.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        DataQuality.ESTIMATED
    )

    val feedState: StateFlow<FeedState> = client.feedStateFlow.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        FeedState()
    )

    val status: StateFlow<String> = client.statusFlow.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        "Disconnected"
    )

    val symbol: StateFlow<String?> = client.symbolFlow.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        null
    )

    val openPositions: StateFlow<List<TradePosition>> = _openPositions
    val closedTrades: StateFlow<List<ClosedTradeRecord>> = _closedTrades
    val pendingOrders: StateFlow<List<PendingTradeOrder>> = _pendingOrders
    val demoBalance: StateFlow<Double> = _demoBalance

    init {
        viewModelScope.launch {
            client.priceFlow.collect { latestPrice ->
                val activeSymbol = client.symbolFlow.value ?: return@collect
                if (latestPrice != null) {
                    triggerPendingOrders(activeSymbol, latestPrice)
                    val previousPrice = lastObservedPriceBySymbol[activeSymbol]
                    autoManageOpenPositions(activeSymbol, previousPrice, latestPrice)
                    lastObservedPriceBySymbol[activeSymbol] = latestPrice
                }
            }
        }
    }

    fun connect(symbol: String, timeframe: String) {
        client.connect(symbol, timeframe)
    }

    fun refreshCandles(symbol: String, timeframe: String) {
        client.refreshCandles(symbol, timeframe)
    }

    fun disconnect() {
        client.disconnect()
    }

    fun findOpenPosition(symbolCode: String, timeframe: String): TradePosition? {
        return _openPositions.value.firstOrNull { it.symbolCode == symbolCode && it.timeframe == timeframe }
    }

    fun openTrade(
        symbol: TradingSymbol,
        timeframe: String,
        analysisResult: AnalysisResult,
        executionPrice: Double?,
        lotSize: Double
    ): Boolean {
        val side = when (analysisResult.bias) {
            TradeBias.BULLISH -> PositionSide.LONG
            TradeBias.BEARISH -> PositionSide.SHORT
            TradeBias.NEUTRAL -> return false
        }
        val entryPrice = executionPrice ?: analysisResult.tradeSetup.entry ?: return false
        val normalizedLotSize = lotSize.coerceIn(0.01, 50.0)
        val stakeUsd = (normalizedLotSize * 10_000.0).coerceAtMost(_demoBalance.value * 2.0)
        val position = TradePosition(
            id = tradeMemoryStore.newPositionId(),
            symbolCode = symbol.code,
            derivSymbol = symbol.derivSymbol,
            timeframe = timeframe,
            side = side,
            lotSize = normalizedLotSize,
            initialLotSize = normalizedLotSize,
            stakeUsd = stakeUsd,
            initialStakeUsd = stakeUsd,
            entryPrice = entryPrice,
            stopLoss = analysisResult.tradeSetup.stopLoss,
            takeProfit = analysisResult.tradeSetup.takeProfit,
            openedAtEpochMillis = System.currentTimeMillis(),
            setupType = analysisResult.setupType,
            rationale = analysisResult.summary,
            confidence = analysisResult.confidence
        )
        _openPositions.value = tradeMemoryStore.upsertOpenPosition(position)
        return true
    }

    fun placePendingOrder(
        symbol: TradingSymbol,
        timeframe: String,
        analysisResult: AnalysisResult,
        lotSize: Double
    ): Boolean {
        val side = when (analysisResult.bias) {
            TradeBias.BULLISH -> PositionSide.LONG
            TradeBias.BEARISH -> PositionSide.SHORT
            TradeBias.NEUTRAL -> return false
        }
        val entryPrice = analysisResult.tradeSetup.entry ?: return false
        val normalizedLotSize = lotSize.coerceIn(0.01, 50.0)
        val stakeUsd = (normalizedLotSize * 10_000.0).coerceAtMost(_demoBalance.value * 2.0)
        val order = PendingTradeOrder(
            id = tradeMemoryStore.newPositionId(),
            symbolCode = symbol.code,
            derivSymbol = symbol.derivSymbol,
            timeframe = timeframe,
            side = side,
            lotSize = normalizedLotSize,
            stakeUsd = stakeUsd,
            targetEntryPrice = entryPrice,
            stopLoss = analysisResult.tradeSetup.stopLoss,
            takeProfit = analysisResult.tradeSetup.takeProfit,
            createdAtEpochMillis = System.currentTimeMillis(),
            note = if (analysisResult.tradeSetup.shouldTrade) "Engine planned entry" else "Demo override pending entry"
        )
        _pendingOrders.value = tradeMemoryStore.savePendingOrder(order)
        return true
    }

    fun cancelPendingOrder(orderId: String) {
        _pendingOrders.value = tradeMemoryStore.removePendingOrder(orderId)
    }

    fun partialCloseTradeById(positionId: String, exitPrice: Double?, fraction: Double) {
        val actualExitPrice = exitPrice ?: price.value ?: return
        val (openPositions, closedTrades, updatedBalance) = tradeMemoryStore.partialClosePositionById(
            positionId = positionId,
            exitPrice = actualExitPrice,
            closedAtEpochMillis = System.currentTimeMillis(),
            fraction = fraction
        )
        _openPositions.value = openPositions
        _closedTrades.value = closedTrades
        _demoBalance.value = updatedBalance
    }

    fun closeTrade(symbolCode: String, timeframe: String, exitPrice: Double?) {
        val actualExitPrice = exitPrice ?: price.value ?: return
        val (openPositions, closedTrades) = tradeMemoryStore.closePosition(
            symbolCode = symbolCode,
            timeframe = timeframe,
            exitPrice = actualExitPrice,
            closedAtEpochMillis = System.currentTimeMillis()
        )
        _openPositions.value = openPositions
        _closedTrades.value = closedTrades
        _demoBalance.value = tradeMemoryStore.loadDemoBalance()
    }

    fun closeTradeById(positionId: String, exitPrice: Double?) {
        val actualExitPrice = exitPrice ?: price.value ?: return
        val (openPositions, closedTrades) = tradeMemoryStore.closePositionById(
            positionId = positionId,
            exitPrice = actualExitPrice,
            closedAtEpochMillis = System.currentTimeMillis()
        )
        _openPositions.value = openPositions
        _closedTrades.value = closedTrades
        _demoBalance.value = tradeMemoryStore.loadDemoBalance()
    }

    fun latestKnownPrice(derivSymbol: String, timeframe: String): Double? {
        return marketSessionStore.loadSession(derivSymbol, timeframe)?.lastPrice
    }

    private fun triggerPendingOrders(activeDerivSymbol: String, latestPrice: Double) {
        val previousPrice = lastObservedPriceBySymbol[activeDerivSymbol] ?: return
        val triggered = _pendingOrders.value.filter { order ->
            order.derivSymbol == activeDerivSymbol && when (order.side) {
                PositionSide.LONG -> previousPrice > order.targetEntryPrice && latestPrice <= order.targetEntryPrice
                PositionSide.SHORT -> previousPrice < order.targetEntryPrice && latestPrice >= order.targetEntryPrice
            }
        }
        if (triggered.isEmpty()) return

        triggered.forEach { order ->
            val position = com.ex57.capital.model.TradePosition(
                id = tradeMemoryStore.newPositionId(),
                symbolCode = order.symbolCode,
                derivSymbol = order.derivSymbol,
                timeframe = order.timeframe,
                side = order.side,
                lotSize = order.lotSize,
                initialLotSize = order.lotSize,
                stakeUsd = order.stakeUsd,
                initialStakeUsd = order.stakeUsd,
                entryPrice = order.targetEntryPrice,
                stopLoss = order.stopLoss,
                takeProfit = order.takeProfit,
                openedAtEpochMillis = System.currentTimeMillis(),
                setupType = com.ex57.capital.model.SetupType.BREAKOUT,
                rationale = order.note,
                confidence = 55
            )
            _openPositions.value = tradeMemoryStore.upsertOpenPosition(position)
            _pendingOrders.value = tradeMemoryStore.removePendingOrder(order.id)
        }
    }

    private fun autoManageOpenPositions(activeDerivSymbol: String, previousPrice: Double?, latestPrice: Double) {
        _openPositions.value
            .filter { it.derivSymbol == activeDerivSymbol }
            .forEach { position ->
                maybeAutoManage(position, previousPrice, latestPrice)
            }
    }

    private fun maybeAutoManage(position: TradePosition, previousPrice: Double?, latestPrice: Double) {
        if (previousPrice == null) return

        val stopLoss = position.stopLoss
        if (stopLoss != null) {
            val stopHit = when (position.side) {
                PositionSide.LONG -> previousPrice > stopLoss && latestPrice <= stopLoss
                PositionSide.SHORT -> previousPrice < stopLoss && latestPrice >= stopLoss
            }
            if (stopHit) {
                val (openPositions, closedTrades) = tradeMemoryStore.closePositionById(
                    positionId = position.id,
                    exitPrice = latestPrice,
                    closedAtEpochMillis = System.currentTimeMillis()
                )
                _openPositions.value = openPositions
                _closedTrades.value = closedTrades
                _demoBalance.value = tradeMemoryStore.loadDemoBalance()
                return
            }
        }

        val takeProfit = position.takeProfit
        if (takeProfit != null) {
            val targetHit = when (position.side) {
                PositionSide.LONG -> previousPrice < takeProfit && latestPrice >= takeProfit
                PositionSide.SHORT -> previousPrice > takeProfit && latestPrice <= takeProfit
            }
            if (targetHit) {
                val (openPositions, closedTrades) = tradeMemoryStore.closePositionById(
                    positionId = position.id,
                    exitPrice = latestPrice,
                    closedAtEpochMillis = System.currentTimeMillis()
                )
                _openPositions.value = openPositions
                _closedTrades.value = closedTrades
                _demoBalance.value = tradeMemoryStore.loadDemoBalance()
                return
            }
        }

        val riskDistance = when (position.side) {
            PositionSide.LONG -> position.entryPrice - (position.stopLoss ?: position.entryPrice)
            PositionSide.SHORT -> (position.stopLoss ?: position.entryPrice) - position.entryPrice
        }
        if (riskDistance <= 0.0) return

        val favorableMove = when (position.side) {
            PositionSide.LONG -> latestPrice - position.entryPrice
            PositionSide.SHORT -> position.entryPrice - latestPrice
        }
        val currentR = favorableMove / riskDistance
        val previousFavorableMove = when (position.side) {
            PositionSide.LONG -> previousPrice - position.entryPrice
            PositionSide.SHORT -> position.entryPrice - previousPrice
        }
        val previousR = previousFavorableMove / riskDistance

        val nextStage = when {
            position.managementStage < 1 && previousR < 0.5 && currentR >= 0.5 -> 1
            position.managementStage < 2 && previousR < 1.0 && currentR >= 1.0 -> 2
            position.managementStage < 3 && previousR < 1.5 && currentR >= 1.5 -> 3
            else -> return
        }

        val scaleFraction = when (nextStage) {
            1, 2 -> 0.25
            3 -> 0.50
            else -> 0.0
        }
        val realizedDelta = (position.stakeUsd * scaleFraction) * (positionPnlPercent(position, latestPrice) / 100.0)
        val remainingStake = (position.stakeUsd * (1.0 - scaleFraction)).coerceAtLeast(0.0)
        val remainingLot = (position.lotSize * (1.0 - scaleFraction)).coerceAtLeast(0.0)

        val adjustedStopLoss = when (nextStage) {
            1 -> position.entryPrice
            2 -> when (position.side) {
                PositionSide.LONG -> position.entryPrice + (riskDistance * 0.5)
                PositionSide.SHORT -> position.entryPrice - (riskDistance * 0.5)
            }
            3 -> when (position.side) {
                PositionSide.LONG -> position.entryPrice + riskDistance
                PositionSide.SHORT -> position.entryPrice - riskDistance
            }
            else -> position.stopLoss
        }

        if (remainingStake <= 1.0 || remainingLot <= 0.01) {
            val (openPositions, closedTrades) = tradeMemoryStore.closePositionById(
                positionId = position.id,
                exitPrice = latestPrice,
                closedAtEpochMillis = System.currentTimeMillis()
            )
            _openPositions.value = openPositions
            _closedTrades.value = closedTrades
            _demoBalance.value = tradeMemoryStore.loadDemoBalance()
            return
        }

        val updatedPosition = position.copy(
            lotSize = remainingLot,
            stakeUsd = remainingStake,
            stopLoss = adjustedStopLoss,
            realizedPnlUsd = position.realizedPnlUsd + realizedDelta,
            managementStage = nextStage
        )
        val (openPositions, updatedBalance) = tradeMemoryStore.applyManagedUpdate(updatedPosition, realizedDelta)
        _openPositions.value = openPositions
        _demoBalance.value = updatedBalance
    }

    private fun positionPnlPercent(position: TradePosition, currentPrice: Double): Double {
        return when (position.side) {
            PositionSide.LONG -> ((currentPrice - position.entryPrice) / position.entryPrice) * 100.0
            PositionSide.SHORT -> ((position.entryPrice - currentPrice) / position.entryPrice) * 100.0
        }
    }

    override fun onCleared() {
        super.onCleared()
        if (client is DerivWebSocketClient) {
            client.shutdown()
        } else {
            client.disconnect()
        }
    }
}
