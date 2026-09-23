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
        fun onStatus(status: String)
        fun onError(message: String)
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()
    private var webSocket: WebSocket? = null
    private var symbol: String = ""
    private var interval: String = "1m"
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
        interval = nextInterval
        dispatch { listener?.onStatus("Connecting to Binance…") }
        loadCandles(nextSymbol, nextInterval)

        val request = Request.Builder()
            .url("wss://stream.binance.com:9443/ws/${nextSymbol.lowercase(Locale.US)}@ticker")
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                dispatch { listener?.onStatus("Live · Binance") }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val json = JSONObject(text)
                    val ticker = Ticker(
                        symbol = json.optString("s", symbol),
                        lastPrice = json.optString("c").toDouble(),
                        priceChangePercent = json.optString("P").toDouble(),
                        highPrice = json.optString("h").toDouble(),
                        lowPrice = json.optString("l").toDouble(),
                        volume = json.optString("v").toDouble()
                    )
                    dispatch { listener?.onTicker(ticker) }
                } catch (_: Exception) {
                    // Ignore malformed frames; the next ticker frame will recover normally.
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(code, reason)
                dispatch { listener?.onStatus("Reconnecting…") }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                dispatch {
                    listener?.onStatus("Offline")
                    listener?.onError("Live stream unavailable. Check your connection.")
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

    private fun loadCandles(pair: String, candleInterval: String) {
        val request = Request.Builder()
            .url("https://api.binance.com/api/v3/klines?symbol=$pair&interval=$candleInterval&limit=80")
            .get()
            .build()

        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) {
                dispatch { listener?.onError("Could not load chart history") }
            }

            override fun onResponse(call: okhttp3.Call, response: Response) {
                response.use {
                    if (!it.isSuccessful) {
                        dispatch { listener?.onError("Binance returned HTTP ${it.code}") }
                        return
                    }
                    try {
                        val body = it.body?.string().orEmpty()
                        val rows = JSONArray(body)
                        val parsed = buildList {
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
                        dispatch { listener?.onCandles(parsed) }
                    } catch (_: Exception) {
                        dispatch { listener?.onError("Chart history could not be read") }
                    }
                }
            }
        })
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
