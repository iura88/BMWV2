package com.e46.boardlauncher.ui

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.view.MotionEvent
import android.view.View

object ThemeHelper {

    fun createCarPlayCardBackground(
        accentColor: Int,
        cornerRadiusPx: Float,
        strokeWidthPx: Int
    ): StateListDrawable {
        val normal = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = cornerRadiusPx
            setColor(0xFF15181C.toInt())
            setStroke(strokeWidthPx, 0xFF282D34.toInt())
        }

        val pressed = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = cornerRadiusPx
            setColor(withAlpha(accentColor, 46))
            setStroke(strokeWidthPx * 2, accentColor)
        }

        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), pressed)
            addState(intArrayOf(android.R.attr.state_selected), pressed)
            addState(intArrayOf(android.R.attr.state_focused), pressed)
            addState(intArrayOf(), normal)
        }
    }

    fun createHeroVehicleBackground(
        accentColor: Int,
        cornerRadiusPx: Float,
        strokeWidthPx: Int
    ): StateListDrawable {
        val normal = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(0xFF1A1E24.toInt(), 0xFF121418.toInt())
        ).apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = cornerRadiusPx
            setStroke(strokeWidthPx, 0xFF2D333B.toInt())
        }

        val pressed = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(withAlpha(accentColor, 55), 0xFF14171B.toInt())
        ).apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = cornerRadiusPx
            setStroke(strokeWidthPx * 2, accentColor)
        }

        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), pressed)
            addState(intArrayOf(), normal)
        }
    }

    fun createDockButtonBackground(
        accentColor: Int,
        cornerRadiusPx: Float,
        strokeWidthPx: Int
    ): StateListDrawable {
        val normal = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = cornerRadiusPx
            setColor(0xFF181C21.toInt())
            setStroke(strokeWidthPx, 0xFF2A3038.toInt())
        }

        val active = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = cornerRadiusPx
            setColor(withAlpha(accentColor, 56))
            setStroke(strokeWidthPx * 2, accentColor)
        }

        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), active)
            addState(intArrayOf(android.R.attr.state_selected), active)
            addState(intArrayOf(), normal)
        }
    }

    fun createPillBadgeBackground(accentColor: Int, cornerRadiusPx: Float, strokeWidthPx: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = cornerRadiusPx
            setColor(withAlpha(accentColor, 34))
            setStroke(strokeWidthPx, withAlpha(accentColor, 140))
        }
    }

    fun createSwatchDrawable(color: Int, selected: Boolean, ringColor: Int, strokePx: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = strokePx * 8f
            setColor(color)
            if (selected) {
                setStroke(strokePx * 3, ringColor)
            } else {
                setStroke(strokePx, 0xFF343940.toInt())
            }
        }
    }

    fun attachTactilePressAnimation(view: View, enabledProvider: () -> Boolean) {
        view.setOnTouchListener { v, event ->
            if (!enabledProvider()) return@setOnTouchListener false
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    v.animate()
                        .scaleX(0.965f)
                        .scaleY(0.965f)
                        .setDuration(90L)
                        .start()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .setDuration(130L)
                        .start()
                }
            }
            false
        }
    }

    fun withAlpha(color: Int, alpha: Int): Int {
        return Color.argb(
            alpha.coerceIn(0, 255),
            Color.red(color),
            Color.green(color),
            Color.blue(color)
        )
    }

    fun createIconTintList(accentColor: Int): ColorStateList {
        return ColorStateList(
            arrayOf(
                intArrayOf(android.R.attr.state_selected),
                intArrayOf(android.R.attr.state_pressed),
                intArrayOf()
            ),
            intArrayOf(
                accentColor,
                accentColor,
                0xFFF2F4F7.toInt()
            )
        )
    }
}
