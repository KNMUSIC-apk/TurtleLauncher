package com.endiq.turtlelauncher.feature.turtle

import android.os.Handler
import android.os.Looper
import com.endiq.turtlelauncher.feature.log.Logging
import org.lwjgl.glfw.CallbackBridge

/**
 * Watches the game's FPS while it runs. Logs the 20-second average every 30 seconds, and calls
 * [start]'s callback once when the average stays low, so the player can try a lighter preset.
 * Runs on the main thread; the native FPS read is cheap.
 */
object FpsMonitor {
    private const val SAMPLE_MS = 2000L
    private const val WINDOW = 10            // 10 samples x 2 s = 20 s
    private const val LOG_EVERY = 15         // every 30 s
    private const val LOW_AVG = 15
    private const val REARM_AVG = 30

    private val handler = Handler(Looper.getMainLooper())
    private val samples = IntArray(WINDOW)
    private var index = 0
    private var filled = 0
    private var ticks = 0L
    private var warned = false
    private var active = false
    private var onLowFps: Runnable? = null

    private val sampler = object : Runnable {
        override fun run() {
            if (!active) return
            sample()
            handler.postDelayed(this, SAMPLE_MS)
        }
    }

    @JvmStatic
    fun start(onLowFps: Runnable) {
        if (active) return
        this.onLowFps = onLowFps
        index = 0
        filled = 0
        ticks = 0
        warned = false
        active = true
        handler.postDelayed(sampler, SAMPLE_MS)
    }

    @JvmStatic
    fun stop() {
        active = false
        handler.removeCallbacks(sampler)
        onLowFps = null
    }

    private fun sample() {
        val fps = CallbackBridge.getCurrentFps()
        if (fps <= 0) return
        samples[index] = fps
        index = (index + 1) % WINDOW
        if (filled < WINDOW) filled++
        ticks++
        if (filled < WINDOW) return

        val avg = samples.average()
        if (ticks % LOG_EVERY == 0L) Logging.i("FpsMonitor", "FPS trung bình 20 s: ${avg.toInt()}")
        if (avg < LOW_AVG && !warned) {
            warned = true
            onLowFps?.run()
        } else if (avg >= REARM_AVG) {
            warned = false
        }
    }
}
