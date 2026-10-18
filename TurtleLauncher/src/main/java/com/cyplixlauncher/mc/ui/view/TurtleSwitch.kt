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
     * Palette themes use the same pill shape in their accent colour: see [applyPaletteStyle].
     */
    private fun applyStyle() {
        val palette = LauncherPalette.current()
        if (palette != null) {
            applyPaletteStyle(palette.accent)
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

    /** Palette themes: a plain pill in the theme's accent when on, a soft neutral when off, white thumb. */
    private fun applyPaletteStyle(accent: Int) {
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
        val onTrack = (accent and 0x00FFFFFF) or (0xE0 shl 24)
        setTrackDrawable(StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_checked), track(onTrack))
            addState(intArrayOf(), track(0x59808A99))
        })
        setThumbDrawable(StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_checked), knob(0xFFFFFFFF.toInt()))
            addState(intArrayOf(), knob(0xFFB0B8C4.toInt()))
        })
    }

    override fun toggle() {
        super.toggle()
        SwitchBurst.play(this, isChecked)
    }
}
