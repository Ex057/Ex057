package com.ex57.capital.data

import android.content.Context
import com.ex57.capital.model.DataQuality
import com.ex57.capital.model.FeedPhase
import com.ex57.capital.model.FeedState
import com.ex57.capital.model.MarketCandle
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject

class DerivWebSocketClient(
    context: Context,
    private val appId: String = "1089"
) : MarketDataSource {
    private val maxStoredTicks = 2_400
    private val maxRequestedCandles = 480
    private val staleThresholdMillis = 15_000L
    private val reconnectThresholdMillis = 30_000L
    private val reconnectDelaysMillis = listOf(1_000L, 2_000L, 5_000L, 10_000L, 20_000L, 30_000L)

    private val client = OkHttpClient.Builder()
        .pingInterval(15, TimeUnit.SECONDS)
        .build()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val sessionStore = MarketSessionStore(context.applicationContext)

    private var webSocket: WebSocket? = null
    private var desiredSymbol: String? = null
    private var desiredTimeframe: String = "15m"
    private var reconnectJob: Job? = null
    private var staleMonitorJob: Job? = null
    private var reconnectAttempt = 0
    private var lastTickEpochMillis: Long? = null
    private var lastConnectAttemptEpochMillis: Long? = null
    private var userInitiatedDisconnect = false
    private var suppressCloseCallback = false

    override val priceFlow = MutableStateFlow<Double?>(null)
    override val priceHistoryFlow = MutableStateFlow<List<Double>>(emptyList())
    override val candleHistoryFlow = MutableStateFlow<List<MarketCandle>>(emptyList())
    override val candleStackFlow = MutableStateFlow<Map<String, List<MarketCandle>>>(emptyMap())
    override val dataQualityFlow = MutableStateFlow(DataQuality.ESTIMATED)
    override val feedStateFlow = MutableStateFlow(FeedState())
    override val statusFlow = MutableStateFlow(FeedPhase.DISCONNECTED.label)
    override val symbolFlow = MutableStateFlow<String?>(null)
    override val timeframeFlow = MutableStateFlow("15m")

    override fun connect(symbol: String, timeframe: String) {
        desiredSymbol = symbol
        desiredTimeframe = timeframe
        timeframeFlow.value = timeframe
        symbolFlow.value = symbol
        userInitiatedDisconnect = false
        reconnectAttempt = 0
        reconnectJob?.cancel()
        applyCachedSession(symbol, timeframe)
        openSocket(
            symbol = symbol,
            timeframe = timeframe,
            reconnecting = false
        )
    }

    override fun refreshCandles(symbol: String, timeframe: String) {
        desiredSymbol = symbol
        desiredTimeframe = timeframe
        timeframeFlow.value = timeframe
        symbolFlow.value = symbol
        webSocket?.let {
            requestTimeframeStack(it, symbol, timeframe)
        } ?: connect(symbol, timeframe)
    }

    override fun disconnect() {
        userInitiatedDisconnect = true
        reconnectJob?.cancel()
        staleMonitorJob?.cancel()
        suppressCloseCallback = true
        webSocket?.close(1000, "Client closed")
        webSocket = null
        updateFeedState(FeedPhase.DISCONNECTED, FeedPhase.DISCONNECTED.label)
    }

    fun shutdown() {
        disconnect()
        scope.cancel()
    }

    private fun openSocket(
        symbol: String,
        timeframe: String,
        reconnecting: Boolean
    ) {
        suppressCloseCallback = true
        webSocket?.close(1000, "Switching connection")
        webSocket = null
        lastConnectAttemptEpochMillis = System.currentTimeMillis()
        updateFeedState(
            phase = if (reconnecting) FeedPhase.RECONNECTING else FeedPhase.CONNECTING,
            message = if (reconnecting) {
                "${FeedPhase.RECONNECTING.label} (attempt ${reconnectAttempt + 1})"
            } else {
                FeedPhase.CONNECTING.label
            }
        )

        val request = Request.Builder()
            .url("wss://ws.binaryws.com/websockets/v3?app_id=$appId")
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                reconnectAttempt = 0
                updateFeedState(FeedPhase.CONNECTED, FeedPhase.CONNECTED.label)
                val subscribeMessage = JSONObject(
                    mapOf(
                        "ticks" to symbol,
                        "subscribe" to 1
                    )
                )
                webSocket.send(subscribeMessage.toString())
                requestTimeframeStack(webSocket, symbol, timeframe)
                startStaleMonitor()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val json = JSONObject(text)
                    if (json.has("error")) {
                        val message = json.getJSONObject("error").optString("message")
                        updateFeedState(FeedPhase.ERROR, "Feed error: $message")
                        scheduleReconnect("subscription error")
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
                        persistSession()
                        return
                    }

                    val tick = json.optJSONObject("tick") ?: return
                    val quote = tick.optDouble("quote", Double.NaN)
                    if (!quote.isNaN()) {
                        lastTickEpochMillis = System.currentTimeMillis()
                        priceFlow.value = quote
                        priceHistoryFlow.value = (priceHistoryFlow.value + quote).takeLast(maxStoredTicks)
                        updateFeedState(FeedPhase.LIVE, FeedPhase.LIVE.label)
                        persistSession()
                    }
                } catch (_: Exception) {
                    updateFeedState(FeedPhase.ERROR, "Feed parse error")
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (userInitiatedDisconnect) return
                dataQualityFlow.value = if (candleHistoryFlow.value.isNotEmpty()) {
                    DataQuality.CACHED
                } else {
                    DataQuality.ESTIMATED
                }
                updateFeedState(FeedPhase.ERROR, "Feed error: ${t.message ?: "unknown"}")
                scheduleReconnect("socket failure")
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (suppressCloseCallback) {
                    suppressCloseCallback = false
                    return
                }
                if (userInitiatedDisconnect) return
                updateFeedState(FeedPhase.DISCONNECTED, FeedPhase.DISCONNECTED.label)
                scheduleReconnect("socket closed")
            }
        })
    }

    private fun scheduleReconnect(reason: String) {
        if (userInitiatedDisconnect) return
        val symbol = desiredSymbol ?: return
        if (reconnectJob?.isActive == true) return

        reconnectJob = scope.launch {
            val delayMillis = reconnectDelaysMillis[reconnectAttempt.coerceAtMost(reconnectDelaysMillis.lastIndex)]
            updateFeedState(
                FeedPhase.RECONNECTING,
                "${FeedPhase.RECONNECTING.label} in ${delayMillis / 1000}s (${reason})"
            )
            delay(delayMillis)
            reconnectAttempt += 1
            openSocket(symbol, desiredTimeframe, reconnecting = true)
        }
    }

    private fun startStaleMonitor() {
        staleMonitorJob?.cancel()
        staleMonitorJob = scope.launch {
            while (!userInitiatedDisconnect) {
                delay(5_000L)
                val symbol = desiredSymbol ?: continue
                val now = System.currentTimeMillis()
                val lastTick = lastTickEpochMillis
                val timeSinceConnect = lastConnectAttemptEpochMillis?.let(now::minus) ?: 0L

                when {
                    lastTick == null && timeSinceConnect >= 12_000L -> {
                        updateFeedState(FeedPhase.WAITING_FOR_DATA, FeedPhase.WAITING_FOR_DATA.label)
                    }

                    lastTick != null && now - lastTick >= reconnectThresholdMillis -> {
                        dataQualityFlow.value = if (candleHistoryFlow.value.isNotEmpty()) {
                            DataQuality.CACHED
                        } else {
                            DataQuality.ESTIMATED
                        }
                        updateFeedState(FeedPhase.STALE, "${FeedPhase.STALE.label} - reconnecting")
                        scheduleReconnect("stale feed")
                    }

                    lastTick != null && now - lastTick >= staleThresholdMillis -> {
                        updateFeedState(FeedPhase.STALE, FeedPhase.STALE.label)
                    }

                    lastTick != null && feedStateFlow.value.phase != FeedPhase.LIVE -> {
                        updateFeedState(FeedPhase.LIVE, FeedPhase.LIVE.label)
                    }
                }

                persistSession(symbol)
            }
        }
    }

    private fun applyCachedSession(symbol: String, timeframe: String) {
        val cached = sessionStore.loadSession(symbol, timeframe) ?: return
        if (cached.priceHistory.isNotEmpty()) {
            priceHistoryFlow.value = cached.priceHistory.takeLast(maxStoredTicks)
        }
        if (cached.candleHistory.isNotEmpty()) {
            candleHistoryFlow.value = cached.candleHistory
        }
        if (cached.candleStack.isNotEmpty()) {
            candleStackFlow.value = cached.candleStack
        }
        if (cached.lastPrice != null) {
            priceFlow.value = cached.lastPrice
        }
        lastTickEpochMillis = cached.updatedAtEpochMillis
        dataQualityFlow.value = if (
            cached.candleHistory.isNotEmpty() || cached.candleStack.isNotEmpty()
        ) {
            DataQuality.CACHED
        } else {
            cached.dataQuality
        }
        if (cached.updatedAtEpochMillis != null) {
            updateFeedState(
                FeedPhase.WAITING_FOR_DATA,
                "${FeedPhase.WAITING_FOR_DATA.label} - restored cached state"
            )
        }
    }

    private fun persistSession(symbolOverride: String? = desiredSymbol) {
        val symbol = symbolOverride ?: return
        sessionStore.saveSession(
            symbol = symbol,
            timeframe = desiredTimeframe,
            lastPrice = priceFlow.value,
            priceHistory = priceHistoryFlow.value,
            candleHistory = candleHistoryFlow.value,
            candleStack = candleStackFlow.value,
            dataQuality = dataQualityFlow.value,
            updatedAtEpochMillis = lastTickEpochMillis
        )
    }

    private fun updateFeedState(
        phase: FeedPhase,
        message: String
    ) {
        val state = FeedState(
            phase = phase,
            message = message,
            reconnectAttempt = reconnectAttempt,
            lastTickEpochMillis = lastTickEpochMillis
        )
        feedStateFlow.value = state
        statusFlow.value = message
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
                "count" to maxRequestedCandles,
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
