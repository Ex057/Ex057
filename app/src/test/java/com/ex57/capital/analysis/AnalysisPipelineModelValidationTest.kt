package com.ex57.capital.analysis

import com.ex57.capital.model.ConfirmationMode
import com.ex57.capital.model.MarketCandle
import com.ex57.capital.model.TradingSymbol
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalysisPipelineModelValidationTest {

    private val symbol = TradingSymbol(
        code = "R_100",
        label = "Volatility 100",
        category = "Synthetic",
        derivSymbol = "R_100"
    )

    @Test
    fun forecastModelProducesBullishSkewOnUptrend() {
        val candles = generateCandles(
            count = 260,
            start = 100.0,
            drift = 0.35
        )
        val input = buildInput(candles, mode = ConfirmationMode.MODERATE)
        val features = FeatureExtractor.extract(input)
        assertNotNull("Feature extraction should succeed with enough candles", features)
        val model = ForecastModelEngine.analyze(input, features!!)

        assertTrue("Bullish probability should dominate on strong uptrend", model.bullishProbability > model.bearishProbability)
        assertTrue("Model confidence should not be trivial", model.directionConfidence > 0.15)
    }

    @Test
    fun forecastModelProducesBearishSkewOnDowntrend() {
        val candles = generateCandles(
            count = 260,
            start = 100.0,
            drift = -0.35
        )
        val input = buildInput(candles, mode = ConfirmationMode.MODERATE)
        val features = FeatureExtractor.extract(input)
        assertNotNull("Feature extraction should succeed with enough candles", features)
        val model = ForecastModelEngine.analyze(input, features!!)

        assertTrue("Bearish probability should dominate on strong downtrend", model.bearishProbability > model.bullishProbability)
        assertTrue("Model confidence should not be trivial", model.directionConfidence > 0.15)
    }

    @Test
    fun analysisIncludesModelConfirmationsAcrossAllModes() {
        val candles = generateCandles(
            count = 260,
            start = 100.0,
            drift = 0.2
        )
        val recentPrices = candles.map { it.close }

        ConfirmationMode.entries.forEach { mode ->
            val result = AnalysisStub.analyze(
                symbol = symbol,
                timeframe = "15m",
                mode = mode,
                candles = candles,
                candleStack = mapOf("15m" to candles),
                recentPrices = recentPrices
            )
            val model = result.forecastModelMetrics
            assertNotNull("Model metrics must be present for mode ${mode.label}", model)

            val confirmationNames = result.confirmations.map { it.name }.toSet()
            assertTrue("Mode ${mode.label} should include model direction agreement confirmation", "Model Direction Agreement" in confirmationNames)
            assertTrue("Mode ${mode.label} should include model direction confidence confirmation", "Model Direction Confidence" in confirmationNames)
            assertTrue("Mode ${mode.label} should include model dispersion confirmation", "Model Dispersion Check" in confirmationNames)
            assertTrue("Mode ${mode.label} should include model target-first confirmation", "Model Target-First Potential" in confirmationNames)
        }
    }

    private fun buildInput(
        candles: List<MarketCandle>,
        mode: ConfirmationMode
    ): AnalysisInput {
        return AnalysisInput(
            timeframe = "15m",
            timeframePlan = AnalysisSupport.buildTimeframePlan("15m"),
            mode = mode,
            signalFilters = com.ex57.capital.model.SignalFilterSettings(),
            closedTrades = emptyList(),
            candles = candles,
            candleStack = mapOf("15m" to candles),
            recentPrices = candles.map { it.close }
        )
    }

    private fun generateCandles(
        count: Int,
        start: Double,
        drift: Double
    ): List<MarketCandle> {
        val candles = ArrayList<MarketCandle>(count)
        var price = start
        val startEpoch = 1_700_000_000L
        repeat(count) { index ->
            val wave = kotlin.math.sin(index / 7.0) * 0.18
            val close = (price + drift + wave).coerceAtLeast(0.1)
            val open = price
            val high = maxOf(open, close) + 0.08
            val low = minOf(open, close) - 0.08
            candles += MarketCandle(
                epoch = startEpoch + (index * 60L),
                open = open,
                high = high,
                low = low,
                close = close
            )
            price = close
        }
        return candles
    }
}
