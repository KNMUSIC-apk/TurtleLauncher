package com.cyplixlauncher.mc.feature.theme

import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.drawable.VectorDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.vectordrawable.graphics.drawable.VectorDrawableCompat
import com.cyplixlauncher.mc.setting.AllSettings
import java.util.Locale

/**
 * User-chosen launcher colors (Settings > Launcher > Màu nền / Màu icon).
 * Empty setting means "use the theme default". Colors are stored as "#AARRGGBB".
 */
object LauncherColors {

    fun parse(hex: String?): Int? =
        if (hex.isNullOrBlank()) null else runCatching { Color.parseColor(hex) }.getOrNull()

    fun toHex(color: Int): String = String.format(Locale.ROOT, "#%08X", color)

    /** Paints the chosen background color behind the launcher's content (under any background image). */
    fun applyBackground(root: View) {
        val color = parse(AllSettings.launcherBackgroundColor.getValue())
        if (color != null) root.setBackgroundColor(color) else root.background = null
    }

    /**
     * Tints every vector icon under [root] with the chosen icon color.
     * Bitmap images (avatars, mod logos, backgrounds) are left as they are.
     */
    fun tintIcons(root: View?) {
        if (root == null) return
        val tint = parse(AllSettings.launcherIconColor.getValue())
        visit(root) { iv ->
            val drawable = iv.drawable
            if (drawable is VectorDrawable || drawable is VectorDrawableCompat) {
                if (tint != null) iv.setColorFilter(tint, PorterDuff.Mode.SRC_IN) else iv.clearColorFilter()
            }
        }
    }

    private fun visit(view: View, block: (ImageView) -> Unit) {
        if (view is ImageView) block(view)
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) visit(view.getChildAt(i), block)
        }
    }
}
