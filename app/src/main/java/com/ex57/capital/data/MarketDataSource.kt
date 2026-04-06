package com.ex57.capital.data

import com.ex57.capital.model.DataQuality
import com.ex57.capital.model.FeedState
import com.ex57.capital.model.MarketCandle
import kotlinx.coroutines.flow.StateFlow

interface MarketDataSource {
    val priceFlow: StateFlow<Double?>
    val priceHistoryFlow: StateFlow<List<Double>>
    val candleHistoryFlow: StateFlow<List<MarketCandle>>
    val candleStackFlow: StateFlow<Map<String, List<MarketCandle>>>
    val dataQualityFlow: StateFlow<DataQuality>
    val feedStateFlow: StateFlow<FeedState>
    val statusFlow: StateFlow<String>
    val symbolFlow: StateFlow<String?>
    val timeframeFlow: StateFlow<String>

    fun connect(symbol: String, timeframe: String)
    fun refreshCandles(symbol: String, timeframe: String)
    fun disconnect()
}
