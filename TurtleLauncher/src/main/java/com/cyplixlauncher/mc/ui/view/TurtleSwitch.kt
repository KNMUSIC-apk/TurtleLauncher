package com.cyplixlauncher.mc.ui.view

import android.content.Context
import android.util.AttributeSet
import android.widget.Switch
import com.cyplixlauncher.mc.ui.view.SwitchBurst

/**
 * Switch that plays a light particle burst when the user flips it.
 * Programmatic changes (setChecked) stay silent so loading a screen does not fire effects.
 */
class TurtleSwitch @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.switchStyle
) : Switch(context, attrs, defStyleAttr) {

    override fun toggle() {
        super.toggle()
        SwitchBurst.play(this, isChecked)
    }
}
