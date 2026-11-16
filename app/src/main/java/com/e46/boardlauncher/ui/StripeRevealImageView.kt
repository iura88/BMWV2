package com.e46.boardlauncher.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Path
import android.util.AttributeSet
import android.view.animation.DecelerateInterpolator
import androidx.appcompat.widget.AppCompatImageView

class StripeRevealImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatImageView(context, attrs, defStyleAttr) {

    private var revealProgress: Float = 0f
    private val clipPath = Path()
    private var animator: ValueAnimator? = null

    fun startStripeReveal(durationMs: Long = 2100L, onFinished: () -> Unit) {
        animator?.cancel()
        revealProgress = 0f
        invalidate()

        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = durationMs
            interpolator = DecelerateInterpolator(1.15f)
            addUpdateListener { anim ->
                revealProgress = anim.animatedValue as Float
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                private var cancelled = false

                override fun onAnimationCancel(animation: Animator) {
                    cancelled = true
                }

                override fun onAnimationEnd(animation: Animator) {
                    if (!cancelled) {
                        onFinished()
                    }
                }
            })
            start()
        }
    }

    fun cancelReveal() {
        animator?.cancel()
        animator = null
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val slantOffset = h * (30f / 72f)
        val totalSpan = w + slantOffset
        val currentTopX = totalSpan * revealProgress

        clipPath.reset()
        clipPath.moveTo(0f, 0f)
        clipPath.lineTo(currentTopX, 0f)
        clipPath.lineTo(currentTopX - slantOffset, h)
        clipPath.lineTo(0f, h)
        clipPath.close()

        val saveCount = canvas.save()
        canvas.clipPath(clipPath)
        super.onDraw(canvas)
        canvas.restoreToCount(saveCount)
    }
}
