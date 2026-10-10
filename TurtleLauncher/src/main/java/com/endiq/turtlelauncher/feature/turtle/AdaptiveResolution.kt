package com.endiq.turtlelauncher.feature.turtle

import android.os.Handler
import android.os.Looper
import com.endiq.turtlelauncher.feature.log.Logging
import com.endiq.turtlelauncher.setting.AllSettings
import com.endiq.turtlelauncher.setting.AllStaticSettings
import org.lwjgl.glfw.CallbackBridge
import kotlin.math.min

/**
 * Adaptive resolution: while a game is running, lower the render scale when FPS stays too low,
 * and raise it back toward the resolution the player chose once FPS has stayed good for a while.
 *
 * Changing the render scale resizes the game window, which itself costs frames and resets the FPS
 * reading. The mode is therefore deliberately slow to react:
 *  - a change is only made after the FPS has stayed out of range for several samples;
 *  - after any change, readings are ignored for a settle period;
 *  - raising back needs a much longer run of good FPS than lowering needs of bad FPS;
 *  - if FPS collapses after a raise, the ratio that failed is remembered as a ceiling for a while,
 *    so the scale does not ping-pong between two values.
 *
 * The player's chosen ratio is never overwritten in settings; only the runtime scale changes.
 * When the mode stops, the scale is restored to the player's chosen ratio.
 */
object AdaptiveResolution {
    private const val SAMPLE_INTERVAL_MS = 2000L
    private const val MIN_RATIO = 50
    private const val STEP = 10

    /** FPS below this counts as struggling. Readings of 0 or less are treated as "unknown" and skipped. */
    private const val LOW_FPS = 40
    /** FPS at or above this counts as comfortable. Between the two values nothing changes. */
    private const val GOOD_FPS = 55

    /** Lower after this many consecutive struggling samples (6 s). */
    private const val LOW_SAMPLES_TO_LOWER = 3
    /** Raise only after this many consecutive comfortable samples (30 s). */
    private const val GOOD_SAMPLES_TO_RAISE = 15
    /** After any change, ignore readings for this many samples (10 s) while the window settles. */
    private const val SETTLE_SAMPLES = 5
    /** A ratio that caused a drop stays off-limits for this many samples (about 5 min). */
    private const val CEILING_HOLD_SAMPLES = 150

    private val handler = Handler(Looper.getMainLooper())
    private var active = false
    private var userRatio = 100
    private var currentRatio = 100
    /** Highest ratio the mode may currently go back to. Equals [userRatio] unless a recent drop set a lower one. */
    private var ceiling = 100
    private var ceilingExpiresAt = 0L
    private var sampleIndex = 0L
    private var lowCount = 0
    private var goodCount = 0
    private var settle = 0
    private var onScaleChanged: Runnable? = null

    private val sampler = object : Runnable {
        override fun run() {
            if (!active) return
            sample()
            handler.postDelayed(this, SAMPLE_INTERVAL_MS)
        }
    }

    /** Starts the mode if the setting is on. [refreshSurface] re-applies the window size on the main thread. */
    @JvmStatic
    fun start(refreshSurface: Runnable) {
        if (!AllSettings.adaptiveResolution.getValue() || active) return
        onScaleChanged = refreshSurface
        userRatio = AllSettings.resolutionRatio.getValue().coerceIn(MIN_RATIO, 100)
        currentRatio = userRatio
        ceiling = userRatio
        ceilingExpiresAt = 0L
        sampleIndex = 0L
        lowCount = 0
        goodCount = 0
        // Let the game finish loading before the first reading counts.
        settle = SETTLE_SAMPLES
        active = true
        Logging.i("AdaptiveResolution", "Started at $userRatio%")
        handler.postDelayed(sampler, SAMPLE_INTERVAL_MS)
    }

    /** Stops the mode and restores the player's chosen resolution. Safe to call when not running. */
    @JvmStatic
    fun stop() {
        if (!active) return
        active = false
        handler.removeCallbacks(sampler)
        if (currentRatio != userRatio) apply(userRatio)
        onScaleChanged = null
        Logging.i("AdaptiveResolution", "Stopped, restored $userRatio%")
    }

    private fun sample() {
        sampleIndex++
        if (ceilingExpiresAt != 0L && sampleIndex >= ceilingExpiresAt) {
            ceiling = userRatio
            ceilingExpiresAt = 0L
        }

        if (settle > 0) {
            settle--
            return
        }

        val fps = CallbackBridge.getCurrentFps()
        if (fps <= 0) return

        when {
            fps < LOW_FPS -> { lowCount++; goodCount = 0 }
            fps >= GOOD_FPS -> { goodCount++; lowCount = 0 }
            else -> { lowCount = 0; goodCount = 0 }
        }

        if (lowCount >= LOW_SAMPLES_TO_LOWER && currentRatio > MIN_RATIO) {
            // The ratio we are leaving is too heavy for now: do not go back to it for a while.
            ceiling = (currentRatio - STEP).coerceAtLeast(MIN_RATIO)
            ceilingExpiresAt = sampleIndex + CEILING_HOLD_SAMPLES
            apply(ceiling)
            lowCount = 0
            settle = SETTLE_SAMPLES
        } else if (goodCount >= GOOD_SAMPLES_TO_RAISE && currentRatio < min(userRatio, ceiling)) {
            apply(min(currentRatio + STEP, min(userRatio, ceiling)))
            goodCount = 0
            settle = SETTLE_SAMPLES
        }
    }

    private fun apply(ratio: Int) {
        if (ratio == currentRatio) return
        currentRatio = ratio
        AllStaticSettings.scaleFactor = ratio / 100f
        Logging.i("AdaptiveResolution", "Render scale -> $ratio%")
        onScaleChanged?.run()
    }
}
