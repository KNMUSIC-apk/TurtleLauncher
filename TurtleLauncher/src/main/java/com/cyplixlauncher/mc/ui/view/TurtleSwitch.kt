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
        applyPaletteStyle()
    }

    /**
     * Same look for every launcher theme: a tinted dark track with a lighter thumb when on,
     * a neutral grey track with a grey thumb when off. The accent follows the active theme.
     */
    private fun applyPaletteStyle() {
        val d = resources.displayMetrics.density
        val accent = LauncherPalette.accentOrDefault()
        val grey = 0xFF9E9E9E.toInt()
        val trackW = (48 * d).toInt()
        val trackH = (28 * d).toInt()
        val thumb = (22 * d).toInt()

        fun track(color: Int) = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 14 * d
            setColor(color)
            setSize(trackW, trackH)
        }
        fun knob(color: Int) = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
            setSize(thumb, thumb)
        }
        setTrackDrawable(StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_checked), track(LauncherPalette.tint(accent, 0.38f)))
            addState(intArrayOf(), track(LauncherPalette.tint(grey, 0.30f)))
        })
        setThumbDrawable(StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_checked), knob(LauncherPalette.lighten(accent, 0.35f)))
            addState(intArrayOf(), knob(grey))
        })
    }

    override fun toggle() {
        super.toggle()
        SwitchBurst.play(this, isChecked)
    }
}
