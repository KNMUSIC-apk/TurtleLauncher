package com.cyplixlauncher.mc.ui.subassembly.account

import net.endiq.launcher.value.MinecraftAccount

interface SelectAccountListener {
    fun onSelect(account: MinecraftAccount)
}
