package com.floaterate.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

class MainActivity : AppCompatActivity() {
    private val repository = BinanceMarketRepository()
    private var selectedSymbol = "BTCUSDT"
    private var selectedInterval = "1m"
    private var currentTicker: Ticker? = null
    private val rangeButtons = mutableMapOf<String, TextView>()

    private lateinit var permissionBanner: View
    private lateinit var permissionText: TextView
    private lateinit var symbolTitle: TextView
    private lateinit var priceText: TextView
    private lateinit var changeText: TextView
    private lateinit var statusText: TextView
    private lateinit var pointerReadout: TextView
    private lateinit var chartView: ChartView
    private lateinit var livePriceRow: View
    private lateinit var forecastPanel: View
    private lateinit var forecastPriceText: TextView
    private lateinit var forecastChangeText: TextView
    private lateinit var forecastDirectionText: TextView
    private lateinit var forecastSignalText: TextView
    private lateinit var forecastDescriptionText: TextView
    private lateinit var liveTab: TextView
    private lateinit var forecastTab: TextView
    private lateinit var overlayButton: TextView
    private lateinit var overlayTitle: TextView
    private lateinit var symbolInput: EditText
    private var forecastCandles: List<Candle> = emptyList()
    private var forecastResult: ForecastResult? = null
    private val modeButtons = mutableMapOf<ChartMode, TextView>()

    private val ink by lazy { ContextCompat.getColor(this, R.color.ink) }
    private val muted by lazy { ContextCompat.getColor(this, R.color.muted) }
    private val surface by lazy { ContextCompat.getColor(this, R.color.surface) }
    private val surfaceElevated by lazy { ContextCompat.getColor(this, R.color.surface_elevated) }
    private val surfaceSoft by lazy { ContextCompat.getColor(this, R.color.surface_soft) }
    private val gold by lazy { ContextCompat.getColor(this, R.color.gold) }
    private val goldSoft by lazy { ContextCompat.getColor(this, R.color.gold_soft) }
    private val positive by lazy { ContextCompat.getColor(this, R.color.positive) }
    private val positiveSoft by lazy { ContextCompat.getColor(this, R.color.positive_soft) }
    private val negative by lazy { ContextCompat.getColor(this, R.color.negative) }
    private val negativeSoft by lazy { ContextCompat.getColor(this, R.color.negative_soft) }
    private val outline by lazy { ContextCompat.getColor(this, R.color.outline) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = ContextCompat.getColor(this, R.color.background)
        window.navigationBarColor = ContextCompat.getColor(this, R.color.background)

        setContentView(buildScreen())
        repository.setListener(repositoryListener)
        refreshPermissionBanner()
        requestNotificationPermissionIfNeeded()
    }

    override fun onStart() {
        super.onStart()
        repository.setListener(repositoryListener)
        repository.connect(selectedSymbol, selectedInterval)
    }

    override fun onStop() {
        repository.disconnect()
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        if (::permissionBanner.isInitialized) refreshPermissionBanner()
    }

    private fun buildScreen(): View {
        val scroll = ScrollView(this).apply {
            setBackgroundColor(ContextCompat.getColor(this@MainActivity, R.color.background))
            isFillViewport = true
            clipToPadding = false
            setPadding(dp(0), dp(10), dp(0), dp(24))
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(10), dp(20), dp(18))
        }
        scroll.addView(content, ViewGroup.LayoutParams(-1, -2))

        content.addView(buildHeader())
        permissionBanner = buildPermissionBanner()
        content.addView(permissionBanner, marginParams(top = 18))
        content.addView(buildChartCard(), marginParams(top = 18))
        content.addView(buildOverlayCard(), marginParams(top = 16))
        content.addView(buildMarketPicker(), marginParams(top = 28))
        content.addView(buildFooter(), marginParams(top = 28))
        return scroll
    }

    private fun buildHeader(): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val mark = TextView(this).apply {
            text = "F"
            labelStyle(19f, ContextCompat.getColor(this@MainActivity, R.color.background), true)
            gravity = Gravity.CENTER
            background = roundedBackground(gold, dp(14f))
        }
        row.addView(mark, LinearLayout.LayoutParams(dp(42), dp(42)))

        val names = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, 0, 0)
        }
        val name = TextView(this).apply {
            text = "FLOATRATE"
            labelStyle(16f, ink, true)
            letterSpacing = 0.12f
        }
        val subtitle = TextView(this).apply {
            text = "MARKET MONITOR"
            labelStyle(10f, muted, true)
            letterSpacing = 0.14f
        }
        names.addView(name)
        names.addView(subtitle, marginParams(top = 3))
        row.addView(names, LinearLayout.LayoutParams(0, -2, 1f))

        val live = TextView(this).apply {
            text = "● LIVE"
            pillStyle(positiveSoft, positive)
        }
        row.addView(live)
        return row
    }

    private fun buildPermissionBanner(): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(12), dp(10), dp(12))
            background = roundedBackground(goldSoft, dp(16f), gold)
        }
        val copy = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val title = TextView(this).apply {
            text = "One permission unlocks floating mode"
            labelStyle(13f, ink, true)
        }
        permissionText = TextView(this).apply {
            text = "Allow display over other apps to pin this chart above Binance."
            labelStyle(11f, muted)
            setLineSpacing(0f, 1.12f)
        }
        copy.addView(title)
        copy.addView(permissionText, marginParams(top = 4))
        card.addView(copy, LinearLayout.LayoutParams(0, -2, 1f))

        val button = TextView(this).apply {
            text = "ALLOW"
            pillStyle(gold, ContextCompat.getColor(this@MainActivity, R.color.background))
            addPressFeedback()
            setOnClickListener { requestOverlayPermission() }
        }
        card.addView(button, marginParams(left = 10))
        return card
    }

    private fun buildChartCard(): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(14))
            background = roundedBackground(surface, dp(22f), outline)
        }

        val topRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val titleStack = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        symbolTitle = TextView(this).apply {
            text = prettySymbol(selectedSymbol)
            labelStyle(16f, ink, true)
        }
        val source = TextView(this).apply {
            text = "SPOT · USDT"
            labelStyle(10f, muted, true)
            letterSpacing = 0.12f
        }
        titleStack.addView(symbolTitle)
        titleStack.addView(source, marginParams(top = 3))
        topRow.addView(titleStack, LinearLayout.LayoutParams(0, -2, 1f))
        statusText = TextView(this).apply {
            text = "CONNECTING…"
            pillStyle(surfaceElevated, muted, outline, horizontalPadding = 10, verticalPadding = 6)
        }
        topRow.addView(statusText)
        card.addView(topRow)

        card.addView(buildQuoteTabs(), marginParams(top = 14))

        val priceRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.BOTTOM
        }
        priceText = TextView(this).apply {
            text = "—"
            labelStyle(31f, ink, true)
            includeFontPadding = false
        }
        priceRow.addView(priceText, LinearLayout.LayoutParams(0, -2, 1f))
        changeText = TextView(this).apply {
            text = "—"
            pillStyle(surfaceElevated, muted, outline, horizontalPadding = 10, verticalPadding = 6)
        }
        priceRow.addView(changeText, marginParams(left = 10, bottom = 2))
        livePriceRow = priceRow
        card.addView(livePriceRow, marginParams(top = 14))

        forecastPanel = buildForecastPanel()
        card.addView(forecastPanel, marginParams(top = 12))
        setQuoteTab(showForecast = false)

        chartView = ChartView(this).apply {
            chartMode = ChartMode.CANDLE
            setBackgroundColor(ContextCompat.getColor(this@MainActivity, R.color.surface_soft))
            onPointerChanged = { candle ->
                pointerReadout.text = candle?.let {
                    "${prettySymbol(selectedSymbol)}  ·  O ${formatPrice(it.open)}   H ${formatPrice(it.high)}   L ${formatPrice(it.low)}   C ${formatPrice(it.close)}"
                } ?: "Tap POINTER mode, then drag across the chart to inspect a candle"
            }
        }
        card.addView(chartView, LinearLayout.LayoutParams(-1, dp(236)).apply {
            topMargin = dp(16)
        })

        pointerReadout = TextView(this).apply {
            text = "Tap POINTER mode, then drag across the chart to inspect a candle"
            labelStyle(10f, muted)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        card.addView(pointerReadout, marginParams(top = 9))

        val modeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(4), dp(4), dp(4), dp(4))
            background = roundedBackground(surfaceElevated, dp(12f))
        }
        ChartMode.values().forEach { mode ->
            val button = TextView(this).apply {
                text = mode.label
                gravity = Gravity.CENTER
                labelStyle(10f, muted, true)
                addPressFeedback()
                setOnClickListener { setChartMode(mode) }
            }
            modeButtons[mode] = button
            modeRow.addView(button, LinearLayout.LayoutParams(0, dp(34), 1f))
        }
        card.addView(modeRow, marginParams(top = 13))
        setChartMode(ChartMode.CANDLE)

        val rangeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val rangeLabel = TextView(this).apply {
            text = "TIME WINDOW"
            labelStyle(10f, muted, true)
            letterSpacing = 0.1f
        }
        rangeRow.addView(rangeLabel, LinearLayout.LayoutParams(0, -2, 1f))
        listOf("1H" to "1m", "4H" to "5m", "1D" to "15m", "1W" to "1h").forEach { (label, interval) ->
            val range = TextView(this).apply {
                text = label
                labelStyle(10f, if (interval == selectedInterval) gold else muted, true)
                gravity = Gravity.CENTER
                setPadding(dp(8), dp(4), dp(8), dp(4))
                background = roundedBackground(
                    if (interval == selectedInterval) goldSoft else ContextCompat.getColor(this@MainActivity, R.color.transparent),
                    dp(8f)
                )
                addPressFeedback()
                setOnClickListener { selectInterval(interval) }
            }
            rangeButtons[interval] = range
            rangeRow.addView(range)
        }
        card.addView(rangeRow, marginParams(top = 14))
        return card
    }

    private fun buildQuoteTabs(): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(4), dp(4), dp(4), dp(4))
            background = roundedBackground(surfaceElevated, dp(12f))
        }
        liveTab = TextView(this).apply {
            text = "LIVE RATE"
            gravity = Gravity.CENTER
            labelStyle(10f, muted, true)
            addPressFeedback()
            setOnClickListener { setQuoteTab(showForecast = false) }
        }
        forecastTab = TextView(this).apply {
            text = "5M ESTIMATE"
            gravity = Gravity.CENTER
            labelStyle(10f, muted, true)
            addPressFeedback()
            setOnClickListener { setQuoteTab(showForecast = true) }
        }
        row.addView(liveTab, LinearLayout.LayoutParams(0, dp(34), 1f))
        row.addView(forecastTab, LinearLayout.LayoutParams(0, dp(34), 1f))
        return row
    }

    private fun buildForecastPanel(): View {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(13), dp(12), dp(13), dp(12))
            background = roundedBackground(surfaceSoft, dp(15f), outline)
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val title = TextView(this).apply {
            text = "ESTIMATED PRICE IN 5 MINUTES"
            labelStyle(10f, muted, true)
            letterSpacing = 0.08f
        }
        header.addView(title, LinearLayout.LayoutParams(0, -2, 1f))
        forecastDirectionText = TextView(this).apply {
            text = "WAITING"
            pillStyle(surfaceElevated, muted, outline, horizontalPadding = 9, verticalPadding = 5)
        }
        header.addView(forecastDirectionText)
        panel.addView(header)

        val estimateRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.BOTTOM
        }
        forecastPriceText = TextView(this).apply {
            text = "—"
            labelStyle(26f, ink, true)
            includeFontPadding = false
        }
        estimateRow.addView(forecastPriceText, LinearLayout.LayoutParams(0, -2, 1f))
        forecastChangeText = TextView(this).apply {
            text = "—"
            pillStyle(surfaceElevated, muted, outline, horizontalPadding = 9, verticalPadding = 5)
        }
        estimateRow.addView(forecastChangeText, marginParams(left = 8, bottom = 1))
        panel.addView(estimateRow, marginParams(top = 10))

        forecastSignalText = TextView(this).apply {
            text = "Signal strength —/100 · Waiting for one-minute candles"
            labelStyle(10f, muted, true)
        }
        panel.addView(forecastSignalText, marginParams(top = 8))
        forecastDescriptionText = TextView(this).apply {
            text = "This is a statistical estimate, not a guaranteed price."
            labelStyle(10f, muted)
            setLineSpacing(0f, 1.08f)
        }
        panel.addView(forecastDescriptionText, marginParams(top = 4))
        return panel
    }

    private fun setQuoteTab(showForecast: Boolean) {
        livePriceRow.visibility = if (showForecast) View.GONE else View.VISIBLE
        forecastPanel.visibility = if (showForecast) View.VISIBLE else View.GONE
        liveTab.labelStyle(10f, if (showForecast) muted else ContextCompat.getColor(this, R.color.background), true)
        forecastTab.labelStyle(10f, if (showForecast) ContextCompat.getColor(this, R.color.background) else muted, true)
        liveTab.background = roundedBackground(
            if (showForecast) ContextCompat.getColor(this, R.color.transparent) else gold,
            dp(9f)
        )
        forecastTab.background = roundedBackground(
            if (showForecast) gold else ContextCompat.getColor(this, R.color.transparent),
            dp(9f)
        )
    }

    private fun buildOverlayCard(): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            background = roundedBackground(surfaceElevated, dp(20f), outline)
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val copy = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        overlayTitle = TextView(this).apply {
            text = "Keep ${prettySymbol(selectedSymbol)} in sight"
            labelStyle(15f, ink, true)
        }
        val note = TextView(this).apply {
            text = "A compact, draggable widget that stays above Binance."
            labelStyle(11f, muted)
        }
        copy.addView(overlayTitle)
        copy.addView(note, marginParams(top = 4))
        row.addView(copy, LinearLayout.LayoutParams(0, -2, 1f))
        val pin = TextView(this).apply {
            text = "↗"
            labelStyle(21f, gold, true)
            gravity = Gravity.CENTER
            background = roundedBackground(goldSoft, dp(13f))
        }
        row.addView(pin, LinearLayout.LayoutParams(dp(42), dp(42)))
        card.addView(row)

        overlayButton = TextView(this).apply {
            text = "FLOAT ${prettySymbol(selectedSymbol)} ABOVE OTHER APPS"
            gravity = Gravity.CENTER
            pillStyle(gold, ContextCompat.getColor(this@MainActivity, R.color.background), horizontalPadding = 10, verticalPadding = 11)
            addPressFeedback()
            setOnClickListener { startFloatingOverlay() }
        }
        card.addView(overlayButton, marginParams(top = 15))

        val stop = TextView(this).apply {
            text = "Stop floating widget"
            gravity = Gravity.CENTER
            labelStyle(11f, muted, true)
            addPressFeedback()
            setOnClickListener {
                stopService(Intent(this@MainActivity, FloatingOverlayService::class.java))
                Toast.makeText(this@MainActivity, "Floating widget stopped", Toast.LENGTH_SHORT).show()
            }
        }
        card.addView(stop, marginParams(top = 12))
        return card
    }

    private fun buildMarketPicker(): View {
        val section = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val title = TextView(this).apply {
            text = "WATCHLIST"
            labelStyle(11f, muted, true)
            letterSpacing = 0.16f
        }
        titleRow.addView(title, LinearLayout.LayoutParams(0, -2, 1f))
        val readOnly = TextView(this).apply {
            text = "READ-ONLY DATA"
            pillStyle(positiveSoft, positive, horizontalPadding = 9, verticalPadding = 5)
        }
        titleRow.addView(readOnly)
        section.addView(titleRow)

        val inputRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        symbolInput = EditText(this).apply {
            hint = "Custom pair, e.g. DOGEUSDT"
            setHintTextColor(muted)
            setTextColor(ink)
            textSize = 13f
            setSingleLine(true)
            imeOptions = EditorInfo.IME_ACTION_DONE
            setPadding(dp(14), 0, dp(14), 0)
            background = roundedBackground(surface, dp(14f), outline)
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    selectSymbolFromInput()
                    true
                } else false
            }
        }
        inputRow.addView(symbolInput, LinearLayout.LayoutParams(0, dp(48), 1f))
        val add = TextView(this).apply {
            text = "ADD"
            gravity = Gravity.CENTER
            pillStyle(surfaceElevated, gold, outline, horizontalPadding = 13, verticalPadding = 9)
            addPressFeedback()
            setOnClickListener { selectSymbolFromInput() }
        }
        inputRow.addView(add, marginParams(left = 8))
        section.addView(inputRow, marginParams(top = 13))

        val quickScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            clipToPadding = false
            setPadding(0, 0, 0, 0)
        }
        val quickRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf("BTCUSDT", "ETHUSDT", "SOLUSDT", "BNBUSDT", "XRPUSDT").forEach { symbol ->
            val chip = TextView(this).apply {
                text = prettySymbol(symbol)
                gravity = Gravity.CENTER
                labelStyle(11f, if (symbol == selectedSymbol) gold else muted, true)
                background = roundedBackground(if (symbol == selectedSymbol) goldSoft else surface, dp(12f), if (symbol == selectedSymbol) gold else outline)
                setPadding(dp(13), dp(9), dp(13), dp(9))
                addPressFeedback()
                setOnClickListener { selectSymbol(symbol) }
            }
            quickRow.addView(chip, marginParams(right = 8))
        }
        quickScroll.addView(quickRow)
        section.addView(quickScroll, marginParams(top = 12))

        val helper = TextView(this).apply {
            text = "Select a pair to update the chart and the next floating widget."
            labelStyle(11f, muted)
        }
        section.addView(helper, marginParams(top = 10))
        return section
    }

    private fun buildFooter(): View {
        val footer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(14), dp(15), dp(14), dp(15))
            background = roundedBackground(surfaceSoft, dp(16f))
        }
        val line1 = TextView(this).apply {
            text = "BINANCE PUBLIC MARKET STREAM"
            labelStyle(10f, muted, true)
            letterSpacing = 0.12f
            gravity = Gravity.CENTER
        }
        val line2 = TextView(this).apply {
            text = "No login · No trading permissions · For market awareness only"
            labelStyle(10f, muted)
            gravity = Gravity.CENTER
        }
        footer.addView(line1)
        footer.addView(line2, marginParams(top = 5))
        return footer
    }

    private val repositoryListener = object : BinanceMarketRepository.Listener {
        override fun onTicker(ticker: Ticker) {
            currentTicker = ticker
            priceText.text = formatPrice(ticker.lastPrice)
            chartView.livePrice = ticker.lastPrice
            updateForecast()
            statusText.text = "● LIVE"
            statusText.pillStyle(positiveSoft, positive, horizontalPadding = 10, verticalPadding = 6)
            val change = ticker.priceChangePercent
            changeText.text = String.format(Locale.US, "%+.2f%%", change)
            changeText.pillStyle(if (change >= 0) positiveSoft else negativeSoft, if (change >= 0) positive else negative, horizontalPadding = 10, verticalPadding = 6)
            val existing = chartView.candles
            if (existing.isNotEmpty()) {
                val last = existing.last()
                val updated = last.copy(
                    high = max(last.high, ticker.lastPrice),
                    low = min(last.low, ticker.lastPrice),
                    close = ticker.lastPrice
                )
                chartView.candles = existing.dropLast(1) + updated
            }
        }

        override fun onCandles(candles: List<Candle>) {
            chartView.candles = candles
            currentTicker?.let { chartView.livePrice = it.lastPrice }
        }

        override fun onForecastCandles(candles: List<Candle>) {
            forecastCandles = candles
            updateForecast()
        }

        override fun onStatus(status: String) {
            statusText.text = status.uppercase(Locale.US)
            statusText.pillStyle(surfaceElevated, muted, outline, horizontalPadding = 10, verticalPadding = 6)
        }

        override fun onError(message: String) {
            Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateForecast() {
        if (!::forecastPriceText.isInitialized) return
        val result = FiveMinuteForecast.predict(forecastCandles, currentTicker?.lastPrice)
        forecastResult = result
        if (result == null) {
            forecastPriceText.text = "—"
            forecastChangeText.text = "—"
            forecastChangeText.pillStyle(surfaceElevated, muted, outline, horizontalPadding = 9, verticalPadding = 5)
            forecastDirectionText.text = "WAITING"
            forecastDirectionText.pillStyle(surfaceElevated, muted, outline, horizontalPadding = 9, verticalPadding = 5)
            forecastSignalText.text = "Signal strength —/100 · Waiting for one-minute candles"
            forecastDescriptionText.text = "This is a statistical estimate, not a guaranteed price."
            return
        }

        val isUp = result.direction == ForecastDirection.UP
        val isDown = result.direction == ForecastDirection.DOWN
        val directionColor = when {
            isUp -> positive
            isDown -> negative
            else -> muted
        }
        val directionBackground = when {
            isUp -> positiveSoft
            isDown -> negativeSoft
            else -> surfaceElevated
        }
        forecastPriceText.text = formatPrice(result.estimatedPrice)
        forecastChangeText.text = String.format(Locale.US, "%+.3f%%", result.changePercent)
        forecastChangeText.pillStyle(directionBackground, directionColor, horizontalPadding = 9, verticalPadding = 5)
        forecastDirectionText.text = result.direction.label
        forecastDirectionText.pillStyle(directionBackground, directionColor, horizontalPadding = 9, verticalPadding = 5)
        forecastSignalText.text = "Signal strength ${result.signalStrength}/100 · ${result.sampleSize} one-minute candles"
        forecastDescriptionText.text = "${result.description} This is an estimate, not a guarantee."
    }

    private fun setChartMode(mode: ChartMode) {
        chartView.chartMode = mode
        modeButtons.forEach { (buttonMode, button) ->
            val selected = buttonMode == mode
            button.labelStyle(10f, if (selected) ContextCompat.getColor(this, R.color.background) else muted, true)
            button.background = roundedBackground(if (selected) gold else ContextCompat.getColor(this, R.color.transparent), dp(9f))
        }
        pointerReadout.text = if (mode == ChartMode.POINTER) {
            "Drag across the chart to inspect OHLC at any minute"
        } else {
            "Tap POINTER mode, then drag across the chart to inspect a candle"
        }
    }

    private fun selectSymbolFromInput() {
        val normalized = BinanceMarketRepository.normalizeSymbol(symbolInput.text.toString())
        if (normalized.length < 5) {
            symbolInput.error = "Try a pair such as BTCUSDT"
            return
        }
        selectSymbol(normalized)
    }

    private fun selectSymbol(symbol: String) {
        val normalized = BinanceMarketRepository.normalizeSymbol(symbol)
        selectedSymbol = normalized
        currentTicker = null
        forecastCandles = emptyList()
        forecastResult = null
        symbolTitle.text = prettySymbol(normalized)
        overlayTitle.text = "Keep ${prettySymbol(normalized)} in sight"
        chartView.candles = emptyList()
        chartView.livePrice = null
        priceText.text = "—"
        changeText.text = "—"
        changeText.pillStyle(surfaceElevated, muted, outline, horizontalPadding = 10, verticalPadding = 6)
        statusText.text = "CONNECTING…"
        statusText.pillStyle(surfaceElevated, muted, outline, horizontalPadding = 10, verticalPadding = 6)
        pointerReadout.text = "Loading ${prettySymbol(normalized)} history…"
        updateForecast()
        overlayButton.text = "FLOAT ${prettySymbol(normalized)} ABOVE OTHER APPS"
        symbolInput.text?.clear()
        repository.connect(normalized, selectedInterval)
    }

    private fun selectInterval(interval: String) {
        selectedInterval = interval
        rangeButtons.forEach { (buttonInterval, button) ->
            val selected = buttonInterval == interval
            button.labelStyle(10f, if (selected) gold else muted, true)
            button.background = roundedBackground(
                if (selected) goldSoft else ContextCompat.getColor(this, R.color.transparent),
                dp(8f)
            )
        }
        statusText.text = "CONNECTING…"
        statusText.pillStyle(surfaceElevated, muted, outline, horizontalPadding = 10, verticalPadding = 6)
        repository.connect(selectedSymbol, selectedInterval)
    }

    private fun startFloatingOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            requestOverlayPermission()
            return
        }
        val intent = Intent(this, FloatingOverlayService::class.java).apply {
            putExtra(FloatingOverlayService.EXTRA_SYMBOL, selectedSymbol)
            putExtra(FloatingOverlayService.EXTRA_MODE, chartView.chartMode.name)
            putExtra(FloatingOverlayService.EXTRA_INTERVAL, selectedInterval)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ContextCompat.startForegroundService(this, intent)
        } else {
            startService(intent)
        }
        Toast.makeText(this, "${prettySymbol(selectedSymbol)} is now floating", Toast.LENGTH_SHORT).show()
    }

    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            } catch (_: Exception) {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
            }
        }
    }

    private fun refreshPermissionBanner() {
        val allowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)
        permissionBanner.visibility = if (allowed) View.GONE else View.VISIBLE
        if (!allowed) {
            permissionText.text = "Allow display over other apps to pin this chart above Binance."
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
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

    private fun marginParams(left: Int = 0, top: Int = 0, right: Int = 0, bottom: Int = 0): ViewGroup.MarginLayoutParams {
        return ViewGroup.MarginLayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            setMargins(dp(left), dp(top), dp(right), dp(bottom))
        }
    }
}
