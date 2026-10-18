package com.cyplixlauncher.mc.renderer.renderers

import com.cyplixlauncher.mc.renderer.RendererInterface
import java.io.File

/**
 * Renderer for Minecraft 26.3 and newer.
 *
 * From 26.3 Minecraft runs on its SDL3 render path and uses Vulkan natively, so OpenGL
 * wrappers (GL4ES, Zink, VirGL, ...) have nothing to translate and fail there. This renderer
 * hands the game the system Vulkan loader instead: the native side gets the "vulkan_zink"
 * id it already recognises (an unknown id would crash the native code), and
 * [com.cyplixlauncher.mc.feature.graphics.GraphicsApiOptions] writes
 * preferredGraphicsBackend="vulkan" to options.txt while this renderer is selected.
 *
 * Needs a device with Vulkan support; the picker hides it otherwise (see Renderers).
 */
class Vulkan26Renderer : RendererInterface {
    companion object {
        const val ID = "VULKAN26"
        /** First Minecraft version this renderer is meant for. */
        const val MIN_MC_VERSION = "26.3"

        private val SYSTEM_VULKAN_CANDIDATES = listOf(
            "/system/lib64/libvulkan.so",
            "/system/lib/libvulkan.so"
        )

        /** The system Vulkan loader, or the 64-bit path when neither is present (the picker then hides this renderer). */
        fun vulkanLibraryPath(): String =
            SYSTEM_VULKAN_CANDIDATES.firstOrNull { File(it).exists() } ?: SYSTEM_VULKAN_CANDIDATES.first()
    }

    override fun getRendererId(): String = ID

    override fun getNativeRendererId(): String = "vulkan_zink"

    override fun getUniqueIdentifier(): String = "26300a26-3c1d-4f7e-9b52-0a7d26e3c263"

    override fun getRendererName(): String = "Vulkan 26.3"

    override fun getRendererEnv(): Lazy<Map<String, String>> = lazy {
        mapOf("SDL_VULKAN_LIBRARY" to vulkanLibraryPath())
    }

    override fun getDlopenLibrary(): Lazy<List<String>> = lazy { emptyList() }

    override fun getRendererLibrary(): String = vulkanLibraryPath()

    override fun getRendererEGL(): String? = null
}
