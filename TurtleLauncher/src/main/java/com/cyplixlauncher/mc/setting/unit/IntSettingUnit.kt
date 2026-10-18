package com.cyplixlauncher.mc.setting.unit

import com.cyplixlauncher.mc.setting.Settings.Manager

class IntSettingUnit(key: String, defaultValue: Int) : AbstractSettingUnit<Int>(key, defaultValue) {
    override fun getValue() = Manager.getValue(key, defaultValue) { it.toIntOrNull() }
}