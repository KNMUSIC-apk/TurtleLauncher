package com.endiq.turtlelauncher.feature.theme

import androidx.appcompat.app.AppCompatDelegate
import com.endiq.turtlelauncher.setting.AllSettings
import com.endiq.turtlelauncher.setting.Settings

/**
 * Maps the "Launcher theme" setting to the app night mode.
 *  - "light"  -> light palette (white backgrounds)
 *  - "dark"   -> dark palette (black backgrounds)
 *  - "system" -> follows the system theme
 */
object LauncherTheme {

    fun nightModeFor(value: String): Int = when (value) {
        "dark" -> AppCompatDelegate.MODE_NIGHT_YES
        "system" -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        else -> AppCompatDelegate.MODE_NIGHT_NO
    }

    /** Applies the theme saved by the user. Called once at app start. */
    fun applySaved() {
        val value = runCatching {
            Settings.refreshSettings()
            AllSettings.launcherTheme.getValue()
        }.getOrDefault("light")
        AppCompatDelegate.setDefaultNightMode(nightModeFor(value))
    }

    /** Applies a theme right after the user picks it. Activities recreate themselves. */
    fun apply(value: String) {
        AppCompatDelegate.setDefaultNightMode(nightModeFor(value))
    }
}
