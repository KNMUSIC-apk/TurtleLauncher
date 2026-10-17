package com.cyplixlauncher.mc.ui.view

import android.content.Context
import android.graphics.Color
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
     * Material-style switch: filled accent track with a white thumb when on,
     * outlined grey track with a grey thumb when off. Colors follow the active launcher theme.
     */
    private fun applyPaletteStyle() {
        val d = resources.displayMetrics.density
        val accent = LauncherPalette.accentOrDefault()
        val off = 0xFF8A9A92.toInt()
        val trackW = (48 * d).toInt()
        val trackH = (28 * d).toInt()
        val thumb = (22 * d).toInt()

        val trackOn = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 14 * d
            setColor(accent)
            setSize(trackW, trackH)
        }
        val trackOff = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 14 * d
            setColor(Color.TRANSPARENT)
            setStroke((2 * d).toInt(), off)
            setSize(trackW, trackH)
        }
        val thumbOn = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.WHITE)
            setSize(thumb, thumb)
        }
        val thumbOff = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(off)
            setSize(thumb, thumb)
        }
        setTrackDrawable(StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_checked), trackOn)
            addState(intArrayOf(), trackOff)
        })
        setThumbDrawable(StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_checked), thumbOn)
            addState(intArrayOf(), thumbOff)
        })
    }

    override fun toggle() {
        super.toggle()
        SwitchBurst.play(this, isChecked)
    }
}
