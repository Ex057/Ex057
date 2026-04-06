package com.ex57.capital.data

import android.content.Context
import com.ex57.capital.model.DataQuality
import com.ex57.capital.model.MarketCandle
import org.json.JSONArray
import org.json.JSONObject

class MarketSessionStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun loadSession(symbol: String, timeframe: String): CachedMarketSession? {
        val raw = prefs.getString(sessionKey(symbol, timeframe), null) ?: return null
        val json = runCatching { JSONObject(raw) }.getOrNull() ?: return null
        return CachedMarketSession(
            symbol = json.optString("symbol"),
            timeframe = json.optString("timeframe"),
            lastPrice = json.optDoubleOrNull("lastPrice"),
            priceHistory = json.optJSONArray("priceHistory").toDoubleList(),
            candleHistory = json.optJSONArray("candleHistory").toCandles(),
            candleStack = json.optJSONObject("candleStack").toCandleStack(),
            dataQuality = json.optString("dataQuality")
                .takeIf { it.isNotBlank() }
                ?.let { runCatching { DataQuality.valueOf(it) }.getOrNull() }
                ?: DataQuality.ESTIMATED,
            updatedAtEpochMillis = json.optLong("updatedAtEpochMillis").takeIf { it > 0L }
        )
    }

    fun saveSession(
        symbol: String,
        timeframe: String,
        lastPrice: Double?,
        priceHistory: List<Double>,
        candleHistory: List<MarketCandle>,
        candleStack: Map<String, List<MarketCandle>>,
        dataQuality: DataQuality,
        updatedAtEpochMillis: Long?
    ) {
        val json = JSONObject()
            .put("symbol", symbol)
            .put("timeframe", timeframe)
            .put("lastPrice", lastPrice)
            .put("priceHistory", JSONArray().apply { priceHistory.forEach(::put) })
            .put("candleHistory", candlesToJson(candleHistory))
            .put("candleStack", candleStackToJson(candleStack))
            .put("dataQuality", dataQuality.name)
            .put("updatedAtEpochMillis", updatedAtEpochMillis)

        prefs.edit().putString(sessionKey(symbol, timeframe), json.toString()).apply()
    }

    private fun candlesToJson(candles: List<MarketCandle>): JSONArray {
        return JSONArray().apply {
            candles.forEach { candle ->
                put(
                    JSONObject()
                        .put("epoch", candle.epoch)
                        .put("open", candle.open)
                        .put("high", candle.high)
                        .put("low", candle.low)
                        .put("close", candle.close)
                )
            }
        }
    }

    private fun candleStackToJson(candleStack: Map<String, List<MarketCandle>>): JSONObject {
        return JSONObject().apply {
            candleStack.forEach { (timeframe, candles) ->
                put(timeframe, candlesToJson(candles))
            }
        }
    }

    private fun JSONArray?.toDoubleList(): List<Double> {
        if (this == null) return emptyList()
        return buildList {
            for (index in 0 until length()) {
                add(optDouble(index))
            }
        }
    }

    private fun JSONArray?.toCandles(): List<MarketCandle> {
        if (this == null) return emptyList()
        return buildList {
            for (index in 0 until length()) {
                val item = optJSONObject(index) ?: continue
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

    private fun JSONObject?.toCandleStack(): Map<String, List<MarketCandle>> {
        if (this == null) return emptyMap()
        val keys = keys()
        val stack = linkedMapOf<String, List<MarketCandle>>()
        while (keys.hasNext()) {
            val key = keys.next()
            stack[key] = optJSONArray(key).toCandles()
        }
        return stack
    }

    private fun JSONObject.optDoubleOrNull(key: String): Double? {
        return if (!has(key) || isNull(key)) null else optDouble(key)
    }

    private fun sessionKey(symbol: String, timeframe: String): String {
        return "$KEY_PREFIX:$symbol:$timeframe"
    }

    companion object {
        private const val PREFS_NAME = "market_session_store"
        private const val KEY_PREFIX = "market_session"
    }
}

data class CachedMarketSession(
    val symbol: String,
    val timeframe: String,
    val lastPrice: Double?,
    val priceHistory: List<Double>,
    val candleHistory: List<MarketCandle>,
    val candleStack: Map<String, List<MarketCandle>>,
    val dataQuality: DataQuality,
    val updatedAtEpochMillis: Long?
)
