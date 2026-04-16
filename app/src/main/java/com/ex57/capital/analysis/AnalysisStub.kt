package com.ex57.capital.analysis

import com.ex57.capital.model.AnalysisResult
import com.ex57.capital.model.ClosedTradeRecord
import com.ex57.capital.model.MarketCandle
import com.ex57.capital.model.SignalFilterSettings
import com.ex57.capital.model.TradePosition
import com.ex57.capital.model.TradingSymbol

object AnalysisStub {
    fun analyze(
        symbol: TradingSymbol,
        timeframe: String,
        mode: com.ex57.capital.model.ConfirmationMode,
        candles: List<MarketCandle>,
        candleStack: Map<String, List<MarketCandle>> = emptyMap(),
        recentPrices: List<Double>,
        signalFilters: SignalFilterSettings = SignalFilterSettings(),
        closedTrades: List<ClosedTradeRecord> = emptyList(),
        openPosition: TradePosition? = null
    ): AnalysisResult {
        val input = AnalysisInput(
            timeframe = timeframe,
            timeframePlan = AnalysisSupport.buildTimeframePlan(timeframe),
            mode = mode,
            signalFilters = signalFilters,
            closedTrades = closedTrades,
            candles = candles,
            candleStack = candleStack,
            recentPrices = recentPrices
        )
        val features = FeatureExtractor.extract(input)
        if (features == null) {
            val currentPrice = candles.lastOrNull()?.close ?: recentPrices.lastOrNull()
            val baseResult = AnalysisSupport.insufficientDataResult()
            return if (currentPrice != null) {
                PositionManager.applyPositionContext(baseResult, openPosition, currentPrice, symbol, timeframe)
            } else {
                baseResult
            }
        }

        val forecastResearch = ForecastResearchEngine.analyze(input, features)
        val forecastModelMetrics = ForecastModelEngine.analyze(input, features)
        val evaluation = StrategyEvaluator.evaluate(
            symbol = symbol,
            input = input,
            features = features,
            forecastResearch = forecastResearch,
            forecastModelMetrics = forecastModelMetrics
        )
        val result = RiskManager.buildResult(symbol, input, features, evaluation)
        return PositionManager.applyPositionContext(result, openPosition, features.last, symbol, timeframe)
    }
}
