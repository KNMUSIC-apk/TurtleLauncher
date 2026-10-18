package com.cyplixlauncher.mc.renderer.renderers

import com.cyplixlauncher.mc.renderer.RendererInterface

/**
 * Krypton Wrapper (NG GL4ES) built-in renderer, backed by libng_gl4es.so which is already
 * shipped in jniLibs. Native id "opengles3" and the env values follow ZalithLauncher2's
 * NGGL4ESRenderer (GPL-3.0, https://github.com/ZalithLauncher/ZalithLauncher2). It sets
 * LIBGL_GL=31 so the wrapper targets OpenGL 3.1. Hidden from the picker if the library is missing.
 */
class KryptonRenderer : RendererInterface {
    companion object {
        const val ID = "KRYPTON"
    }

    override fun getRendererId(): String = ID

    override fun getNativeRendererId(): String = "opengles3"

    override fun getUniqueIdentifier(): String = "e7b90ed6-e518-4d4e-93dc-5c7133cd5b31"

    override fun getRendererName(): String = "Krypton Wrapper"

    override fun getRendererEnv(): Lazy<Map<String, String>> = lazy {
        mapOf(
            "LIBGL_USE_MC_COLOR" to "1",
            "LIBGL_GL" to "31",
            "LIBGL_ES" to "3",
            "LIBGL_NORMALIZE" to "1",
            "LIBGL_NOERROR" to "1"
        )
    }

    override fun getDlopenLibrary(): Lazy<List<String>> = lazy { emptyList() }

    override fun getRendererLibrary(): String = "libng_gl4es.so"

    override fun getRendererEGL(): String = "libEGL.so"
}
