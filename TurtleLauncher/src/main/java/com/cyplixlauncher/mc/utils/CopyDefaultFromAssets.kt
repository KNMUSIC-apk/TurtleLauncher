package com.cyplixlauncher.mc.utils

import android.content.Context
import com.cyplixlauncher.mc.utils.path.PathManager
import net.endiq.launcher.Tools
import java.io.File
import java.io.IOException


class CopyDefaultFromAssets {
    companion object {
        @JvmStatic
        @Throws(IOException::class)
        fun copyFromAssets(context: Context?) {
            // Default control layout.
            if (checkDirectoryEmpty(PathManager.DIR_CTRLMAP_PATH)) {
                Tools.copyAssetFile(context, "default.json", PathManager.DIR_CTRLMAP_PATH, false)
            }
            Tools.copyAssetFile(context, "turtle_control_presets/survival.json", PathManager.DIR_CTRLMAP_PATH, "Survival.json", false)

            // One-time: replace the old bundled defaults on existing installs with the new layout.
            if (!com.cyplixlauncher.mc.setting.AllSettings.ctrlmapDefaultsV2.getValue()) {
                Tools.copyAssetFile(context, "default.json", PathManager.DIR_CTRLMAP_PATH, true)
                Tools.copyAssetFile(context, "turtle_control_presets/survival.json", PathManager.DIR_CTRLMAP_PATH, "Survival.json", true)
                com.cyplixlauncher.mc.setting.AllSettings.ctrlmapDefaultsV2.put(true).save()
            }
        }

        private fun checkDirectoryEmpty(dir: String?): Boolean {
            val controlDir = dir?.let { File(it) }
            val files = controlDir?.listFiles()
            return files?.isEmpty() ?: true
        }
    }
}
