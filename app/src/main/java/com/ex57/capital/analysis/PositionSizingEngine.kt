package com.ex57.capital.analysis

import com.ex57.capital.model.SymbolTradingSpec
import com.ex57.capital.model.TradingSymbol
import kotlin.math.abs
import kotlin.math.roundToInt

data class PositionSizingQuote(
    val normalizedLotSize: Double,
    val notionalUsd: Double,
    val usedMarginUsd: Double,
    val estimatedSpreadCostUsd: Double,
    val stopDistancePercent: Double?,
    val estimatedRiskUsd: Double?
)

object PositionSizingEngine {
    fun normalizeLot(symbol: TradingSymbol, requestedLot: Double): Double {
        return normalizeLot(symbol.spec, requestedLot)
    }

    fun normalizeLot(spec: SymbolTradingSpec, requestedLot: Double): Double {
        val clamped = requestedLot.coerceIn(spec.minLot, spec.maxLot)
        val stepsFromMin = ((clamped - spec.minLot) / spec.lotStep).roundToInt().coerceAtLeast(0)
        val normalized = spec.minLot + (stepsFromMin * spec.lotStep)
        return normalized.coerceIn(spec.minLot, spec.maxLot)
    }

    fun quote(
        symbol: TradingSymbol,
        entryPrice: Double,
        stopLoss: Double?,
        requestedLot: Double
    ): PositionSizingQuote {
        val normalizedLot = normalizeLot(symbol, requestedLot)
        val spec = symbol.spec
        val notionalUsd = (normalizedLot * spec.contractSize * entryPrice).coerceAtLeast(0.0)
        val usedMarginUsd = if (spec.effectiveLeverage <= 0.0) {
            notionalUsd
        } else {
            notionalUsd / spec.effectiveLeverage
        }
        val estimatedSpreadCostUsd = (normalizedLot * spec.contractSize * spec.typicalSpread).coerceAtLeast(0.0)
        val stopDistancePercent = stopLoss?.let { stop ->
            (abs(entryPrice - stop) / entryPrice.coerceAtLeast(0.00001)) * 100.0
        }
        val estimatedRiskUsd = stopLoss?.let { stop ->
            notionalUsd * (abs(entryPrice - stop) / entryPrice.coerceAtLeast(0.00001))
        }

        return PositionSizingQuote(
            normalizedLotSize = normalizedLot,
            notionalUsd = notionalUsd,
            usedMarginUsd = usedMarginUsd,
            estimatedSpreadCostUsd = estimatedSpreadCostUsd,
            stopDistancePercent = stopDistancePercent,
            estimatedRiskUsd = estimatedRiskUsd
        )
    }
}
