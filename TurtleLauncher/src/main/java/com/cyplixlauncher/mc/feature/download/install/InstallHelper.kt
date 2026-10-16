package com.cyplixlauncher.mc.feature.download.install

import com.endiq.mcgui.ProgressLayout
import com.cyplixlauncher.mc.R
import com.cyplixlauncher.mc.event.value.DownloadProgressKeyEvent
import com.cyplixlauncher.mc.feature.download.item.ModLoaderWrapper
import com.cyplixlauncher.mc.feature.download.item.VersionItem
import com.cyplixlauncher.mc.feature.log.Logging
import com.cyplixlauncher.mc.feature.version.VersionsManager
import com.cyplixlauncher.mc.task.Task
import com.cyplixlauncher.mc.utils.path.PathManager
import net.endiq.launcher.progresskeeper.DownloaderProgressWrapper
import net.endiq.launcher.utils.DownloadUtils
import org.apache.commons.io.FileUtils
import org.greenrobot.eventbus.EventBus
import java.io.File
import java.io.IOException

class InstallHelper {
    companion object {
        @Throws(Throwable::class)
        fun downloadFile(version: VersionItem, targetFile: File, progressKey: String) {
            downloadFile(version, targetFile, progressKey, null)
        }

        @Throws(Throwable::class)
        fun downloadFile(
            version: VersionItem,
            targetFile: File,
            progressKey: String,
            listener: OnFileDownloadedListener?
        ) {
            Task.runTask {
                EventBus.getDefault().post(DownloadProgressKeyEvent(progressKey, true))
                try {
                    val downloadBuffer = ByteArray(8192)
                    DownloadUtils.ensureSha1<Void?>(targetFile, version.fileHash) {
                        Logging.i(
                            "InstallHelper",
                            "Download Url: ${version.fileUrl}"
                        )
                        DownloadUtils.downloadFileMonitored(
                            version.fileUrl, targetFile, downloadBuffer,
                            DownloaderProgressWrapper(R.string.download_install_download_file, progressKey)
                        )
                        null
                    }
                } finally {
                    listener?.onEnded(targetFile)
                    ProgressLayout.clearProgress(progressKey)
                    EventBus.getDefault().post(DownloadProgressKeyEvent(progressKey, false))
                }
            }.execute()
        }

        @Throws(IOException::class)
        fun installModPack(
            version: VersionItem,
            customName: String,
            installFunction: ModPackInstallFunction
        ): ModLoaderWrapper? {
            val modpackFile = File(
                PathManager.DIR_CACHE, "$customName.cf"
            ) // Cache File

            val modLoaderInfo: ModLoaderWrapper?
            try {
                val downloadBuffer = ByteArray(8192)
                DownloadUtils.ensureSha1<Void?>(
                    modpackFile, version.fileHash
                ) {
                    Logging.i("InstallHelper", "Download Url: ${version.fileUrl}")
                    DownloadUtils.downloadFileMonitored(
                        version.fileUrl, modpackFile, downloadBuffer,
                        DownloaderProgressWrapper(
                            R.string.modpack_download_downloading_metadata,
                            ProgressLayout.INSTALL_RESOURCE
                        )
                    )
                    null
                }

                // Install the modpack
                modLoaderInfo = installFunction.install(modpackFile, VersionsManager.getVersionPath(customName))
            } finally {
                FileUtils.deleteQuietly(modpackFile)
                ProgressLayout.clearProgress(ProgressLayout.INSTALL_RESOURCE)
            }
            modLoaderInfo ?: return null
            Logging.i("InstallHelper", "ModLoader is ${modLoaderInfo.nameById}")

            return modLoaderInfo
        }
    }
}