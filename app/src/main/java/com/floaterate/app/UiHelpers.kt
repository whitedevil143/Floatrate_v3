package com.floaterate.app

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import kotlin.math.roundToInt

fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

fun Context.dp(value: Float): Float = value * resources.displayMetrics.density

fun roundedBackground(
    fillColor: Int,
    radius: Float,
    strokeColor: Int? = null,
    strokeWidth: Int = 1
): GradientDrawable {
    return GradientDrawable().apply {
        setColor(fillColor)
        cornerRadius = radius
        if (strokeColor != null) setStroke(strokeWidth, strokeColor)
    }
}

fun TextView.labelStyle(sizeSp: Float, color: Int, bold: Boolean = false) {
    textSize = sizeSp
    setTextColor(color)
    typeface = Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
}

fun TextView.pillStyle(
    fillColor: Int,
    textColor: Int,
    strokeColor: Int? = null,
    horizontalPadding: Int = 12,
    verticalPadding: Int = 7
) {
    setTextColor(textColor)
    textSize = 11f
    typeface = Typeface.create("sans-serif", Typeface.BOLD)
    gravity = android.view.Gravity.CENTER
    background = roundedBackground(fillColor, 24f, strokeColor)
    setPadding(context.dp(horizontalPadding), context.dp(verticalPadding), context.dp(horizontalPadding), context.dp(verticalPadding))
    minHeight = context.dp(32)
}

fun TextView.addPressFeedback() {
    isClickable = true
    isFocusable = true
    stateListAnimator = null
    setOnTouchListener { view, event ->
        when (event.action) {
            android.view.MotionEvent.ACTION_DOWN -> view.alpha = 0.7f
            android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> view.alpha = 1f
        }
        false
    }
}

fun View.setMargins(left: Int = 0, top: Int = 0, right: Int = 0, bottom: Int = 0) {
    val params = layoutParams as? ViewGroup.MarginLayoutParams ?: return
    params.setMargins(context.dp(left), context.dp(top), context.dp(right), context.dp(bottom))
    layoutParams = params
}

fun color(hex: String): Int = Color.parseColor(hex)
