package com.cyplixlauncher.mc.setting.unit

import com.cyplixlauncher.mc.setting.Settings.Manager

class LongSettingUnit(key: String, defaultValue: Long) : AbstractSettingUnit<Long>(key, defaultValue) {
    override fun getValue() = Manager.getValue(key, defaultValue) { it.toLongOrNull() }
}