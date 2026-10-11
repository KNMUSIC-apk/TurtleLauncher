package com.endiq.turtlelauncher.renderer.renderers

import com.endiq.turtlelauncher.renderer.RendererInterface

/**
 * Zink - a Vulkan-backed OpenGL implementation (Mesa's zink gallium driver, via
 * libglxshim.so/libEGL_mesa.so). Library names and the unique identifier are taken directly
 * from FCL-Team/FoldCraftLauncher's own RendererManager.kt (ID_ZINK), not guessed.
 *
 * Requires the device to actually support Vulkan - gate renderer availability on that at
 * the call site (Renderers.getCompatibleRenderers already does this by id).
 */
class ZinkRenderer : RendererInterface {
    companion object {
        const val ID = "ZINK"
    }

    override fun getRendererId(): String = ID

    override fun getNativeRendererId(): String = "vulkan_zink"

    override fun getUniqueIdentifier(): String = "18d93f17-ff53-a319-fa61-58709a77bf87"

    override fun getRendererName(): String = "Vulkan Zink"

    /**
     * Advertise OpenGL 4.6 / GLSL 4.60 to Mesa's zink driver. Newer Minecraft builds (26.x)
     * refuse to start when the GL version is only 2.1/3.x. Values taken from ZalithLauncher's
     * VulkanZinkRenderer (GPL-3.0, https://github.com/ZalithLauncher/ZalithLauncher).
     */
    override fun getRendererEnv(): Lazy<Map<String, String>> = lazy {
        mapOf(
            "MESA_GL_VERSION_OVERRIDE" to "4.6",
            "MESA_GLSL_VERSION_OVERRIDE" to "460"
        )
    }

    override fun getDlopenLibrary(): Lazy<List<String>> = lazy { emptyList() }

    override fun getRendererLibrary(): String = "libglxshim.so"

    override fun getRendererEGL(): String = "libEGL_mesa.so"
}
