package com.endiq.turtlelauncher.feature.turtle

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View

/**
 * In-game HUD card: FPS with ping on top and a sparkline, then CPU, GPU and RAM rows with rings.
 * Drawn on a Canvas (no child views) so an update costs one invalidate instead of many setText calls.
 * Each row is shown or hidden by its own switch in Settings > HUD.
 */
class HudCardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    /** One frame of numbers. Null means "not available on this device" and is drawn as "--". */
    data class Snapshot(
        val fps: Int,
        val pingMs: Int?,
        val cpuPercent: Int?,
        val cpuTempC: Int?,
        val gpuPercent: Int?,
        val gpuTempC: Int?,
        val ramUsedGb: Float,
        val ramTotalGb: Float
    )

    private var showCpu = true
    private var showGpu = true
    private var showRam = true
    private var showFps = true
    private var showPing = true

    private var snapshot: Snapshot? = null
    private val fpsHistory = FloatArray(HISTORY) { 0f }
    private var historyCount = 0

    private val density = resources.displayMetrics.density
    private fun dp(v: Float) = v * density

    private val cardBg = Color.parseColor("#D9080A12")
    private val cardStroke = Color.parseColor("#1FFFFFFF")
    private val textPrimary = Color.parseColor("#F2F5F0")
    private val textMuted = Color.parseColor("#A8B0A6")
    private val ringTrack = Color.parseColor("#26FFFFFF")
    private val good = Color.parseColor("#22C55E")
    private val warn = Color.parseColor("#FBBF24")
    private val bad = Color.parseColor("#EF4444")
    private val accent = Color.parseColor("#A78BFA")

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = cardBg; style = Paint.Style.FILL }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = cardStroke; style = Paint.Style.STROKE; strokeWidth = dp(1f)
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = textMuted; textSize = sp(10f); typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        letterSpacing = 0.12f
    }
    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = textPrimary; textSize = sp(13f); typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private val bigPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = textPrimary; textSize = sp(24f); typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private val smallPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = textMuted; textSize = sp(10f) }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = dp(3f); strokeCap = Paint.Cap.ROUND
    }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = accent; style = Paint.Style.STROKE; strokeWidth = dp(1.5f)
    }
    private val dividerPaint = Paint().apply { color = cardStroke; strokeWidth = dp(1f) }
    private val rect = RectF()
    private val path = Path()

    private val cardWidth = dp(184f)
    private val pad = dp(14f)

    /** Which rows are visible. Called when the switches change. */
    fun setRows(cpu: Boolean, gpu: Boolean, ram: Boolean, fps: Boolean, ping: Boolean) {
        showCpu = cpu; showGpu = gpu; showRam = ram; showFps = fps; showPing = ping
        requestLayout()
        invalidate()
    }

    /** Push a new frame of numbers. */
    fun update(next: Snapshot) {
        snapshot = next
        if (historyCount < HISTORY) {
            fpsHistory[historyCount++] = next.fps.toFloat()
        } else {
            System.arraycopy(fpsHistory, 1, fpsHistory, 0, HISTORY - 1)
            fpsHistory[HISTORY - 1] = next.fps.toFloat()
        }
        invalidate()
    }

    /** Whether any row is enabled; the wrapper hides the whole card when none are. */
    fun hasVisibleRows(): Boolean = showCpu || showGpu || showRam || showFps || showPing

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(cardWidth.toInt(), measureHeight().toInt())
    }

    private fun measureHeight(): Float {
        var h = pad * 2
        if (showFps) h += dp(58f)
        if (showCpu) h += dp(48f)
        if (showGpu) h += dp(48f)
        if (showRam) h += dp(48f)
        if (!hasVisibleRows()) h = 0f
        return h
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val h = measureHeight()
        if (h <= 0f) return
        rect.set(0.5f, 0.5f, width - 0.5f, h - 0.5f)
        val radius = dp(14f)
        canvas.drawRoundRect(rect, radius, radius, bgPaint)
        canvas.drawRoundRect(rect, radius, radius, strokePaint)

        val s = snapshot
        var y = pad
        val left = pad
        val right = width - pad

        if (showFps) {
            canvas.drawText("FPS", left, y + dp(10f), labelPaint)
            val fpsText = (s?.fps ?: 0).toString()
            canvas.drawText(fpsText, left, y + dp(40f), bigPaint)
            if (showPing) {
                val pingText = s?.pingMs?.let { "$it ms" } ?: "-- ms"
                val w = smallPaint.measureText(pingText)
                canvas.drawText(pingText, right - w, y + dp(10f), smallPaint)
            }
            drawSparkline(canvas, left, right, y + dp(44f), y + dp(56f))
            y += dp(58f)
            if (showCpu || showGpu || showRam) canvas.drawLine(left, y - dp(1f), right, y - dp(1f), dividerPaint)
        }

        if (showCpu) {
            drawLabelRow(canvas, "CPU", y)
            val temp = s?.cpuTempC
            drawRing(canvas, left + dp(64f), y + dp(24f), tempColor(temp), tempFraction(temp))
            canvas.drawText(temp?.let { "$it°C" } ?: "--", left + dp(84f), y + dp(29f), valuePaint)
            drawRing(canvas, right - dp(44f), y + dp(24f), usageColor(s?.cpuPercent), (s?.cpuPercent ?: 0) / 100f)
            canvas.drawText(s?.cpuPercent?.let { "$it%" } ?: "--", right - dp(24f), y + dp(29f), valuePaint)
            y += dp(48f)
        }

        if (showGpu) {
            drawLabelRow(canvas, "GPU", y)
            val temp = s?.gpuTempC
            drawRing(canvas, left + dp(64f), y + dp(24f), tempColor(temp), tempFraction(temp))
            canvas.drawText(temp?.let { "$it°C" } ?: "--", left + dp(84f), y + dp(29f), valuePaint)
            drawRing(canvas, right - dp(44f), y + dp(24f), usageColor(s?.gpuPercent), (s?.gpuPercent ?: 0) / 100f)
            canvas.drawText(s?.gpuPercent?.let { "$it%" } ?: "--", right - dp(24f), y + dp(29f), valuePaint)
            y += dp(48f)
        }

        if (showRam) {
            drawLabelRow(canvas, "RAM", y)
            val frac = if (s != null && s.ramTotalGb > 0f) (s.ramUsedGb / s.ramTotalGb).coerceIn(0f, 1f) else 0f
            drawRing(canvas, left + dp(64f), y + dp(24f), usageColor((frac * 100).toInt()), frac)
            val text = s?.let { String.format("%.1fGB", it.ramUsedGb) } ?: "--"
            canvas.drawText(text, left + dp(84f), y + dp(29f), valuePaint)
        }
    }

    private fun drawLabelRow(canvas: Canvas, label: String, top: Float) {
        canvas.drawText(label, pad, top + dp(28f), labelPaint)
    }

    private fun drawRing(canvas: Canvas, cx: Float, cy: Float, color: Int, fraction: Float) {
        val r = dp(11f)
        rect.set(cx - r, cy - r, cx + r, cy + r)
        ringPaint.color = ringTrack
        canvas.drawArc(rect, 0f, 360f, false, ringPaint)
        ringPaint.color = color
        canvas.drawArc(rect, -90f, 360f * fraction.coerceIn(0f, 1f), false, ringPaint)
    }

    private fun drawSparkline(canvas: Canvas, left: Float, right: Float, top: Float, bottom: Float) {
        if (historyCount < 2) return
        val maxFps = (0 until historyCount).maxOf { fpsHistory[it] }.coerceAtLeast(1f)
        val n = historyCount
        val stepX = (right - left) / (HISTORY - 1)
        path.reset()
        for (i in 0 until n) {
            val x = left + stepX * (HISTORY - n + i)
            val y = bottom - (bottom - top) * (fpsHistory[i] / maxFps)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        canvas.drawPath(path, linePaint)
    }

    private fun tempColor(c: Int?): Int = when {
        c == null -> textMuted
        c >= 80 -> bad
        c >= 70 -> warn
        else -> good
    }

    private fun tempFraction(c: Int?): Float = if (c == null) 0f else ((c - 30) / 70f).coerceIn(0f, 1f)

    private fun usageColor(p: Int?): Int = when {
        p == null -> textMuted
        p >= 85 -> bad
        p >= 65 -> warn
        else -> accent
    }

    private fun sp(v: Float) = v * resources.displayMetrics.scaledDensity

    companion object {
        private const val HISTORY = 40
    }
}
