package com.endiq.turtlelauncher.renderer.renderers

import com.endiq.turtlelauncher.renderer.RendererInterface

/**
 * GL4ES 1.1.5 built-in renderer. Uses the libgl4es_115.so already shipped in jniLibs
 * (no renderer previously referenced it), on the same native "opengles" path as
 * Holy GL4ES 1.1.4. Hidden from the picker automatically if the library is missing.
 */
class GL4ES115Renderer : RendererInterface {
    companion object {
        const val ID = "GL4ES115"
    }

    override fun getRendererId(): String = ID

    override fun getNativeRendererId(): String = "opengles"

    override fun getUniqueIdentifier(): String = "f75f5174-e176-4c1f-a6ff-e9029dad1de0"

    override fun getRendererName(): String = "LTA"

    override fun getRendererEnv(): Lazy<Map<String, String>> = lazy { emptyMap() }

    override fun getDlopenLibrary(): Lazy<List<String>> = lazy { emptyList() }

    override fun getRendererLibrary(): String = "libgl4es_115.so"

    override fun getRendererEGL(): String = "libEGL.so"
}
