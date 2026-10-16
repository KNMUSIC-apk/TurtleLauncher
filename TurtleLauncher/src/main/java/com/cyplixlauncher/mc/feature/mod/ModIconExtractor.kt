package com.cyplixlauncher.mc.feature.mod

import android.content.Context
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import com.moandjiezana.toml.Toml
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipFile

/**
 * Finds a mod's logo inside its jar and caches it as a PNG under the app cache dir, so the mod
 * list can show it like other launchers do. Reads fabric.mod.json, quilt.mod.json and
 * mods.toml / neoforge.mods.toml. Mods with no logo get a ".none" marker so the jar is not
 * opened again on every refresh.
 */
object ModIconExtractor {
    private const val CACHE_DIR = "mod_icons"

    /** The cached logo for this jar, or null if the mod has none or it could not be read. */
    @JvmStatic
    fun iconFileFor(context: Context, jar: File): File? {
        if (!jar.isFile) return null
        val dir = File(context.cacheDir, CACHE_DIR).apply { mkdirs() }
        val key = sha1("${jar.absolutePath}|${jar.length()}|${jar.lastModified()}")
        val png = File(dir, "$key.png")
        if (png.isFile && png.length() > 0) return png
        val none = File(dir, "$key.none")
        if (none.exists()) return null

        val ok = runCatching { extract(jar, png) }.getOrDefault(false)
        if (ok) return png
        png.delete()
        runCatching { none.createNewFile() }
        return null
    }

    private fun extract(jar: File, target: File): Boolean {
        ZipFile(jar).use { zip ->
            val iconPath = findIconPath(zip) ?: return false
            val entry = zip.getEntry(iconPath.trimStart('/')) ?: return false
            zip.getInputStream(entry).use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
            return target.length() > 0
        }
    }

    private fun findIconPath(zip: ZipFile): String? {
        zip.getEntry("fabric.mod.json")?.let { return fabricIcon(read(zip, it)) }
        zip.getEntry("quilt.mod.json")?.let { return quiltIcon(read(zip, it)) }
        zip.getEntry("META-INF/neoforge.mods.toml")?.let { return tomlLogo(read(zip, it)) }
        zip.getEntry("META-INF/mods.toml")?.let { return tomlLogo(read(zip, it)) }
        return null
    }

    private fun read(zip: ZipFile, entry: java.util.zip.ZipEntry): String {
        return zip.getInputStream(entry).bufferedReader().use { it.readText() }
    }

    private fun fabricIcon(json: String): String? {
        val obj = JsonParser.parseString(json).takeIf { it.isJsonObject }?.asJsonObject ?: return null
        return pickIcon(obj.get("icon"))
    }

    private fun quiltIcon(json: String): String? {
        val obj = JsonParser.parseString(json).takeIf { it.isJsonObject }?.asJsonObject ?: return null
        val metadata = obj.getAsJsonObject("quilt_loader")?.getAsJsonObject("metadata") ?: return null
        return pickIcon(metadata.get("icon"))
    }

    /** An icon is either one path, or a map of size -> path. The largest size wins. */
    private fun pickIcon(element: JsonElement?): String? {
        if (element == null || element.isJsonNull) return null
        if (element.isJsonPrimitive) return element.asString
        if (element.isJsonObject) {
            return element.asJsonObject.entrySet()
                .maxByOrNull { it.key.toIntOrNull() ?: 0 }
                ?.value?.takeIf { it.isJsonPrimitive }?.asString
        }
        return null
    }

    private fun tomlLogo(text: String): String? {
        return runCatching { Toml().read(text).getString("logoFile") }.getOrNull()
    }

    private fun sha1(text: String): String {
        return MessageDigest.getInstance("SHA-1").digest(text.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }
}
