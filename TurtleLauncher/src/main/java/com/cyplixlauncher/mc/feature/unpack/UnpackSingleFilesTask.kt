package com.cyplixlauncher.mc.feature.unpack

import android.content.Context
import com.cyplixlauncher.mc.feature.log.Logging.e
import com.cyplixlauncher.mc.utils.CopyDefaultFromAssets.Companion.copyFromAssets
import com.cyplixlauncher.mc.utils.path.PathManager
import net.endiq.launcher.Tools

class UnpackSingleFilesTask(val context: Context) : AbstractUnpackTask() {
    override fun isNeedUnpack(): Boolean = true

    override fun run() {
        runCatching {
            copyFromAssets(context)
            Tools.copyAssetFile(context, "resolv.conf", PathManager.DIR_DATA, false)
        }.getOrElse { e("AsyncAssetManager", "Failed to unpack critical components !") }
    }
}