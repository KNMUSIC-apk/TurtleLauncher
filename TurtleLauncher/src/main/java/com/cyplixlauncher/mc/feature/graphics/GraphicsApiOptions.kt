package com.cyplixlauncher.mc.feature.graphics

import com.cyplixlauncher.mc.feature.MCOptions
import com.cyplixlauncher.mc.setting.AllSettings

/**
 * Graphics API for Minecraft 26.2+, written to options.txt as preferredGraphicsBackend.
 * Same scheme as Zalith Launcher 2:
 *  - DEFAULT: leave the game's own choice alone (only written when the game has no value yet)
 *  - DEFAULT_OPENGL: OpenGL, but only when the game has no value yet
 *  - OPENGL / VULKAN: always written
 */
object GraphicsApiOptions {
    private const val KEY = "preferredGraphicsBackend"

    @JvmStatic
    fun apply() {
        val mode = runCatching { AllSettings.graphicsApi.getValue() }.getOrDefault("DEFAULT_OPENGL")
        when (mode) {
            "DEFAULT" -> if (!MCOptions.containsKey(KEY)) MCOptions.set(KEY, "\"default\"")
            "DEFAULT_OPENGL" -> if (!MCOptions.containsKey(KEY)) MCOptions.set(KEY, "\"opengl\"")
            "OPENGL" -> MCOptions.set(KEY, "\"opengl\"")
            "VULKAN" -> MCOptions.set(KEY, "\"vulkan\"")
        }
    }
}
