package com.cyplixlauncher.mc.ui.dialog

import android.content.Context
import android.graphics.Color
import android.widget.FrameLayout
import androidx.appcompat.app.AlertDialog
import com.cyplixlauncher.mc.R
import com.cyplixlauncher.mc.feature.theme.LauncherColors
import net.endiq.launcher.colorselector.ColorSelector

/**
 * Color chooser built on the existing [ColorSelector] (hue/saturation square, hex field).
 * [onPicked] gets "#AARRGGBB" when saved, or "" when the user picks the theme default.
 */
object ColorPickerDialog {

    fun show(context: Context, title: String, currentHex: String, onPicked: (String?) -> Unit) {
        val density = context.resources.displayMetrics.density
        val container = FrameLayout(context).apply {
            layoutParams = android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                (420 * density).toInt()
            )
        }
        val initial = LauncherColors.parse(currentHex) ?: Color.parseColor("#FF4FB58A")
        var picked = initial

        val selector = ColorSelector(context, container) { color -> picked = color }
        selector.setAlphaEnabled(false)
        selector.show(initial)

        AlertDialog.Builder(context)
            .setTitle(title)
            .setView(container)
            .setPositiveButton(R.string.generic_save) { _, _ -> onPicked(LauncherColors.toHex(picked)) }
            .setNeutralButton(R.string.generic_default) { _, _ -> onPicked("") }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}
