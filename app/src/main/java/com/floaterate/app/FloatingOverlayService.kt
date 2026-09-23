package com.floaterate.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

class FloatingOverlayService : Service() {
    companion object {
        const val EXTRA_SYMBOL = "extra_symbol"
        const val EXTRA_MODE = "extra_mode"
        const val EXTRA_INTERVAL = "extra_interval"
        private const val CHANNEL_ID = "floatrate_market_overlay"
        private const val NOTIFICATION_ID = 731
    }

    private lateinit var windowManager: WindowManager
    private lateinit var windowParams: WindowManager.LayoutParams
    private var overlayPanel: OverlayPanel? = null
    private val repository = BinanceMarketRepository()
    private var activeSymbol = "BTCUSDT"
    private var collapsed = false

    private val repositoryListener = object : BinanceMarketRepository.Listener {
        override fun onTicker(ticker: Ticker) {
            overlayPanel?.renderTicker(ticker)
        }

        override fun onCandles(candles: List<Candle>) {
            overlayPanel?.renderCandles(candles)
        }

        override fun onStatus(status: String) {
            overlayPanel?.renderStatus(status)
        }

        override fun onError(message: String) {
            overlayPanel?.renderStatus("OFFLINE")
        }
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        repository.setListener(repositoryListener)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val requestedSymbol = intent?.getStringExtra(EXTRA_SYMBOL).orEmpty()
        val requestedMode = intent?.getStringExtra(EXTRA_MODE).orEmpty()
        val requestedInterval = intent?.getStringExtra(EXTRA_INTERVAL).orEmpty()
        activeSymbol = BinanceMarketRepository.normalizeSymbol(requestedSymbol.ifBlank { activeSymbol })
        if (activeSymbol.isBlank()) activeSymbol = "BTCUSDT"
        val mode = requestedMode.toChartMode()
        val interval = requestedInterval.takeIf { it in BinanceMarketRepository.SUPPORTED_INTERVALS } ?: "1m"
        getSystemService(NotificationManager::class.java)?.notify(NOTIFICATION_ID, buildNotification())
        showOrUpdateOverlay(mode)
        repository.connect(activeSymbol, interval)
        return START_STICKY
    }

    private fun showOrUpdateOverlay(mode: ChartMode) {
        val panel = overlayPanel
        if (panel == null) {
            windowParams = WindowManager.LayoutParams(
                dp(340),
                dp(246),
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = dp(18)
                y = dp(120)
            }
            overlayPanel = OverlayPanel(
                context = this,
                onClose = { stopSelf() },
                onCollapseChanged = { isCollapsed -> resizeOverlay(isCollapsed) }
            ).also { created ->
                created.setDragListener { deltaX, deltaY ->
                    windowParams.x += deltaX
                    windowParams.y += deltaY
                    try {
                        windowManager.updateViewLayout(created, windowParams)
                    } catch (_: Exception) {
                        // The view may be in the process of being removed.
                    }
                }
                created.setSymbol(activeSymbol)
                created.setChartMode(mode)
                windowManager.addView(created, windowParams)
            }
        } else {
            panel.setSymbol(activeSymbol)
            panel.setChartMode(mode)
        }
    }

    private fun resizeOverlay(isCollapsed: Boolean) {
        collapsed = isCollapsed
        windowParams.width = if (collapsed) dp(188) else dp(340)
        windowParams.height = if (collapsed) dp(88) else dp(246)
        overlayPanel?.let {
            try {
                windowManager.updateViewLayout(it, windowParams)
            } catch (_: Exception) {
                // Ignore updates during service shutdown.
            }
        }
    }

    override fun onDestroy() {
        repository.disconnect()
        overlayPanel?.let {
            try {
                windowManager.removeView(it)
            } catch (_: Exception) {
                // Already removed.
            }
        }
        overlayPanel = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentTitle("FloatRate is live")
            .setContentText("${prettySymbol(activeSymbol)} is floating above your apps")
            .setContentIntent(openApp)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Floating market widget",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Keeps the selected public market stream available while you trade."
                }
            )
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun String?.toChartMode(): ChartMode {
        return try {
            ChartMode.valueOf(this.orEmpty())
        } catch (_: Exception) {
            ChartMode.CANDLE
        }
    }

    private fun prettySymbol(symbol: String): String {
        val quotes = listOf("USDT", "USDC", "BUSD", "BTC", "ETH")
        val quote = quotes.firstOrNull { symbol.endsWith(it) && symbol.length > it.length }
        return if (quote == null) symbol else symbol.removeSuffix(quote) + "/" + quote
    }
}

private class OverlayPanel(
    context: Context,
    private val onClose: () -> Unit,
    private val onCollapseChanged: (Boolean) -> Unit
) : FrameLayout(context) {
    private val ink = ContextCompat.getColor(context, R.color.ink)
    private val muted = ContextCompat.getColor(context, R.color.muted)
    private val surface = ContextCompat.getColor(context, R.color.surface)
    private val surfaceElevated = ContextCompat.getColor(context, R.color.surface_elevated)
    private val surfaceSoft = ContextCompat.getColor(context, R.color.surface_soft)
    private val gold = ContextCompat.getColor(context, R.color.gold)
    private val goldSoft = ContextCompat.getColor(context, R.color.gold_soft)
    private val positive = ContextCompat.getColor(context, R.color.positive)
    private val positiveSoft = ContextCompat.getColor(context, R.color.positive_soft)
    private val negative = ContextCompat.getColor(context, R.color.negative)
    private val negativeSoft = ContextCompat.getColor(context, R.color.negative_soft)
    private val outline = ContextCompat.getColor(context, R.color.outline)

    private val symbolText: TextView
    private val priceText: TextView
    private val changeText: TextView
    private val statusText: TextView
    private val collapseButton: TextView
    private val chartView: ChartView
    private val chartContainer: View
    private val bottomRow: View
    private var collapsed = false
    private var dragListener: ((Int, Int) -> Unit)? = null
    private var downRawX = 0f
    private var downRawY = 0f
    private var downHandled = false

    init {
        setPadding(context.dp(12), context.dp(10), context.dp(12), context.dp(10))
        background = roundedBackground(surface, context.dp(18f), outline)
        elevation = context.dp(8f)

        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        addView(content, LayoutParams(-1, -1))

        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val dragArea = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val handle = TextView(context).apply {
            text = "⠿"
            labelStyle(17f, muted, true)
            gravity = Gravity.CENTER
        }
        dragArea.addView(handle, LinearLayout.LayoutParams(context.dp(24), context.dp(30)))
        val titleStack = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        symbolText = TextView(context).apply { labelStyle(14f, ink, true) }
        val source = TextView(context).apply {
            text = "BINANCE SPOT"
            labelStyle(8f, muted, true)
            letterSpacing = 0.12f
        }
        titleStack.addView(symbolText)
        titleStack.addView(source, overlayMargin(context, top = 2))
        dragArea.addView(titleStack)
        header.addView(dragArea, LinearLayout.LayoutParams(0, -2, 1f))

        collapseButton = TextView(context).apply {
            text = "⌃"
            labelStyle(17f, muted, true)
            gravity = Gravity.CENTER
            background = roundedBackground(surfaceElevated, context.dp(9f))
            isClickable = true
            isFocusable = true
            setOnClickListener {
                collapsed = !collapsed
                setCollapsedView(collapsed)
                onCollapseChanged(collapsed)
            }
        }
        header.addView(collapseButton, LinearLayout.LayoutParams(context.dp(32), context.dp(30)).apply {
            leftMargin = context.dp(5)
        })
        val close = TextView(context).apply {
            text = "×"
            labelStyle(19f, muted, false)
            gravity = Gravity.CENTER
            background = roundedBackground(surfaceElevated, context.dp(9f))
            isClickable = true
            isFocusable = true
            setOnClickListener { onClose() }
        }
        header.addView(close, LinearLayout.LayoutParams(context.dp(32), context.dp(30)).apply {
            leftMargin = context.dp(5)
        })
        content.addView(header)

        installDragGesture(dragArea)

        val priceRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.BOTTOM
        }
        priceText = TextView(context).apply {
            text = "—"
            labelStyle(23f, ink, true)
            includeFontPadding = false
        }
        priceRow.addView(priceText, LinearLayout.LayoutParams(0, -2, 1f))
        changeText = TextView(context).apply {
            text = "—"
            pillStyle(surfaceElevated, muted, outline, horizontalPadding = 8, verticalPadding = 5)
        }
        priceRow.addView(changeText, overlayMargin(context, left = 7, bottom = 1))
        content.addView(priceRow, overlayMargin(context, top = 9))

        chartView = ChartView(context).apply {
            chartMode = ChartMode.CANDLE
            setBackgroundColor(surfaceSoft)
        }
        chartContainer = chartView
        content.addView(chartView, LinearLayout.LayoutParams(-1, context.dp(128)).apply {
            topMargin = context.dp(9)
        })

        bottomRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        statusText = TextView(context).apply {
            text = "CONNECTING…"
            pillStyle(surfaceElevated, muted, outline, horizontalPadding = 8, verticalPadding = 5)
        }
        bottomRow.addView(statusText, LinearLayout.LayoutParams(0, -2, 1f))
        val hint = TextView(context).apply {
            text = "DRAG ⠿  ·  TAP ⌃"
            labelStyle(8f, muted, true)
            letterSpacing = 0.08f
        }
        bottomRow.addView(hint)
        content.addView(bottomRow, overlayMargin(context, top = 8))
    }

    fun setDragListener(listener: (Int, Int) -> Unit) {
        dragListener = listener
    }

    fun setSymbol(symbol: String) {
        symbolText.text = prettySymbol(symbol)
    }

    fun setChartMode(mode: ChartMode) {
        chartView.chartMode = mode
    }

    fun renderTicker(ticker: Ticker) {
        priceText.text = formatPrice(ticker.lastPrice)
        val change = ticker.priceChangePercent
        changeText.text = String.format(Locale.US, "%+.2f%%", change)
        changeText.pillStyle(
            if (change >= 0) positiveSoft else negativeSoft,
            if (change >= 0) positive else negative,
            horizontalPadding = 8,
            verticalPadding = 5
        )
        chartView.livePrice = ticker.lastPrice
        val current = chartView.candles
        if (current.isNotEmpty()) {
            val last = current.last()
            chartView.candles = current.dropLast(1) + last.copy(
                high = max(last.high, ticker.lastPrice),
                low = min(last.low, ticker.lastPrice),
                close = ticker.lastPrice
            )
        }
    }

    fun renderCandles(candles: List<Candle>) {
        chartView.candles = candles
    }

    fun renderStatus(status: String) {
        val isLive = status.contains("live", true)
        statusText.text = if (isLive) "● LIVE" else status.uppercase(Locale.US)
        statusText.pillStyle(
            if (isLive) positiveSoft else surfaceElevated,
            if (isLive) positive else muted,
            if (isLive) null else outline,
            horizontalPadding = 8,
            verticalPadding = 5
        )
    }

    private fun setCollapsedView(value: Boolean) {
        chartContainer.visibility = if (value) View.GONE else View.VISIBLE
        bottomRow.visibility = if (value) View.GONE else View.VISIBLE
        collapseButton.text = if (value) "⌄" else "⌃"
    }

    private fun installDragGesture(view: View) {
        view.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    downHandled = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - downRawX).toInt()
                    val dy = (event.rawY - downRawY).toInt()
                    if (dx != 0 || dy != 0) {
                        dragListener?.invoke(dx, dy)
                        downRawX = event.rawX
                        downRawY = event.rawY
                        downHandled = true
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    performClick()
                    true
                }
                else -> true
            }
        }
    }

    private fun prettySymbol(symbol: String): String {
        val quotes = listOf("USDT", "USDC", "BUSD", "BTC", "ETH")
        val quote = quotes.firstOrNull { symbol.endsWith(it) && symbol.length > it.length }
        return if (quote == null) symbol else symbol.removeSuffix(quote) + "/" + quote
    }

    private fun formatPrice(value: Double): String {
        return when {
            value >= 1000 -> String.format(Locale.US, "%,.2f", value)
            value >= 1 -> String.format(Locale.US, "%.2f", value)
            value >= 0.01 -> String.format(Locale.US, "%.4f", value)
            else -> String.format(Locale.US, "%.8f", value)
        }
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}

private fun overlayMargin(
    context: Context,
    left: Int = 0,
    top: Int = 0,
    right: Int = 0,
    bottom: Int = 0
): LinearLayout.LayoutParams {
    return LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
        setMargins(context.dp(left), context.dp(top), context.dp(right), context.dp(bottom))
    }
}
