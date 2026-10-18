package com.cyplixlauncher.mc.feature.theme

import androidx.appcompat.app.AppCompatDelegate
import com.cyplixlauncher.mc.setting.AllSettings
import com.cyplixlauncher.mc.setting.Settings

/**
 * Maps the "Launcher theme" setting to the app night mode and the active palette.
 *  - "light"     -> light palette (white backgrounds)
 *  - "dark"      -> dark palette (black backgrounds)
 *  - "system"    -> follows the system theme
 *  - "nebula", "cyberpunk", "emerald" -> dark palette themes from [LauncherPalette]
 *  - "liquid" -> light frosted-glass palette theme (follows the light base)
 */
object LauncherTheme {

    fun nightModeFor(value: String): Int = when (value) {
        "dark", "nebula", "cyberpunk", "emerald" -> AppCompatDelegate.MODE_NIGHT_YES
        "system" -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        else -> AppCompatDelegate.MODE_NIGHT_NO
    }

    /** Applies the theme saved by the user. Called once at app start. */
    fun applySaved() {
        val value = runCatching {
            Settings.refreshSettings()
            AllSettings.launcherTheme.getValue()
        }.getOrDefault("light")
        LauncherPalette.setActive(value)
        AppCompatDelegate.setDefaultNightMode(nightModeFor(value))
    }

    /**
     * Applies a theme right after the user picks it.
     * @return true when the night mode did not change, so the caller must recreate its activity
     *         itself (AppCompat only recreates on a mode change).
     */
    fun apply(value: String): Boolean {
        LauncherPalette.setActive(value)
        val mode = nightModeFor(value)
        val sameMode = AppCompatDelegate.getDefaultNightMode() == mode
        AppCompatDelegate.setDefaultNightMode(mode)
        return sameMode
    }
}
