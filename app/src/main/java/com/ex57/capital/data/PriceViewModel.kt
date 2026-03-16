package com.ex57.capital.data

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ex57.capital.model.DataQuality
import com.ex57.capital.model.MarketCandle
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class PriceViewModel : ViewModel() {
    private val client = DerivWebSocketClient()

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

    fun connect(symbol: String, timeframe: String) {
        client.connect(symbol, timeframe)
    }

    fun refreshCandles(symbol: String, timeframe: String) {
        client.refreshCandles(symbol, timeframe)
    }

    fun disconnect() {
        client.disconnect()
    }

    override fun onCleared() {
        super.onCleared()
        client.disconnect()
    }
}
