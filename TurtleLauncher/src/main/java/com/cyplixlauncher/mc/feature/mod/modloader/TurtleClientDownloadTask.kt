package com.cyplixlauncher.mc.feature.mod.modloader

import com.endiq.mcgui.ProgressLayout
import com.cyplixlauncher.mc.R
import com.cyplixlauncher.mc.feature.download.enums.ModLoader
import com.cyplixlauncher.mc.feature.mod.ModrinthDirectApi
import com.cyplixlauncher.mc.feature.version.install.InstallTask
import com.cyplixlauncher.mc.utils.path.PathManager
import net.endiq.launcher.progresskeeper.ProgressKeeper
import java.io.File
import java.io.IOException

class TurtleClientDownloadTask(private val mcVersion: String) : InstallTask {
    companion object {
        private const val MODRINTH_SLUG = "turtleclient"
    }

    @Throws(Exception::class)
    override fun run(customName: String): File {
        ProgressKeeper.submitProgress(ProgressLayout.INSTALL_RESOURCE, 0, R.string.mod_download_progress, "Turtle Client")

        val versionObj = ModrinthDirectApi.findCompatibleVersion(MODRINTH_SLUG, mcVersion, ModLoader.FABRIC)
            ?: throw IOException("No Turtle Client build is published for Minecraft $mcVersion yet.")
        val primaryFile = ModrinthDirectApi.primaryFileOf(versionObj)
            ?: throw IOException("Turtle Client's Modrinth listing for Minecraft $mcVersion has no downloadable file.")
        val url = primaryFile.get("url")?.takeIf { it.isJsonPrimitive }?.asString
            ?: throw IOException("Turtle Client's Modrinth file entry is missing a download URL.")
        val fileName = primaryFile.get("filename")?.takeIf { it.isJsonPrimitive }?.asString
            ?: "TurtleClient-$mcVersion.jar"

        val destinationFile = File(PathManager.DIR_CACHE, fileName)
        ModrinthDirectApi.downloadTo(url, destinationFile)

        ProgressLayout.clearProgress(ProgressLayout.INSTALL_RESOURCE)
        return destinationFile
    }
}
