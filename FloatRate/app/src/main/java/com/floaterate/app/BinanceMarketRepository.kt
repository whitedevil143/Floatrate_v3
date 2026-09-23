package com.floaterate.app

import android.os.Handler
import android.os.Looper
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Public, read-only Binance market stream. No account or trading credentials are used.
 */
class BinanceMarketRepository {
    interface Listener {
        fun onTicker(ticker: Ticker)
        fun onCandles(candles: List<Candle>)
        fun onForecastCandles(candles: List<Candle>)
        fun onStatus(status: String)
        fun onError(message: String)
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()
    private var webSocket: WebSocket? = null
    private var symbol: String = ""
    private var listener: Listener? = null

    fun setListener(listener: Listener?) {
        this.listener = listener
    }

    fun connect(rawSymbol: String, requestedInterval: String = "1m") {
        val nextSymbol = normalizeSymbol(rawSymbol)
        val nextInterval = requestedInterval.takeIf { it in SUPPORTED_INTERVALS } ?: "1m"
        if (nextSymbol.isBlank()) {
            dispatch { listener?.onError("Enter a valid Binance pair, for example BTCUSDT") }
            return
        }

        disconnectSocket()
        symbol = nextSymbol
        dispatch { listener?.onStatus("Connecting to Binance…") }

        // Chart candles follow the selected UI timeframe. Forecast candles are
        // always one-minute candles so the five-minute estimate uses the right scale.
        loadCandles(nextSymbol, nextInterval, forecast = false)
        loadCandles(nextSymbol, "1m", forecast = true)

        val request = Request.Builder()
            .url("wss://stream.binance.com:9443/ws/${nextSymbol.lowercase(Locale.US)}@ticker")
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                dispatch {
                    if (symbol == nextSymbol) listener?.onStatus("Live · Binance")
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val json = JSONObject(text)
                    val ticker = Ticker(
                        symbol = json.optString("s", nextSymbol),
                        lastPrice = json.optString("c").toDouble(),
                        priceChangePercent = json.optString("P").toDouble(),
                        highPrice = json.optString("h").toDouble(),
                        lowPrice = json.optString("l").toDouble(),
                        volume = json.optString("v").toDouble()
                    )
                    dispatch {
                        if (symbol == nextSymbol) listener?.onTicker(ticker)
                    }
                } catch (_: Exception) {
                    // Ignore malformed frames; the next ticker frame will recover normally.
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(code, reason)
                dispatch {
                    if (symbol == nextSymbol) listener?.onStatus("Reconnecting…")
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                dispatch {
                    if (symbol == nextSymbol) {
                        listener?.onStatus("Offline")
                        listener?.onError("Live stream unavailable. Check your connection.")
                    }
                }
            }
        })
    }

    fun disconnect() {
        disconnectSocket()
        listener = null
    }

    private fun disconnectSocket() {
        webSocket?.cancel()
        webSocket = null
    }

    private fun loadCandles(pair: String, candleInterval: String, forecast: Boolean) {
        val limit = if (forecast) 60 else 80
        val request = Request.Builder()
            .url("https://api.binance.com/api/v3/klines?symbol=$pair&interval=$candleInterval&limit=$limit")
            .get()
            .build()

        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) {
                dispatch {
                    if (symbol == pair && !forecast) listener?.onError("Could not load chart history")
                }
            }

            override fun onResponse(call: okhttp3.Call, response: Response) {
                response.use {
                    if (!it.isSuccessful) {
                        dispatch {
                            if (symbol == pair && !forecast) listener?.onError("Binance returned HTTP ${it.code}")
                        }
                        return
                    }
                    try {
                        val parsed = parseCandles(it.body?.string().orEmpty())
                        dispatch {
                            if (symbol == pair) {
                                if (forecast) listener?.onForecastCandles(parsed)
                                else listener?.onCandles(parsed)
                            }
                        }
                    } catch (_: Exception) {
                        dispatch {
                            if (symbol == pair && !forecast) listener?.onError("Chart history could not be read")
                        }
                    }
                }
            }
        })
    }

    private fun parseCandles(body: String): List<Candle> {
        val rows = JSONArray(body)
        return buildList {
            for (index in 0 until rows.length()) {
                val row = rows.getJSONArray(index)
                add(
                    Candle(
                        openTime = row.getLong(0),
                        open = row.getString(1).toDouble(),
                        high = row.getString(2).toDouble(),
                        low = row.getString(3).toDouble(),
                        close = row.getString(4).toDouble(),
                        volume = row.getString(5).toDouble()
                    )
                )
            }
        }
    }

    private fun dispatch(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else mainHandler.post(block)
    }

    companion object {
        val SUPPORTED_INTERVALS = setOf("1m", "5m", "15m", "1h")

        fun normalizeSymbol(raw: String): String = raw
            .trim()
            .uppercase(Locale.US)
            .replace("/", "")
            .replace("-", "")
            .replace(" ", "")
            .filter { it.isLetterOrDigit() }
    }
}
