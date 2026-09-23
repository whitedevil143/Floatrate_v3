package com.floaterate.app

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.math.roundToInt

/**
 * Small, transparent on-device heuristic for a five-minute estimate.
 *
 * It blends recent log-price momentum, a short/long EMA spread, and a linear
 * trend slope. It is deliberately damped by recent volatility and capped so
 * that the UI never presents an exaggerated number as a fact.
 */
object FiveMinuteForecast {
    private const val HORIZON_MINUTES = 5

    fun predict(rawCandles: List<Candle>, livePrice: Double? = null): ForecastResult? {
        val closes = rawCandles
            .map { it.close }
            .filter { it.isFinite() && it > 0.0 }
            .takeLast(60)

        if (closes.size < 8) return null
        val current = livePrice?.takeIf { it.isFinite() && it > 0.0 } ?: closes.last()
        val logPrices = closes.map(::ln)
        val returns = logPrices.zipWithNext { previous, next -> next - previous }
        if (returns.isEmpty()) return null

        val recentStart = max(0, closes.lastIndex - 5)
        val fiveBarMomentum = ln(closes.last() / closes[recentStart])
        val slopePerBar = regressionSlope(logPrices.takeLast(min(24, logPrices.size)))
        val fastEma = ema(closes, 5)
        val slowEma = ema(closes, 20)
        val emaSpread = ln(fastEma / slowEma)
        val volatility = standardDeviation(returns.takeLast(min(24, returns.size)))

        // Each forecast input is expressed as a five-minute log return.
        val trendComponent = slopePerBar * HORIZON_MINUTES
        val rawFiveMinuteReturn = trendComponent * 0.45 + fiveBarMomentum * 0.35 + emaSpread * 0.20
        val volatilityDamp = (1.0 / (1.0 + volatility * 18.0)).coerceIn(0.45, 1.0)
        val dampedReturn = (rawFiveMinuteReturn * volatilityDamp).coerceIn(-0.02, 0.02)
        val estimated = current * exp(dampedReturn)
        val changePercent = (exp(dampedReturn) - 1.0) * 100.0

        val expectedNoise = max(volatility * sqrt(HORIZON_MINUTES.toDouble()), 0.00035)
        val rawSignal = abs(dampedReturn) / expectedNoise
        val signalStrength = (50.0 + rawSignal * 32.0).roundToInt().coerceIn(1, 99)
        val direction = when {
            abs(changePercent) < 0.025 || signalStrength < 53 -> ForecastDirection.MIXED
            changePercent > 0 -> ForecastDirection.UP
            else -> ForecastDirection.DOWN
        }
        val description = when (direction) {
            ForecastDirection.UP -> "Recent momentum and moving-average trend lean upward."
            ForecastDirection.DOWN -> "Recent momentum and moving-average trend lean downward."
            ForecastDirection.MIXED -> "The short-term signals are mixed; movement may stay noisy."
        }

        return ForecastResult(
            currentPrice = current,
            estimatedPrice = estimated,
            changePercent = changePercent,
            direction = direction,
            signalStrength = signalStrength,
            sampleSize = closes.size,
            description = description
        )
    }

    private fun ema(values: List<Double>, period: Int): Double {
        val usablePeriod = min(period, values.size).coerceAtLeast(1)
        val alpha = 2.0 / (usablePeriod + 1.0)
        var value = values.take(usablePeriod).average()
        values.drop(usablePeriod).forEach { next ->
            value = alpha * next + (1.0 - alpha) * value
        }
        return value
    }

    private fun regressionSlope(values: List<Double>): Double {
        if (values.size < 2) return 0.0
        val meanX = (values.size - 1) / 2.0
        val meanY = values.average()
        var numerator = 0.0
        var denominator = 0.0
        values.forEachIndexed { index, value ->
            val x = index - meanX
            numerator += x * (value - meanY)
            denominator += x * x
        }
        return if (denominator == 0.0) 0.0 else numerator / denominator
    }

    private fun standardDeviation(values: List<Double>): Double {
        if (values.size < 2) return 0.0
        val mean = values.average()
        val variance = values.sumOf { (it - mean) * (it - mean) } / (values.size - 1)
        return sqrt(variance)
    }
}
