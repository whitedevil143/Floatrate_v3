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
