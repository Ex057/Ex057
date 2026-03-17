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
) : MarketDataSource {
    private val maxStoredTicks = 600

    private val client = OkHttpClient.Builder()
        .pingInterval(15, TimeUnit.SECONDS)
        .build()

    private var webSocket: WebSocket? = null

    override val priceFlow = MutableStateFlow<Double?>(null)
    override val priceHistoryFlow = MutableStateFlow<List<Double>>(emptyList())
    override val candleHistoryFlow = MutableStateFlow<List<MarketCandle>>(emptyList())
    override val candleStackFlow = MutableStateFlow<Map<String, List<MarketCandle>>>(emptyMap())
    override val dataQualityFlow = MutableStateFlow(DataQuality.ESTIMATED)
    override val statusFlow = MutableStateFlow("Disconnected")
    override val symbolFlow = MutableStateFlow<String?>(null)
    override val timeframeFlow = MutableStateFlow("15m")

    override fun connect(symbol: String, timeframe: String) {
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
                requestTimeframeStack(webSocket, symbol, timeframe)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val json = JSONObject(text)
                    if (json.has("error")) {
                        statusFlow.value = "Error: ${json.getJSONObject("error").optString("message")}"
                        return
                    }
                    if (json.has("candles")) {
                        val parsedCandles = parseCandles(json.getJSONArray("candles"))
                        val granularity = json.optJSONObject("echo_req")?.optInt("granularity")
                        val candleTimeframe = granularity?.let(::secondsToTimeframe) ?: timeframeFlow.value
                        candleStackFlow.value = candleStackFlow.value.toMutableMap().apply {
                            put(candleTimeframe, parsedCandles)
                        }
                        if (candleTimeframe == timeframeFlow.value) {
                            candleHistoryFlow.value = parsedCandles
                        }
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

    override fun refreshCandles(symbol: String, timeframe: String) {
        timeframeFlow.value = timeframe
        webSocket?.let {
            requestTimeframeStack(it, symbol, timeframe)
        }
    }

    override fun disconnect() {
        webSocket?.close(1000, "Client closed")
        webSocket = null
        statusFlow.value = "Disconnected"
        priceFlow.value = null
        priceHistoryFlow.value = emptyList()
        candleHistoryFlow.value = emptyList()
        candleStackFlow.value = emptyMap()
        dataQualityFlow.value = DataQuality.ESTIMATED
    }

    private fun requestTimeframeStack(
        webSocket: WebSocket,
        symbol: String,
        timeframe: String
    ) {
        stackTimeframesFor(timeframe).forEach { requestTimeframe ->
            sendCandleHistoryRequest(webSocket, symbol, requestTimeframe)
        }
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
            "30m" -> 1800
            "1h" -> 3600
            "2h" -> 7200
            "4h" -> 14400
            "8h" -> 28800
            "1d" -> 86400
            else -> 900
        }
    }

    private fun secondsToTimeframe(seconds: Int): String {
        return when (seconds) {
            60 -> "1m"
            300 -> "5m"
            900 -> "15m"
            1800 -> "30m"
            3600 -> "1h"
            7200 -> "2h"
            14400 -> "4h"
            28800 -> "8h"
            86400 -> "1d"
            else -> timeframeFlow.value
        }
    }

    private fun stackTimeframesFor(timeframe: String): List<String> {
        return when (timeframe) {
            "1m" -> listOf("15m", "5m", "1m")
            "5m" -> listOf("1h", "15m", "5m", "1m")
            "15m" -> listOf("4h", "1h", "15m", "5m")
            "30m" -> listOf("4h", "1h", "30m", "15m")
            "1h" -> listOf("1d", "4h", "1h", "15m")
            "2h" -> listOf("1d", "4h", "2h", "30m")
            "4h" -> listOf("1d", "8h", "4h", "1h")
            "8h" -> listOf("1d", "8h", "8h", "2h")
            "1d" -> listOf("1d", "8h", "1d", "4h")
            else -> listOf(timeframe)
        }.distinct()
    }
}
