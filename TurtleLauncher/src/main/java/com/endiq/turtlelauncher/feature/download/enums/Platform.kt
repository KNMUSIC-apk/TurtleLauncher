package com.endiq.turtlelauncher.feature.download.enums

import com.endiq.turtlelauncher.feature.download.platform.AbstractPlatformHelper
import com.endiq.turtlelauncher.feature.download.platform.curseforge.CurseForgeHelper
import com.endiq.turtlelauncher.feature.download.platform.modrinth.ModrinthHelper

enum class Platform(val pName: String, val helper: AbstractPlatformHelper) {
    MODRINTH("Modrinth", ModrinthHelper()),
    CURSEFORGE("CurseForge", CurseForgeHelper());

    /** Whether this source can search and install the given resource type. */
    fun supports(classify: Classify): Boolean = when (this) {
        MODRINTH -> classify != Classify.WORLD // Modrinth has no world archives
        CURSEFORGE -> classify == Classify.WORLD // CurseForge is used for worlds only
    }

    companion object {
        /** The sources offered in a resource tab's source spinner, in display order. */
        fun forClassify(classify: Classify): List<Platform> {
            return Platform.entries.filter { it.supports(classify) }
        }

        /** The source a resource tab starts on. */
        fun defaultFor(classify: Classify): Platform {
            return forClassify(classify).first()
        }
    }
}
