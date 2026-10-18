package com.cyplixlauncher.mc.renderer.renderers

import com.cyplixlauncher.mc.renderer.RendererInterface

/**
 * Mesa Panfrost renderer for Mali GPUs. Restored from commit e9c74e6 with the package
 * renamed to com.endiq. Native id "gallium_panfrost" is handled in jni/egl_bridge.c, and
 * it uses the same libOSMesa_8.so as the Freedreno renderer.
 */
class PanfrostRenderer : RendererInterface {
    companion object {
        const val ID = "gallium_panfrost"
    }

    override fun getRendererId(): String = ID

    override fun getUniqueIdentifier(): String = "9b2808c4-11af-4c72-a9c6-94c940396475"

    override fun getRendererName(): String = "Panfrost (Mali)"

    override fun getRendererEnv(): Lazy<Map<String, String>> = lazy { emptyMap() }

    override fun getDlopenLibrary(): Lazy<List<String>> = lazy { emptyList() }

    override fun getRendererLibrary(): String = "libOSMesa_8.so"
}
