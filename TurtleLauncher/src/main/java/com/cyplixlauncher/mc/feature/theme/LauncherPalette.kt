package com.cyplixlauncher.mc.feature.theme

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import java.util.Random

/**
 * Built-in launcher color themes (Dark Nebula, Cyberpunk, Emerald Void).
 * Each one is a dark palette: a base color, a soft glow, and a light decoration layer
 * (stars, hairlines or dust). The decoration is drawn once into a cached drawable, so the
 * background costs no per-frame work.
 */
object LauncherPalette {

    enum class Style { NEBULA, CYBERPUNK, EMERALD }

    class Palette(
        val key: String,
        val bg: Int,
        val accent: Int,
        val glowA: Int,
        val glowB: Int,
        val style: Style
    )

    private val PALETTES = listOf(
        Palette("nebula", 0xFF0B1020.toInt(), 0xFF00C8FF.toInt(), 0xFF5427A8.toInt(), 0xFF00C8FF.toInt(), Style.NEBULA),
        Palette("cyberpunk", 0xFF09090F.toInt(), 0xFF00E5FF.toInt(), 0xFFA855F7.toInt(), 0xFF00E5FF.toInt(), Style.CYBERPUNK),
        Palette("emerald", 0xFF071713.toInt(), 0xFF34D399.toInt(), 0xFF087F6B.toInt(), 0xFF34D399.toInt(), Style.EMERALD)
    )

    /** Accent used by switches and highlights when no palette theme is active. */
    private val DEFAULT_ACCENT = 0xFF4DB6AC.toInt()

    @Volatile
    private var active: Palette? = null
    private var cachedKey: String? = null
    private var cachedDrawable: Drawable? = null

    fun isPaletteKey(key: String?): Boolean = PALETTES.any { it.key == key }

    /** Called whenever the launcher theme setting is applied or loaded. */
    fun setActive(key: String?) {
        active = PALETTES.firstOrNull { it.key == key }
    }

    fun accentOrDefault(): Int = active?.accent ?: DEFAULT_ACCENT

    fun tint(color: Int, alpha: Float): Int = withAlpha(color, alpha)

    fun lighten(color: Int, t: Float): Int = blend(color, 0xFFFFFFFF.toInt(), t)

    /** Background drawable for the active palette theme, or null for the built-in light/dark themes. */
    fun backgroundDrawable(context: Context): Drawable? {
        val p = active ?: return null
        if (cachedKey == p.key) return cachedDrawable

        val metrics = context.resources.displayMetrics
        val longSide = maxOf(metrics.widthPixels, metrics.heightPixels).toFloat()
        val base = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(p.bg, blend(p.bg, p.glowA, 0.14f))
        )
        val glowA = radialGlow(p.glowA, 0.42f, longSide * 0.75f, 0.88f, 0.12f)
        val glowB = radialGlow(p.glowB, 0.26f, longSide * 0.6f, 0.08f, 0.94f)
        val layer = LayerDrawable(arrayOf(base, glowA, glowB, DecorDrawable(p.style, metrics.density)))
        cachedKey = p.key
        cachedDrawable = layer
        return layer
    }

    private fun radialGlow(color: Int, alpha: Float, radiusPx: Float, cx: Float, cy: Float): GradientDrawable =
        GradientDrawable().apply {
            gradientType = GradientDrawable.RADIAL_GRADIENT
            gradientRadius = radiusPx
            setGradientCenter(cx, cy)
            colors = intArrayOf(withAlpha(color, alpha), withAlpha(color, 0f))
        }

    private fun withAlpha(color: Int, alpha: Float): Int {
        val a = (alpha.coerceIn(0f, 1f) * 255).toInt()
        return (color and 0x00FFFFFF) or (a shl 24)
    }

    private fun blend(from: Int, to: Int, t: Float): Int {
        fun ch(shift: Int) = ((from shr shift) and 0xFF) + (((to shr shift) and 0xFF) - ((from shr shift) and 0xFF)) * t
        return Color.argb(255, ch(16).toInt(), ch(8).toInt(), ch(0).toInt())
    }

    /** Static decoration: stars, hairlines or dust, placed with a fixed seed so it looks the same every time. */
    private class DecorDrawable(private val style: Style, private val density: Float) : Drawable() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val random = Random(7)
        private val dots: FloatArray
        private val alphas: IntArray
        private val radii: FloatArray
        private val count: Int

        init {
            count = when (style) {
                Style.NEBULA -> 90
                Style.CYBERPUNK -> 0
                Style.EMERALD -> 120
            }
            dots = FloatArray(count * 2) { random.nextFloat() }
            alphas = IntArray(count) { 40 + random.nextInt(190) }
            radii = FloatArray(count) { 0.4f + random.nextFloat() * if (style == Style.NEBULA) 1.0f else 0.6f }
        }

        override fun draw(canvas: Canvas) {
            val b: Rect = bounds
            val w = b.width().toFloat()
            val h = b.height().toFloat()
            when (style) {
                Style.NEBULA -> {
                    paint.color = Color.WHITE
                    for (i in 0 until count) {
                        paint.alpha = alphas[i]
                        canvas.drawCircle(b.left + dots[i * 2] * w, b.top + dots[i * 2 + 1] * h, radii[i] * density, paint)
                    }
                }
                Style.CYBERPUNK -> {
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = density
                    val lines = floatArrayOf(0.18f, 0.36f, 0.62f, 0.81f, 0.93f)
                    lines.forEachIndexed { i, y ->
                        paint.color = if (i % 2 == 0) 0xFF00E5FF.toInt() else 0xFFA855F7.toInt()
                        paint.alpha = 45 + i * 6
                        canvas.drawLine(b.left.toFloat(), b.top + y * h, b.right.toFloat(), b.top + y * h, paint)
                    }
                    paint.style = Paint.Style.FILL
                }
                Style.EMERALD -> {
                    paint.color = 0xFF34D399.toInt()
                    for (i in 0 until count) {
                        paint.alpha = alphas[i] / 3 + 20
                        canvas.drawCircle(b.left + dots[i * 2] * w, b.top + dots[i * 2 + 1] * h, radii[i] * density, paint)
                    }
                }
            }
        }

        override fun setAlpha(alpha: Int) {}
        override fun setColorFilter(colorFilter: ColorFilter?) {}
        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }
}
