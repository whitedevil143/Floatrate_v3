package com.floaterate.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class ChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#283447")
        strokeWidth = context.dp(1f)
        style = Paint.Style.STROKE
    }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F2B84B")
        strokeWidth = context.dp(2f)
        style = Paint.Style.STROKE
    }
    private val candlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val wickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = context.dp(1f)
        style = Paint.Style.STROKE
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#8B99AD")
        textSize = context.dp(10f)
        typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL)
    }
    private val pointerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F2B84B")
        strokeWidth = context.dp(1f)
        style = Paint.Style.STROKE
        pathEffect = android.graphics.DashPathEffect(floatArrayOf(context.dp(4f), context.dp(4f)), 0f)
    }
    private val pointerFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#192436")
        style = Paint.Style.FILL
    }

    var chartMode: ChartMode = ChartMode.CANDLE
        set(value) {
            field = value
            invalidate()
        }

    var candles: List<Candle> = emptyList()
        set(value) {
            field = value.takeLast(80)
            invalidate()
        }

    var livePrice: Double? = null
        set(value) {
            field = value
            invalidate()
        }

    var onPointerChanged: ((Candle?) -> Unit)? = null

    private var pointerX: Float? = null
    private var selectedIndex: Int = -1
    private val dateFormat = SimpleDateFormat("HH:mm", Locale.US)

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val left = context.dp(42f)
        val right = width - context.dp(10f)
        val top = context.dp(12f)
        val bottom = height - context.dp(24f)

        if (right <= left || bottom <= top) return
        drawGrid(canvas, left, right, top, bottom)

        if (candles.isEmpty()) {
            textPaint.textAlign = Paint.Align.CENTER
            canvas.drawText("Waiting for live market data", (left + right) / 2f, (top + bottom) / 2f, textPaint)
            textPaint.textAlign = Paint.Align.LEFT
            return
        }

        val visible = candles.takeLast(80)
        var minValue = visible.minOf { it.low }
        var maxValue = visible.maxOf { it.high }
        livePrice?.let {
            minValue = min(minValue, it)
            maxValue = max(maxValue, it)
        }
        val padding = (maxValue - minValue).coerceAtLeast(0.0000001) * 0.12
        minValue -= padding
        maxValue += padding
        val valueRange = (maxValue - minValue).coerceAtLeast(0.0000001)
        val plotWidth = right - left
        val slot = plotWidth / visible.size.toFloat()

        fun xFor(index: Int): Float = left + slot * (index + 0.5f)
        fun yFor(value: Double): Float = (bottom - ((value - minValue) / valueRange * (bottom - top))).toFloat()

        when (chartMode) {
            ChartMode.CANDLE -> drawCandles(canvas, visible, slot, ::xFor, ::yFor, top, bottom)
            ChartMode.LINE, ChartMode.POINTER -> drawLine(canvas, visible, ::xFor, ::yFor)
        }

        drawAxisLabels(canvas, minValue, maxValue, left, right, top, bottom)

        if (chartMode == ChartMode.POINTER && pointerX != null) {
            drawPointer(canvas, visible, pointerX!!.coerceIn(left, right), left, right, top, bottom, ::xFor, ::yFor, minValue, maxValue)
        }
    }

    private fun drawGrid(canvas: Canvas, left: Float, right: Float, top: Float, bottom: Float) {
        val rows = 4
        for (row in 0..rows) {
            val y = top + (bottom - top) * row / rows
            canvas.drawLine(left, y, right, y, gridPaint)
        }
        val columns = 4
        for (column in 0..columns) {
            val x = left + (right - left) * column / columns
            canvas.drawLine(x, top, x, bottom, gridPaint)
        }
    }

    private fun drawAxisLabels(canvas: Canvas, minValue: Double, maxValue: Double, left: Float, right: Float, top: Float, bottom: Float) {
        textPaint.textAlign = Paint.Align.RIGHT
        for (row in 0..4) {
            val value = maxValue - (maxValue - minValue) * row / 4.0
            val y = top + (bottom - top) * row / 4f
            canvas.drawText(formatPrice(value), left - context.dp(6f), y + context.dp(3f), textPaint)
        }
        textPaint.textAlign = Paint.Align.LEFT
        if (candles.isNotEmpty()) {
            val first = candles.takeLast(80).first().openTime
            val last = candles.takeLast(80).last().openTime
            canvas.drawText(dateFormat.format(Date(first)), left, height - context.dp(6f), textPaint)
            textPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText(dateFormat.format(Date(last)), right, height - context.dp(6f), textPaint)
        }
        textPaint.textAlign = Paint.Align.LEFT
    }

    private fun drawCandles(
        canvas: Canvas,
        data: List<Candle>,
        slot: Float,
        xFor: (Int) -> Float,
        yFor: (Double) -> Float,
        top: Float,
        bottom: Float
    ) {
        val bodyWidth = max(context.dp(2f), slot * 0.58f)
        data.forEachIndexed { index, candle ->
            val x = xFor(index)
            val isUp = candle.close >= candle.open
            val color = if (isUp) Color.parseColor("#27D3A2") else Color.parseColor("#FF6F83")
            candlePaint.color = color
            wickPaint.color = color
            canvas.drawLine(x, yFor(candle.high), x, yFor(candle.low), wickPaint)
            val bodyTop = min(yFor(candle.open), yFor(candle.close))
            val bodyBottom = max(yFor(candle.open), yFor(candle.close)).coerceAtLeast(bodyTop + context.dp(1f))
            canvas.drawRect(x - bodyWidth / 2f, bodyTop, x + bodyWidth / 2f, bodyBottom, candlePaint)
        }
    }

    private fun drawLine(canvas: Canvas, data: List<Candle>, xFor: (Int) -> Float, yFor: (Double) -> Float) {
        val path = Path()
        data.forEachIndexed { index, candle ->
            val x = xFor(index)
            val y = yFor(candle.close)
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        canvas.drawPath(path, linePaint)
        data.forEachIndexed { index, candle ->
            if (index == data.lastIndex) {
                candlePaint.color = linePaint.color
                canvas.drawCircle(xFor(index), yFor(candle.close), context.dp(3f), candlePaint)
            }
        }
    }

    private fun drawPointer(
        canvas: Canvas,
        data: List<Candle>,
        x: Float,
        left: Float,
        right: Float,
        top: Float,
        bottom: Float,
        xFor: (Int) -> Float,
        yFor: (Double) -> Float,
        minValue: Double,
        maxValue: Double
    ) {
        var nearest = 0
        var distance = Float.MAX_VALUE
        data.forEachIndexed { index, _ ->
            val currentDistance = abs(xFor(index) - x)
            if (currentDistance < distance) {
                distance = currentDistance
                nearest = index
            }
        }
        selectedIndex = nearest
        val candle = data[nearest]
        val pointerPosition = xFor(nearest)
        val valuePosition = yFor(candle.close)
        canvas.drawLine(pointerPosition, top, pointerPosition, bottom, pointerPaint)
        canvas.drawLine(left, valuePosition, right, valuePosition, pointerPaint)
        candlePaint.color = linePaint.color
        canvas.drawCircle(pointerPosition, valuePosition, context.dp(4f), candlePaint)

        val tooltipWidth = context.dp(126f)
        val tooltipHeight = context.dp(44f)
        val tooltipLeft = (pointerPosition - tooltipWidth / 2f).coerceIn(left, right - tooltipWidth)
        val tooltipTop = if (valuePosition - tooltipHeight - context.dp(8f) > top) {
            valuePosition - tooltipHeight - context.dp(8f)
        } else {
            valuePosition + context.dp(8f)
        }
        canvas.drawRoundRect(
            RectF(tooltipLeft, tooltipTop, tooltipLeft + tooltipWidth, tooltipTop + tooltipHeight),
            context.dp(8f), context.dp(8f), pointerFillPaint
        )
        textPaint.color = Color.parseColor("#F4F7FB")
        textPaint.textSize = context.dp(10f)
        canvas.drawText(formatPrice(candle.close), tooltipLeft + context.dp(9f), tooltipTop + context.dp(18f), textPaint)
        textPaint.color = Color.parseColor("#8B99AD")
        canvas.drawText(dateFormat.format(Date(candle.openTime)), tooltipLeft + context.dp(9f), tooltipTop + context.dp(34f), textPaint)
        textPaint.color = Color.parseColor("#8B99AD")
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (chartMode != ChartMode.POINTER || candles.isEmpty()) return true
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                pointerX = event.x
                invalidate()
                val data = candles.takeLast(80)
                val plotLeft = context.dp(42f)
                val plotRight = width - context.dp(10f)
                val slot = (plotRight - plotLeft) / data.size.toFloat()
                val index = ((event.x - plotLeft) / slot).toInt().coerceIn(0, data.lastIndex)
                onPointerChanged?.invoke(data[index])
                return true
            }
            MotionEvent.ACTION_UP -> {
                performClick()
                return true
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun formatPrice(value: Double): String {
        return when {
            value >= 1000 -> String.format(Locale.US, "%,.2f", value)
            value >= 1 -> String.format(Locale.US, "%.2f", value)
            value >= 0.01 -> String.format(Locale.US, "%.4f", value)
            else -> String.format(Locale.US, "%.8f", value)
        }
    }
}
