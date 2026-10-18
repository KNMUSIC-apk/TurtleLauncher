package com.cyplixlauncher.mc.setting.unit

import com.cyplixlauncher.mc.setting.Settings.Manager

class BooleanSettingUnit(key: String, defaultValue: Boolean) : AbstractSettingUnit<Boolean>(key, defaultValue) {
    override fun getValue() = Manager.getValue(key, defaultValue) { it.toBoolean() }
}