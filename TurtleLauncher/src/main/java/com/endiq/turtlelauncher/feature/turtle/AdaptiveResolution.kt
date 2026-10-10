package com.endiq.turtlelauncher.feature.turtle

import android.os.Handler
import android.os.Looper
import com.endiq.turtlelauncher.feature.log.Logging
import com.endiq.turtlelauncher.setting.AllSettings
import com.endiq.turtlelauncher.setting.AllStaticSettings
import org.lwjgl.glfw.CallbackBridge
import kotlin.math.min

/**
 * Adaptive resolution: while a game is running, lower the render scale when FPS is too low, and
 * raise it back toward the resolution the player chose once FPS has recovered.
 *
 * The player's chosen ratio is never overwritten in settings; only the runtime scale changes.
 * When the mode stops, the scale is restored to the player's chosen ratio.
 */
object AdaptiveResolution {
    private const val SAMPLE_INTERVAL_MS = 2000L
    private const val MIN_RATIO = 50
    private const val STEP = 10
    /** Below this FPS for two samples in a row, the scale is lowered. */
    private const val LOW_FPS = 40
    /** At or above this FPS for three samples in a row, the scale is raised back. */
    private const val GOOD_FPS = 55
    private const val COOLDOWN_SAMPLES = 2

    private val handler = Handler(Looper.getMainLooper())
    private var active = false
    private var userRatio = 100
    private var currentRatio = 100
    private var lowCount = 0
    private var highCount = 0
    private var cooldown = 0
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
        lowCount = 0
        highCount = 0
        cooldown = 0
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
        if (cooldown > 0) {
            cooldown--
            return
        }
        val fps = CallbackBridge.getCurrentFps()
        when {
            fps in 0 until LOW_FPS -> { lowCount++; highCount = 0 }
            fps >= GOOD_FPS -> { highCount++; lowCount = 0 }
            else -> { lowCount = 0; highCount = 0 }
        }

        if (lowCount >= 2 && currentRatio > MIN_RATIO) {
            apply((currentRatio - STEP).coerceAtLeast(MIN_RATIO))
            lowCount = 0
            cooldown = COOLDOWN_SAMPLES
        } else if (highCount >= 3 && currentRatio < userRatio) {
            apply(min(currentRatio + STEP, userRatio))
            highCount = 0
            cooldown = COOLDOWN_SAMPLES
        }
    }

    private fun apply(ratio: Int) {
        currentRatio = ratio
        AllStaticSettings.scaleFactor = ratio / 100f
        Logging.i("AdaptiveResolution", "Render scale -> $ratio%")
        onScaleChanged?.run()
    }
}
