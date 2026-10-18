package com.cyplixlauncher.mc.ui.view

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.util.AttributeSet
import android.widget.Switch
import com.cyplixlauncher.mc.feature.theme.LauncherPalette

/**
 * Switch that plays a light particle burst when the user flips it.
 * Programmatic changes (setChecked) stay silent so loading a screen does not fire effects.
 */
class TurtleSwitch @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.switchStyle
) : Switch(context, attrs, defStyleAttr) {

    init {
        applyStyle()
    }

    /**
     * Default look (built-in themes): dark track, teal thumb when on, grey when off.
     * Palette themes get their own switch: see [applyPaletteStyle].
     */
    private fun applyStyle() {
        val palette = LauncherPalette.current()
        if (palette != null) {
            applyPaletteStyle(palette)
            return
        }
        val d = resources.displayMetrics.density
        val trackW = (36 * d).toInt()
        val trackH = (20 * d).toInt()
        val thumb = (24 * d).toInt()

        fun track(color: Int) = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = trackH / 2f
            setColor(color)
            setSize(trackW, trackH)
        }
        fun knob(color: Int) = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
            setSize(thumb, thumb)
        }
        setTrackDrawable(StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_checked), track(0xFF3D5C57.toInt()))
            addState(intArrayOf(), track(0xFF3A3F3E.toInt()))
        })
        setThumbDrawable(StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_checked), knob(0xFF7FCBC4.toInt()))
            addState(intArrayOf(), knob(0xFF8E9694.toInt()))
        })
    }

    /**
     * Switch in the active palette theme's own style:
     *  - Liquid Glass: translucent glass track with a bright edge and a white thumb.
     *  - Cyberpunk: square-cut track with a neon edge.
     *  - Nebula / Emerald: accent-coloured track with a soft edge.
     */
    private fun applyPaletteStyle(p: LauncherPalette.Palette) {
        val d = resources.displayMetrics.density
        val trackW = (36 * d).toInt()
        val trackH = (20 * d).toInt()
        val thumb = (24 * d).toInt()
        val edge = maxOf(1, (d + 0.5f).toInt())
        val radius = if (p.style == LauncherPalette.Style.CYBERPUNK) 2 * d else trackH / 2f
        val thumbOn = if (p.style == LauncherPalette.Style.LIQUID) 0xFFFFFFFF.toInt() else p.iconTint

        fun track(fill: Int, stroke: Int) = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            setColor(fill)
            setStroke(edge, stroke)
            setSize(trackW, trackH)
        }
        fun knob(color: Int) = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
            setSize(thumb, thumb)
        }
        val onFill = (p.accent and 0x00FFFFFF) or (0xB3 shl 24)
        setTrackDrawable(StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_checked), track(onFill, p.frame))
            addState(intArrayOf(), track(0x33808A99, p.frame and 0x00FFFFFF or (0x55 shl 24)))
        })
        setThumbDrawable(StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_checked), knob(thumbOn))
            addState(intArrayOf(), knob(0xFFB0B8C4.toInt()))
        })
    }

    override fun toggle() {
        super.toggle()
        SwitchBurst.play(this, isChecked)
    }
}
