package com.ex57.capital.data

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ex57.capital.model.AnalysisResult
import com.ex57.capital.model.ClosedTradeRecord
import com.ex57.capital.model.DataQuality
import com.ex57.capital.model.MarketCandle
import com.ex57.capital.model.PositionSide
import com.ex57.capital.model.TradeBias
import com.ex57.capital.model.TradePosition
import com.ex57.capital.model.TradingSymbol
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class PriceViewModel(application: Application) : AndroidViewModel(application) {
    private val client: MarketDataSource = DerivWebSocketClient()
    private val tradeMemoryStore = TradeMemoryStore(application)
    private val _openPositions = MutableStateFlow(tradeMemoryStore.loadOpenPositions())
    private val _closedTrades = MutableStateFlow(tradeMemoryStore.loadClosedTrades())

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
        executionPrice: Double?
    ) {
        val side = when (analysisResult.bias) {
            TradeBias.BULLISH -> PositionSide.LONG
            TradeBias.BEARISH -> PositionSide.SHORT
            TradeBias.NEUTRAL -> return
        }
        val entryPrice = executionPrice ?: analysisResult.tradeSetup.entry ?: return
        val position = TradePosition(
            id = tradeMemoryStore.newPositionId(),
            symbolCode = symbol.code,
            derivSymbol = symbol.derivSymbol,
            timeframe = timeframe,
            side = side,
            entryPrice = entryPrice,
            stopLoss = analysisResult.tradeSetup.stopLoss,
            takeProfit = analysisResult.tradeSetup.takeProfit,
            openedAtEpochMillis = System.currentTimeMillis(),
            setupType = analysisResult.setupType,
            rationale = analysisResult.summary,
            confidence = analysisResult.confidence
        )
        _openPositions.value = tradeMemoryStore.upsertOpenPosition(position)
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
    }

    override fun onCleared() {
        super.onCleared()
        client.disconnect()
    }
}
