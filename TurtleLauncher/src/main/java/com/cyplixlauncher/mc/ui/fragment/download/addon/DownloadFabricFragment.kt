package com.cyplixlauncher.mc.ui.fragment.download.addon

import com.cyplixlauncher.mc.R
import com.cyplixlauncher.mc.feature.mod.modloader.FabricLikeUtils

class DownloadFabricFragment : DownloadFabricLikeFragment(FabricLikeUtils.FABRIC_UTILS, R.drawable.ic_fabric) {
    companion object {
        const val TAG: String = "DownloadFabricFragment"
    }
}