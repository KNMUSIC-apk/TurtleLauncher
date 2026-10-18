package com.cyplixlauncher.mc.feature.download

import androidx.lifecycle.ViewModel
import com.cyplixlauncher.mc.feature.download.item.InfoItem
import com.cyplixlauncher.mc.feature.download.platform.AbstractPlatformHelper


class InfoViewModel : ViewModel() {
    var platformHelper: AbstractPlatformHelper? = null
    var infoItem: InfoItem? = null
}