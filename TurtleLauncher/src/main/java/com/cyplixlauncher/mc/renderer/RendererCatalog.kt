package com.cyplixlauncher.mc.renderer

import com.cyplixlauncher.mc.renderer.renderers.AngleRenderer
import com.cyplixlauncher.mc.renderer.renderers.FreedrenoRenderer
import com.cyplixlauncher.mc.renderer.renderers.HolyGL4ESRenderer
import com.cyplixlauncher.mc.renderer.renderers.KryptonRenderer
import com.cyplixlauncher.mc.renderer.renderers.NWRenderer
import com.cyplixlauncher.mc.renderer.renderers.VGPURenderer
import com.cyplixlauncher.mc.renderer.renderers.Vulkan26Renderer
import com.cyplixlauncher.mc.renderer.renderers.VirGLRenderer
import com.cyplixlauncher.mc.renderer.renderers.ZinkRenderer

object RendererCatalog {
    enum class Badge { RECOMMENDED, STABLE, EXPERIMENTAL }

    data class Entry(
        val minMinecraftVersion: String? = null,
        val maxMinecraftVersion: String? = null,
        val badge: Badge,
        val supportsShaderPacks: Boolean = false
    )

    private val entries: Map<String, Entry> = mapOf(
        HolyGL4ESRenderer.ID to Entry(maxMinecraftVersion = "1.21.4", badge = Badge.STABLE),
        KryptonRenderer.ID to Entry(badge = Badge.EXPERIMENTAL),
        NWRenderer.ID to Entry(badge = Badge.EXPERIMENTAL),
        VirGLRenderer.ID to Entry(badge = Badge.EXPERIMENTAL, supportsShaderPacks = true),
        ZinkRenderer.ID to Entry(badge = Badge.STABLE, supportsShaderPacks = true),
        FreedrenoRenderer.ID to Entry(badge = Badge.EXPERIMENTAL),
        VGPURenderer.ID to Entry(minMinecraftVersion = "1.16.5", badge = Badge.EXPERIMENTAL),
        AngleRenderer.ID to Entry(badge = Badge.EXPERIMENTAL),
        com.cyplixlauncher.mc.renderer.renderers.LTWRenderer.ID to Entry(badge = Badge.EXPERIMENTAL),
        com.cyplixlauncher.mc.renderer.renderers.MobileGluesRenderer.ID to Entry(badge = Badge.EXPERIMENTAL),
        Vulkan26Renderer.ID to Entry(minMinecraftVersion = Vulkan26Renderer.MIN_MC_VERSION, badge = Badge.EXPERIMENTAL)
    )

    fun get(rendererId: String): Entry? = entries[rendererId]
    fun supportsShaderPacks(rendererId: String): Boolean = entries[rendererId]?.supportsShaderPacks ?: false
}
