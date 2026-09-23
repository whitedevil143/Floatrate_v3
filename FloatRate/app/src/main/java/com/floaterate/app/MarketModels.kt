package com.floaterate.app

data class Candle(
    val openTime: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Double
)

data class Ticker(
    val symbol: String,
    val lastPrice: Double,
    val priceChangePercent: Double,
    val highPrice: Double,
    val lowPrice: Double,
    val volume: Double
)

enum class ChartMode(val label: String) {
    CANDLE("CANDLES"),
    LINE("LINE"),
    POINTER("POINTER")
}

enum class ForecastDirection(val label: String) {
    UP("UP BIAS"),
    DOWN("DOWN BIAS"),
    MIXED("MIXED")
}

data class ForecastResult(
    val currentPrice: Double,
    val estimatedPrice: Double,
    val changePercent: Double,
    val direction: ForecastDirection,
    val signalStrength: Int,
    val sampleSize: Int,
    val description: String
)
