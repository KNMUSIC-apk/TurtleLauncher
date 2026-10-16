package com.cyplixlauncher.mc.ui.fragment.download.addon

import com.cyplixlauncher.mc.R
import com.cyplixlauncher.mc.feature.mod.modloader.FabricLikeUtils

class DownloadQuiltFragment : DownloadFabricLikeFragment(FabricLikeUtils.QUILT_UTILS, R.drawable.ic_quilt) {
    companion object {
        const val TAG: String = "DownloadQuiltFragment"
    }
}