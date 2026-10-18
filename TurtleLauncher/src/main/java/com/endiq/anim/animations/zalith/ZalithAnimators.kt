package com.endiq.anim.animations.zalith

import android.animation.Animator
import android.animation.ObjectAnimator
import android.animation.TimeInterpolator
import android.view.View
import com.endiq.anim.animations.BaseAnimator
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp

/**
 * Screen-swap curves ported from Zalith Launcher 2 (utils/animation/_Easing.kt and
 * AnimationUtils.kt). Durations are not set here: AnimPlayer applies the launcher's
 * animation speed to the whole set, the same way it does for every other animation.
 */

/** Zalith's JellyBounce: a damped cosine that overshoots by about 60% and settles in roughly three swings. */
internal val ZalithJellyCurve = TimeInterpolator { t ->
    val x = t.toDouble()
    (1.0 - 0.6 * exp(-8.0 * x) * cos(6.0 * PI * x)).toFloat()
}

/** Zalith's Bounce: four decaying bounces that end exactly on the target. */
internal val ZalithBounceCurve = TimeInterpolator { t ->
    fun bounce(x: Float): Float = x * x * 8f
    val input = t * 1.1226f
    when {
        input < 0.3535f -> bounce(input)
        input < 0.7408f -> bounce(input - 0.54719f) + 0.7f
        input < 0.9644f -> bounce(input - 0.8526f) + 0.9f
        else -> bounce(input - 1.0435f) + 0.95f
    }
}

/** Jelly: the screen fades in, grows from 90% with a springy overshoot and rises a little. */
class ZalithJellyInAnimator : BaseAnimator() {
    override fun getAnimators(target: View): Array<Animator> = arrayOf<Animator>(
        fadeIn(target),
        ObjectAnimator.ofFloat(target, "scaleX", 0.9f, 1f).apply { interpolator = ZalithJellyCurve },
        ObjectAnimator.ofFloat(target, "scaleY", 0.9f, 1f).apply { interpolator = ZalithJellyCurve },
        ObjectAnimator.ofFloat(target, "translationY", drift(target), 0f).apply { interpolator = ZalithJellyCurve }
    )
}

/** Bounce: fades in and lands with Zalith's bounce curve, growing from 92%. */
class ZalithBounceInAnimator : BaseAnimator() {
    override fun getAnimators(target: View): Array<Animator> = arrayOf<Animator>(
        fadeIn(target),
        ObjectAnimator.ofFloat(target, "scaleX", 0.92f, 1f).apply { interpolator = ZalithBounceCurve },
        ObjectAnimator.ofFloat(target, "scaleY", 0.92f, 1f).apply { interpolator = ZalithBounceCurve },
        ObjectAnimator.ofFloat(target, "translationY", drift(target), 0f).apply { interpolator = ZalithBounceCurve }
    )
}

/** Slice: the screen slides in from the right by a short distance while it fades in. */
class ZalithSliceInAnimator : BaseAnimator() {
    override fun getAnimators(target: View): Array<Animator> = arrayOf<Animator>(
        fadeIn(target),
        translate(target, "translationX", screenWidth(target) * 0.08f, 0f)
    )
}
