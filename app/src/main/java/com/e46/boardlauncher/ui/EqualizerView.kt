package com.e46.boardlauncher.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.sin

class EqualizerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFF6A1A.toInt()
        style = Paint.Style.FILL
    }

    private val barRect = RectF()
    private var phase = 0f
    private var isPlaying = false
    private var animator: ValueAnimator? = null

    fun setAccentColor(color: Int) {
        paint.color = color
        invalidate()
    }

    fun setPlaying(playing: Boolean, animationsEnabled: Boolean) {
        if (isPlaying == playing && (animator?.isRunning == true) == (playing && animationsEnabled)) {
            return
        }
        isPlaying = playing
        animator?.cancel()
        if (playing && animationsEnabled) {
            animator = ValueAnimator.ofFloat(0f, (Math.PI * 2).toFloat()).apply {
                duration = 900L
                repeatCount = ValueAnimator.INFINITE
                interpolator = LinearInterpolator()
                addUpdateListener {
                    phase = it.animatedValue as Float
                    invalidate()
                }
                start()
            }
        } else {
            phase = 0.8f
            invalidate()
        }
    }

    fun stop() {
        animator?.cancel()
        animator = null
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val bars = 4
        val gap = w * 0.14f
        val barWidth = (w - gap * (bars - 1)) / bars
        val radius = barWidth * 0.4f

        for (i in 0 until bars) {
            val fraction = if (isPlaying) {
                0.28f + 0.72f * ((sin(phase + i * 1.4f) + 1f) * 0.5f)
            } else {
                0.22f
            }
            val barH = h * fraction
            val left = i * (barWidth + gap)
            barRect.set(left, h - barH, left + barWidth, h)
            canvas.drawRoundRect(barRect, radius, radius, paint)
        }
    }
}
