package com.ex57.capital.data

import com.ex57.capital.model.DataQuality
import com.ex57.capital.model.MarketCandle
import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class DerivWebSocketClient(
    private val appId: String = "1089"
) {
    private val maxStoredTicks = 600

    private val client = OkHttpClient.Builder()
        .pingInterval(15, TimeUnit.SECONDS)
        .build()

    private var webSocket: WebSocket? = null

    val priceFlow = MutableStateFlow<Double?>(null)
    val priceHistoryFlow = MutableStateFlow<List<Double>>(emptyList())
    val candleHistoryFlow = MutableStateFlow<List<MarketCandle>>(emptyList())
    val dataQualityFlow = MutableStateFlow(DataQuality.ESTIMATED)
    val statusFlow = MutableStateFlow("Disconnected")
    val symbolFlow = MutableStateFlow<String?>(null)
    val timeframeFlow = MutableStateFlow("15m")

    fun connect(symbol: String, timeframe: String) {
        disconnect()
        statusFlow.value = "Connecting"
        symbolFlow.value = symbol
        timeframeFlow.value = timeframe

        val request = Request.Builder()
            .url("wss://ws.binaryws.com/websockets/v3?app_id=$appId")
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                statusFlow.value = "Connected"
                val subscribeMessage = JSONObject(
                    mapOf(
                        "ticks" to symbol,
                        "subscribe" to 1
                    )
                )
                webSocket.send(subscribeMessage.toString())
                sendCandleHistoryRequest(webSocket, symbol, timeframe)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val json = JSONObject(text)
                    if (json.has("error")) {
                        statusFlow.value = "Error: ${json.getJSONObject("error").optString("message")}"
                        return
                    }
                    if (json.has("candles")) {
                        candleHistoryFlow.value = parseCandles(json.getJSONArray("candles"))
                        dataQualityFlow.value = DataQuality.EXCHANGE_OHLC
                        return
                    }
                    val tick = json.optJSONObject("tick") ?: return
                    val quote = tick.optDouble("quote", Double.NaN)
                    if (!quote.isNaN()) {
                        priceFlow.value = quote
                        priceHistoryFlow.value = (priceHistoryFlow.value + quote).takeLast(maxStoredTicks)
                    }
                } catch (ex: Exception) {
                    statusFlow.value = "Parse error"
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                statusFlow.value = "Failure: ${t.message ?: "unknown"}"
                dataQualityFlow.value = DataQuality.ESTIMATED
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                statusFlow.value = "Closed"
            }
        })
    }

    fun refreshCandles(symbol: String, timeframe: String) {
        timeframeFlow.value = timeframe
        webSocket?.let {
            sendCandleHistoryRequest(it, symbol, timeframe)
        }
    }

    fun disconnect() {
        webSocket?.close(1000, "Client closed")
        webSocket = null
        statusFlow.value = "Disconnected"
        priceFlow.value = null
        priceHistoryFlow.value = emptyList()
        candleHistoryFlow.value = emptyList()
        dataQualityFlow.value = DataQuality.ESTIMATED
    }

    private fun sendCandleHistoryRequest(
        webSocket: WebSocket,
        symbol: String,
        timeframe: String
    ) {
        val message = JSONObject(
            mapOf(
                "ticks_history" to symbol,
                "adjust_start_time" to 1,
                "count" to 120,
                "end" to "latest",
                "granularity" to timeframeToSeconds(timeframe),
                "style" to "candles"
            )
        )
        webSocket.send(message.toString())
    }

    private fun parseCandles(candles: JSONArray): List<MarketCandle> {
        return buildList {
            for (index in 0 until candles.length()) {
                val item = candles.optJSONObject(index) ?: continue
                add(
                    MarketCandle(
                        epoch = item.optLong("epoch"),
                        open = item.optDouble("open"),
                        high = item.optDouble("high"),
                        low = item.optDouble("low"),
                        close = item.optDouble("close")
                    )
                )
            }
        }
    }

    private fun timeframeToSeconds(timeframe: String): Int {
        return when (timeframe) {
            "1m" -> 60
            "5m" -> 300
            "15m" -> 900
            "1h" -> 3600
            "4h" -> 14400
            "1d" -> 86400
            else -> 900
        }
    }
}
