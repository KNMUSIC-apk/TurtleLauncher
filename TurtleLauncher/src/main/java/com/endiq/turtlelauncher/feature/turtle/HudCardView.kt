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
 * In-game HUD strip, laid out horizontally: FPS | CPU | GPU | RAM | PING.
 * Each cell is shown only when its switch is on in the in-game HUD tab. The whole strip is scaled
 * by the "HUD size" slider. Drawn on a Canvas so an update is one invalidate, not many setText calls.
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

    private enum class Cell { FPS, CPU, GPU, RAM, PING }

    private var showCpu = true
    private var showGpu = true
    private var showRam = true
    private var showFps = true
    private var showPing = true
    private var scale = 1f

    private var snapshot: Snapshot? = null
    private val fpsHistory = FloatArray(HISTORY)
    private var historyCount = 0

    private val density = resources.displayMetrics.density
    private val scaledDensity = resources.displayMetrics.scaledDensity
    private fun dp(v: Float) = v * density
    private fun sp(v: Float) = v * scaledDensity

    private val cardBg = Color.parseColor("#D90B1A12")
    private val cardStroke = Color.parseColor("#33FFFFFF")
    private val divider = Color.parseColor("#26FFFFFF")
    private val textPrimary = Color.parseColor("#F2F7F3")
    private val textMuted = Color.parseColor("#A8B8AE")
    private val ringTrack = Color.parseColor("#2AFFFFFF")
    private val good = Color.parseColor("#4ADE80")
    private val warn = Color.parseColor("#FBBF24")
    private val bad = Color.parseColor("#F87171")
    private val accent = Color.parseColor("#7FD6B0")

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = cardBg; style = Paint.Style.FILL }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = cardStroke; style = Paint.Style.STROKE; strokeWidth = dp(1f)
    }
    private val dividerPaint = Paint().apply { color = divider; strokeWidth = dp(1f) }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = textMuted; textSize = sp(8f); typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        letterSpacing = 0.1f
    }
    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = textPrimary; textSize = sp(12f); typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private val bigPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = textPrimary; textSize = sp(16f); typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = dp(2.5f); strokeCap = Paint.Cap.ROUND
    }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = accent; style = Paint.Style.STROKE; strokeWidth = dp(1.2f)
    }
    private val rect = RectF()
    private val path = Path()

    private val rowHeight = dp(44f)
    private val edgePad = dp(8f)

    /** Which cells are visible. Called when the switches change. */
    fun setRows(cpu: Boolean, gpu: Boolean, ram: Boolean, fps: Boolean, ping: Boolean) {
        showCpu = cpu; showGpu = gpu; showRam = ram; showFps = fps; showPing = ping
        requestLayout()
        invalidate()
    }

    /** Size of the strip as a percentage (50..150). */
    fun setScalePercent(percent: Int) {
        val next = percent.coerceIn(50, 150) / 100f
        if (next != scale) {
            scale = next
            requestLayout()
            invalidate()
        }
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

    fun hasVisibleRows(): Boolean = showCpu || showGpu || showRam || showFps || showPing

    private fun visibleCells(): List<Cell> = buildList {
        if (showFps) add(Cell.FPS)
        if (showCpu) add(Cell.CPU)
        if (showGpu) add(Cell.GPU)
        if (showRam) add(Cell.RAM)
        if (showPing) add(Cell.PING)
    }

    private fun cellWidth(c: Cell): Float = dp(when (c) {
        Cell.FPS -> 64f
        Cell.CPU, Cell.GPU -> 72f
        Cell.RAM -> 80f
        Cell.PING -> 56f
    })

    private fun contentWidth(): Float {
        val cells = visibleCells()
        if (cells.isEmpty()) return 0f
        return cells.sumOf { cellWidth(it).toDouble() }.toFloat() + edgePad * 2
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = if (hasVisibleRows()) contentWidth() * scale else 0f
        val h = if (hasVisibleRows()) (rowHeight + edgePad) * scale else 0f
        setMeasuredDimension(w.toInt(), h.toInt())
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!hasVisibleRows()) return
        canvas.save()
        canvas.scale(scale, scale)

        val h = rowHeight + edgePad
        val w = contentWidth()
        rect.set(0.5f, 0.5f, w - 0.5f, h - 0.5f)
        val radius = dp(12f)
        canvas.drawRoundRect(rect, radius, radius, bgPaint)
        canvas.drawRoundRect(rect, radius, radius, strokePaint)

        var x = edgePad
        val cells = visibleCells()
        cells.forEachIndexed { index, cell ->
            val cw = cellWidth(cell)
            when (cell) {
                Cell.FPS -> drawFps(canvas, x, cw)
                Cell.CPU -> drawTemp(canvas, x, "CPU", snapshot?.cpuTempC)
                Cell.GPU -> drawTemp(canvas, x, "GPU", snapshot?.gpuTempC)
                Cell.RAM -> drawRam(canvas, x)
                Cell.PING -> drawPing(canvas, x)
            }
            x += cw
            if (index < cells.lastIndex) {
                canvas.drawLine(x, dp(8f), x, h - dp(8f), dividerPaint)
            }
        }
        canvas.restore()
    }

    private fun drawFps(canvas: Canvas, x: Float, cw: Float) {
        canvas.drawText("FPS", x + dp(4f), dp(14f), labelPaint)
        canvas.drawText((snapshot?.fps ?: 0).toString(), x + dp(4f), dp(34f), bigPaint)
        drawSparkline(canvas, x + dp(4f), x + cw - dp(6f), dp(30f), dp(40f))
    }

    private fun drawPing(canvas: Canvas, x: Float) {
        canvas.drawText("PING", x + dp(6f), dp(14f), labelPaint)
        val ms = snapshot?.pingMs
        canvas.drawText(ms?.let { "$it ms" } ?: "-- ms", x + dp(6f), dp(34f), valuePaint)
    }

    /** Temperature ring with its value. */
    private fun drawTemp(canvas: Canvas, x: Float, label: String, tempC: Int?) {
        canvas.drawText(label, x + dp(4f), dp(14f), labelPaint)
        val cy = dp(29f)
        val r = dp(8f)
        drawRing(canvas, x + dp(12f) + r, cy, r, tempColor(tempC), tempFraction(tempC))
        canvas.drawText(tempC?.let { "$it°" } ?: "--", x + dp(34f), dp(33f), valuePaint)
    }

    private fun drawRam(canvas: Canvas, x: Float) {
        canvas.drawText("RAM", x + dp(4f), dp(14f), labelPaint)
        val s = snapshot
        val frac = if (s != null && s.ramTotalGb > 0f) (s.ramUsedGb / s.ramTotalGb).coerceIn(0f, 1f) else 0f
        val cy = dp(29f)
        val r = dp(8f)
        drawRing(canvas, x + dp(12f) + r, cy, r, usageColor((frac * 100).toInt()), frac)
        val text = s?.let { String.format("%.1fGB", it.ramUsedGb) } ?: "--"
        canvas.drawText(text, x + dp(34f), dp(33f), valuePaint)
    }

    private fun drawRing(canvas: Canvas, cx: Float, cy: Float, r: Float, color: Int, fraction: Float) {
        rect.set(cx - r, cy - r, cx + r, cy + r)
        ringPaint.color = ringTrack
        canvas.drawArc(rect, 0f, 360f, false, ringPaint)
        ringPaint.color = color
        canvas.drawArc(rect, -90f, 360f * fraction.coerceIn(0f, 1f), false, ringPaint)
    }

    private fun drawSparkline(canvas: Canvas, left: Float, right: Float, top: Float, bottom: Float) {
        if (historyCount < 2 || right <= left) return
        val maxFps = (0 until historyCount).maxOf { fpsHistory[it] }.coerceAtLeast(1f)
        val stepX = (right - left) / (HISTORY - 1)
        path.reset()
        for (i in 0 until historyCount) {
            val x = left + stepX * (HISTORY - historyCount + i)
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

    companion object {
        private const val HISTORY = 40
    }
}
