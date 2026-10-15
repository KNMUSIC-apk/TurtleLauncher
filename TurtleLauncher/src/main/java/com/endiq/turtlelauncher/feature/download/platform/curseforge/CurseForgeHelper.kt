package com.endiq.turtlelauncher.feature.download.platform.curseforge

import android.widget.Toast
import com.endiq.turtlelauncher.BuildConfig
import com.endiq.turtlelauncher.R
import com.endiq.turtlelauncher.context.ContextExecutor
import com.endiq.turtlelauncher.feature.download.Filters
import com.endiq.turtlelauncher.feature.download.enums.Category
import com.endiq.turtlelauncher.feature.download.enums.Classify
import com.endiq.turtlelauncher.feature.download.enums.Platform
import com.endiq.turtlelauncher.feature.download.enums.VersionType
import com.endiq.turtlelauncher.feature.download.install.InstallHelper
import com.endiq.turtlelauncher.feature.download.install.OnFileDownloadedListener
import com.endiq.turtlelauncher.feature.download.install.UnpackWorldZipHelper
import com.endiq.turtlelauncher.feature.download.item.InfoItem
import com.endiq.turtlelauncher.feature.download.item.ModLoaderWrapper
import com.endiq.turtlelauncher.feature.download.item.ScreenshotItem
import com.endiq.turtlelauncher.feature.download.item.SearchResult
import com.endiq.turtlelauncher.feature.download.item.VersionItem
import com.endiq.turtlelauncher.feature.download.platform.AbstractPlatformHelper
import com.endiq.turtlelauncher.feature.download.platform.PlatformNotSupportedException
import com.endiq.turtlelauncher.feature.log.Logging
import com.endiq.turtlelauncher.setting.AllSettings
import com.endiq.turtlelauncher.utils.ZHTools
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import net.endiq.launcher.modloaders.modpacks.api.ApiHandler
import org.apache.commons.io.FileUtils
import java.io.File
import java.util.Date

private const val CURSEFORGE_API_URL = "https://api.curseforge.com/v1"
private const val CURSEFORGE_MINECRAFT_GAME_ID = 432
private const val CURSEFORGE_MODS_CLASS_ID = 6
private const val CURSEFORGE_RESOURCE_PACKS_CLASS_ID = 12
private const val CURSEFORGE_WORLDS_CLASS_ID = 17
private const val CURSEFORGE_SHADERS_CLASS_ID = 6552
private const val CURSEFORGE_SEARCH_PAGE_SIZE = 20
private const val CURSEFORGE_FILE_PAGE_SIZE = 50
private const val CURSEFORGE_TAG = "CurseForgeHelper"

/** Thrown when no CurseForge API key is available, so the page can tell the person what to do. */
class CurseForgeApiKeyMissingException : RuntimeException("No CurseForge API key is configured")

/**
 * CurseForge source for mods, resource packs, shader packs and worlds. Modpacks stay on Modrinth
 * for now: their install path (ModLoader setup) is not wired for CurseForge yet.
 *
 * CurseForge's v1 API needs a key on every request. The key entered in Settings is used first;
 * the build-time key (CURSEFORGE_API_KEY, from the CI secret) is the fallback.
 */
class CurseForgeHelper : AbstractPlatformHelper(ApiHandler(CURSEFORGE_API_URL)) {
    override fun copy(): AbstractPlatformHelper {
        return CurseForgeHelper()
    }

    override fun getWebUrl(infoItem: InfoItem): String? {
        val section = when (infoItem.classify) {
            Classify.MOD -> "mc-mods"
            Classify.RESOURCE_PACK -> "texture-packs"
            Classify.SHADER_PACK -> "shaders"
            Classify.WORLD -> "worlds"
            else -> return null
        }
        return "https://www.curseforge.com/minecraft/$section/${infoItem.slug}"
    }

    // The world list does not show a screenshot gallery yet.
    override fun getScreenshots(projectId: String): List<ScreenshotItem> {
        return emptyList()
    }

    @Throws(Throwable::class)
    override fun searchWorld(filters: Filters, lastResult: SearchResult): SearchResult? =
        searchByClass(CURSEFORGE_WORLDS_CLASS_ID, Classify.WORLD, filters, lastResult)

    @Throws(Throwable::class)
    override fun searchMod(filters: Filters, lastResult: SearchResult): SearchResult? =
        searchByClass(CURSEFORGE_MODS_CLASS_ID, Classify.MOD, filters, lastResult)

    @Throws(Throwable::class)
    override fun searchResourcePack(filters: Filters, lastResult: SearchResult): SearchResult? =
        searchByClass(CURSEFORGE_RESOURCE_PACKS_CLASS_ID, Classify.RESOURCE_PACK, filters, lastResult)

    @Throws(Throwable::class)
    override fun searchShaderPack(filters: Filters, lastResult: SearchResult): SearchResult? =
        searchByClass(CURSEFORGE_SHADERS_CLASS_ID, Classify.SHADER_PACK, filters, lastResult)

    private fun searchByClass(
        classId: Int,
        classify: Classify,
        filters: Filters,
        lastResult: SearchResult
    ): SearchResult? {
        val params = HashMap<String, Any>()
        params["gameId"] = CURSEFORGE_MINECRAFT_GAME_ID
        params["classId"] = classId
        filters.category.curseforgeID?.let { params["categoryId"] = it }
        if (filters.name.isNotBlank()) params["searchFilter"] = filters.name.trim()
        filters.mcVersion?.let { params["gameVersion"] = it }
        params["sortField"] = filters.sort.curseforge
        params["sortOrder"] = "desc"
        params["index"] = lastResult.previousCount
        params["pageSize"] = CURSEFORGE_SEARCH_PAGE_SIZE

        val response = client().get("mods/search", params, JsonObject::class.java) ?: return null
        val hits = response.arrOrNull("data") ?: return null

        val infoItems = hits.mapNotNull { element -> toInfoItem(element, classify) }
        return lastResult.apply {
            this.infoItems.addAll(infoItems)
            previousCount += hits.size()
            totalResultCount = response.objOrNull("pagination")?.intOrNull("totalCount") ?: 0
            isLastPage = hits.size() < CURSEFORGE_SEARCH_PAGE_SIZE
        }
    }

    @Throws(Throwable::class)
    override fun getWorldVersions(infoItem: InfoItem, force: Boolean): List<VersionItem>? =
        getFileVersions(infoItem)

    @Throws(Throwable::class)
    override fun getModVersions(infoItem: InfoItem, force: Boolean): List<VersionItem>? =
        getFileVersions(infoItem)

    @Throws(Throwable::class)
    override fun getResourcePackVersions(infoItem: InfoItem, force: Boolean): List<VersionItem>? =
        getFileVersions(infoItem)

    @Throws(Throwable::class)
    override fun getShaderPackVersions(infoItem: InfoItem, force: Boolean): List<VersionItem>? =
        getFileVersions(infoItem)

    private fun getFileVersions(infoItem: InfoItem): List<VersionItem>? {
        val params = HashMap<String, Any>()
        params["index"] = 0
        params["pageSize"] = CURSEFORGE_FILE_PAGE_SIZE

        val response = client().get("mods/${infoItem.projectId}/files", params, JsonObject::class.java) ?: return null
        val files = response.arrOrNull("data") ?: return null

        return files.mapNotNull { element ->
            runCatching {
                toVersionItem(infoItem, element.asJsonObject)
            }.getOrElse { e ->
                Logging.e(CURSEFORGE_TAG, "Skipped a CurseForge file that could not be read", e)
                null
            }
        }
    }

    @Throws(Throwable::class)
    override fun installWorld(infoItem: InfoItem, version: VersionItem, targetPath: File, progressKey: String) {
        // The archive is saved beside the other worlds first, then unpacked the same way a
        // world zip picked from local storage is.
        InstallHelper.downloadFile(version, targetPath, progressKey, OnFileDownloadedListener { file ->
            // If the download itself failed, its own error is already reported; nothing to unpack.
            if (!file.isFile) return@OnFileDownloadedListener
            runCatching {
                UnpackWorldZipHelper.unpackFile(file, AbstractPlatformHelper.getWorldPath())
            }.onFailure { e ->
                Logging.e(CURSEFORGE_TAG, "Failed to unpack the CurseForge world", e)
                FileUtils.deleteQuietly(file)
                ContextExecutor.showToast(R.string.download_install_unpack_world_error, Toast.LENGTH_SHORT)
            }
        })
    }

    @Throws(Throwable::class)
    override fun searchModPack(filters: Filters, lastResult: SearchResult): SearchResult? = unsupported()

    @Throws(Throwable::class)
    override fun getModPackVersions(infoItem: InfoItem, force: Boolean): List<VersionItem>? = unsupported()

    @Throws(Throwable::class)
    override fun installMod(infoItem: InfoItem, version: VersionItem, targetPath: File, progressKey: String) {
        InstallHelper.downloadFile(version, targetPath, progressKey)
    }

    @Throws(Throwable::class)
    override fun installModPack(version: VersionItem, customName: String): ModLoaderWrapper? = unsupported()

    @Throws(Throwable::class)
    override fun installResourcePack(infoItem: InfoItem, version: VersionItem, targetPath: File, progressKey: String) {
        InstallHelper.downloadFile(version, targetPath, progressKey)
    }

    @Throws(Throwable::class)
    override fun installShaderPack(infoItem: InfoItem, version: VersionItem, targetPath: File, progressKey: String) {
        InstallHelper.downloadFile(version, targetPath, progressKey)
    }

    private fun unsupported(): Nothing {
        throw PlatformNotSupportedException("CurseForge modpacks are not supported in TurtleLauncher yet.")
    }

    /** A client for one request, so a key changed in Settings applies to the next search. */
    private fun client(): ApiHandler {
        val key = resolveApiKey()
        if (key.isEmpty()) throw CurseForgeApiKeyMissingException()
        return ApiHandler(CURSEFORGE_API_URL, key)
    }

    private fun resolveApiKey(): String {
        val userKey = AllSettings.curseForgeApiKey.getValue().trim()
        if (userKey.isNotEmpty()) return userKey
        return BuildConfig.CURSEFORGE_API_KEY.trim()
    }

    private fun toInfoItem(element: JsonElement, classify: Classify): InfoItem? {
        val mod = element.takeIf { it.isJsonObject }?.asJsonObject ?: return null
        val id = mod.longOrNull("id") ?: return null
        val slug = mod.strOrNull("slug") ?: return null
        val name = mod.strOrNull("name") ?: return null
        val logo = mod.objOrNull("logo")

        return InfoItem(
            classify,
            Platform.CURSEFORGE,
            id.toString(),
            slug,
            mod.arrOrNull("authors")
                ?.mapNotNull { author -> author.takeIf { it.isJsonObject }?.asJsonObject?.strOrNull("name") }
                ?.toTypedArray(),
            name,
            mod.strOrNull("summary").orEmpty(),
            mod.longOrNull("downloadCount") ?: 0L,
            parseDate(mod.strOrNull("dateReleased") ?: mod.strOrNull("dateCreated")),
            logo?.strOrNull("thumbnailUrl") ?: logo?.strOrNull("url"),
            mod.arrOrNull("categories")?.mapNotNull { category -> toCategory(category, classify) } ?: emptyList()
        )
    }

    private fun toCategory(element: JsonElement, classify: Classify): Category? {
        val id = element.takeIf { it.isJsonObject }?.asJsonObject?.longOrNull("id")?.toString() ?: return null
        return Category.entries.firstOrNull { it.classify == classify && it.curseforgeID == id }
    }

    private fun toVersionItem(infoItem: InfoItem, file: JsonObject): VersionItem? {
        // The author can turn off third-party downloads. Without a URL there is nothing to install.
        val downloadUrl = file.strOrNull("downloadUrl")?.takeIf { it.isNotBlank() } ?: return null
        val fileName = file.strOrNull("fileName")?.takeIf { it.isNotBlank() } ?: return null

        return VersionItem(
            infoItem.projectId,
            file.strOrNull("displayName")?.takeIf { it.isNotBlank() } ?: fileName,
            file.longOrNull("downloadCount") ?: 0L,
            parseDate(file.strOrNull("fileDate")),
            file.arrOrNull("gameVersions")
                ?.mapNotNull { version -> version.takeIf { it.isJsonPrimitive }?.asString }
                ?: emptyList(),
            toVersionType(file.intOrNull("releaseType")),
            fileName,
            sha1Of(file),
            downloadUrl
        )
    }

    private fun toVersionType(releaseType: Int?): VersionType {
        // CurseForge: 1 = release, 2 = beta, 3 = alpha.
        return when (releaseType) {
            2 -> VersionType.BETA
            3 -> VersionType.ALPHA
            else -> VersionType.RELEASE
        }
    }

    private fun sha1Of(file: JsonObject): String? {
        return file.arrOrNull("hashes")?.firstNotNullOfOrNull { hash ->
            val obj = hash.takeIf { it.isJsonObject }?.asJsonObject ?: return@firstNotNullOfOrNull null
            // Algorithm 1 is SHA-1 in CurseForge's API.
            if (obj.intOrNull("algo") == 1) obj.strOrNull("value") else null
        }
    }

    private fun parseDate(value: String?): Date {
        if (value.isNullOrBlank()) return Date(0)
        return runCatching { ZHTools.getDate(value) }.getOrDefault(Date(0))
    }
}

private fun JsonObject.objOrNull(key: String): JsonObject? =
    get(key)?.takeIf { it.isJsonObject }?.asJsonObject

private fun JsonObject.arrOrNull(key: String): JsonArray? =
    get(key)?.takeIf { it.isJsonArray }?.asJsonArray

private fun JsonObject.strOrNull(key: String): String? =
    get(key)?.takeIf { it.isJsonPrimitive }?.asString

private fun JsonObject.longOrNull(key: String): Long? =
    get(key)?.takeIf { it.isJsonPrimitive }?.asLong

private fun JsonObject.intOrNull(key: String): Int? =
    get(key)?.takeIf { it.isJsonPrimitive }?.asInt
