package com.cyplixlauncher.mc.feature.mod.modloader

import com.endiq.mcgui.ProgressLayout
import com.cyplixlauncher.mc.R
import com.cyplixlauncher.mc.feature.download.item.VersionItem
import com.cyplixlauncher.mc.feature.version.install.InstallTask
import com.cyplixlauncher.mc.utils.path.PathManager
import net.endiq.launcher.Tools
import net.endiq.launcher.progresskeeper.ProgressKeeper
import net.endiq.launcher.utils.DownloadUtils
import java.io.File

class FabricLikeApiModDownloadTask(private val fileName: String, private val versionItem: VersionItem) : InstallTask, Tools.DownloaderFeedback {
    @Throws(Exception::class)
    override fun run(customName: String): File {
        ProgressKeeper.submitProgress(ProgressLayout.INSTALL_RESOURCE, 0, R.string.mod_download_progress, versionItem.fileName)
        val destinationFile = File(PathManager.DIR_CACHE, "$fileName.jar")
        DownloadUtils.downloadFileMonitored(versionItem.fileUrl, destinationFile, ByteArray(8192), this)
        ProgressLayout.clearProgress(ProgressLayout.INSTALL_RESOURCE)
        return destinationFile
    }

    override fun updateProgress(curr: Long, max: Long) {
        val progress100 = ((curr.toFloat() / max.toFloat()) * 100f).toInt()
        ProgressKeeper.submitProgress(ProgressLayout.INSTALL_RESOURCE, progress100, R.string.mod_download_progress, versionItem.fileName)
    }
}