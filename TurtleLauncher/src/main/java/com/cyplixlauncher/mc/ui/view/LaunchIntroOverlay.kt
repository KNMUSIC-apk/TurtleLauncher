package com.cyplixlauncher.mc.ui.view

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.app.Activity
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import com.cyplixlauncher.mc.R
import com.cyplixlauncher.mc.setting.AllSettings

/**
 * Short intro shown on cold start: the launcher logo pops in, then the "Cyplix Launcher"
 * text slides out from behind it and the whole overlay fades away. Runs on the UI thread with
 * property animations only, so it stays cheap; it is skipped when animations are turned off.
 */
object LaunchIntroOverlay {
    private const val LOGO_DP = 96f
    private const val GAP_DP = 14f

    fun play(activity: Activity) {
        if (!AllSettings.animation.getValue()) return
        val content = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
        val density = activity.resources.displayMetrics.density
        val logoPx = (LOGO_DP * density).toInt()
        val gapPx = (GAP_DP * density).toInt()

        val bg = androidx.core.content.ContextCompat.getColor(activity, R.color.bg_deep)
        val textColor = androidx.core.content.ContextCompat.getColor(activity, R.color.turtle_primary_text)

        val text = TextView(activity).apply {
            text = "Cyplix Launcher"
            setTextColor(textColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f)
            typeface = ResourcesCompat.getFont(activity, R.font.oxanium_semibold)
            alpha = 0f
            translationX = -(logoPx + gapPx).toFloat()
        }

        val logo = ImageView(activity).apply {
            setImageResource(R.mipmap.ic_launcher)
            scaleX = 0.6f
            scaleY = 0.6f
            alpha = 0f
            translationZ = 1f // keep the logo above the text while the text slides out from under it
        }

        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(logo, LinearLayout.LayoutParams(logoPx, logoPx))
            addView(text, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginStart = gapPx })
        }

        val overlay = FrameLayout(activity).apply {
            setBackgroundColor(bg)
            isClickable = true
            addView(row, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            ))
        }
        content.addView(overlay, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))

        val logoIn = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(logo, View.ALPHA, 0f, 1f),
                ObjectAnimator.ofFloat(logo, View.SCALE_X, 0.6f, 1f),
                ObjectAnimator.ofFloat(logo, View.SCALE_Y, 0.6f, 1f)
            )
            duration = 480
            interpolator = OvershootInterpolator(1.2f)
        }
        val textOut = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(text, View.TRANSLATION_X, -(logoPx + gapPx).toFloat(), 0f),
                ObjectAnimator.ofFloat(text, View.ALPHA, 0f, 1f)
            )
            duration = 520
            startDelay = 200
            interpolator = DecelerateInterpolator()
        }
        val fadeOut = ObjectAnimator.ofFloat(overlay, View.ALPHA, 1f, 0f).apply {
            duration = 280
            startDelay = 650
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    content.removeView(overlay)
                }
            })
        }

        AnimatorSet().apply {
            playTogether(logoIn, textOut, fadeOut)
            start()
        }
    }
}
