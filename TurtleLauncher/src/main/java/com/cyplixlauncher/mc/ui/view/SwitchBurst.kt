package com.cyplixlauncher.mc.ui.view

import android.animation.ValueAnimator
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import com.cyplixlauncher.mc.R
import com.cyplixlauncher.mc.utils.anim.TurtleTransitions
import androidx.core.content.ContextCompat
import kotlin.math.cos
import kotlin.math.sin

/**
 * A short particle burst around a switch: a ring plus small dots that fly out and fade.
 * It is drawn in the window's overlay, so it costs nothing when idle and only runs for ~420 ms.
 * Skipped when animations are disabled in settings.
 */
object SwitchBurst {
    private const val DURATION_MS = 420L
    private const val PARTICLES = 8

    fun play(anchor: View, on: Boolean) {
        if (!TurtleTransitions.isEnabled()) return
        if (!anchor.isAttachedToWindow) return
        val decor = anchor.rootView as? ViewGroup ?: return

        // Pulse the switch itself so the press feels physical.
        anchor.animate().cancel()
        anchor.animate().scaleX(0.92f).scaleY(0.92f).setDuration(90).withEndAction {
            anchor.animate().scaleX(1f).scaleY(1f).setDuration(170).start()
        }.start()

        val anchorLoc = IntArray(2).also { anchor.getLocationInWindow(it) }
        val decorLoc = IntArray(2).also { decor.getLocationInWindow(it) }
        val cx = (anchorLoc[0] - decorLoc[0] + anchor.width / 2f)
        val cy = (anchorLoc[1] - decorLoc[1] + anchor.height / 2f)
        val density = anchor.resources.displayMetrics.density
        val color = ContextCompat.getColor(
            anchor.context,
            if (on) R.color.switch_track_on else R.color.turtle_divider
        )

        val drawable = BurstDrawable(cx, cy, anchor.width / 2f, density, color)
        drawable.setBounds(0, 0, decor.width, decor.height)
        decor.overlay.add(drawable)

        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = DURATION_MS
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                drawable.progress = it.animatedValue as Float
                drawable.invalidateSelf()
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    decor.overlay.remove(drawable)
                }
            })
            start()
        }
    }

    private class BurstDrawable(
        private val cx: Float,
        private val cy: Float,
        private val baseRadius: Float,
        private val density: Float,
        private val color: Int
    ) : Drawable() {
        var progress: Float = 0f
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = this@BurstDrawable.color }
        private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = this@BurstDrawable.color
            style = Paint.Style.STROKE
            strokeWidth = 1.5f * density
        }
        private val angles = FloatArray(PARTICLES) { i -> i * (2f * Math.PI.toFloat() / PARTICLES) }

        override fun draw(canvas: Canvas) {
            val p = progress
            if (p <= 0f || p >= 1f) return
            val fade = 1f - p
            ring.alpha = (fade * 110).toInt()
            canvas.drawCircle(cx, cy, baseRadius + baseRadius * 0.9f * p, ring)

            val dist = baseRadius + 22f * density * p
            val dot = (2.2f - 1.2f * p) * density
            paint.alpha = (fade * 255).toInt()
            for (a in angles) {
                canvas.drawCircle(cx + cos(a) * dist, cy + sin(a) * dist, dot, paint)
            }
        }

        override fun setAlpha(alpha: Int) {}
        override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {}
        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }
}
